package com.plusgroup.pos.printer

/**
 * TIKÈ PARYAJ (Parye Plis+) — fòma resi a kalkile ISIT LA yon sèl fwa,
 * epi SunmiPrinterHelper ak BluetoothPrinterHelper enprime menm liy yo.
 *
 *            PLUS GROUP
 *            PARYE PLIS+
 *           TIKE PARYAJ
 *        KOD: PP-7K2M9Q        (gwo)
 *   Dat: 14/11/2026 09:12 AM
 *   Ajan: Jean
 *   Tip: Konbine 2 match
 *   --------------------------------
 *   1) Real Madrid vs Barcelona     (nòmal)
 *      21/11 07:30 - La Liga
 *      > Plis pase 2.5          1.85   (PICK: gra + pi fonse)
 *   2) ...
 *   --------------------------------
 *   Miz                    100,00 HTG
 *   Kot total                    3.42
 *   --------------------------------
 *            GEN POSIB
 *           342,00 HTG          (gwo)
 *             [ QR ]
 *   Touche ak kòd la oswa QR la
 *
 * - Pa gen ":" apre etikèt yo, chak match sou pwòp liy li (pa koupe nan
 *   mitan dat la), seleksyon an agoch / kòt la adwat.
 */
data class ParyajSelection(
    val eventLabel: String,
    val league: String,
    val whenText: String,
    val pick: String,
    val odds: String,
    val live: Boolean = false,
)

data class ParyajReceipt(
    /** Mòd franchiz: pa logo/non antrepriz la, se "PARYE PLIS+" sèlman */
    val franchise: Boolean = false,
    val companyName: String,
    val phone: String,
    val agent: String,
    val code: String,
    val dateText: String,
    val typeText: String,
    val selections: List<ParyajSelection>,
    val stake: String,
    val totalOdds: String,
    val potentialWin: String,
    val footer: String = "Touche ak kod la oswa QR la",
    val qrData: String? = code,
    /** KÒD PATAJ (sql/048): yon lòt moun ka jwe menm chwa yo. Se PA kòd tikè a. */
    val shareCode: String? = null,
)

/** Yon liy resi: style = N (nòmal), B (gra), BIG (gwo + gra), PICK (sa kliyan an jwe a: gra + doub enpresyon → pi fonse). */
data class ReceiptLine(val text: String, val style: Style = Style.N, val center: Boolean = false) {
    enum class Style { N, B, BIG, PICK }
}

object ParyajReceiptFormat {

    /** Liy ki vin ANVAN QR la (antèt, match yo, total, gen posib). */
    fun body(r: ParyajReceipt, width: Int): List<ReceiptLine> {
        val out = ArrayList<ReceiptLine>()
        val dashes = "-".repeat(width)
        fun c(t: String, s: ReceiptLine.Style = ReceiptLine.Style.N) { if (t.isNotBlank()) out += ReceiptLine(t, s, center = true) }
        fun l(t: String, s: ReceiptLine.Style = ReceiptLine.Style.N) { out += ReceiptLine(t, s) }

        c(r.companyName, ReceiptLine.Style.B)
        if (!r.franchise && !r.companyName.equals("PARYE PLIS+", ignoreCase = true)) c("PARYE PLIS+")
        if (r.phone.isNotBlank()) c("Tel ${r.phone}")
        c("TIKE PARYAJ", ReceiptLine.Style.B)
        c("KOD ${r.code}", ReceiptLine.Style.BIG)
        l(dashes)
        l(pair("Dat", r.dateText, width))
        if (r.agent.isNotBlank()) l(pair("Ajan", r.agent, width))
        l(pair("Tip", r.typeText, width))
        l(dashes)

        r.selections.forEachIndexed { i, s ->
            val num = "${i + 1}) "
            val indent = " ".repeat(num.length)
            // Match la (nòmal) → sa kliyan an JWE a anba l, PI FONSE (PICK) ak "> " devan
            // Jwèt vityèl: non tiraj la parèt yon sèl fwa (premye liy), lòt liy yo vid
            if (s.eventLabel.isNotBlank()) wrap(s.eventLabel, width - num.length).forEachIndexed { k, part ->
                l((if (k == 0) num else indent) + part)
            }
            val meta = listOf(if (s.live) "LIVE" else s.whenText, s.league).filter { it.isNotBlank() }.joinToString(" - ")
            if (meta.isNotBlank()) wrap(meta, width - indent.length).forEach { l(indent + it) }
            // Seleksyon agoch, kòt adwat; si seleksyon an twò long li pran 2 liy (tout an PICK)
            val mark = "> "
            val pickW = width - indent.length - mark.length - s.odds.length - 1
            val pick = wrap(s.pick, pickW.coerceAtLeast(8))
            pick.dropLast(1).forEachIndexed { k, part -> l(indent + (if (k == 0) mark else "  ") + part, ReceiptLine.Style.PICK) }
            l(pair(indent + (if (pick.size == 1) mark else "  ") + pick.lastOrNull().orEmpty(), s.odds, width), ReceiptLine.Style.PICK)
            if (i < r.selections.size - 1 && r.selections[i + 1].eventLabel.isNotBlank()) l("")
        }

        l(dashes)
        l(pair("Miz", r.stake, width))
        if (r.totalOdds != "-") l(pair("Kot total", r.totalOdds, width))
        l(dashes)
        c("GEN POSIB", ReceiptLine.Style.B)
        c(r.potentialWin, ReceiptLine.Style.BIG)
        return out
    }

    /** Liy ki vin APRE QR la. */
    fun after(r: ParyajReceipt, width: Int): List<ReceiptLine> {
        val share = r.shareCode?.takeIf { it.isNotBlank() }?.let {
            listOf(
                ReceiptLine("-".repeat(width)),
                ReceiptLine("KOD PATAJ (jwe menm chwa yo)", center = true),
                ReceiptLine(it, ReceiptLine.Style.B, center = true),
            )
        } ?: emptyList()
        return listOf(ReceiptLine(r.code, ReceiptLine.Style.B, center = true)) +
            wrap(r.footer, width).map { ReceiptLine(it, center = true) } +
            share +
            ReceiptLine("Bon chans!", center = true)
    }

    /** Etikèt agoch, valè adwat, SAN ":" — si pa gen plas, valè a desann sou liy pa l. */
    fun pair(left: String, right: String, width: Int): String {
        val space = width - left.length - right.length
        return if (space >= 1) left + " ".repeat(space) + right
        else left + "\n" + right.padStart(width)
    }

    /** Koupe yon tèks sou mo yo (pa nan mitan yon mo si posib). */
    fun wrap(text: String, width: Int): List<String> {
        val w = width.coerceAtLeast(4)
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (word0 in words) {
            var word = word0
            while (word.length > w) { // mo ki pi long pase liy lan
                if (cur.isNotEmpty()) { lines += cur.toString(); cur = StringBuilder() }
                lines += word.take(w); word = word.drop(w)
            }
            if (cur.isEmpty()) cur.append(word)
            else if (cur.length + 1 + word.length <= w) cur.append(' ').append(word)
            else { lines += cur.toString(); cur = StringBuilder(word) }
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines
    }
}
