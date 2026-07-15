package com.plusgroup.pos.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import woyou.aidlservice.jiuiv5.IWoyouService

/**
 * Kouch entegrasyon ak sèvis enprimant SUNMI a.
 *
 * IMPÒTAN: Sa a mache SÈLMAN sou aparèy SUNMI (li itilize sèvis sistèm
 * "woyou.aidlservice.jiuiv5" ki deja enstale nan ROM SUNMI a). Li p ap
 * mache sou lòt telefòn/tablèt Android òdinè.
 *
 * Ou dwe telechaje vrè fichye `IWoyouService.aidl` la nan pòtal devlopè
 * SUNMI a epi mete l nan:
 *   app/src/main/aidl/woyou/aidlservice/jiuiv5/IWoyouService.aidl
 * anvan pwojè a ka konpile.
 */
class SunmiPrinterHelper(private val context: Context) {

    private var woyouService: IWoyouService? = null
    var isConnected: Boolean = false
        private set

    private var onReadyCallback: (() -> Unit)? = null

    private val connService = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            woyouService = null
            isConnected = false
            Log.w(TAG, "Sèvis enprimant SUNMI a dekonekte")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            woyouService = IWoyouService.Stub.asInterface(service)
            isConnected = true
            Log.i(TAG, "Sèvis enprimant SUNMI a konekte")
            onReadyCallback?.invoke()
        }
    }

    /**
     * Konekte ak sèvis enprimant SUNMI a. Rele sa nan onCreate() ekran an,
     * epi tann `onReady` anvan w eseye enprime.
     */
    fun connect(onReady: (() -> Unit)? = null) {
        onReadyCallback = onReady
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            context.bindService(intent, connService, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Echèk konekte ak sèvis enprimant SUNMI a", e)
        }
    }

    fun disconnect() {
        if (isConnected) {
            try {
                context.unbindService(connService)
            } catch (e: Exception) {
                Log.e(TAG, "Echèk dekonekte", e)
            }
            isConnected = false
        }
    }

    /**
     * Enprime yon tès senp — itilize sa pou verifye entegrasyon an mache
     * anvan w bati resi konplè tikè yo.
     *
     * VÈSYON DEBUG: chak apèl AIDL log kòd retou li (anpil metòd IWoyouService
     * retounen yon kòd erè entye olye jete yon eksepsyon — sa pèmèt nou izole
     * EGZAKTEMAN ki kòmand ki echwe, menm si Logcat pa montre okenn crash).
     */
    fun printTestReceipt() {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        try {
            svc.printerInit(null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)

            svc.printTextWithFont("PLUS GROUP\n", null, 28f, null)
            Thread.sleep(50)

            svc.printTextWithFont("Test enprimant - tout bon\n", null, 24f, null)
            Thread.sleep(50)

            svc.lineWrap(5, null)
            svc.cutpaper(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk enprime tès la", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Sekans entèwonp", e)
        }
    }

    /**
     * Enprime yon resi tikè bolèt. Ranpli/ajiste fòma a selon bezwen reyèl
     * lè n rive nan etap "vann tikè" a.
     */
    fun printTicketReceipt(
        companyName: String,
        drawName: String,
        ticketNumber: String,
        numbers: String,
        betAmount: String,
        footerMessage: String,
    ) {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        try {
            svc.printerInit(null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)
            svc.printTextWithFont("$companyName\n", null, 28f, null)
            Thread.sleep(50)
            svc.printTextWithFont("BONNE CHANCE!\n", null, 24f, null)
            Thread.sleep(50)
            svc.lineWrap(1, null)
            Thread.sleep(50)

            svc.setAlignment(0, null)
            Thread.sleep(50)
            svc.printTextWithFont("Tiraj: $drawName\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("Tikè No: $ticketNumber\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("Nimewo: $numbers\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("Montan: $betAmount HTG\n", null, 24f, null)
            Thread.sleep(50)
            svc.lineWrap(1, null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)
            svc.printQRCode(ticketNumber, 8, 0, null)
            Thread.sleep(50)
            svc.lineWrap(1, null)
            Thread.sleep(50)
            svc.printTextWithFont("$footerMessage\n", null, 24f, null)
            Thread.sleep(50)

            svc.lineWrap(5, null)
            svc.cutpaper(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk enprime resi tikè a", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Sekans entèwonp", e)
        }
    }

    /**
     * Enprime yon Fich konplè, nan menm fòma resi bolèt tradisyonèl la
     * (menm fòma ak `BluetoothPrinterHelper.printFicheReceipt`).
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
        lines: List<Triple<String, String, String>>, // (kòd, nimewo, pri fòmate)
        grandTotal: String,
        footerMessage: String,
        qrData: String?,
    ) {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        try {
            svc.printerInit(null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)
            svc.printTextWithFont("$companyName\n", null, 28f, null)
            Thread.sleep(50)
            if (promoLine.isNotBlank()) {
                svc.printTextWithFont("$promoLine\n", null, 24f, null)
                Thread.sleep(50)
            }

            svc.setAlignment(0, null)
            Thread.sleep(50)
            if (phone.isNotBlank()) {
                svc.printTextWithFont("Tel: $phone\n", null, 24f, null)
                Thread.sleep(50)
            }
            svc.printTextWithFont("Vendeur: $vendeur\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("Fecha: $dateTimeText\n", null, 24f, null)
            Thread.sleep(50)
            svc.lineWrap(1, null)
            Thread.sleep(50)

            svc.printTextWithFont("Fiche: $ficheNumber\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("$DASHES\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont("$drawName: $drawTotal\n", null, 24f, null)
            Thread.sleep(50)

            for ((code, numero, prix) in lines) {
                svc.printTextWithFont(twoColumnLine("$code   $numero", prix) + "\n", null, 24f, null)
                Thread.sleep(50)
            }

            svc.printTextWithFont("$DASHES\n", null, 24f, null)
            Thread.sleep(50)
            svc.printTextWithFont(twoColumnLine("Total:  ${String.format("%03d", lines.size)}", grandTotal) + "\n", null, 24f, null)
            Thread.sleep(50)
            svc.lineWrap(1, null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)
            if (footerMessage.isNotBlank()) {
                svc.printTextWithFont("$footerMessage\n", null, 24f, null)
                Thread.sleep(50)
                svc.lineWrap(1, null)
                Thread.sleep(50)
            }

            if (!qrData.isNullOrBlank()) {
                svc.printQRCode(qrData, 8, 0, null)
                Thread.sleep(50)
                svc.lineWrap(1, null)
                Thread.sleep(50)
            }

            svc.lineWrap(5, null)
            svc.cutpaper(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk enprime Fich la", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Sekans entèwonp", e)
        }
    }

    // Aliyen tèks agoch ak yon valè adwat sou menm liy (menm lojik ak
    // BluetoothPrinterHelper, sipoze lajè ~32 karaktè).
    private fun twoColumnLine(left: String, right: String, width: Int = 32): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    companion object {
        private const val TAG = "SunmiPrinterHelper"
        private const val DASHES = "--------------------------------"
    }
}