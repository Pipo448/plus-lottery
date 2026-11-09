package com.plusgroup.pos.printer

import android.content.ComponentName
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import woyou.aidlservice.jiuiv5.IWoyouService
import woyou.aidlservice.jiuiv5.ICallback
import com.plusgroup.pos.util.Money

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
 *
 * NÒT TEKNIK enpòtan (aprè anpil tès sou yon aparèy SUNMI V2 reyèl,
 * API 25 / Android 7.1.1):
 * 1. `printText()` senp SANS font eksplisit sanble itilize yon codaj CJK
 *    (Chinwa) pa default sou ansyen sèvis sa a — li fè tèks Latin/Kreyòl
 *    vin defòme an senbòl Chinwa (mojibake) san jete okenn erè. SÈL
 *    `printTextWithFont()` jere codaj Latin kòrèkteman, kidonk nou sèvi
 *    ak li pou TOUT liy, pa jis kèk grenn.
 * 2. `printerInit()` bezwen yon délè pi long (~200ms) anvan premye
 *    kòmand tèks la, paske sèvis la sanble bezwen "chaje" font/codaj la;
 *    san sa, premye karaktè(s) yo ka rete defòme menm ak printTextWithFont.
 * 3. Chak kòmand AIDL swiv pa yon ti délè (~50ms) — san sa, "race
 *    condition" fè premye karaktè yon liy vin defòme lè l swiv yon
 *    lineWrap() twò vit.
 * 4. Tout sekans enprime a egzekite sou yon THREAD SEPARE (pa sou
 *    main/UI thread), paske Thread.sleep() itilize pou jere pwoblèm anwo
 *    yo ta jele ekran an si l te egzekite sou main thread.
 * 5. `lineWrap()` final la dwe ase gwo (8, pa 3-5) pou fè tout rès fich
 *    la fizikman soti anvan koupe a, sinon dènye pati a rete "kwense"
 *    anndan mekanis enprimant lan.
 */
// Tout tèks pase pa la: espas espesyal (U+202F…) → espas nòmal, sinon
// enprimant lan enprime fatra ("10ʎ ? 00,") olye de chif yo.
private fun IWoyouService.printSafe(text: String, typeface: String?, size: Float, cb: ICallback?) =
    printTextWithFont(Money.printable(text), typeface, size, cb)

class SunmiPrinterHelper(private val context: Context) {

    // Tout resi yo pase youn apre lòt (egz. plizyè fich pou plizyè tiraj):
    // yo pa janm melanje sou papye a.
    private val printQueue = java.util.concurrent.Executors.newSingleThreadExecutor()


    private var woyouService: IWoyouService? = null
    var isConnected: Boolean = false
        private set

    // Plizyè ekran ka tann koneksyon an an menm tan
    private val pendingCallbacks = mutableListOf<() -> Unit>()
    @Volatile private var binding = false

    private fun flushCallbacks() {
        val list = synchronized(pendingCallbacks) { pendingCallbacks.toList().also { pendingCallbacks.clear() } }
        list.forEach { cb -> try { cb() } catch (e: Exception) { Log.e(TAG, "Callback enprimant", e) } }
    }

    private val connService = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            woyouService = null
            isConnected = false
            binding = false
            Log.w(TAG, "Sèvis enprimant SUNMI a dekonekte")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            woyouService = IWoyouService.Stub.asInterface(service)
            isConnected = true
            binding = false
            Log.i(TAG, "Sèvis enprimant SUNMI a konekte")
            flushCallbacks()
        }
    }

    /**
     * Konekte ak sèvis enprimant SUNMI a. Rele sa nan onCreate() ekran an,
     * epi tann `onReady` anvan w eseye enprime.
     */
    fun connect(onReady: (() -> Unit)? = null) {
        // Deja konekte → enprime touswit (pa re-konekte chak fich)
        if (isConnected && woyouService != null) {
            onReady?.invoke()
            return
        }
        if (onReady != null) synchronized(pendingCallbacks) { pendingCallbacks.add(onReady) }
        if (binding) return // yon koneksyon deja an kou: callback la ap rele apre
        binding = true
        try {
            val intent = Intent()
            intent.setPackage("woyou.aidlservice.jiuiv5")
            intent.action = "woyou.aidlservice.jiuiv5.IWoyouService"
            val ok = context.bindService(intent, connService, Context.BIND_AUTO_CREATE)
            if (!ok) {
                binding = false
                flushCallbacks()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Echèk konekte ak sèvis enprimant SUNMI a", e)
            binding = false
            flushCallbacks()
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

    /** Ti délè ant chak kòmand AIDL — evite "race condition" mojibake. */
    private fun pause(ms: Long = 15) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            // Pa gwo zafè si sa entèwonp — kontinye kanmenm.
        }
    }

    /**
     * Enprime yon tès senp — itilize sa pou verifye entegrasyon an mache
     * anvan w bati resi konplè tikè yo. Egzekite sou yon thread separe.
     */
    /**
     * Fòse kodpaj karaktè enprimant lan sou PC437 (Standard Europe/USA) via
     * yon kòmand ESC/POS brit: ESC t 0 (0x1B 0x74 0x00). Sa rezoud pwoblèm
     * "mojibake" (tèks Latin ki tradui an senbòl Chinwa) ki rive lè
     * enprimant lan rete sou yon kodpaj GBK/CJK pa default.
     */
    private fun forceLatinCodepage(svc: IWoyouService) {
        try {
            svc.sendRAWData(byteArrayOf(0x1B, 0x74, 0x00), null)
        } catch (e: RemoteException) {
            Log.e(TAG, "Echèk fòse kodpaj Latin", e)
        }
    }

    fun printTestReceipt() {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        printQueue.execute {
            Log.e(TAG, "=== MAKI_VESYON_9 EGZEKITE (printTestReceipt) ===")
            try {
                svc.printerInit(null)
                pause(120) // délè apre init pou font/codaj la chaje
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()

                svc.printSafe("PLUS GROUP\n", null, 28f, null)
                pause()

                svc.printSafe("Test enprimant - tout bon\n", null, 24f, null)
                pause()

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime tès la", e)
            }
        }
    }

    /**
     * Enprime yon resi tikè bolèt. Egzekite sou yon thread separe.
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
        printQueue.execute {
            try {
                svc.printerInit(null)
                pause(120)
                forceLatinCodepage(svc)
                pause(60)

                svc.setAlignment(1, null)
                pause()
                svc.printSafe("$companyName\n", null, 28f, null)
                pause()
                svc.printSafe("BONNE CHANCE!\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(0, null)
                pause()
                svc.printSafe("Tiraj: $drawName\n", null, 24f, null)
                pause()
                svc.printSafe("Tikè No: $ticketNumber\n", null, 24f, null)
                pause()
                svc.printSafe("Nimewo: $numbers\n", null, 24f, null)
                pause()
                svc.printSafe("Montan: $betAmount HTG\n", null, 24f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(1, null)
                pause()
                svc.printQRCode(ticketNumber, 8, 0, null)
                pause()
                svc.lineWrap(1, null)
                pause()
                svc.printSafe("$footerMessage\n", null, 24f, null)
                pause()

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime resi tikè a", e)
            }
        }
    }

    /**
     * Enprime yon Fich konplè, nan menm fòma resi bolèt tradisyonèl la
     * (menm fòma ak `BluetoothPrinterHelper.printFicheReceipt`).
     * Egzekite sou yon thread separe pou l pa jele ekran an.
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
        logo: Bitmap? = null,
        sections: List<ReceiptSection>? = null,
    ) {
        // Fich plizyè tiraj: yon seksyon pou chak tiraj
        val secs = sections?.takeIf { it.isNotEmpty() } ?: listOf(ReceiptSection(drawName, drawTotal, lines))
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        printQueue.execute {
            Log.e(TAG, "=== MAKI_VESYON_9 EGZEKITE (printFicheReceipt) ===")
            try {
                svc.printerInit(null)
                pause(120) // délè apre init pou font/codaj la chaje
                forceLatinCodepage(svc)
                pause(60)

                // Logo tenant lan (Mon compte → Paramètres)
                if (logo != null) {
                    try {
                        svc.setAlignment(1, null)
                        pause()
                        svc.printBitmap(logo, null)
                        pause(120)
                        svc.lineWrap(1, null)
                        pause()
                    } catch (e: Exception) {
                        Log.w(TAG, "Logo a pa t enprime", e)
                    }
                }

                svc.setAlignment(1, null)
                pause()
                svc.printSafe("$companyName\n", null, fs(28f), null)
                pause()
                if (promoLine.isNotBlank()) {
                    svc.printSafe("$promoLine\n", null, fs(24f), null)
                    pause()
                }

                if (phone.isNotBlank()) {
                    svc.printSafe("${ReceiptLabels.tel}: $phone\n", null, fs(24f), null)
                    pause()
                }
                // Non ajan an anlè fich la, byen vizib
                if (vendeur.isNotBlank() && vendeur != "—") {
                    svc.printSafe("${ReceiptLabels.agent}: $vendeur\n", null, fs(26f), null)
                    pause()
                }

                svc.setAlignment(0, null)
                pause()
                svc.printSafe("Ticket: $dateTimeText\n", null, fs(24f), null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.printSafe("${ReceiptLabels.fiche}: $ficheNumber\n", null, fs(24f), null)
                pause()
                svc.printSafe("-".repeat(ReceiptLabels.lineWidth) + "\n", null, fs(24f), null)
                pause()
                secs.forEachIndexed { idx, sec ->
                    if (idx > 0) {
                        svc.lineWrap(1, null)
                        pause()
                    }
                    // Non tiraj la sèlman (total la parèt yon sèl fwa anba)
                    svc.printSafe("${sec.drawName}\n", null, fs(26f), null)
                    pause()
                    for ((code, numero, prix) in sec.lines) {
                        svc.printSafe(twoColumnLine("$code   $numero", prix, ReceiptLabels.lineWidth) + "\n", null, fs(24f), null)
                        pause()
                    }
                }

                svc.printSafe("-".repeat(ReceiptLabels.lineWidth) + "\n", null, fs(24f), null)
                pause()
                if (secs.size > 1) {
                    svc.printSafe(ReceiptLabels.draws(secs.size) + "\n", null, fs(24f), null)
                    pause()
                }
                // TOTAL: sèl montan anba a, an gra epi pi gwo pase rès tèks la
                try { svc.sendRAWData(byteArrayOf(0x1B, 0x45, 0x01), null) } catch (_: Exception) {}
                pause()
                svc.printSafe(twoColumnLine(ReceiptLabels.total, grandTotal, totalWidth()) + "\n", null, fs(32f), null)
                pause()
                try { svc.sendRAWData(byteArrayOf(0x1B, 0x45, 0x00), null) } catch (_: Exception) {}
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(1, null)
                pause()
                if (footerMessage.isNotBlank()) {
                    svc.printSafe("$footerMessage\n", null, fs(24f), null)
                    pause()
                    svc.lineWrap(1, null)
                    pause()
                }

                if (!qrData.isNullOrBlank()) {
                    svc.printQRCode(qrData, 8, 0, null)
                    pause()
                    svc.lineWrap(1, null)
                    pause()
                }

                // Mon compte → "Kòd ba sou fich la": kòd ba ITF anba QR la
                ReceiptLabels.barcodeData(ficheNumber)?.let { code ->
                    try {
                        svc.setAlignment(1, null)
                        pause()
                        svc.printBarCode(code, 5, 80, 2, 2, null) // 5 = ITF, tèks anba
                        pause()
                        svc.lineWrap(1, null)
                        pause()
                    } catch (e: Exception) {
                        Log.w(TAG, "Kòd ba a pa t enprime", e)
                    }
                }

                // Délè final pi gwo — asire tout papye a fizikman soti
                // anvan koupe a, sinon rès la rete kwense anndan.
                // Ase papye pou fich la depase lam koupe a, san gaspiye tan
                svc.lineWrap(5, null)
                pause(80)
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime Fich la", e)
            }
        }
    }

    /**
     * Enprime yon RAPÒ jenerik (Rapport partiel, Fin tirage, Fiche
     * gagnant, Transactions, Fich elimine) nan menm fòma ak resi DVN
     * Lotto a: antèt konpayi santre, kèk liy antèt agoch (Date/Vendeur/
     * Pos...), yon liy `----`, epi chan "Etikèt: Valè" aliyen an 2 kolòn.
     * `footerFields` opsyonèl ajoute yon dezyèm seksyon apre yon dezyèm
     * liy `----` (itil pou Depot/Retrait/Balance nan F.TIRAGE).
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
        logo: Bitmap? = null,
    ) {
        val svc = woyouService ?: run {
            Log.w(TAG, "Enprimant pa konekte — pa ka enprime")
            return
        }
        printQueue.execute {
            try {
                svc.printerInit(null)
                pause(120)
                forceLatinCodepage(svc)
                pause(60)

                // Logo + non tenant lan, menm jan ak fich yo
                if (logo != null) {
                    try {
                        svc.setAlignment(1, null)
                        pause()
                        svc.printBitmap(logo, null)
                        pause(120)
                        svc.lineWrap(1, null)
                        pause()
                    } catch (e: Exception) {
                        Log.w(TAG, "Logo a pa t enprime", e)
                    }
                }
                svc.setAlignment(1, null)
                pause()
                svc.printSafe("$companyName\n", null, 28f, null)
                pause()
                if (branchCode.isNotBlank()) {
                    svc.printSafe("$branchCode\n", null, 24f, null)
                    pause()
                }
                if (phone.isNotBlank()) {
                    svc.printSafe("Tel: $phone\n", null, 24f, null)
                    pause()
                }
                if (agent.isNotBlank()) {
                    svc.printSafe("Ajan: $agent\n", null, 26f, null)
                    pause()
                }
                svc.printSafe("$reportTitle\n", null, 26f, null)
                pause()
                svc.lineWrap(1, null)
                pause()

                svc.setAlignment(0, null)
                pause()
                for ((label, value) in headerLines) {
                    svc.printSafe("$label: $value\n", null, 24f, null)
                    pause()
                }

                svc.printSafe("$DASHES\n", null, 24f, null)
                pause()
                for ((label, value) in bodyFields) {
                    svc.printSafe(twoColumnLine("$label:", value) + "\n", null, 24f, null)
                    pause()
                }

                if (footerFields.isNotEmpty()) {
                    svc.printSafe("$DASHES\n", null, 24f, null)
                    pause()
                    for ((label, value) in footerFields) {
                        svc.printSafe(twoColumnLine("$label:", value) + "\n", null, 24f, null)
                        pause()
                    }
                }

                svc.lineWrap(12, null)
                pause(300) // ase tan pou motè papye a fin fè tout avans lan anvan koupe
                svc.cutpaper(null)
            } catch (e: RemoteException) {
                Log.e(TAG, "Echèk enprime rapò a", e)
            }
        }
    }

    // Aliyen tèks agoch ak yon valè adwat sou menm liy (menm lojik ak
    // BluetoothPrinterHelper, sipoze lajè ~32 karaktè).
    /** Gwosè lèt dapre Mon compte → "Gwosè enprimant" (piti / nòmal / gwo). */
    private fun fs(v: Float): Float = v * com.plusgroup.pos.util.TenantRules.printScale

    /** Kantite karaktè sou liy TOTAL la (font 32 olye de 24 → mwens karaktè). */
    private fun totalWidth(): Int = (ReceiptLabels.lineWidth * 24) / 32

    private fun twoColumnLine(left: String, right: String, width: Int = 32): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right else "$left $right"
    }

    companion object {
        private const val TAG = "SunmiPrinterHelper"
        private const val DASHES = "--------------------------------"
    }
}