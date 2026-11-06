package com.plusgroup.pos.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SÈVÈ APK a (tankou "LOAD SERVEUR" SGL).
 *
 *  • Chak SÈVÈ se yon backend konplè (Render + Supabase) ki ka gen anpil bolèt.
 *  • Lis sèvè yo toujou soti sou sèvè SANTRAL la (SÈVÈ 1, adrès fiks anba a).
 *  • Ajan an chwazi youn; tout demann APK a ale sou adrès sèvè sa a.
 *  • Pa default (premye fwa, oswa ansyen enstalasyon): SÈVÈ 1 — anyen pa kase.
 *
 * Chwa a RETE menm apre "Dekonekte", pou ajan an pa rechwazi l chak fwa.
 */
object ServerConfig {

    /** Sèvè santral la: se la APK a chaje lis sèvè yo. */
    const val CENTRAL_API = "https://plusgroup-lottery-api.onrender.com/api/v1/"
    const val DEFAULT_CODE = "S1"
    const val DEFAULT_NAME = "SÈVÈ 1"

    private const val PREFS = "plus_pos_server"
    private const val KEY_CODE = "code"
    private const val KEY_NAME = "name"
    private const val KEY_API = "api_url"
    private const val KEY_LIST = "list_json"
    private const val KEY_DEVICE_TENANT = "device_tenant"

    data class Server(val code: String, val name: String, val apiUrl: String)

    val DEFAULT = Server(DEFAULT_CODE, DEFAULT_NAME, CENTRAL_API)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "https://x.com" oswa "https://x.com/api/v1" → "https://x.com/api/v1/" ; null si pa https */
    fun normalizeApiUrl(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (!s.startsWith("https://", ignoreCase = true) || s.length < 12) return null
        val base = s.trimEnd('/').removeSuffix("/api/v1")
        return "$base/api/v1/"
    }

    fun current(context: Context): Server {
        val p = prefs(context)
        val code = p.getString(KEY_CODE, null)
        val api = normalizeApiUrl(p.getString(KEY_API, null))
        if (code.isNullOrBlank() || api == null) return DEFAULT
        return Server(code, p.getString(KEY_NAME, null) ?: code, api)
    }

    fun apiUrl(context: Context): String = current(context).apiUrl

    /** Chwazi yon sèvè. Retounen true si se yon LÒT sèvè (sesyon an dwe efase). */
    fun select(context: Context, server: Server): Boolean {
        val api = normalizeApiUrl(server.apiUrl) ?: return false
        val before = current(context)
        prefs(context).edit()
            .putString(KEY_CODE, server.code)
            .putString(KEY_NAME, server.name)
            .putString(KEY_API, api)
            .apply()
        val changed = before.code != server.code || before.apiUrl != api
        if (changed) prefs(context).edit().remove(KEY_DEVICE_TENANT).apply()
        return changed
    }

    // ----- Dènye lis ki te chaje (pou chwazi san entènèt) -----
    fun saveList(context: Context, servers: List<Server>) {
        prefs(context).edit().putString(KEY_LIST, Gson().toJson(servers)).apply()
    }

    fun cachedList(context: Context): List<Server> = try {
        val json = prefs(context).getString(KEY_LIST, null)
        if (json.isNullOrBlank()) emptyList()
        else Gson().fromJson<List<Server?>>(json, object : TypeToken<List<Server?>>() {}.type).orEmpty()
            // Gson pa respekte non-null Kotlin: jete antre ki pa konplè yo
            .mapNotNull { sv ->
                val code = sv?.code as String?
                val name = sv?.name as String?
                val api = normalizeApiUrl(sv?.apiUrl as String?)
                if (code.isNullOrBlank() || api == null) null else Server(code, name ?: code, api)
            }
    } catch (_: Exception) {
        emptyList()
    }

    // ----- Bolèt aparèy sa a mare ak li (DEVICE ID) sou sèvè a -----
    fun deviceTenant(context: Context): String? = prefs(context).getString(KEY_DEVICE_TENANT, null)

    fun saveDeviceTenant(context: Context, slug: String?) {
        prefs(context).edit().putString(KEY_DEVICE_TENANT, slug).apply()
    }
}
