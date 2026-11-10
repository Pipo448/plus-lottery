package com.plusgroup.pos.util

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * MIZAJOU APK A.
 *
 * Sit Plus Lotterie a gen yon ti fichye `download/version.json`:
 *   {
 *     "versionCode": 2,              ← chif build.gradle (versionCode)
 *     "versionName": "1.1.0",
 *     "url": "/download/plus-lotterie.apk",
 *     "notes": "Resi an 2 kolòn, ...",
 *     "mandatory": false              ← true = ajan an pa ka di "Pita"
 *   }
 *
 * Lè versionCode sa a pi gwo pase pa APK ki enstale a, ajan an wè yon
 * mesaj "Nouvo mizajou" ak yon bouton TELECHAJE. APK a telechaje ak
 * DownloadManager, epi Android louvri ekran enstalasyon an. Si sa pa mache,
 * nou louvri lyen an nan navigatè a.
 *
 * Tcheke otomatik: sou akèy la, pa plis pase yon fwa chak 3 è.
 * Tcheke alamen: Paramèt → MIZAJOU.
 */
object AppUpdate {
    private const val TAG = "AppUpdate"

    /** Kote version.json ye (premye ki reponn lan genyen). */
    private val VERSION_URLS = listOf(
        "https://pluslotterie.com/download/version.json",
        "https://plus-frontend-0bcm.onrender.com/download/version.json",
    )

    private const val PREFS = "plus_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_SNOOZE_CODE = "snooze_code"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"
    private const val AUTO_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 è
    private const val SNOOZE_MS = 12 * 60 * 60 * 1000L        // "Pita" = 12 è

    data class Release(
        val versionCode: Long,
        val versionName: String,
        val url: String,
        val notes: String,
        val mandatory: Boolean,
    )

    @Volatile private var showing = false

    fun installedCode(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (_: Exception) { 0L }

    fun installedName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    /**
     * @param manual true = ajan an klike MIZAJOU (toujou tcheke + di l si li a jou).
     */
    fun check(activity: Activity, scope: LifecycleCoroutineScope, manual: Boolean = false) {
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_INTERVAL_MS) return
        if (showing) return

        if (manual) Toast.makeText(activity, Lang.tr("Ap tcheke mizajou..."), Toast.LENGTH_SHORT).show()

        scope.launch {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            if (activity.isFinishing) return@launch
            if (release == null) {
                if (manual) Toast.makeText(activity, Lang.tr("Pa ka tcheke mizajou kounye a. Verifye entènèt la."), Toast.LENGTH_LONG).show()
                return@launch
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            val current = installedCode(activity)
            if (release.versionCode <= current) {
                if (manual) {
                    Toast.makeText(
                        activity,
                        Lang.tr("Ou gen dènye vèsyon an ({v}).").replace("{v}", installedName(activity)),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return@launch
            }

            // "Pita" deja klike pou vèsyon sa a → pa deranje ajan an otomatikman
            val snoozed = prefs.getLong(KEY_SNOOZE_CODE, -1L) == release.versionCode &&
                now < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
            if (!manual && snoozed && !release.mandatory) return@launch

            showDialog(activity, release)
        }
    }

    private fun fetchLatest(): Release? {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        for (u in VERSION_URLS) {
            try {
                // ?t= pou evite yon vye kopi nan cache
                val req = Request.Builder().url("$u?t=${System.currentTimeMillis() / 60000}").build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use
                    val body = res.body?.string() ?: return@use
                    val j = JSONObject(body)
                    val code = j.optLong("versionCode", 0L)
                    val rawUrl = j.optString("url", "").trim()
                    if (code <= 0 || rawUrl.isEmpty()) return@use
                    val abs = URL(URL(u), rawUrl).toString() // "/download/x.apk" → URL konplè
                    return Release(
                        versionCode = code,
                        versionName = j.optString("versionName", code.toString()),
                        url = abs,
                        notes = j.optString("notes", ""),
                        mandatory = j.optBoolean("mandatory", false),
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "version.json pa disponib sou $u: ${e.message}")
            }
        }
        return null
    }

    private fun showDialog(activity: Activity, r: Release) {
        showing = true
        val msg = buildString {
            append(Lang.tr("Gen yon nouvo vèsyon Plus Lotterie:"))
            append(" ").append(r.versionName)
            append("\n").append(Lang.tr("Vèsyon ou genyen an:")).append(" ").append(installedName(activity))
            if (r.notes.isNotBlank()) append("\n\n").append(r.notes)
            if (r.mandatory) append("\n\n").append(Lang.tr("Mizajou sa a obligatwa pou w kontinye vann."))
        }
        val b = AlertDialog.Builder(activity)
            .setTitle(Lang.tr("Nouvo mizajou disponib"))
            .setMessage(msg)
            .setCancelable(!r.mandatory)
            .setPositiveButton(Lang.tr("TELECHAJE")) { _, _ -> download(activity, r) }
            .setOnDismissListener { showing = false }
        if (!r.mandatory) {
            b.setNegativeButton(Lang.tr("Pita")) { _, _ ->
                activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_SNOOZE_CODE, r.versionCode)
                    .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
                    .apply()
            }
        }
        b.show()
    }

    /** DownloadManager → ekran enstalasyon. Si sa echwe: navigatè a. */
    private fun download(activity: Activity, r: Release) {
        val app = activity.applicationContext
        try {
            // Android 8+: APK a dwe gen dwa "Enstale aplikasyon enkoni"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity, Lang.tr("Pèmèt \"Enstale aplikasyon enkoni\" pou Plus Lotterie, epi klike TELECHAJE ankò."), Toast.LENGTH_LONG).show()
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                )
                return
            }

            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "plus-lotterie-${r.versionName}.apk".replace(Regex("[^A-Za-z0-9._-]"), "_")
            val req = DownloadManager.Request(Uri.parse(r.url))
                .setTitle("Plus Lotterie ${r.versionName}")
                .setDescription(Lang.tr("Mizajou APK"))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = dm.enqueue(req)
            Toast.makeText(activity, Lang.tr("Mizajou a ap telechaje... Gade notifikasyon an."), Toast.LENGTH_LONG).show()

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                    try { app.unregisterReceiver(this) } catch (_: Exception) {}
                    val uri = dm.getUriForDownloadedFile(id)
                    if (uri == null) {
                        openInBrowser(app, r.url)
                        return
                    }
                    try {
                        app.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Pa ka louvri enstalasyon an", e)
                        openInBrowser(app, r.url)
                    }
                }
            }
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager echwe, n ap louvri navigatè a", e)
            openInBrowser(app, r.url)
        }
    }

    private fun openInBrowser(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Pa gen navigatè", e)
            Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
        }
    }
}
