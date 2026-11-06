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

    /** Kòd ba ITF: sèlman chif, kantite pè (nimewo fich 12 chif). */
    fun barcodeData(ficheNumber: String): String? {
        val digits = ficheNumber.filter { it.isDigit() }
        return digits.takeIf { TenantRules.printBarcode && it.length >= 6 && it.length % 2 == 0 }
    }
}
