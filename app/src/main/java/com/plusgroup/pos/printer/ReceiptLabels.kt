package com.plusgroup.pos.printer

import com.plusgroup.pos.util.TenantRules

/**
 * Mo ki sou fich enprime a dapre Mon compte → "Lang fich la".
 * "Ticket" (dat/lè) rete menm jan nan tout lang (jan sa te mande a).
 */
object ReceiptLabels {
    private val lang get() = TenantRules.lang

    val agent: String get() = when (lang) { "fr" -> "Vendeur"; "en" -> "Agent"; "es" -> "Vendedor"; else -> "Ajan" }
    val fiche: String get() = when (lang) { "en" -> "Ticket No"; "es" -> "Boleto"; else -> "Fiche" }
    val total: String get() = "Total"
    val tel: String get() = when (lang) { "es" -> "Tel"; else -> "Tel" }

    fun draws(n: Int): String = when (lang) {
        "fr" -> "$n tirages"
        "en" -> "$n draws"
        "es" -> "$n sorteos"
        else -> "$n tiraj"
    }

    /** Lajè yon liy (karaktè) dapre gwosè enprimant lan. */
    val lineWidth: Int get() = when (TenantRules.printSize) {
        "gwo" -> 26
        "piti" -> 37 // Sunmi: lèt 0.85×; Bluetooth: font B (gen plas pou 42)
        else -> 32
    }

    /** Antèt kolòn yo lè resi a an 2 kolòn. */
    val colPlay: String get() = when (lang) { "fr" -> "BOULE"; "en" -> "PLAY"; "es" -> "JUGADA"; else -> "BOUL" }
    val colAmount: String get() = when (lang) { "fr" -> "MISE"; "en" -> "AMOUNT"; "es" -> "MONTO"; else -> "KOB" }

    /**
     * RESI AN 2 KOLÒN (Paramèt → "Resi an 2 kolòn"): pou ekonomize papye.
     *
     *   BOUL        KOB  BOUL        KOB
     *   05            3  50            3
     *   30*93    GRATIS  25312-1   2 000
     *
     * - Kòd jwèt la (BO, MG, L3…) pa parèt: fòm nimewo a deja di ki jwèt li ye
     *   (2 chif = borlette, 3 = loto3, 4 = loto4, 5 = loto5, X*Y = maryaj).
     * - Opsyon an kole ak nimewo a: "25312 (O1)" → "25312-1".
     * - ",00" retire sou montan won yo: "2 000,00" → "2 000".
     * - Kolòn gòch la plen anvan (lekti anwo → anba, apre sa kolòn dwat la).
     * - Si yon liy pa antre nan mwatye a, lis la vid → resi a rete sou 1 kolòn.
     */
    fun columnRows(lines: List<Triple<String, String, String>>, width: Int): List<String> {
        if (lines.isEmpty()) return emptyList()
        val gap = 2
        val half = (width - gap) / 2
        fun cell(left: String, right: String): String {
            val space = half - left.length - right.length
            return if (space > 0) left + " ".repeat(space) + right else "$left $right"
        }
        val cells = lines.map { t ->
            val numero = t.second.replace(Regex("""\s*\(O(\d+)\)"""), "-$1").trim()
            val amount = t.third.trim().removeSuffix(",00").removeSuffix(".00")
            cell(numero, amount)
        }
        // Yon liy pa antre nan mwatye papye a (egz. enprimant "gwo"): pa koupe
        // nimewo a — kite resi a sou 1 kolòn (lis vid = moun ki rele a fè 1 kolòn).
        if (cells.any { it.length > half }) return emptyList()
        val rows = (lines.size + 1) / 2
        val out = ArrayList<String>(rows + 1)
        out += cell(colPlay, colAmount).padEnd(half) + " ".repeat(gap) + cell(colPlay, colAmount)
        for (i in 0 until rows) {
            val a = cells[i].padEnd(half)
            val j = i + rows
            out += if (j < cells.size) a + " ".repeat(gap) + cells[j] else a.trimEnd()
        }
        return out
    }

    /** Kòd ba ITF: sèlman chif, kantite pè (nimewo fich 12 chif). */
    fun barcodeData(ficheNumber: String): String? {
        val digits = ficheNumber.filter { it.isDigit() }
        return digits.takeIf { TenantRules.printBarcode && it.length >= 6 && it.length % 2 == 0 }
    }
}
