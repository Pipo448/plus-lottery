package com.plusgroup.pos.printer

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.plusgroup.pos.util.TenantBranding

/**
 * Kouch inifye ki kache diferans ant enprimant SUNMI (entegre) ak
 * enprimant Bluetooth eksitèn. Rès app la rele SÈLMAN PrinterManager,
 * li pa bezwen konnen ki kalite enprimant ki reyèlman itilize.
 *
 * Deteksyon:
 *   - Si aparèy la se yon SUNMI (Build.MANUFACTURER/BRAND genyen "sunmi"),
 *     itilize sèvis entegre SUNMI a.
 *   - Sinon, itilize enprimant Bluetooth eksitèn (itilizatè a chwazi l
 *     yon sèl fwa nan Paramèt app la).
 */
class PrinterManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("plus_pos_printer", Context.MODE_PRIVATE)

    private val isSunmiDevice: Boolean = detectSunmi()

    // Koneksyon enprimant lan PATAJE ant tout ekran yo e li rete louvri:
    // fich la enprime touswit, san tann yon nouvo koneksyon chak fwa.
    companion object {
        @Volatile private var sharedSunmi: SunmiPrinterHelper? = null
        private val sharedBluetooth = BluetoothPrinterHelper()
        private val btLock = Any()
        // Resi Bluetooth yo enprime nan lòd yo te voye a, youn apre lòt
        private val btQueue = java.util.concurrent.Executors.newSingleThreadExecutor()
        private const val KEY_PRINTER_MAC = "printer_mac"
    }

    private val sunmiHelper: SunmiPrinterHelper?
        get() = sharedSunmi
    private val bluetoothHelper: BluetoothPrinterHelper
        get() = sharedBluetooth

    /** Enprime Bluetooth sou yon lòt thread (ekran an pa bloke), resi a an yon sèl kou. */
    private fun runBluetooth(block: () -> Unit) {
        btQueue.execute {
            synchronized(btLock) {
                if (!bluetoothHelper.isConnected) {
                    getSavedPrinterAddress()?.let { bluetoothHelper.connectByAddress(it) }
                }
                bluetoothHelper.batched(block)
            }
        }
    }

    val printerType: PrinterType
        get() = if (isSunmiDevice) PrinterType.SUNMI_INTEGRATED else PrinterType.BLUETOOTH_EXTERNAL

    // Dènye mesaj erè Bluetooth la — pèmèt MainActivity montre l dirèkteman
    // sou ekran an (Toast), san bezwen Logcat/kab USB.
    val lastPrinterError: String?
        get() = bluetoothHelper.lastError
            ?: if (getSavedPrinterAddress() == null) {
                "Pa gen okenn enprimant chwazi anndan app la. Klike 'Chwazi enprimant Bluetooth' pou chwazi l, menm si li deja pè nan Paramèt Android."
            } else null

    fun connect(onReady: (() -> Unit)? = null) {
        if (isSunmiDevice) {
            val helper = sharedSunmi ?: synchronized(btLock) {
                sharedSunmi ?: SunmiPrinterHelper(context.applicationContext).also { sharedSunmi = it }
            }
            helper.connect(onReady)
        } else {
            // Deja konekte → pa re-konekte (sa te pran plizyè segonn chak fich)
            if (bluetoothHelper.isConnected) {
                onReady?.invoke()
                return
            }
            // Konekte ak dènye enprimant itilizatè a te chwazi a (si genyen).
            // Si pa gen okenn anrejistre, `isReady()` ap fo.
            val savedAddress = getSavedPrinterAddress()
            if (savedAddress != null) {
                Thread {
                    synchronized(btLock) {
                        if (!bluetoothHelper.isConnected) bluetoothHelper.connectByAddress(savedAddress)
                    }
                    onReady?.invoke()
                }.start()
            } else {
                // ENPÒTAN: si pa gen okenn enprimant sove, rele callback la
                // TOUJOU — sinon ekran an rete "ap konekte..." pou tout tan
                // san rezon, paske pèsonn pa t janm chwazi yon enprimant
                // anndan app la (menm si l pè nan Paramèt Android).
                onReady?.invoke()
            }
        }
    }

    /**
     * Koneksyon an pataje e li rete louvri pou pwochen fich la soti touswit.
     * (Ansyen ekran yo ki rele disconnect() nan onDestroy pa koupe l ankò.)
     */
    fun disconnect() {
        // pa fè anyen ekspre
    }

    /** Fèmen koneksyon an toutbon (egz. lè ajan an chwazi yon lòt enprimant). */
    fun releaseConnection() {
        sharedSunmi?.disconnect()
        sharedSunmi = null
        synchronized(btLock) { bluetoothHelper.disconnect() }
    }

    fun isReady(): Boolean {
        return if (isSunmiDevice) sunmiHelper?.isConnected == true
        else bluetoothHelper.isConnected
    }

    fun printTestReceipt() {
        if (isSunmiDevice) sunmiHelper?.printTestReceipt()
        else runBluetooth { bluetoothHelper.printTestReceipt() }
    }

    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        if (isSunmiDevice) {
            sunmiHelper?.printTicketReceipt(companyName, drawName, ticketNumber, numbers, betAmount, footerMessage)
        } else {
            runBluetooth { bluetoothHelper.printTicketReceipt(companyName, drawName, ticketNumber, numbers, betAmount, footerMessage) }
        }
    }

    /**
     * Enprime Fich la nan fòma resi bolèt konplè (ak QR).
     * Sipòte tou de kalite enprimant: SUNMI entegre ak Bluetooth eksitèn.
     */
    fun printFicheReceipt(
        companyName: String,
        promoLine: String,
        phone: String,
        vendeur: String,
        dateTimeText: String,
        ficheNumber: String,
        drawName: String,
        drawTotal: String,
        lines: List<Triple<String, String, String>>,
        grandTotal: String,
        footerMessage: String,
        qrData: String?,
        sections: List<ReceiptSection>? = null,
    ) {
        // "·" pa nan codepage enprimant yo (li te parèt kòm 路): ranplase l
        val vendeur = vendeur.replace("·", "-")
        // Logo tenant lan anlè fich la (si l genyen youn e si "Enprime logo a" aktif)
        val logo = try { TenantBranding.logoForPrint(context) } catch (_: Exception) { null }
        if (isSunmiDevice) {
            sunmiHelper?.printFicheReceipt(
                companyName, promoLine, phone, vendeur, dateTimeText, ficheNumber,
                drawName, drawTotal, lines, grandTotal, footerMessage, qrData, logo, sections,
            )
        } else {
            runBluetooth {
                bluetoothHelper.printFicheReceipt(
                    companyName, promoLine, phone, vendeur, dateTimeText, ficheNumber,
                    drawName, drawTotal, lines, grandTotal, footerMessage, qrData, logo, sections,
                )
            }
        }
    }

    /**
     * Enprime yon RAPÒ jenerik (Rapport partiel, Fin tirage, Fiche
     * gagnant, Transactions, Fich elimine) — antèt konpayi, kèk liy
     * kontèks (Date/Vendeur/Pos...), yon seksyon chan "Etikèt: Valè",
     * ak yon dezyèm seksyon opsyonèl (`footerFields`, egzanp
     * Depot/Retrait/Balance).
     */
    fun printReportReceipt(
        companyName: String,
        branchCode: String,
        phone: String,
        reportTitle: String,
        headerLines: List<Pair<String, String>>,
        bodyFields: List<Pair<String, String>>,
        footerFields: List<Pair<String, String>> = emptyList(),
        agent: String = "",
    ) {
        val agent = agent.replace("·", "-")
        // Menm logo ak fich yo (Mon compte → Paramètres)
        val logo = try { TenantBranding.logoForPrint(context) } catch (_: Exception) { null }
        if (isSunmiDevice) {
            sunmiHelper?.printReportReceipt(
                companyName, branchCode, phone, reportTitle, headerLines, bodyFields, footerFields, agent, logo,
            )
        } else {
            runBluetooth {
                bluetoothHelper.printReportReceipt(
                    companyName, branchCode, phone, reportTitle, headerLines, bodyFields, footerFields, agent, logo,
                )
            }
        }
    }

    // --- Jesyon chwa enprimant Bluetooth (sèlman itilize si pa SUNMI) ---

    fun getPairedBluetoothPrinters(): List<BluetoothDevice> = bluetoothHelper.getPairedDevices()

    fun selectBluetoothPrinter(device: BluetoothDevice): Boolean {
        val macAddress = device.address
        prefs.edit().putString(KEY_PRINTER_MAC, macAddress).apply()
        return bluetoothHelper.connect(device)
    }

    fun getSavedPrinterAddress(): String? = prefs.getString(KEY_PRINTER_MAC, null)

    fun hasConfiguredPrinter(): Boolean = isSunmiDevice || getSavedPrinterAddress() != null

    private fun detectSunmi(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        return manufacturer.contains("sunmi") || brand.contains("sunmi")
    }

    enum class PrinterType {
        SUNMI_INTEGRATED,
        BLUETOOTH_EXTERNAL,
    }

}