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
            // TÈS: RANVÈSE LÒD 2 liy yo — "Test enprimant" (senp, san gwo
            // font) PREMYE, "PLUS GROUP" (gwo font) DEZYÈM. Ipotèz: se
            // POZISYON kòmand lan nan sekans lan (2yèm kòmand enprime)
            // ki pwoblèm nan, pa kontni/fòma liy lan.
            svc.printerInit(null)
            Thread.sleep(50)

            svc.setAlignment(1, null)
            Thread.sleep(50)

            svc.printText("Test enprimant - tout bon\n", null)
            Thread.sleep(50)

            svc.printTextWithFont("PLUS GROUP\n", null, 32f, null)
            Thread.sleep(50)

            svc.lineWrap(5, null)
            Thread.sleep(50)

            svc.cutpaper(null)

            Log.i(TAG, "Sekans tès fini ak lòd ranvèse")
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

            svc.setAlignment(1, null)
            svc.printTextWithFont("$companyName\n", null, 32f, null)
            svc.printText("BONNE CHANCE!\n", null)
            svc.lineWrap(1, null)

            svc.setAlignment(0, null)
            svc.printText("Tiraj: $drawName\n", null)
            svc.printText("Tikè No: $ticketNumber\n", null)
            svc.printText("Nimewo: $numbers\n", null)
            svc.printText("Montan: $betAmount HTG\n", null)
            svc.lineWrap(1, null)

            svc.setAlignment(1, null)
            svc.printQRCode(ticketNumber, 8, 0, null)
            svc.lineWrap(1, null)
            svc.printText("$footerMessage\n", null)

            svc.lineWrap(5, null)
            svc.cutpaper(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk enprime resi tikè a", e)
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

            svc.setAlignment(1, null)
            svc.printTextWithFont("$companyName\n", null, 32f, null)
            if (promoLine.isNotBlank()) svc.printText("$promoLine\n", null)
            svc.setAlignment(0, null)
            if (phone.isNotBlank()) svc.printText("Tel: $phone\n", null)
            svc.printText("Vendeur: $vendeur\n", null)
            svc.printText("Fecha: $dateTimeText\n", null)
            svc.lineWrap(1, null)

            svc.printText("Fiche: $ficheNumber\n", null)
            svc.printText("$DASHES\n", null)
            svc.printText("$drawName: $drawTotal\n", null)

            for ((code, numero, prix) in lines) {
                svc.printText(twoColumnLine("$code   $numero", prix) + "\n", null)
            }

            svc.printText("$DASHES\n", null)
            svc.printText(twoColumnLine("Total:  ${String.format("%03d", lines.size)}", grandTotal) + "\n", null)
            svc.lineWrap(1, null)

            svc.setAlignment(1, null)
            if (footerMessage.isNotBlank()) {
                svc.printText("$footerMessage\n", null)
                svc.lineWrap(1, null)
            }

            if (!qrData.isNullOrBlank()) {
                svc.printQRCode(qrData, 8, 0, null)
                svc.lineWrap(1, null)
            }

            svc.lineWrap(5, null)
            svc.cutpaper(null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk enprime Fich la", e)
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