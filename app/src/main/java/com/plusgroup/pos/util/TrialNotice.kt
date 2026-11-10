package com.plusgroup.pos.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.plusgroup.pos.network.ApiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VÈSYON ESÈ 7 JOU sou APK a: lè 2 jou oswa mwens rete, ajan an wè yon
 * mesaj (1 fwa pa jou) pou di bolèt la ap kanpe otomatikman, ak bouton
 * pou kontakte Plus Group. Lè l fini, sèvè a bloke login lan ak mesaj la.
 */
object TrialNotice {
    private const val PREFS = "plus_trial"
    private const val PHONE = "+50942449024"
    private const val WA = "50942449024"

    suspend fun check(activity: Activity) {
        val st = try {
            ApiClient.getService(activity.applicationContext).getTrialStatus().body()?.data
        } catch (_: Exception) { null } ?: return
        if (st.isTrial != true) return
        val days = st.daysLeft ?: return
        if (days > 2) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("shown", "") == today) return
        prefs.edit().putString("shown", today).apply()
        if (activity.isFinishing) return

        val left = when {
            days <= 0 -> Lang.tr("jodi a")
            days == 1 -> Lang.tr("demen")
            else -> Lang.tr("nan {n} jou").replace("{n}", days.toString())
        }
        AlertDialog.Builder(activity)
            .setTitle(Lang.tr("Vèsyon esè a prèske fini"))
            .setMessage(
                Lang.tr("Bolèt sa a se yon esè 7 jou. L ap kanpe otomatikman {q}. Kontakte Plus Group pou n kreye bolèt la nèt.")
                    .replace("{q}", left)
            )
            .setPositiveButton("WhatsApp") { _, _ ->
                open(activity, "https://wa.me/$WA")
            }
            .setNeutralButton(Lang.tr("Rele")) { _, _ ->
                open(activity, "tel:$PHONE", Intent.ACTION_DIAL)
            }
            .setNegativeButton(Lang.tr("Fèmen"), null)
            .show()
    }

    private fun open(activity: Activity, uri: String, action: String = Intent.ACTION_VIEW) {
        try { activity.startActivity(Intent(action, Uri.parse(uri))) } catch (_: Exception) {}
    }
}
