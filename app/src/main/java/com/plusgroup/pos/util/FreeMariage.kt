package com.plusgroup.pos.util

import java.text.DecimalFormat

/**
 * Maryaj gratis (Konfigirasyon → Mariage gratuit, sql/016–017).
 * Backend lan ajoute yo sou fich la lè total la rive "a pati de" montan an.
 * Yo gen game_type = "mariage_gratuit", mize 0; sou resi a yo parèt
 * avèk kòd "MG" epi "GRATIS" kote montan an ta ye.
 */
object FreeMariage {
    const val TYPE = "mariage_gratuit"
    const val CODE = "MG"
    const val LABEL = "GRATIS"

    fun isFree(gameType: String?): Boolean = gameType?.trim()?.lowercase() == TYPE

    /** Montan pou afiche: "GRATIS" pou maryaj gratis, sinon montan an. */
    fun amountText(gameType: String?, amount: Double?, money: DecimalFormat): String =
        if (isFree(gameType)) LABEL else money.format(amount ?: 0.0)
}
