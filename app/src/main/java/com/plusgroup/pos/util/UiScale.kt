package com.plusgroup.pos.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.plusgroup.pos.DashboardActivity
import kotlin.math.roundToInt

/**
 * Gwosè ekran aplikasyon an (zoom), pou POS ak telefòn.
 *
 *  • Ajan an chwazi l nan Paramèt oswa nan meni 3 pwen an (Gwosè ekran).
 *  • Li chanje tout ekran yo an menm tan (dp ak tèks), san touche telefòn lan.
 *  • Gwosè tèks telefòn lan limite a 115% andedan app la: si telefòn lan
 *    mete tèks yo "trè gwo", ekran yo pa debòde ankò.
 */
object UiScale {

    private const val PREFS = "plus_pos_ui"
    private const val KEY_SCALE = "ui_scale"
    private const val MAX_FONT_SCALE = 1.15f

    /** Chwa yo: valè → non */
    val OPTIONS: List<Pair<Float, String>> = listOf(
        0.80f to "Trè piti",
        0.90f to "Piti",
        1.00f to "Nòmal",
        1.10f to "Gwo",
    )

    fun get(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_SCALE, 1.0f)

    fun set(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SCALE, scale).apply()
    }

    /** Aplike zoom lan sou kontèks yon Activity (rele nan attachBaseContext). */
    fun wrap(base: Context): Context {
        val scale = get(base)
        val current = base.resources.configuration
        val needFontClamp = current.fontScale > MAX_FONT_SCALE
        if (scale == 1.0f && !needFontClamp) return base

        val cfg = Configuration(current)
        if (needFontClamp) cfg.fontScale = MAX_FONT_SCALE
        if (scale != 1.0f) {
            val baseDpi = base.resources.displayMetrics.densityDpi
            cfg.densityDpi = (baseDpi * scale).roundToInt().coerceAtLeast(120)
        }
        return base.createConfigurationContext(cfg)
    }

    /** Dyalòg pou chwazi gwosè a. Lè l chanje, app la rekòmanse sou akèy la. */
    fun showPicker(activity: Activity) {
        val currentScale = get(activity)
        val labels = OPTIONS.map { (v, name) -> "$name (${(v * 100).roundToInt()}%)" }.toTypedArray()
        val checked = OPTIONS.indexOfFirst { it.first == currentScale }.let { if (it < 0) 2 else it }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Gwosè ekran")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                val chosen = OPTIONS[which].first
                if (chosen == currentScale) return@setSingleChoiceItems
                set(activity, chosen)
                val intent = Intent(activity, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity.startActivity(intent)
                activity.finish()
            }
            .setNegativeButton("Anile", null)
            .show()
    }
}
