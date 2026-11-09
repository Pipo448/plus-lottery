package com.plusgroup.pos.util

import android.content.Context

/**
 * Preferans enpresyon LOKAL sou aparèy la (chak ajan chwazi pa l nan Paramèt).
 *   columns = true → liy fich yo enprime sou 2 kolòn (ekonomize papye).
 */
object PrintPrefs {
    private const val PREFS = "plus_print"
    private const val KEY_COLUMNS = "receipt_columns"

    fun columns(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_COLUMNS, false)

    fun setColumns(ctx: Context, on: Boolean) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_COLUMNS, on).apply()
    }
}
