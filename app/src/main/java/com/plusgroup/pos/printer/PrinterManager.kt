package com.plusgroup.pos.printer

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

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

    private var sunmiHelper: SunmiPrinterHelper? = null
    private val bluetoothHelper = BluetoothPrinterHelper()

    val printerType: PrinterType
        get() = if (isSunmiDevice) PrinterType.SUNMI_INTEGRATED else PrinterType.BLUETOOTH_EXTERNAL

    // Dènye mesaj erè Bluetooth la — pèmèt MainActivity montre l dirèkteman
    // sou ekran an (Toast), san bezwen Logcat/kab USB.
    val lastPrinterError: String?
        get() = bluetoothHelper.lastError

    fun connect(onReady: (() -> Unit)? = null) {
        if (isSunmiDevice) {
            sunmiHelper = SunmiPrinterHelper(context).also {
                it.connect(onReady)
            }
        } else {
            // Pou Bluetooth, konekte ak dènye enprimant itilizatè a te chwazi a
            // (si genyen). Si pa gen okenn anrejistre, `isReady()` ap fo.
            val savedAddress = getSavedPrinterAddress()
            if (savedAddress != null) {
                Thread {
                    bluetoothHelper.connectByAddress(savedAddress)
                    onReady?.invoke()
                }.start()
            }
        }
    }

    fun disconnect() {
        sunmiHelper?.disconnect()
        bluetoothHelper.disconnect()
    }

    fun isReady(): Boolean {
        return if (isSunmiDevice) sunmiHelper?.isConnected == true
        else bluetoothHelper.isConnected
    }

    fun printTestReceipt() {
        if (isSunmiDevice) sunmiHelper?.printTestReceipt()
        else bluetoothHelper.printTestReceipt()
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
            bluetoothHelper.printTicketReceipt(companyName, drawName, ticketNumber, numbers, betAmount, footerMessage)
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

    companion object {
        private const val KEY_PRINTER_MAC = "printer_mac"
    }
}