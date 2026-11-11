package com.plusgroup.pos.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dat ki soti nan backend lan → lè Ayiti.
 *
 *  - "2026-11-11T18:41:18.12+00:00" / "...Z" (timestamptz) = lè UTC → nou
 *    konvèti l an lè Ayiti (18:41 UTC = 1:41 PM Ayiti).
 *  - "2026-11-11T13:41:18" san zòn (egz. datePrinted) = deja lè lokal → nou
 *    montre l jan l ye a.
 */
object Dates {
    val HAITI: TimeZone = TimeZone.getTimeZone("America/Port-au-Prince")
    private val OFFSET = Regex("""(Z|[+-]\d{2}(:?\d{2})?)$""")

    fun parse(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(' ', 'T')
        val base = s.take(19)
        return try {
            val m = OFFSET.find(s)
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            if (m == null) {
                p.timeZone = HAITI
                p.parse(base)
            } else {
                val z = m.value
                p.timeZone = if (z == "Z") TimeZone.getTimeZone("UTC")
                else {
                    val d = z.substring(1).replace(":", "").padEnd(4, '0')
                    TimeZone.getTimeZone("GMT" + z[0] + d.substring(0, 2) + ":" + d.substring(2, 4))
                }
                p.parse(base)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun format(date: Date, pattern: String, locale: Locale = Locale.US): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = HAITI }.format(date)

    /** Lis / rapò: "2026-11-11 13:41:18" (lè Ayiti). */
    fun local(raw: String?, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        if (raw.isNullOrBlank()) return "—"
        return parse(raw)?.let { format(it, pattern) } ?: raw
    }

    /** Menm fòma ak premye resi a: "11/Nov/2026 01:41 PM". */
    fun receipt(raw: String?): String = local(raw, "dd/MMM/yyyy hh:mm a")
}
