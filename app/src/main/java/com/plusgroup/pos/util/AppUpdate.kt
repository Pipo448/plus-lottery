package com.plusgroup.pos.util

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private const val KEY_DL_CODE = "dl_code"        // vèsyon nou te telechaje
    private const val KEY_DL_FROM = "dl_from_code"   // vèsyon ki te enstale lè sa a
    private const val KEY_DL_AT = "dl_at"
    private const val STUCK_MS = 30 * 60 * 1000L     // 30 min apre telechajman
    // Tcheke otomatik sou TOUT ekran yo (BaseActivity), maks 1 fwa chak 20 min
    private const val AUTO_INTERVAL_MS = 20 * 60 * 1000L       // 20 min
    private const val SNOOZE_MS = 2 * 60 * 60 * 1000L          // "Pita" = 2 è, apre sa mesaj la tounen

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

            // SEKIRITE ANTI-BOUK: nou te deja telechaje vèsyon sa a, men APK ki
            // enstale a toujou menm jan (egz. yo mete yon ansyen APK sou sit la
            // ak yon version.json ki di plis). Pa deranje ajan an otomatikman ankò
            // pou vèsyon sa a — li ka toujou eseye nan Paramèt → MIZAJOU.
            val stuck = prefs.getLong(KEY_DL_CODE, -1L) == release.versionCode &&
                prefs.getLong(KEY_DL_FROM, -1L) == current &&
                now - prefs.getLong(KEY_DL_AT, 0L) > STUCK_MS
            if (!manual && stuck) {
                Log.w(TAG, "Vèsyon ${release.versionCode} telechaje deja men li pa enstale/ pa pi nouvo — pa mande ankò")
                return@launch
            }

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
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DL_CODE, r.versionCode)
                .putLong(KEY_DL_FROM, installedCode(app))
                .putLong(KEY_DL_AT, System.currentTimeMillis())
                .apply()
            // Fenèt pwogrè DIRÈKTEMAN sou ekran an (ajan an pa bezwen chèche notifikasyon)
            showProgress(activity, dm, id, r)
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager echwe, n ap louvri navigatè a", e)
            openInBrowser(app, r.url)
        }
    }

    /**
     * Fenèt "Ap telechaje mizajou a" ak ba pwogrè + pousantaj + MB.
     * Li tcheke DownloadManager chak ½ segonn. Lè l fini → ekran Enstale a
     * louvri pou kont li. Si l echwe → mesaj klè + bouton "Eseye nan navigatè".
     */
    private fun showProgress(activity: Activity, dm: DownloadManager, id: Long, r: Release) {
        val app = activity.applicationContext
        val dp = activity.resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            minimumHeight = (10 * dp).toInt()
        }
        val pct = TextView(activity).apply {
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            text = "0%"
        }
        val info = TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            text = Lang.tr("Ap konekte...")
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(pct)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            })
            addView(info)
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        val dialog = AlertDialog.Builder(activity)
            .setTitle(Lang.tr("Ap telechaje mizajou a") + " · " + r.versionName)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(Lang.tr("Anile")) { _, _ ->
                done = true
                try { dm.remove(id) } catch (_: Exception) {}
            }
            .create()
        dialog.show()

        fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f", b / 1048576.0)

        fun failed(msg: String) {
            done = true
            if (dialog.isShowing) dialog.dismiss()
            if (activity.isFinishing) return
            AlertDialog.Builder(activity)
                .setTitle(Lang.tr("Mizajou a pa telechaje"))
                .setMessage(msg)
                .setPositiveButton(Lang.tr("Eseye nan navigatè")) { _, _ -> openInBrowser(app, r.url) }
                .setNegativeButton(Lang.tr("Fèmen"), null)
                .show()
        }

        val tick = object : Runnable {
            override fun run() {
                if (done) return
                var status = -1
                var got = 0L
                var total = -1L
                var mime: String? = null
                try {
                    dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (c.moveToFirst()) {
                            status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            mime = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "query DownloadManager", e)
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        done = true
                        if (dialog.isShowing) dialog.dismiss()
                        // Sit la te bay yon paj HTML olye APK a (fichye a poko sou sit la)
                        if (mime != null && mime!!.contains("html", ignoreCase = true) || (total in 1..200_000)) {
                            try { dm.remove(id) } catch (_: Exception) {}
                            failed(Lang.tr("Fichye APK a poko disponib sou sit la. Eseye ankò pita oswa kontakte administratè a."))
                            return
                        }
                        installDownloaded(activity, dm, id, r)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        try { dm.remove(id) } catch (_: Exception) {}
                        failed(Lang.tr("Telechajman an echwe. Verifye entènèt la epi eseye ankò."))
                    }
                    -1 -> {
                        // Telechajman an anile (pa egzanp nan notifikasyon an)
                        done = true
                        if (dialog.isShowing) dialog.dismiss()
                    }
                    else -> {
                        if (total > 0) {
                            val p = ((got * 100) / total).toInt().coerceIn(0, 100)
                            bar.isIndeterminate = false
                            bar.progress = p
                            pct.text = "$p%"
                            info.text = "${mb(got)} / ${mb(total)} MB"
                        } else if (got > 0) {
                            info.text = "${mb(got)} MB"
                        } else if (status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) {
                            info.text = Lang.tr("Ap tann koneksyon an...")
                        }
                        handler.postDelayed(this, 500)
                    }
                }
            }
        }
        handler.post(tick)
    }

    private fun installDownloaded(activity: Activity, dm: DownloadManager, id: Long, r: Release) {
        val app = activity.applicationContext
        val uri = dm.getUriForDownloadedFile(id)
        if (uri == null) {
            openInBrowser(app, r.url)
            return
        }
        try {
            Toast.makeText(app, Lang.tr("Telechajman fini. Klike \"Enstale\"."), Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Pa ka louvri enstalasyon an", e)
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
