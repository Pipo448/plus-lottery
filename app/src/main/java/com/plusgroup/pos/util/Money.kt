package com.plusgroup.pos.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/**
 * Fòma kòb pou TOUT APK a: "10 000,00".
 *
 * Poukisa: DecimalFormat("#,##0.00") san senbòl eksplisit swiv lang aparèy la.
 * Sou yon telefòn an fransè, separatè mil yo se yon "espas fen" (U+202F) —
 * enprimant tèmik yo pa konnen karaktè sa a, yo enprime "10ʎ ? 00," sou fich la.
 * Isit la separatè yo fiks: espas nòmal (ASCII) + vigil.
 */
object Money {
    private val symbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    /** Nouvo DecimalFormat (pa pataje: DecimalFormat pa thread-safe). */
    fun format(): DecimalFormat = DecimalFormat("#,##0.00", symbols)

    fun fmt(value: Number?): String = format().format(value?.toDouble() ?: 0.0)

    /**
     * Tèks pou enprimant tèmik: tout kalite espas espesyal → espas nòmal,
     * tirè/apostwòf "bèl" → ASCII. (Aksan yo rete: enprimant yo jere Latin-1.)
     */
    fun printable(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (ch in Normalizer.normalize(text, Normalizer.Form.NFC)) {
            sb.append(
                when (ch) {
                    ' ', ' ', ' ', ' ', ' ', ' ', ' ' -> ' '
                    '​', '‌', '‍', '﻿' -> ""
                    '‐', '‑', '‒', '–', '—', '−' -> '-'
                    '‘', '’', 'ʼ' -> '\''
                    '“', '”' -> '"'
                    '…' -> "..."
                    '→' -> "->"
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
