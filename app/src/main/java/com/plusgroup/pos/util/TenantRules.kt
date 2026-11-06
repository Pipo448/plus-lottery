package com.plusgroup.pos.util

/**
 * Paramèt Mon compte → Paramètres jan APK a aplike yo. Chak kle gen MENM
 * valè defo ak backend lan (TENANT_SETTING_DEFAULTS), pou yon tenant ki
 * poko janm touche paramèt li yo wè menm konpòtman sou web la ak sou POS la.
 *
 * Backend lan aplike règ vant yo tou (double tiraj, kantite pa fich, maryaj
 * 25×25, limit, fich ki genyen) — sa a se pou ajan an wè erè a anvan l voye.
 */
object TenantRules {

    private val DEFAULTS = mapOf(
        "printer_size" to "normal",
        "langue" to "ht",
        "re_imprimer_fiche" to "oui",
        "voir_fiche_gagnant" to "oui",
        "barecode" to "non",
        "box" to "non",
        "whatsapp" to "non",
        "double_tirage" to "oui",
        "boule_par_boule" to "non",
        "boule_paire" to "non",
        "boule_a_revers" to "non",
        "pionte" to "non",
        "mariage_automatique" to "non",
        "lotto4_automatique" to "non",
        "option_loto4_automatique" to "0",
        "modifier_lot" to "non",
        "interuption_limite" to "non",
        "interval_minute_eliminer_fiche" to "5",
        "eliminer_sans_confirmation" to "non",
        "kantite_boul" to "100",
        "kantite_mariaj" to "100",
        "kantite_loto3" to "100",
        "kantite_loto4" to "50",
        "kantite_loto5" to "0",
    )

    private fun raw(key: String): String =
        TenantBranding.settings[key]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULTS[key].orEmpty()

    private fun on(key: String): Boolean = raw(key).lowercase() in setOf("oui", "true", "1", "yes", "wi")

    private fun int(key: String): Int = raw(key).toIntOrNull()?.coerceAtLeast(0) ?: 0

    // ----- Fich ak enprimant -----
    /** 0.85 (piti) · 1.0 (nòmal) · 1.2 (gwo) */
    val printScale: Float get() = when (raw("printer_size").lowercase()) {
        "gwo" -> 1.2f
        "piti" -> 0.85f
        else -> 1f
    }
    val printSize: String get() = raw("printer_size").lowercase()
    val lang: String get() = raw("langue").lowercase().takeIf { it in setOf("ht", "fr", "en", "es") } ?: "ht"
    val canReprint: Boolean get() = on("re_imprimer_fiche")
    val seeWinners: Boolean get() = on("voir_fiche_gagnant")
    val printBarcode: Boolean get() = on("barecode")
    val showBox: Boolean get() = on("box")
    val showShare: Boolean get() = on("whatsapp")

    // ----- Vant ak jwèt -----
    val doubleDraw: Boolean get() = on("double_tirage")
    val sameMariage: Boolean get() = on("boule_par_boule")
    val showPairGrap: Boolean get() = on("boule_paire")
    val showRevers: Boolean get() = on("boule_a_revers")
    val showPwent: Boolean get() = on("pionte")
    val showMAuto: Boolean get() = on("mariage_automatique")
    val showL4Auto: Boolean get() = on("lotto4_automatique")
    val canEditPrice: Boolean get() = on("modifier_lot")

    /** Opsyon ki deja koche nan L4Auto: "12" → {1,2}; "0", "oui", vid → {1,2,3} */
    val l4AutoOptions: Set<Int> get() {
        val picked = raw("option_loto4_automatique").filter { it in '1'..'3' }.map { it - '0' }.toSet()
        return if (picked.isEmpty()) setOf(1, 2, 3) else picked
    }

    // ----- Anilasyon -----
    val cancelMinutes: Int get() = int("interval_minute_eliminer_fiche")
    val cancelWithoutConfirm: Boolean get() = on("eliminer_sans_confirmation")

    // ----- Kantite maksimòm pa fich (0 = pa gen limit) -----
    fun maxPerFiche(category: String): Int = when (category.uppercase()) {
        "BORLETTE" -> int("kantite_boul")
        "MARIAGE" -> int("kantite_mariaj")
        "LOTO3" -> int("kantite_loto3")
        "LOTO4" -> int("kantite_loto4")
        "LOTO5" -> int("kantite_loto5")
        else -> 0
    }

    fun categoryLabel(category: String): String = when (category.uppercase()) {
        "BORLETTE" -> "boul borlette"
        "MARIAGE" -> "maryaj"
        "LOTO3" -> "loto3"
        "LOTO4" -> "loto4"
        "LOTO5" -> "loto5"
        else -> category.lowercase()
    }
}
