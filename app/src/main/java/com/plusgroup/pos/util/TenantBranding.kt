package com.plusgroup.pos.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.plusgroup.pos.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Idantite tenant lan (Mon compte → Paramètres): logo, non, telefòn,
 * adrès, mesaj. Yo parèt sou akèy la epi anlè chak fich enprime.
 *
 *  • Done yo rafrechi chak 5 minit (oswa lè w fòse l), kidonk lè admin lan
 *    chanje logo a sou web la, POS yo wè l san yo pa rekonekte.
 *  • Logo a sere nan cache aparèy la: si pa gen entènèt, dènye logo a
 *    toujou parèt e toujou enprime.
 */
object TenantBranding {

    private const val PREFS = "plus_pos_branding"
    private const val REFRESH_MS = 2 * 60 * 1000L
    private const val PRINT_WIDTH = 288 // ~ 3/4 papye 58 mm (384 pwen)
    private const val PRINT_MAX_HEIGHT = 150

    @Volatile var companyName: String? = null; private set
    @Volatile var phone: String? = null; private set
    @Volatile var address: String? = null; private set
    @Volatile var message: String? = null; private set
    /** Ajan an jan l parèt anlè fich yo ak rapò yo: "004 · Jean Pierre" */
    @Volatile var vendeur: String? = null; private set
    @Volatile var branch: String? = null; private set
    @Volatile var printLogo: Boolean = true; private set

    /** TOUT paramèt Mon compte → Paramètres (kle → valè). Wè TenantRules. */
    @Volatile var settings: Map<String, String> = emptyMap(); private set

    @Volatile private var loadedAt = 0L
    @Volatile private var screenLogo: Bitmap? = null
    @Volatile private var printBitmap: Bitmap? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun logoFile(context: Context) = File(context.applicationContext.filesDir, "tenant_logo.img")

    private fun agentLabel(code: String?, name: String?): String? {
        val c = code?.trim().orEmpty()
        val n = name?.trim().orEmpty()
        return when {
            c.isEmpty() && n.isEmpty() -> null
            c.isEmpty() -> n
            n.isEmpty() || n.equals(c, ignoreCase = true) -> c
            else -> "$c - $n"
        }
    }

    /** Valè ki sere sou aparèy la, SAN tann rezo a (pou enprime touswit). */
    fun ensureCached(context: Context) {
        if (companyName == null) restoreFromPrefs(context.applicationContext)
    }

    /**
     * Ekran login nan rekonèt bolèt la ak DEVICE ID a: sere non ak logo
     * bolèt la TOUSWIT (anvan konekte), pou akèy la ak fich yo.
     * newTenant = true → efase enfòmasyon ansyen bolèt la (telefòn, adrès...).
     * name = null + logoUrl = null → retounen sou logo Plus Group.
     */
    suspend fun setServer(context: Context, name: String?, logoUrl: String?, newTenant: Boolean) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldUrl = prefs.getString("logo_url", "").orEmpty()
        val url = logoUrl.orEmpty()
        val edit = prefs.edit()
            .putString("company_name", name)
            .putString("logo_url", url)
        if (newTenant) edit.remove("phone").remove("address").remove("message").remove("vendeur").remove("branch").remove("settings_json")
        edit.apply()
        if (newTenant) loadedAt = 0L
        if (url != oldUrl || (url.isNotBlank() && !logoFile(app).exists()) || (url.isBlank() && logoFile(app).exists())) {
            downloadLogo(app, url) // url vid → efase logo ansyen bolèt la
        }
        restoreFromPrefs(app, reloadImages = true)
    }

    /** Chaje done yo (sèvè a), sinon sa ki te sere sou aparèy la. */
    suspend fun load(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        restoreFromPrefs(app)
        if (!force && System.currentTimeMillis() - loadedAt < REFRESH_MS) return

        try {
            val api = ApiClient.getService(app)
            val settings = api.getCompanySettings().body()?.data.orEmpty()
                .associate { (it.key ?: "") to (it.value ?: "") }
            val profile = try { api.getProfile().body()?.data } catch (_: Exception) { null }

            val name = settings["company_name"]?.takeIf { it.isNotBlank() } ?: profile?.tenantName
            val logoUrl = settings["logo"].orEmpty()

            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val oldUrl = prefs.getString("logo_url", "").orEmpty()
            prefs.edit()
                .putString("company_name", name)
                .putString("phone", settings["company_phone"])
                .putString("address", settings["addresse"])
                .putString("message", settings["message"])
                .putString("vendeur", agentLabel(profile?.username, profile?.fullName))
                .putString("branch", profile?.branchName)
                .putBoolean("print_logo", settings["logo_sou_fich"] != "non")
                .putString("logo_url", logoUrl)
                .putString("settings_json", com.google.gson.Gson().toJson(settings))
                .apply()

            if (logoUrl != oldUrl || (logoUrl.isNotBlank() && !logoFile(app).exists()) || (logoUrl.isBlank() && logoFile(app).exists())) {
                downloadLogo(app, logoUrl)
            }
            loadedAt = System.currentTimeMillis()
            restoreFromPrefs(app, reloadImages = true)
        } catch (_: Exception) {
            // San entènèt: kenbe sa ki te sere a
        }
    }

    private suspend fun downloadLogo(context: Context, url: String): Unit = withContext(Dispatchers.IO) {
        val file = logoFile(context)
        if (url.isBlank()) {
            file.delete()
            return@withContext
        }
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@use
                val bytes = res.body?.bytes() ?: return@use
                if (bytes.isEmpty() || bytes.size > 3 * 1024 * 1024) return@use
                val tmp = File(file.parentFile, "tenant_logo.tmp")
                tmp.writeBytes(bytes)
                if (BitmapFactory.decodeFile(tmp.absolutePath) != null) tmp.renameTo(file) else tmp.delete()
            }
        } catch (_: Exception) {
        }
        Unit
    }

    private fun restoreFromPrefs(context: Context, reloadImages: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        companyName = prefs.getString("company_name", null)
        phone = prefs.getString("phone", null)
        address = prefs.getString("address", null)
        message = prefs.getString("message", null)
        vendeur = prefs.getString("vendeur", null)
        branch = prefs.getString("branch", null)
        printLogo = prefs.getBoolean("print_logo", true)
        settings = try {
            val json = prefs.getString("settings_json", null)
            if (json.isNullOrBlank()) emptyMap()
            else com.google.gson.Gson().fromJson<Map<String, String>>(
                json, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type,
            ).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
        if (reloadImages || screenLogo == null) {
            val file = logoFile(context)
            screenLogo = if (file.exists()) decodeSampled(file, 512) else null
            printBitmap = null
        }
    }

    /** Logo pou ekran an (null = pa gen logo, kite logo Plus la). */
    fun logo(context: Context): Bitmap? {
        if (screenLogo == null) restoreFromPrefs(context.applicationContext, reloadImages = true)
        return screenLogo
    }

    /**
     * Logo pou enprimant tèmik la: fon blan, nwa/blan, lajè fiks.
     * null si tenant lan pa gen logo oswa si li dezaktive "Enprime logo a".
     */
    fun logoForPrint(context: Context): Bitmap? {
        if (!printLogo) return null
        printBitmap?.let { return it }
        val src = logo(context) ?: return null
        // Lajè maks PRINT_WIDTH, wotè maks PRINT_MAX_HEIGHT (pa gaspiye papye)
        val ratio = minOf(PRINT_WIDTH.toFloat() / src.width, PRINT_MAX_HEIGHT.toFloat() / src.height)
        val w = (src.width * ratio).toInt().coerceAtLeast(8)
        val h = (src.height * ratio).toInt().coerceAtLeast(8)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE) // logo transparan → fon blan
        canvas.drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0f, 0f, null)

        // Nwa/blan ak "dithering" (Floyd–Steinberg): koulè yo (oranj, ble...)
        // tounen ti pwen olye yo disparèt nèt sou papye tèmik la.
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val lum = FloatArray(px.size) { i ->
            val c = px[i]
            (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = lum[i]
                val q = if (old < 128f) 0f else 255f
                px[i] = if (q == 0f) Color.BLACK else Color.WHITE
                val err = old - q
                if (x + 1 < w) lum[i + 1] += err * 7f / 16f
                if (y + 1 < h) {
                    if (x > 0) lum[i + w - 1] += err * 3f / 16f
                    lum[i + w] += err * 5f / 16f
                    if (x + 1 < w) lum[i + w + 1] += err * 1f / 16f
                }
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        printBitmap = out
        return out
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
