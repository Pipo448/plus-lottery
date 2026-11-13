package com.plusgroup.pos.util

import android.content.Context
import com.google.gson.Gson

/**
 * Fich ajan an ap ekri a (bouyon). Si l peze RETOU oswa l kite ekran an
 * pandan l gen boul sou fich la, lè l retounen tout boul yo la toujou.
 *
 *  - Sove lokalman sou aparèy la (SharedPreferences), pa sou sèvè a.
 *  - Bouyon an valab sèlman menm jou a (lè Ayiti) e pou menm ajan an.
 *  - Efase otomatikman lè fich la vann oswa lè ajan an peze "Efase tout".
 */
object FicheDraft {
    private const val PREFS = "plus_fiche_draft"
    private const val KEY = "draft"
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

    data class Line(
        val drawName: String = "",
        val numero: String = "",
        val price: Double = 0.0,
        val category: String = "",
        val optionLabel: String? = null,
    )

    data class Draft(
        val owner: String? = null,
        val day: String = "",
        val savedAt: Long = 0L,
        val drawIds: List<String> = emptyList(),
        val gameId: String? = null,
        val lines: List<Line> = emptyList(),
        val boul: String = "",
        val montant: String = "",
    )

    private val gson = Gson()

    /** Ajan ki konekte a (champ "sub" nan token an) — bouyon an pa pase bay yon lòt ajan. */
    fun owner(ctx: Context): String? = try {
        val tok = SessionManager(ctx).getToken() ?: return null
        val part = tok.split('.').getOrNull(1) ?: return null
        val json = String(android.util.Base64.decode(part, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
        org.json.JSONObject(json).optString("sub").ifBlank { null }
    } catch (_: Exception) { null }

    private fun today(): String = Dates.format(java.util.Date(), "yyyy-MM-dd")

    fun save(ctx: Context, owner: String?, drawIds: List<String>, gameId: String?, lines: List<Line>, boul: String, montant: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (lines.isEmpty() && boul.isBlank() && montant.isBlank()) {
            p.edit().remove(KEY).apply()
            return
        }
        val d = Draft(owner, today(), System.currentTimeMillis(), drawIds, gameId, lines, boul, montant)
        try { p.edit().putString(KEY, gson.toJson(d)).apply() } catch (_: Exception) { }
    }

    fun load(ctx: Context, owner: String?): Draft? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val d = try { gson.fromJson(raw, Draft::class.java) } catch (_: Exception) { null } ?: return null
        val fresh = d.day == today() && System.currentTimeMillis() - d.savedAt < MAX_AGE_MS
        val sameOwner = d.owner.isNullOrBlank() || owner.isNullOrBlank() || d.owner == owner
        if (!fresh || !sameOwner) { clear(ctx); return null }
        return d
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
