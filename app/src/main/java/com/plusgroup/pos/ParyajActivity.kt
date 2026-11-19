package com.plusgroup.pos

import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.printer.ParyajReceipt
import com.plusgroup.pos.printer.ParyajSelection
import com.plusgroup.pos.printer.PrinterManager
import com.plusgroup.pos.util.ApiErrors
import com.plusgroup.pos.util.DeviceIdHelper
import com.plusgroup.pos.util.Lang
import com.plusgroup.pos.util.Money
import com.plusgroup.pos.util.TenantBranding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * PARYE PLIS+ sou POS (sql/040) — ajan an vann paryaj espòtif kach.
 *
 *  • MATCH / LIVE : lis match yo ak kòt 1 · X · 2 (maj bolèt la deja ladan l).
 *                   "+" → tout mache yo (Doub chans, Plis/Mwens, Andikap, Tou de ekip make).
 *  • TIKÈ         : ba anba a → miz → VANN → resi a enprime (kòd PP + 8 chif).
 *  • VERIFYE      : tape/eskane kòd la → estati → PEYE (se ajan ki vann li a ki peye) / ANILE (10 min).
 *  • RAPÒ         : vant paryaj jodi a (separe de bolèt), komisyon, sa pou peye.
 *
 *  Yon sèl chwa pa match. Si kòt yo chanje anvan VANN, sèvè a voye nouvo kòt yo
 *  (409 ODDS_CHANGED) → ajan an wè yo epi konfime ankò.
 */
class ParyajActivity : BaseActivity() {

    companion object {
        const val EXTRA_CODE = "EXTRA_PARYE_CODE"
        private const val NAVY = 0xFF071A4A.toInt()
        private const val BLUE = 0xFF1F63FF.toInt()
        private const val GOLD = 0xFFFFC21A.toInt()
        private const val RED = 0xFFD80F22.toInt()
        private const val GREEN = 0xFF0F8A3A.toInt()
        private const val INK = 0xFF0B1630.toInt()
        private const val MUTED = 0xFF5B6785.toInt()
        private const val BG = 0xFFEEF2FA.toInt()
        private const val LINE = 0xFFE1E6F0.toInt()
    }

    data class Sel(
        val eventId: String,
        val market: String,
        val outcome: String,
        val point: Double?,
        var odds: Double,
        val label: String,
        val eventLabel: String,
        val commenceAt: String?,
        var changed: Boolean = false,
    )

    private val slip = mutableListOf<Sel>()
    private var info: JsonObject? = null
    private var lastEvents: JsonArray = JsonArray()
    private var tab = 0
    private var requestId = UUID.randomUUID().toString()
    private var acceptChanges = false
    private var stakeText = ""
    private var lastCode: String? = null
    /** false = paryaj poko aktive pou bolèt la: ajan an ka gade match yo e chwazi, men li pa ka vann. */
    private var active = true
    private var previewMessage = "Paryaj poko aktive pou bolèt ou a. Ou ka gade match yo ak kòt yo, men ou pa ka vann tikè. Mande administratè bolèt la aktive \"Parye Plis+\"."

    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabViews: List<TextView>
    private lateinit var slipBar: LinearLayout
    private lateinit var tvSlip: TextView
    private val printer by lazy { PrinterManager(applicationContext) }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result?.contents?.trim()?.uppercase().orEmpty()
        if (code.isNotEmpty()) verify(Regex("PP\\d{8}").find(code)?.value ?: code)
    }

    // ==================== UI ====================
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radius: Int = 14, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(s: String, size: Float = 15f, color: Int = INK, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(s: String, bg: Int, fg: Int = Color.WHITE, onClick: () -> Unit): TextView =
        text(s, 15f, fg, true).apply {
            gravity = Gravity.CENTER
            background = round(bg, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f, top: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top); marginEnd = dp(end) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(Color.WHITE, 16, LINE)
        setPadding(dp(12), dp(10), dp(12), dp(12))
        layoutParams = lp(top = 10)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // Antèt
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NAVY)
            setPadding(dp(8), dp(10), dp(12), dp(10))
        }
        header.addView(text("‹", 30f, Color.WHITE, true).apply {
            setPadding(dp(10), 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        header.addView(text("PARYAJ", 20f, Color.WHITE, true), lp(0, weight = 1f))
        header.addView(text("PARYE PLIS+", 13f, GOLD, true))
        root.addView(header)

        // Onglè
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NAVY)
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        // Endèks lojik yo: 0 MATCH · 1 LIVE · 2 VERIFYE · 3 RAPÒ · 4 PARI (lis pari ajan an vann)
        // Lòd sou ekran an: MATCH · LIVE · PARI · VERIFYE · RAPÒ
        val names = listOf("MATCH", "LIVE", "VERIFYE", "RAPÒ", "PARI")
        tabViews = names.mapIndexed { i, n ->
            text(Lang.tr(n), 13f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                maxLines = 1
                setOnClickListener { selectTab(i) }
            }
        }
        val visual = listOf(0, 1, 4, 2, 3)
        visual.forEachIndexed { pos, i ->
            tabsRow.addView(tabViews[i], lp(0, weight = if (i == 2) 1.25f else 1f, end = if (pos < visual.size - 1) 5 else 0))
        }
        root.addView(tabsRow)

        // Kontni
        scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(16))
        }
        scroll.addView(content)
        // Rale ekran an desann (swipe) → rafrechi match yo / tikè a / rapò a
        val swipe = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this)
        swipe.setColorSchemeColors(BLUE, RED, GOLD)
        swipe.addView(scroll)
        swipe.setOnRefreshListener {
            lifecycleScope.launch {
                if (info == null) loadInfo()
                when (tab) {
                    0 -> loadEvents(false)
                    1 -> loadEvents(true)
                    2 -> if (!active) message("Paryaj poko aktive", previewMessage) else lastCode?.let { verify(it) } ?: showVerify(null)
                    3 -> if (active) loadReport() else message("Paryaj poko aktive", previewMessage)
                    4 -> if (active) loadMyBets(betsDays) else message("Paryaj poko aktive", previewMessage)
                }
                swipe.postDelayed({ swipe.isRefreshing = false }, 1000L)
            }
        }
        root.addView(swipe, lp(h = 0, weight = 1f))

        // Ba tikè a (anba)
        slipBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NAVY)
            setPadding(dp(14), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        tvSlip = text("", 15f, Color.WHITE, true)
        slipBar.addView(tvSlip, lp(0, weight = 1f))
        slipBar.addView(button(Lang.tr("TIKÈ ›"), RED) { openSlip() })
        root.addView(slipBar)

        setContentView(root)

        lifecycleScope.launch {
            loadInfo()
            val code = intent.getStringExtra(EXTRA_CODE)
            if (!code.isNullOrBlank()) {
                selectTab(2)
                verify(code)
            } else {
                selectTab(0)
            }
        }
    }

    private fun selectTab(i: Int) {
        tab = i
        tabViews.forEachIndexed { idx, v ->
            v.background = if (idx == i) round(GOLD, 10) else round(0x22FFFFFF, 10)
            v.setTextColor(if (idx == i) INK else Color.WHITE)
        }
        when (i) {
            0 -> loadEvents(false)
            1 -> loadEvents(true)
            2 -> if (active) showVerify(null) else message("Paryaj poko aktive", previewMessage)
            3 -> if (active) loadReport() else message("Paryaj poko aktive", previewMessage)
            4 -> if (active) loadMyBets(betsDays) else message("Paryaj poko aktive", previewMessage)
            5 -> renderVirtual(reload = true)
        }
        if (i != 5) stopVgTicker()
    }

    private fun loading() {
        content.removeAllViews()
        content.addView(ProgressBar(this).apply { isIndeterminate = true }, lp(top = 40).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun message(title: String, body: String) {
        content.removeAllViews()
        val c = card()
        c.addView(text(Lang.tr(title), 17f, INK, true))
        c.addView(text(Lang.tr(body), 14f, MUTED).apply { setPadding(0, dp(6), 0, 0) })
        content.addView(c)
    }

    private fun errorOf(raw: String?, fallback: String): String = ApiErrors.parse(raw) ?: fallback

    // ==================== DONE ====================
    private suspend fun loadInfo() {
        try {
            val res = ApiClient.getService(applicationContext).paryeInfo()
            if (res.isSuccessful) {
                info = obj(res.body(), "data")
                // Ansyen sèvè yo pa voye "active" → konsidere l aktif
                active = info?.get("active")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                str(info, "message")?.let { previewMessage = it }
            } else {
                val raw = res.errorBody()?.string()
                message("Paryaj pa disponib", errorOf(raw, "Administratè a pa louvri paryaj sou POS."))
            }
        } catch (e: java.io.IOException) {
            message("Pa gen koneksyon", "Verifye entènèt la epi eseye ankò.")
        } catch (e: Exception) {
            message("Erè", "Pa ka chaje paryaj: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Gson: getAsJsonObject() voye ClassCastException si valè a se `null` (JsonNull) → itilize sa yo. */
    private fun obj(o: JsonObject?, k: String): JsonObject? =
        o?.get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun bool(o: JsonObject?, k: String): Boolean =
        try { o?.get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean == true } catch (_: Exception) { false }

    private fun arr(o: JsonObject?, k: String): JsonArray? =
        o?.get(k)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun num(o: JsonObject?, k: String, def: Double = 0.0): Double =
        try { o?.get(k)?.takeIf { !it.isJsonNull }?.asDouble ?: def } catch (_: Exception) { def }

    private fun str(o: JsonObject?, k: String): String? =
        try { o?.get(k)?.takeIf { !it.isJsonNull }?.asString } catch (_: Exception) { null }

    private val haiti: TimeZone = TimeZone.getTimeZone("America/Port-au-Prince")
    private fun whenText(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val d = p.parse(iso.take(19)) ?: return ""
            SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = haiti }.format(d)
        } catch (_: Exception) { "" }
    }

    // ==================== MATCH ====================
    private fun loadEvents(live: Boolean) {
        if (info == null) return
        loading()
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeEvents(live = if (live) "1" else null)
                if (!res.isSuccessful) {
                    message("Erè", errorOf(res.errorBody()?.string(), "Pa ka chaje match yo."))
                    return@launch
                }
                lastEvents = arr(res.body(), "data") ?: JsonArray()
                renderEvents()
            } catch (e: java.io.IOException) {
                message("Pa gen koneksyon", "Verifye entènèt la epi eseye ankò.")
            } catch (e: Exception) {
                message("Erè", "Pa ka montre done yo: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Filt lis match yo: "ALL" = tout, "TOP" = top match sèlman, oswa non yon peyi. */
    private var filter = "ALL"

    private data class Ev(val o: JsonObject, val country: String, val flag: String, val cRank: Int,
                          val league: String, val lRank: Int, val start: String, val top: Boolean)

    private fun evOf(e: JsonObject): Ev {
        val league = str(e, "leagueName") ?: str(e, "league") ?: ""
        return Ev(
            e,
            country = str(e, "country") ?: "Lòt chanpyona",
            flag = str(e, "flag") ?: "🏳️",
            cRank = num(e, "countryRank", 90.0).toInt(),
            league = league,
            lRank = num(e, "leagueRank", 50.0).toInt(),
            start = str(e, "commenceAt") ?: "",
            top = bool(e, "top"),
        )
    }

    /**
     * Lis match yo klase pwofesyonèl:
     *   [ba filt peyi]  →  ⭐ TOP MATCH  →  PEYI (drapo)  →  lig  →  match yo pa lè.
     * Peyi yo nan lòd backend lan bay (Entènasyonal, gwo chanpyona yo, lòt yo).
     */
    private fun renderEvents() {
        content.removeAllViews()
        if (!active) content.addView(previewBanner())
        if (active && vgEnabled()) content.addView(button(Lang.tr("🎡 JWÈT VITYÈL: ROULETTE · LUCKY 6 · AVYON"), 0xFF0B6B3C.toInt()) { selectTab(5) }, lp(top = 6))
        if (active) content.addView(button(Lang.tr("KÒD PATAJ: CHAJE YON PARI"), BLUE) { askShareCode() }, lp(top = 6))
        if (lastEvents.size() == 0) {
            message(if (tab == 1) "Pa gen match an dirèk" else "Pa gen match", "Tounen pita.")
            return
        }
        val all = lastEvents.mapNotNull { runCatching { evOf(it.asJsonObject) }.getOrNull() }
        val tops = all.filter { it.top }.sortedBy { it.start }
        val countries = all.groupBy { it.country }.entries
            .sortedWith(compareBy({ it.value.first().cRank }, { it.key }))
        if (filter == "TOP" && tops.isEmpty()) filter = "ALL"
        if (filter != "ALL" && filter != "TOP" && countries.none { it.key == filter }) filter = "ALL"

        // 1) Ba filt (glise sou kote): Tout · ⭐ Top · 🇬🇧 Angletè (12) …
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(2)) }
        fun chip(label: String, key: String) {
            val on = filter == key
            bar.addView(text(label, 13f, if (on) Color.WHITE else INK, true).apply {
                background = round(if (on) NAVY else Color.WHITE, 18, if (on) NAVY else LINE)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener { filter = key; renderEvents(); scroll.scrollTo(0, 0) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        chip("Tout (${all.size})", "ALL")
        if (tops.isNotEmpty()) chip("⭐ Top", "TOP")
        countries.forEach { (c, list) -> chip("${list.first().flag} $c (${list.size})", c) }
        content.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(bar)
        })

        // 2) TOP MATCH (sèlman nan "Tout" ak "Top")
        if ((filter == "ALL" || filter == "TOP") && tops.isNotEmpty()) {
            content.addView(sectionHeader("⭐  TOP MATCH", "${tops.size}", GOLD, INK))
            tops.forEach { content.addView(eventCard(it.o, "${it.flag} ${it.league}")) }
        }
        if (filter == "TOP") return

        // 3) Peyi → lig → match
        for ((country, list) in countries) {
            if (filter != "ALL" && filter != country) continue
            content.addView(sectionHeader("${list.first().flag}  ${country.uppercase()}", "${list.size}", NAVY, Color.WHITE))
            list.groupBy { it.league }.entries
                .sortedWith(compareBy({ it.value.first().lRank }, { it.key }))
                .forEach { (league, evs) ->
                    content.addView(text("$league  ·  ${evs.size}", 13f, BLUE, true).apply { setPadding(dp(4), dp(10), 0, dp(2)) })
                    evs.sortedBy { it.start }.forEach { content.addView(eventCard(it.o)) }
                }
        }
    }

    /** Tèt seksyon (TOP MATCH oswa yon peyi). */
    private fun sectionHeader(title: String, count: String, bg: Int, fg: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = round(bg, 12)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = lp(top = 14)
        }
        row.addView(text(title, 14f, fg, true), lp(0, weight = 1f))
        row.addView(text(count, 13f, fg, true))
        return row
    }

    /** Bann jòn: mòd gade (paryaj poko aktive pou bolèt la). */
    private fun previewBanner(): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFFFFF4D0.toInt(), 14, GOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = lp(top = 10)
        }
        c.addView(text("MÒD GADE", 13f, 0xFF8A5A00.toInt(), true))
        c.addView(text("Paryaj poko aktive pou bolèt ou a. Ou ka gade match yo epi chwazi ekip, men ou pa ka vann tikè.", 13f, INK))
        return c
    }

    /** Notifikasyon lè ajan an eseye vann pandan paryaj poko aktive. */
    private fun notifyInactive() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Paryaj poko aktive")
            .setMessage(previewMessage)
            .setPositiveButton(Lang.tr("OK"), null)
            .show()
    }

    /** Non ekip la ak logo klib li (oswa drapo peyi chanpyona a si pa gen logo) */
    private fun teamRow(name: String, logo: String?, flag: String?): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val flagTv = text(flag ?: "", 15f, INK, false)
        if (!logo.isNullOrBlank()) {
            val iv = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
            row.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
            com.plusgroup.pos.util.TeamLogos.load(logo, iv) {
                iv.visibility = View.GONE
                if (!flag.isNullOrBlank()) row.addView(flagTv, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
            }
        } else if (!flag.isNullOrBlank()) {
            row.addView(flagTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        row.addView(text(name, 16f, INK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** @param tag non lig la (pou TOP MATCH kote match yo soti nan plizyè lig) */
    private fun eventCard(e: JsonObject, tag: String? = null): View {
        val c = card().apply { layoutParams = lp(top = 6) }
        val id = str(e, "id") ?: return c
        val home = str(e, "home") ?: ""
        val away = str(e, "away") ?: ""
        val live = bool(e, "live")
        val score = obj(e, "score")
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val time = if (live) "● LIVE" else whenText(str(e, "commenceAt"))
        top.addView(text(if (tag != null) "$time  ·  $tag" else time, 12f, if (live) RED else MUTED, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, weight = 1f))
        if (live && score != null) top.addView(text("${num(score, "home").toInt()} - ${num(score, "away").toInt()}", 14f, RED, true))
        c.addView(top)
        c.addView(teamRow(home, str(e, "homeLogo"), str(e, "flag")).apply { setPadding(0, dp(4), 0, 0) })
        c.addView(teamRow(away, str(e, "awayLogo"), str(e, "flag")))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val h = obj(e, "h2h")
        val evLabel = "$home - $away"
        val start = str(e, "commenceAt")
        fun add(outcome: String, top: String, label: String) {
            val odds = num(h, outcome)
            row.addView(oddButton(top, odds, isOn(id, "h2h", outcome, null), bool(e, "suspended")) {
                toggle(Sel(id, "h2h", outcome, null, odds, label, evLabel, start))
            }, lp(0, weight = 1f, end = 6))
        }
        add("home", "1", home)
        if (h != null && h.get("draw")?.isJsonNull == false) add("draw", "X", "Nil")
        add("away", "2", away)
        row.addView(button("+", BLUE) { openMarkets(id) }, lp(dp(52)))
        c.addView(row)
        return c
    }

    /** locked = kòt bloke (gòl / kòt pa ajou): pal ak kadna, pa ka klike */
    private fun oddButton(top: String, odds: Double, on: Boolean, locked: Boolean = false, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (on) round(GOLD, 12) else round(0xFFE8EEFF.toInt(), 12, 0xFFC9D8FA.toInt())
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isClickable = odds > 1.0 && !locked
            if (odds > 1.0 && !locked) setOnClickListener { onClick() }
            if (locked) alpha = 0.4f
        }
        box.addView(text(if (locked) "🔒 $top" else top, 11f, if (on) INK else MUTED, true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        box.addView(text(if (odds > 1.0) String.format(Locale.US, "%.2f", odds) else "—", 16f, INK, true).apply { gravity = Gravity.CENTER })
        return box
    }

    private fun isOn(eventId: String, market: String, outcome: String, point: Double?): Boolean =
        slip.any { it.eventId == eventId && it.market == market && it.outcome == outcome && it.point == point }

    private fun toggle(s: Sel) {
        val same = slip.firstOrNull { it.eventId == s.eventId && it.market == s.market && it.outcome == s.outcome && it.point == s.point }
        if (same != null) {
            slip.remove(same)
        } else {
            slip.removeAll { it.eventId == s.eventId } // yon sèl chwa pa match
            val max = num(info, "maxSelections", 12.0).toInt()
            if (slip.size >= max) {
                Toast.makeText(this, "Maksimòm $max match pa tikè.", Toast.LENGTH_SHORT).show()
                return
            }
            slip.add(s)
        }
        acceptChanges = false
        refreshSlipBar()
        if (tab == 0 || tab == 1) {
            val y = scroll.scrollY
            renderEvents()
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    // ==================== JWÈT VITYÈL (sql/049) ====================
    // American Roulette + Lucky 6: yon tikè = yon tiraj, chak liy gen pwòp miz li.
    private data class VgLine(val spec: String, val label: String, val odds: Double, val stake: Double)

    private var vgGame = "roulette"
    private var vgType = "n"
    private val vgLines = mutableListOf<VgLine>()
    private var vgReq = UUID.randomUUID().toString()
    private var vgGames: JsonArray = JsonArray()
    private var vgStatus: TextView? = null
    private val vgHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var vgTick: Runnable? = null
    private var vgLoadedAt = 0L
    private var vgOffset = 0L

    /** Mòd franchiz (super admin): "PARYE PLIS+" sèlman sou tikè yo, pa non antrepriz la */
    private fun franchise(): Boolean = info?.get("franchise")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    private fun brandName(): String = if (franchise()) "PARYE PLIS+" else (TenantBranding.companyName?.uppercase() ?: "PLUS GROUP")

    private fun vgEnabled(): Boolean = obj(info, "virtual")?.let { bool(it, "enabled") } == true

    private fun vgNow() = System.currentTimeMillis() + vgOffset
    private fun iso(s: String?): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s!!.take(19))!!.time
    } catch (_: Exception) { 0L }
    private fun clock(ms: Long): String { val s = Math.max(0L, (ms + 999) / 1000); return "${s / 60}:${String.format(Locale.US, "%02d", s % 60)}" }

    private fun vgCur(): JsonObject? = vgGames.firstOrNull { str(it.asJsonObject, "game") == vgGame }?.asJsonObject

    private fun stopVgTicker() { vgTick?.let { vgHandler.removeCallbacks(it) }; vgTick = null }

    private fun startVgTicker() {
        stopVgTicker()
        val r = object : Runnable {
            override fun run() {
                if (tab != 5) return
                updateVgStatus()
                if (System.currentTimeMillis() - vgLoadedAt > 5000) loadVgState()
                vgHandler.postDelayed(this, 1000)
            }
        }
        vgTick = r
        vgHandler.postDelayed(r, 1000)
    }

    private fun loadVgState(after: (() -> Unit)? = null) {
        vgLoadedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).vgState()
                val d = obj(res.body(), "data")
                if (res.isSuccessful && d != null) {
                    vgGames = arr(d, "games") ?: JsonArray()
                    val st = iso(str(d, "serverTime"))
                    if (st > 0) vgOffset = st - System.currentTimeMillis()
                }
            } catch (_: Exception) { }
            updateVgStatus()
            after?.invoke()
        }
    }

    private fun updateVgStatus() {
        val tv = vgStatus ?: return
        val g = vgCur() ?: run { tv.text = Lang.tr("Ap chaje…"); return }
        val b = obj(g, "betting")
        val last = obj(g, "last")
        val now = vgNow()
        val closes = iso(str(b, "closesAt"))
        val draw = iso(str(b, "drawAt"))
        val lastRes = last?.let { obj(it, "result") }
        if (vgGame == "crash") {
            // Avyon: sèvè a pa bay pwen kote l ap tonbe pandan vòl la; nou kalkile miltiplikatè a ak lè a
            val fl = obj(g, "flying")
            val flAt = iso(str(fl, "drawAt"))
            val lastX = lastRes?.let { num(it, "crash") }
            val hist = (arr(g, "history") ?: JsonArray()).take(6).joinToString("  ") { h -> "x" + String.format(Locale.US, "%.2f", num(obj(h.asJsonObject, "result"), "crash")) }
            val head = when {
                fl != null && flAt in 1..now -> "✈️ Vòl #${str(fl, "short")} AP VOLE: x${String.format(Locale.US, "%.2f", Math.floor(Math.exp(0.08 * (now - flAt) / 1000.0) * 100) / 100)}"
                now < closes -> "Vòl #${str(b, "short")} · paryaj fèmen nan ${clock(closes - now)}"
                else -> "Paryaj fèmen · vòl #${str(b, "short")} dekole nan ${clock(draw - now)}"
            }
            val tail = if (lastX != null) "\nDènye vòl #${str(last, "short")}: tonbe nan x${String.format(Locale.US, "%.2f", lastX)}" else ""
            tv.text = "$head$tail${if (hist.isNotEmpty()) "\n$hist" else ""}"
            return
        }
        val lastTxt = if (lastRes == null) "" else if (vgGame == "roulette") "Dènye rezilta #${str(last, "short")}: ${str(lastRes, "number")}"
            else "Dènye tiraj #${str(last, "short")}: premye boul ${arr(lastRes, "balls")?.firstOrNull()?.asInt ?: "-"}"
        val head = if (now < closes) "Tiraj #${str(b, "short")} · paryaj fèmen nan ${clock(closes - now)}"
            else "Paryaj fèmen · tiraj #${str(b, "short")} nan ${clock(draw - now)}"
        tv.text = if (lastTxt.isEmpty()) head else "$head\n$lastTxt"
    }

    private fun renderVirtual(reload: Boolean = false) {
        if (!active || !vgEnabled()) { message("Jwèt vityèl", "Jwèt vityèl yo poko ouvè pou antrepriz ou a. Administratè a ka ouvri yo nan panèl la: Parye Plis+ → Paramèt."); return }
        val allowed = arr(obj(info, "virtual"), "games")?.map { it.asString } ?: listOf("roulette", "lucky6", "crash")
        if (vgGame !in allowed && allowed.isNotEmpty()) vgGame = allowed.first()
        content.removeAllViews()

        // Chwa jwèt la
        val gRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val gList = listOf("roulette" to "🎡 ROULETTE", "lucky6" to "🎱 LUCKY 6", "crash" to "✈️ AVYON").filter { it.first in allowed }
        gList.forEachIndexed { i, (k, l) ->
            val on = k == vgGame
            gRow.addView(button(l, if (on) GOLD else NAVY, if (on) INK else Color.WHITE) {
                if (vgGame != k) { vgGame = k; vgLines.clear(); vgType = when (k) { "roulette" -> "n"; "crash" -> "auto"; else -> "pick" }; vgReq = UUID.randomUUID().toString(); renderVirtual() }
            }, lp(0, weight = 1f, end = if (i < gList.size - 1) 6 else 0))
        }
        content.addView(gRow)

        val st = card()
        val status = text(Lang.tr("Ap chaje…"), 15f, INK, true)
        vgStatus = status
        st.addView(status)
        content.addView(st, lp(top = 8))

        // Antre yon paryaj
        val c = card()
        if (vgGame == "crash") {
            vgType = "auto"
            c.addView(text(Lang.tr("Avyon: parye anvan l dekole. Si miltiplikatè a rive nan kote ou chwazi a anvan avyon an tonbe, ou genyen miz × miltiplikatè. Yon sèl paryaj pa tikè."), 14f, MUTED, true))
            val etX = EditText(this).apply {
                hint = Lang.tr("Retire otomatikman nan (egz. 2.00)")
                setText("2.00")
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 18f
            }
            c.addView(etX, lp(top = 8))
            val pr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("1.5", "2", "3", "5", "10").forEachIndexed { i, x ->
                pr.addView(text("x$x", 14f, INK, true).apply {
                    gravity = Gravity.CENTER; background = round(0xFFEEF2FB.toInt(), 10); setPadding(dp(4), dp(9), dp(4), dp(9))
                    setOnClickListener { etX.setText(x) }
                }, lp(0, weight = 1f, end = if (i < 4) 6 else 0))
            }
            c.addView(pr, lp(top = 6))
            val etS = EditText(this).apply {
                hint = Lang.tr("Miz (HTG)")
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 18f
            }
            c.addView(etS, lp(top = 4))
            c.addView(button(Lang.tr("METE SOU TIKÈ A"), BLUE) {
                val stake = etS.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                if (stake <= 0) { Toast.makeText(this, Lang.tr("Mete miz la."), Toast.LENGTH_SHORT).show(); return@button }
                val line = vgBuild("auto", etX.text.toString(), stake) ?: return@button
                vgLines.clear(); vgLines.add(line)
                vgReq = UUID.randomUUID().toString()
                renderVirtual()
            }, lp(top = 8))
        } else {
        c.addView(text(Lang.tr("Kalite paryaj"), 14f, MUTED, true))
        val types: List<Pair<String, String>> = if (vgGame == "roulette") listOf(
            "n" to "Nimewo x36", "split" to "Cheval x18", "street" to "Street x12", "corner" to "Katye x9", "line" to "Liy 6 x6", "five" to "0-00-1-2-3 x7",
            "dozen:1" to "1-12 x3", "dozen:2" to "13-24 x3", "dozen:3" to "25-36 x3", "col:1" to "Kolòn 1 x3", "col:2" to "Kolòn 2 x3", "col:3" to "Kolòn 3 x3",
            "red" to "Wouj x2", "black" to "Nwa x2", "even" to "Pè x2", "odd" to "Enpè x2", "low" to "1-18 x2", "high" to "19-36 x2",
        ) else listOf(
            "pick" to "6 nimewo", "first:over" to "Premye > 24,5", "first:under" to "Premye < 24,5", "first:even" to "Premye pè", "first:odd" to "Premye enpè",
            "sum5:over" to "Total 5 > 122,5", "sum5:under" to "Total 5 < 122,5", "more:even" to "Plis pè (5)", "more:odd" to "Plis enpè (5)",
            "first:color:red" to "Wouj x7", "first:color:green" to "Vèt x7", "first:color:blue" to "Ble x7", "first:color:purple" to "Mov x7",
            "first:color:brown" to "Mawon x7", "first:color:yellow" to "Jòn x7", "first:color:orange" to "Zoranj x7", "first:color:black" to "Nwa x7",
        )
        types.chunked(3).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { i, (k, l) ->
                val on = k == vgType
                r.addView(text(l, 13f, if (on) Color.WHITE else INK, true).apply {
                    gravity = Gravity.CENTER; maxLines = 2
                    background = round(if (on) BLUE else 0xFFEEF2FB.toInt(), 10)
                    setPadding(dp(4), dp(9), dp(4), dp(9))
                    setOnClickListener { vgType = k; renderVirtual() }
                }, lp(0, weight = 1f, end = if (i < row.size - 1) 6 else 0))
            }
            repeat(3 - row.size) { r.addView(View(this), lp(0, weight = 1f)) }
            c.addView(r, lp(top = 6))
        }

        val needsNum = vgType in listOf("n", "split", "street", "corner", "line", "pick")
        val etNum = EditText(this).apply {
            hint = when (vgType) {
                "n" -> "Nimewo (0, 00, 1–36)"
                "split" -> "2 nimewo ki kole (egz. 17 20)"
                "street" -> "Yon nimewo nan ranje a (egz. 34)"
                "corner" -> "Pi piti nimewo kare a (egz. 17)"
                "line" -> "Yon nimewo nan premye ranje a"
                else -> "6 nimewo 1–48 (egz. 3 8 15 22 31 44)"
            }
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 18f
        }
        if (needsNum) {
            c.addView(etNum, lp(top = 8))
            if (vgType == "pick") c.addView(button(Lang.tr("🎲 6 NIMEWO O AZA"), NAVY) {
                etNum.setText((1..48).shuffled().take(6).sorted().joinToString(" "))
            }, lp(top = 4))
        }
        val etStake = EditText(this).apply {
            hint = Lang.tr("Miz (HTG)")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 18f
        }
        c.addView(etStake, lp(top = 4))
        c.addView(button(Lang.tr("AJOUTE SOU TIKÈ A"), BLUE) {
            val stake = etStake.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            if (stake <= 0) { Toast.makeText(this, Lang.tr("Mete miz la."), Toast.LENGTH_SHORT).show(); return@button }
            val line = vgBuild(vgType, etNum.text.toString(), stake)
            if (line == null) return@button
            val i = vgLines.indexOfFirst { it.spec == line.spec }
            if (i >= 0) vgLines[i] = vgLines[i].copy(stake = vgLines[i].stake + stake) else vgLines.add(line)
            vgReq = UUID.randomUUID().toString()
            renderVirtual()
        }, lp(top = 8))
        }
        content.addView(c, lp(top = 8))

        // Tikè a
        val money = Money.format()
        val t = card()
        t.addView(text(Lang.tr("Tikè"), 16f, INK, true))
        if (vgLines.isEmpty()) t.addView(text(Lang.tr("Ajoute omwen yon paryaj."), 14f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        vgLines.forEach { l ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
            r.addView(text("${l.label}  x${if (l.odds % 1.0 == 0.0) l.odds.toInt() else l.odds}", 15f, 0xFF050B1F.toInt(), true), lp(0, weight = 1f))
            r.addView(text("${money.format(l.stake)}", 15f, INK, true).apply { setPadding(dp(8), 0, dp(8), 0) })
            r.addView(text("✕", 18f, RED, true).apply { setPadding(dp(10), dp(4), dp(6), dp(4)); setOnClickListener { vgLines.remove(l); vgReq = UUID.randomUUID().toString(); renderVirtual() } })
            t.addView(r)
        }
        val total = vgLines.sumOf { it.stake }
        if (vgLines.isNotEmpty()) {
            t.addView(text("${Lang.tr("Total miz")}: ${money.format(total)} HTG", 16f, INK, true).apply { setPadding(0, dp(10), 0, 0) })
            t.addView(button(Lang.tr("VANN TIKÈ A"), RED) { vgSell() }, lp(top = 10))
        }
        content.addView(t, lp(top = 8))

        updateVgStatus()
        if (reload || vgGames.size() == 0) loadVgState()
        startVgTicker()
    }

    /** Fòm nan → spec sèvè a (menm règ ak backend lan) */
    private fun vgBuild(type: String, raw: String, stake: Double): VgLine? {
        fun err(m: String): VgLine? { Toast.makeText(this, Lang.tr(m), Toast.LENGTH_LONG).show(); return null }
        val nums = raw.split(Regex("[^0-9]+")).filter { it.isNotBlank() }
        val n = nums.firstOrNull()
        val v = n?.toIntOrNull()
        if (vgGame == "crash") {
            val x = raw.trim().replace(',', '.').toDoubleOrNull() ?: return err("Tape kote pou retire a (egz. 2.00).")
            if (x < 1.01 || x > 100) return err("Retrè otomatik la dwe ant x1.01 ak x100.")
            val xr = Math.floor(x * 100) / 100
            val xs = String.format(Locale.US, "%.2f", xr)
            return VgLine("auto:$xs", "Avyon: retire otomatik nan x$xs", xr, stake)
        }
        if (vgGame == "roulette") {
            return when (type) {
                "n" -> {
                    val x = raw.trim()
                    if (x == "00" || x == "0") VgLine("n:$x", "Nimewo $x", 36.0, stake)
                    else if (v != null && v in 1..36) VgLine("n:$v", "Nimewo $v", 36.0, stake) else err("Nimewo a dwe 0, 00 oswa ant 1 ak 36.")
                }
                "split" -> {
                    val a = nums.getOrNull(0)?.toIntOrNull(); val b = nums.getOrNull(1)?.toIntOrNull()
                    if (a == null || b == null) return err("Tape 2 nimewo (egz. 17 20).")
                    val lo = minOf(a, b); val hi = maxOf(a, b)
                    if (lo < 1 || hi > 36 || !(hi - lo == 3 || (hi - lo == 1 && lo % 3 != 0))) return err("$lo ak $hi pa kole sou tab la.")
                    VgLine("split:$lo-$hi", "Cheval $lo-$hi", 18.0, stake)
                }
                "street" -> { if (v == null || v !in 1..36) return err("Tape yon nimewo ant 1 ak 36."); val r = (v + 2) / 3; VgLine("street:$r", "Street ${3 * r - 2}-${3 * r - 1}-${3 * r}", 12.0, stake) }
                "corner" -> { if (v == null || v < 1 || v > 32 || v % 3 == 0) return err("Katye: tape pi piti nimewo kare a (pa 3, 6, 9… ni 33–36)."); VgLine("corner:$v", "Katye $v-${v + 1}-${v + 3}-${v + 4}", 9.0, stake) }
                "line" -> { if (v == null || v !in 1..36) return err("Tape yon nimewo ant 1 ak 36."); val r = minOf(11, (v + 2) / 3); VgLine("line:$r", "Liy 6 (${3 * r - 2}-${3 * r + 3})", 6.0, stake) }
                "five" -> VgLine("five", "Senk (0-00-1-2-3)", 7.0, stake)
                "dozen:1" -> VgLine(type, "1ye douzèn (1-12)", 3.0, stake)
                "dozen:2" -> VgLine(type, "2yèm douzèn (13-24)", 3.0, stake)
                "dozen:3" -> VgLine(type, "3yèm douzèn (25-36)", 3.0, stake)
                "col:1", "col:2", "col:3" -> VgLine(type, "Kolòn ${type.last()}", 3.0, stake)
                "red" -> VgLine(type, "Wouj", 2.0, stake)
                "black" -> VgLine(type, "Nwa", 2.0, stake)
                "even" -> VgLine(type, "Pè", 2.0, stake)
                "odd" -> VgLine(type, "Enpè", 2.0, stake)
                "low" -> VgLine(type, "Ba (1-18)", 2.0, stake)
                "high" -> VgLine(type, "Wo (19-36)", 2.0, stake)
                else -> null
            }
        }
        if (type == "pick") {
            val ns = nums.mapNotNull { it.toIntOrNull() }.distinct()
            if (ns.size != 6 || ns.any { it !in 1..48 }) return err("Tape egzakteman 6 nimewo diferan ant 1 ak 48.")
            val sorted = ns.sorted()
            return VgLine("pick:${sorted.joinToString(",")}", "6 nimewo: ${sorted.joinToString("-")}", 10000.0, stake)
        }
        val labels = mapOf(
            "first:over" to "Premye boul > 24,5", "first:under" to "Premye boul < 24,5", "first:even" to "Premye boul pè", "first:odd" to "Premye boul enpè",
            "sum5:over" to "Total 5 premye boul > 122,5", "sum5:under" to "Total 5 premye boul < 122,5", "more:even" to "Plis boul pè nan 5 premye yo", "more:odd" to "Plis boul enpè nan 5 premye yo",
        )
        labels[type]?.let { return VgLine(type, it, 1.85, stake) }
        val colors = mapOf("red" to "Wouj", "green" to "Vèt", "blue" to "Ble", "purple" to "Mov", "brown" to "Mawon", "yellow" to "Jòn", "orange" to "Zoranj", "black" to "Nwa")
        if (type.startsWith("first:color:")) return VgLine(type, "Koulè premye boul: ${colors[type.removePrefix("first:color:")]}", 7.0, stake)
        return null
    }

    private fun vgSell() {
        if (vgLines.isEmpty()) return
        val g = vgCur()
        val b = obj(g, "betting")
        val body = JsonObject().apply {
            addProperty("game", vgGame)
            if (b != null && vgNow() < iso(str(b, "closesAt"))) addProperty("roundId", str(b, "id"))
            addProperty("requestId", vgReq)
            addProperty("deviceCode", DeviceIdHelper.getDeviceId(this@ParyajActivity))
            add("lines", JsonArray().apply { vgLines.forEach { l -> add(JsonObject().apply { addProperty("bet", l.spec); addProperty("stake", l.stake) }) } })
        }
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).vgSell(body)
                if (!res.isSuccessful) {
                    MaterialAlertDialogBuilder(this@ParyajActivity).setTitle(Lang.tr("Tikè a pa pase"))
                        .setMessage(errorOf(res.errorBody()?.string(), "Eseye ankò.")).setPositiveButton("OK", null).show()
                    return@launch
                }
                val d = obj(res.body(), "data") ?: JsonObject()
                printVirtual(d)
                val money = Money.format()
                MaterialAlertDialogBuilder(this@ParyajActivity)
                    .setTitle("${Lang.tr("Tikè vann")} · ${str(d, "code")}")
                    .setMessage("${str(d, "gameName")} #${str(d, "roundShort")}\n" +
                        "${Lang.tr("Total miz")}: ${money.format(num(d, "stake"))} HTG\n" +
                        "${Lang.tr("Gen posib (maks)")}: ${money.format(num(d, "potentialWin"))} HTG")
                    .setPositiveButton("OK", null)
                    .setNeutralButton(Lang.tr("Reenprime")) { _, _ -> printVirtual(d) }
                    .show()
                vgLines.clear()
                vgReq = UUID.randomUUID().toString()
                renderVirtual()
            } catch (e: java.io.IOException) {
                Toast.makeText(this@ParyajActivity, Lang.tr("Pa gen koneksyon. Peze VANN ankò: tikè a p ap double."), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@ParyajActivity, "Erè: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun printVirtual(d: JsonObject) {
        val money = Money.format()
        lifecycleScope.launch {
            TenantBranding.ensureCached(applicationContext)
            val drawAt = iso(str(d, "drawAt"))
            val drawTxt = if (drawAt > 0) SimpleDateFormat("dd/MM HH:mm", Locale.US).apply { timeZone = haiti }.format(Date(drawAt)) else ""
            val label = "${str(d, "gameName") ?: ""} #${str(d, "roundShort") ?: ""}"
            val lines = arr(d, "lines") ?: JsonArray()
            val receipt = ParyajReceipt(
                franchise = franchise(),
                companyName = brandName(),
                phone = TenantBranding.phone.orEmpty(),
                agent = TenantBranding.vendeur ?: "—",
                code = str(d, "code") ?: "—",
                dateText = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date()),
                typeText = "JWET VITYEL",
                selections = lines.mapIndexed { i, el ->
                    val l = el.asJsonObject
                    val odds = num(l, "odds")
                    ParyajSelection(
                        eventLabel = if (i == 0) label else "",
                        league = if (i == 0) "Tiraj $drawTxt" else "",
                        whenText = "",
                        pick = "${str(l, "label")} (${money.format(num(l, "stake"))})",
                        odds = "x" + (if (odds % 1.0 == 0.0) odds.toInt().toString() else odds.toString()),
                    )
                },
                stake = "${money.format(num(d, "stake"))} HTG",
                totalOdds = "-",
                potentialWin = "${money.format(num(d, "potentialWin"))} HTG",
                footer = "Gade rezilta a sou ekran an. Touche ak kod la.",
                shareCode = null,
            )
            printer.connect {
                runOnUiThread {
                    if (!printer.isReady()) {
                        Toast.makeText(this@ParyajActivity, "Tikè a VANN, men enprimant lan pa konekte. Konekte l epi peze Reenprime.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printer.printParyajReceipt(receipt)
                }
            }
        }
    }

    override fun onDestroy() { stopVgTicker(); super.onDestroy() }

    // ==================== KÒD PATAJ (sql/048) ====================
    /** Kliyan an bay kòd pataj yon lòt pari → menm chwa yo monte sou tikè a ak kòt aktyèl yo. */
    private fun askShareCode() {
        val et = EditText(this).apply {
            hint = "K7M2QX"
            isSingleLine = true
            textSize = 22f
            gravity = Gravity.CENTER
            filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(8))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0)
            addView(text(Lang.tr("Antre kòd pataj ki sou tikè oswa sou telefòn kliyan an (6 karaktè)."), 14f, MUTED))
            addView(et, lp(top = 8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(Lang.tr("Kòd pataj"))
            .setView(box)
            .setPositiveButton(Lang.tr("Chaje")) { _, _ -> loadShare(et.text.toString()) }
            .setNegativeButton(Lang.tr("Anile"), null)
            .show()
        et.requestFocus()
    }

    private fun loadShare(raw: String) {
        val code = raw.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        if (code.length != 6) {
            Toast.makeText(this, Lang.tr("Kòd pataj la gen 6 karaktè (egz. K7M2QX)."), Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeShare(code)
                if (!res.isSuccessful) {
                    Toast.makeText(this@ParyajActivity, errorOf(res.errorBody()?.string(), "Kòd pataj la pa jwenn."), Toast.LENGTH_LONG).show()
                    return@launch
                }
                val d = obj(res.body(), "data") ?: JsonObject()
                val sels = arr(d, "selections") ?: JsonArray()
                val off = (arr(d, "unavailable") ?: JsonArray()).map { el -> el.asJsonObject.let { "• ${str(it, "eventLabel") ?: ""}: ${Lang.tr(str(it, "reason") ?: "")}" } }
                if (sels.size() == 0) {
                    MaterialAlertDialogBuilder(this@ParyajActivity)
                        .setTitle(Lang.tr("Okenn chwa disponib"))
                        .setMessage((listOf(Lang.tr("Okenn chwa nan kòd sa a pa disponib ankò.")) + off).joinToString("\n"))
                        .setPositiveButton("OK", null).show()
                    return@launch
                }
                slip.clear()
                for (el in sels) {
                    val x = el.asJsonObject
                    slip.add(Sel(
                        eventId = str(x, "eventId") ?: continue,
                        market = str(x, "market") ?: continue,
                        outcome = str(x, "outcome") ?: continue,
                        point = x.get("point")?.takeIf { !it.isJsonNull }?.asDouble,
                        odds = num(x, "odds"),
                        label = str(x, "label") ?: "",
                        eventLabel = str(x, "eventLabel") ?: "",
                        commenceAt = str(x, "commenceAt"),
                    ))
                }
                acceptChanges = false
                requestId = UUID.randomUUID().toString()
                refreshSlipBar()
                if (tab <= 1) renderEvents()
                if (off.isNotEmpty()) {
                    MaterialAlertDialogBuilder(this@ParyajActivity)
                        .setTitle(Lang.tr("{n} chwa chaje").replace("{n}", slip.size.toString()))
                        .setMessage((listOf(Lang.tr("Chwa sa yo pa disponib ankò:")) + off).joinToString("\n"))
                        .setPositiveButton("OK") { _, _ -> openSlip() }.show()
                } else {
                    Toast.makeText(this@ParyajActivity, Lang.tr("{n} chwa chaje").replace("{n}", slip.size.toString()), Toast.LENGTH_SHORT).show()
                    openSlip()
                }
            } catch (e: java.io.IOException) {
                Toast.makeText(this@ParyajActivity, Lang.tr("Pa gen koneksyon."), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ParyajActivity, "Erè: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun totalOdds(): Double = slip.fold(1.0) { a, s -> a * s.odds }

    private fun refreshSlipBar() {
        if (slip.isEmpty()) {
            slipBar.visibility = View.GONE
            return
        }
        slipBar.visibility = View.VISIBLE
        tvSlip.text = "${slip.size} chwa · kòt ${String.format(Locale.US, "%.2f", totalOdds())}"
    }

    // ==================== TOUT MACHE YO ====================
    private fun openMarkets(id: String) {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeEvent(id)
                if (!res.isSuccessful) {
                    Toast.makeText(this@ParyajActivity, errorOf(res.errorBody()?.string(), "Match sa a pa disponib."), Toast.LENGTH_LONG).show()
                    return@launch
                }
                val e = obj(res.body(), "data") ?: return@launch
                showMarketsDialog(e)
            } catch (ex: java.io.IOException) {
                Toast.makeText(this@ParyajActivity, "Pa gen koneksyon.", Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                Toast.makeText(this@ParyajActivity, "Erè: ${ex.message ?: ex.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMarketsDialog(e: JsonObject) {
        val id = str(e, "id") ?: return
        val home = str(e, "home") ?: ""
        val away = str(e, "away") ?: ""
        val evLabel = "$home - $away"
        val start = str(e, "commenceAt")
        val m = obj(e, "markets") ?: JsonObject()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(8)) }
        var dialog: AlertDialog? = null

        fun opt(top: String, odds: Double, make: () -> Sel): Triple<String, Double, () -> Sel> = Triple(top, odds, make)
        fun section(title: String) {
            body.addView(text(Lang.tr(title), 14f, INK, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        }
        fun rowOf(vararg items: Triple<String, Double, () -> Sel>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3)) }
            items.forEachIndexed { i, (top, odds, make) ->
                val probe = make()
                row.addView(oddButton(top, odds, isOn(probe.eventId, probe.market, probe.outcome, probe.point), bool(e, "suspended")) {
                    toggle(make())
                    dialog?.dismiss()
                }, lp(0, weight = 1f, end = if (i < items.size - 1) 6 else 0))
            }
            body.addView(row)
        }

        obj(m, "h2h")?.let { h ->
            section("Rezilta final (1X2)")
            val hasDraw = h.get("draw")?.isJsonNull == false
            if (hasDraw) {
                rowOf(
                    opt(home, num(h, "home")) { Sel(id, "h2h", "home", null, num(h, "home"), home, evLabel, start) },
                    opt("Nil", num(h, "draw")) { Sel(id, "h2h", "draw", null, num(h, "draw"), "Nil", evLabel, start) },
                    opt(away, num(h, "away")) { Sel(id, "h2h", "away", null, num(h, "away"), away, evLabel, start) },
                )
            } else {
                rowOf(
                    opt(home, num(h, "home")) { Sel(id, "h2h", "home", null, num(h, "home"), home, evLabel, start) },
                    opt(away, num(h, "away")) { Sel(id, "h2h", "away", null, num(h, "away"), away, evLabel, start) },
                )
            }
        }
        obj(m, "double")?.let { d ->
            section("Doub chans")
            rowOf(
                opt("1X", num(d, "home_draw")) { Sel(id, "double", "home_draw", null, num(d, "home_draw"), "$home oswa Nil (1X)", evLabel, start) },
                opt("12", num(d, "home_away")) { Sel(id, "double", "home_away", null, num(d, "home_away"), "$home oswa $away (12)", evLabel, start) },
                opt("X2", num(d, "draw_away")) { Sel(id, "double", "draw_away", null, num(d, "draw_away"), "Nil oswa $away (X2)", evLabel, start) },
            )
        }
        arr(m, "totals")?.takeIf { it.size() > 0 }?.let { arr ->
            section("Plis / Mwens (total gòl)")
            for (el in arr) {
                val l = el.asJsonObject
                val p = num(l, "point")
                val ps = fmtPoint(p)
                rowOf(
                    opt("Plis $ps", num(l, "over")) { Sel(id, "totals", "over", p, num(l, "over"), "Plis $ps", evLabel, start) },
                    opt("Mwens $ps", num(l, "under")) { Sel(id, "totals", "under", p, num(l, "under"), "Mwens $ps", evLabel, start) },
                )
            }
        }
        arr(m, "spreads")?.takeIf { it.size() > 0 }?.let { arr ->
            section("Andikap")
            for (el in arr) {
                val l = el.asJsonObject
                val p = num(l, "point")
                val hp = signed(p)
                val ap = signed(-p)
                rowOf(
                    opt("$home ($hp)", num(l, "home")) { Sel(id, "spreads", "home", p, num(l, "home"), "$home ($hp)", evLabel, start) },
                    opt("$away ($ap)", num(l, "away")) { Sel(id, "spreads", "away", p, num(l, "away"), "$away ($ap)", evLabel, start) },
                )
            }
        }
        obj(m, "btts")?.let { b ->
            section("Tou de ekip make")
            rowOf(
                opt("Wi", num(b, "yes")) { Sel(id, "btts", "yes", null, num(b, "yes"), "Tou de ekip make: Wi", evLabel, start) },
                opt("Non", num(b, "no")) { Sel(id, "btts", "no", null, num(b, "no"), "Tou de ekip make: Non", evLabel, start) },
            )
        }

        val sv = ScrollView(this).apply { addView(body) }
        // Tout laj ak tout wotè ekran an (menm dimansyon ak lis match yo), pa yon ti fenèt nan mitan
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(evLabel)
            .setView(sv)
            .setNegativeButton(Lang.tr("Fèmen"), null)
            .setBackgroundInsetStart(0)
            .setBackgroundInsetEnd(0)
            .setBackgroundInsetTop(0)
            .setBackgroundInsetBottom(0)
            .show()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun fmtPoint(p: Double): String =
        if (p == Math.floor(p)) p.toInt().toString() else String.format(Locale.US, "%.1f", p)

    private fun signed(p: Double): String = (if (p > 0) "+" else "") + fmtPoint(p)

    // ==================== TIKÈ (BILTEN) ====================
    private fun openSlip() {
        if (slip.isEmpty()) return
        val minStake = num(info, "minStake", 25.0)
        val maxStake = num(info, "maxStake", 0.0)
        val maxWin = num(info, "maxWin", 0.0)
        val money = Money.format()

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(4)) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        val tvTotal = text("", 15f, INK, true).apply { setPadding(0, dp(10), 0, 0) }
        val tvWin = text("", 17f, GREEN, true)
        val tvMsg = text("", 13f, RED, true).apply { visibility = View.GONE; setPadding(0, dp(6), 0, 0) }
        val tvCap = text("", 13f, MUTED).apply { visibility = View.GONE; setPadding(0, dp(4), 0, 0) }
        val quickBtns = ArrayList<Pair<Int, View>>()
        // Miz maks pou KÒT SA A: miz × kòt pa ka depase gen maks (menm règ ak backend: maxStakeFor)
        fun allowed(odds: Double): Double {
            val cap = if (maxStake > 0) maxStake else Double.MAX_VALUE
            val byWin = if (maxWin > 0 && odds > 1) Math.floor(maxWin / odds + 1e-9) else cap
            return maxOf(minStake, minOf(cap, byWin))
        }
        val et = EditText(this).apply {
            hint = "Miz (min ${money.format(minStake)})"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setText(stakeText)
        }

        fun recompute() {
            val stake = et.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            stakeText = et.text.toString()
            val odds = totalOdds()
            var win = stake * odds
            if (maxWin > 0 && win > maxWin) win = maxWin
            val max = allowed(odds)
            tvTotal.text = "${if (slip.size > 1) "Konbine ${slip.size} match" else "Pari senp"} · kòt ${String.format(Locale.US, "%.2f", odds)}"
            quickBtns.forEach { (v, b) -> b.isEnabled = v <= max; b.alpha = if (v <= max) 1f else 0.35f }
            val generalCap = if (maxStake > 0) maxStake else Double.MAX_VALUE
            val limited = maxWin > 0 && generalCap > max
            tvCap.visibility = if (limited) View.VISIBLE else View.GONE
            tvCap.text = "Ak kòt sa a, miz maks la se ${money.format(max)} HTG (gen maks ${money.format(maxWin)} HTG)"
            if (stake > max) {
                tvWin.setTextColor(RED)
                tvWin.text = "Miz twò wo: max ${money.format(max)} HTG"
            } else {
                tvWin.setTextColor(GREEN)
                tvWin.text = "Gen posib: ${money.format(win)} HTG"
            }
        }
        fun renderList() {
            list.removeAllViews()
            slip.toList().forEach { s ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = round(if (s.changed) 0xFFFFF4D0.toInt() else Color.WHITE, 12, if (s.changed) GOLD else LINE)
                    setPadding(dp(10), dp(8), dp(8), dp(8))
                }
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                col.addView(text(s.label, 15f, INK, true))
                col.addView(text(s.eventLabel, 12f, MUTED))
                row.addView(col, lp(0, weight = 1f))
                row.addView(text(String.format(Locale.US, "%.2f", s.odds), 16f, BLUE, true).apply { setPadding(dp(8), 0, dp(8), 0) })
                row.addView(text("✕", 18f, RED, true).apply {
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        slip.remove(s)
                        acceptChanges = false
                        refreshSlipBar()
                        if (slip.isEmpty()) { (body.tag as? AlertDialog)?.dismiss(); if (tab <= 1) renderEvents() } else { renderList(); recompute() }
                    }
                })
                list.addView(row, lp(top = 6))
            }
        }
        renderList()
        body.addView(tvTotal)
        body.addView(et, lp(top = 6))
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(25, 50, 100, 250, 500).forEachIndexed { i, v ->
            val b = button("$v", 0xFFE8EEFF.toInt(), INK) { et.setText(v.toString()); et.setSelection(et.text.length) }
            quickBtns += v to b
            quick.addView(b, lp(0, weight = 1f, end = if (i < 4) 4 else 0))
        }
        body.addView(quick, lp(top = 6))
        body.addView(tvCap)
        body.addView(tvWin, lp(top = 8))
        body.addView(tvMsg)
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { recompute() }
        })
        recompute()

        val sv = ScrollView(this).apply { addView(body) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Tikè paryaj")
            .setView(sv)
            .setNegativeButton(Lang.tr("Fèmen")) { _, _ -> if (tab <= 1) renderEvents() }
            .setNeutralButton(Lang.tr("Efase tout")) { _, _ ->
                slip.clear(); acceptChanges = false; refreshSlipBar(); if (tab <= 1) renderEvents()
            }
            .setPositiveButton(if (acceptChanges) "KONFIME" else "VANN", null)
            .create()
        body.tag = dialog
        dialog.setOnShowListener {
            val btn = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            btn.setOnClickListener {
                if (!active) { notifyInactive(); return@setOnClickListener }
                val stake = et.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                tvMsg.visibility = View.GONE
                if (stake < minStake) { tvMsg.text = "Miz minimòm: ${money.format(minStake)} HTG"; tvMsg.visibility = View.VISIBLE; return@setOnClickListener }
                if (maxStake > 0 && stake > maxStake) { tvMsg.text = "Miz maksimòm: ${money.format(maxStake)} HTG"; tvMsg.visibility = View.VISIBLE; return@setOnClickListener }
                val max = allowed(totalOdds())
                if (stake > max) {
                    tvMsg.text = "Ak kòt sa a, miz maksimòm lan se ${money.format(max)} HTG. Kliyan an t ap peye ${money.format(stake)} HTG pou touche sèlman ${money.format(maxWin)} HTG."
                    tvMsg.visibility = View.VISIBLE
                    et.setText(max.toLong().toString()); et.setSelection(et.text.length)
                    return@setOnClickListener
                }
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(et.windowToken, 0)
                btn.isEnabled = false
                sell(stake) { ok, msg, changed ->
                    btn.isEnabled = true
                    if (ok) {
                        dialog.dismiss()
                        if (tab <= 1) renderEvents()
                    } else {
                        tvMsg.text = msg
                        tvMsg.visibility = View.VISIBLE
                        if (changed) { renderList(); recompute(); btn.text = "KONFIME" }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun sell(stake: Double, done: (ok: Boolean, msg: String, changed: Boolean) -> Unit) {
        val body = JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("stake", stake)
            addProperty("acceptChanges", acceptChanges)
            addProperty("deviceCode", DeviceIdHelper.getDeviceId(this@ParyajActivity))
            add("selections", JsonArray().apply {
                slip.forEach { s ->
                    add(JsonObject().apply {
                        addProperty("eventId", s.eventId)
                        addProperty("market", s.market)
                        addProperty("outcome", s.outcome)
                        if (s.point != null) addProperty("point", s.point)
                        addProperty("odds", s.odds)
                    })
                }
            })
        }
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeSell(body)
                if (res.isSuccessful) {
                    val d = obj(res.body(), "data") ?: JsonObject()
                    slip.clear()
                    acceptChanges = false
                    stakeText = ""
                    requestId = UUID.randomUUID().toString()
                    refreshSlipBar()
                    printTicket(d)
                    showSold(d)
                    done(true, "", false)
                    return@launch
                }
                val raw = res.errorBody()?.string()
                val code = ApiErrors.code(raw)
                if (res.code() == 409 && code == "ODDS_CHANGED") {
                    applyChanges(raw)
                    acceptChanges = true
                    refreshSlipBar()
                    done(false, "Kòt yo chanje (an jòn). Verifye yo epi peze KONFIME.", true)
                    return@launch
                }
                // Lòt erè: nouvo requestId pou pwochen esè a (sof si se yon erè rezo)
                if (res.code() in 400..499) requestId = UUID.randomUUID().toString()
                done(false, errorOf(raw, "Tikè a pa vann. Eseye ankò."), false)
            } catch (e: Exception) {
                // Erè rezo: KENBE menm requestId → si l te pase, sèvè a pa vann li 2 fwa
                done(false, "Pa gen koneksyon. Peze VANN ankò (tikè a p ap vann 2 fwa).", false)
            }
        }
    }

    private fun applyChanges(raw: String?) {
        try {
            val arr = arr(JsonParser.parseString(raw ?: return).asJsonObject, "changes") ?: return
            for (el in arr) {
                val c = el.asJsonObject
                val s = slip.firstOrNull { it.eventId == str(c, "eventId") && it.market == str(c, "market") && it.outcome == str(c, "outcome") } ?: continue
                s.odds = num(c, "odds", s.odds)
                s.changed = true
            }
        } catch (_: Exception) { }
    }

    private fun showSold(d: JsonObject) {
        val money = Money.format()
        val code = str(d, "code") ?: "—"
        MaterialAlertDialogBuilder(this)
            .setTitle("✓ Tikè $code vann")
            .setMessage(
                "Miz: ${money.format(num(d, "stake"))} HTG\n" +
                    "Kòt total: ${String.format(Locale.US, "%.2f", num(d, "totalOdds"))}\n" +
                    "Gen posib: ${money.format(num(d, "potentialWin"))} HTG\n" +
                    (str(d, "shareCode")?.let { "Kòd pataj: $it\n" } ?: "") + "\n" +
                    "Kliyan an dwe kenbe tikè a. Si l genyen, se ou ki peye l (VERIFYE → PEYE).",
            )
            .setPositiveButton("OK", null)
            .setNeutralButton(Lang.tr("Reenprime")) { _, _ -> printTicket(d) }
            .show()
    }

    // ==================== ENPRIME ====================
    private fun printTicket(d: JsonObject) {
        val money = Money.format()
        lifecycleScope.launch {
            TenantBranding.ensureCached(applicationContext)
            val company = brandName()
            val vendeur = TenantBranding.vendeur ?: "—"
            val phone = TenantBranding.phone.orEmpty()
            val code = str(d, "code") ?: "—"
            val dt = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
            val sels = arr(d, "selections") ?: JsonArray()
            val receipt = ParyajReceipt(
                franchise = franchise(),
                companyName = company,
                phone = phone,
                agent = vendeur,
                code = code,
                dateText = dt,
                typeText = if (sels.size() > 1) "Konbine ${sels.size()} match" else "Pari senp",
                selections = sels.map { el: JsonElement ->
                    val s = el.asJsonObject
                    ParyajSelection(
                        eventLabel = str(s, "eventLabel") ?: "",
                        league = str(s, "league") ?: "",
                        whenText = whenText(str(s, "commenceAt")),
                        pick = str(s, "label") ?: "",
                        odds = String.format(Locale.US, "%.2f", num(s, "odds")),
                        live = bool(s, "isLive"),
                    )
                },
                stake = "${money.format(num(d, "stake"))} HTG",
                totalOdds = String.format(Locale.US, "%.2f", num(d, "totalOdds")),
                potentialWin = "${money.format(num(d, "potentialWin"))} HTG",
                shareCode = str(d, "shareCode"),
            )
            printer.connect {
                runOnUiThread {
                    if (!printer.isReady()) {
                        Toast.makeText(this@ParyajActivity, "Tikè a VANN, men enprimant lan pa konekte. Konekte l epi peze Reenprime.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printer.printParyajReceipt(receipt)
                }
            }
        }
    }

    // ==================== VERIFYE / PEYE / ANILE ====================
    private fun showVerify(result: JsonObject?) {
        content.removeAllViews()
        val c = card()
        c.addView(text(Lang.tr("Verifye yon tikè paryaj"), 17f, INK, true))
        c.addView(text(Lang.tr("Tape kòd ki sou tikè a (PP + 8 chif) oswa eskane l."), 13f, MUTED).apply { setPadding(0, dp(4), 0, dp(6)) })
        val et = EditText(this).apply {
            hint = "PP12345678"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setText(str(result, "code") ?: "")
        }
        et.setOnEditorActionListener { _, _, _ -> verify(et.text.toString()); true }
        c.addView(et)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(Lang.tr("CHÈCHE"), BLUE) { verify(et.text.toString()) }, lp(0, weight = 1f, end = 6))
        row.addView(button(Lang.tr("SCAN"), NAVY) {
            scanLauncher.launch(ScanOptions().setPrompt("Mete kòd tikè a nan kare a").setBeepEnabled(true).setOrientationLocked(true).setCaptureActivity(ScanActivity::class.java))
        }, lp(0, weight = 1f))
        c.addView(row, lp(top = 6))
        content.addView(c)
        if (result != null) content.addView(betCard(result))
    }

    private fun verify(raw: String) {
        val code = raw.trim().uppercase()
        if (code.matches(Regex("PP\\d{8}"))) lastCode = code
        if (!code.matches(Regex("PP\\d{8}"))) {
            Toast.makeText(this, "Kòd la dwe sanble PP12345678.", Toast.LENGTH_SHORT).show()
            return
        }
        if (tab != 2) {
            tab = 2
            tabViews.forEachIndexed { idx, v -> v.background = if (idx == 2) round(GOLD, 10) else round(0x22FFFFFF, 10); v.setTextColor(if (idx == 2) INK else Color.WHITE) }
        }
        loading()
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeVerify(code)
                if (!res.isSuccessful) {
                    showVerify(null)
                    Toast.makeText(this@ParyajActivity, errorOf(res.errorBody()?.string(), "Tikè pa jwenn."), Toast.LENGTH_LONG).show()
                    return@launch
                }
                showVerify(obj(res.body(), "data"))
            } catch (e: java.io.IOException) {
                showVerify(null)
                Toast.makeText(this@ParyajActivity, "Pa gen koneksyon.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showVerify(null)
                Toast.makeText(this@ParyajActivity, "Erè: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun statusText(b: JsonObject): Pair<String, Int> {
        val st = str(b, "status") ?: ""
        val paid = str(b, "paidAt") != null
        return when {
            st == "pending" -> "AP TANN (match yo poko fini)" to BLUE
            st == "won" && paid -> "GENYEN · DEJA PEYE" to MUTED
            st == "won" -> "GENYEN" to GREEN
            st == "void" && paid -> "ANILE · DEJA RANBOUSE" to MUTED
            st == "void" -> "ANILE · RANBOUSE KLIYAN AN" to GOLD
            st == "lost" -> "PÈDI" to RED
            st == "cancelled" -> "TIKÈ ANILE" to MUTED
            else -> st.uppercase() to MUTED
        }
    }

    private fun betCard(b: JsonObject): View {
        val money = Money.format()
        val c = card()
        val (st, color) = statusText(b)
        c.addView(text(str(b, "code") ?: "", 20f, INK, true))
        c.addView(text(st, 15f, color, true).apply { setPadding(0, dp(4), 0, dp(6)) })
        str(b, "shareCode")?.let { sc ->
            c.addView(text("${Lang.tr("Kòd pataj")}: $sc", 14f, BLUE, true).apply { setPadding(0, 0, 0, dp(6)) })
        }
        if (str(b, "channel") == "online") {
            c.addView(text("Pari an liy: kòb la antre sou balans jwè a otomatikman.", 13f, MUTED))
        }
        val sels = arr(b, "selections") ?: JsonArray()
        if (sels.size() > 0) c.addView(text(Lang.tr("Klike sou yon match pou w wè rezilta l."), 12f, MUTED).apply { setPadding(0, 0, 0, dp(2)) })
        for (el in sels) {
            val s = el.asJsonObject
            val result = str(s, "result")
            val res = when (result) { "won" -> "✓"; "lost" -> "✗"; "void" -> "—"; else -> "…" }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(8), dp(4), dp(8))
                background = round(0xFFF6F8FD.toInt(), 12)
                isClickable = true
                setOnClickListener { showSelection(s) }
            }
            row.addView(text(res, 16f, when (result) { "won" -> GREEN; "lost" -> RED; else -> MUTED }, true).apply { setPadding(0, 0, dp(8), 0) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            // Sa kliyan an JWE a: pi fonse, pi gwo
            col.addView(text(str(s, "label") ?: "", 15f, 0xFF050B1F.toInt(), true).apply { setTypeface(typeface, Typeface.BOLD) })
            val ev = obj(s, "event")
            val hs = ev?.get("homeScore")?.takeIf { !it.isJsonNull }?.asInt
            val asc = ev?.get("awayScore")?.takeIf { !it.isJsonNull }?.asInt
            val matchLine = if (ev != null && hs != null && asc != null)
                "${str(ev, "home")} $hs – $asc ${str(ev, "away")} · ${if (str(ev, "status") == "live") "LIVE" else Lang.tr("Final")}"
            else "${str(s, "eventLabel") ?: ""} · ${whenText(str(s, "commenceAt"))}"
            col.addView(text(matchLine, 12f, MUTED))
            row.addView(col, lp(0, weight = 1f))
            row.addView(text(String.format(Locale.US, "%.2f", num(s, "odds")), 14f, BLUE, true))
            row.addView(text("›", 20f, MUTED, true).apply { setPadding(dp(8), 0, dp(2), 0) })
            c.addView(row, lp(top = 6))
        }
        c.addView(text(
            "Miz: ${money.format(num(b, "stake"))} HTG · Kòt: ${String.format(Locale.US, "%.2f", num(b, "totalOdds"))}\n" +
                (if (str(b, "status") == "won" || str(b, "status") == "void") "A PEYE: ${money.format(num(b, "payout"))} HTG" else "Gen posib: ${money.format(num(b, "potentialWin"))} HTG"),
            15f, INK, true,
        ).apply { setPadding(0, dp(10), 0, 0) })
        val agent = str(b, "agentName")
        if (agent != null && !bool(b, "mine")) {
            c.addView(text("Vandè: $agent — se li ki pou peye tikè sa a.", 13f, RED).apply { setPadding(0, dp(6), 0, 0) })
        }
        val code = str(b, "code") ?: ""
        if (bool(b, "canPay")) {
            c.addView(button("PEYE ${money.format(num(b, "payout"))} HTG", GREEN) { confirmPay(code, num(b, "payout")) }, lp(top = 10))
        }
        if (bool(b, "canCancel")) {
            c.addView(button("ANILE TIKÈ A", RED) { confirmCancel(code) }, lp(top = 8))
        }
        return c
    }

    /** Klike sou yon match nan tikè a: skò final, sa l te jwe a, poukisa li genyen / pèdi. */
    private fun showSelection(s: JsonObject) {
        val fr = Lang.isFr(this)
        fun bi(o: JsonObject?): String? = o?.let { str(it, if (fr) "fr" else "ht") ?: str(it, "ht") }
        val ev = obj(s, "event")
        val result = str(s, "result") ?: "pending"
        val (badge, badgeColor) = when (result) {
            "won" -> "GENYEN" to GREEN
            "lost" -> "PÈDI" to RED
            "void" -> "ANILE (kòt 1)" to GOLD
            else -> (if (str(ev, "status") == "live") "AP JWE" else "AP TANN") to BLUE
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), dp(4)) }

        val meta = listOf(str(s, "league"), whenText(str(s, "commenceAt"))).filter { !it.isNullOrBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) box.addView(text(meta, 13f, MUTED))

        // Tablo skò a
        val home = str(ev, "home") ?: (str(s, "eventLabel") ?: "").split(Regex("\\s+[–-]\\s+")).getOrNull(0).orEmpty()
        val away = str(ev, "away") ?: (str(s, "eventLabel") ?: "").split(Regex("\\s+[–-]\\s+")).getOrNull(1).orEmpty()
        val hs = ev?.get("homeScore")?.takeIf { !it.isJsonNull }?.asInt
        val asc = ev?.get("awayScore")?.takeIf { !it.isJsonNull }?.asInt
        val board = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = round(NAVY, 14); setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        board.addView(text(home, 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2 }, lp(0, weight = 1f))
        board.addView(text(if (hs != null && asc != null) "$hs – $asc" else "vs", 26f, GOLD, true).apply { gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0) })
        board.addView(text(away, 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 2 }, lp(0, weight = 1f))
        box.addView(board, lp(top = 8))
        val st = when (str(ev, "status")) {
            "finished" -> Lang.tr("Match la fini")
            "live" -> Lang.tr("Match la ap jwe")
            "cancelled" -> Lang.tr("Match la anile")
            else -> if (hs != null) Lang.tr("Match la fini") else Lang.tr("Match la poko jwe")
        }
        box.addView(text(st, 12f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }, lp())

        // Sa l te jwe a
        val played = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(0xFFF6F8FD.toInt(), 14, LINE); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        played.addView(text(Lang.tr("Kliyan an te parye sou"), 12f, MUTED, true))
        played.addView(text(str(s, "label") ?: "", 19f, 0xFF050B1F.toInt(), true).apply { setTypeface(typeface, Typeface.BOLD) })
        bi(obj(s, "pick"))?.takeIf { it.isNotBlank() && it != str(s, "label") }?.let { played.addView(text(it, 14f, INK, true)) }
        val mk = bi(obj(s, "marketName")) ?: ""
        played.addView(text(listOf(mk, "${Lang.tr("Kòt")} ${String.format(Locale.US, "%.2f", num(s, "odds"))}").filter { it.isNotBlank() }.joinToString(" · "), 13f, MUTED).apply { setPadding(0, dp(2), 0, 0) })
        box.addView(played, lp(top = 12))

        box.addView(text(Lang.tr(badge), 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = round(badgeColor, 12); setPadding(0, dp(8), 0, dp(8))
        }, lp(top = 12))
        bi(obj(s, "explain"))?.let { box.addView(text(it, 14f, INK).apply { setPadding(0, dp(10), 0, 0) }) }

        // Estatistik match la (gòl, minit, posesyon…): founisè kòt la pa bay yo → n louvri yon rechèch
        if (home.isNotBlank() && away.isNotBlank()) {
            val day = str(s, "commenceAt")?.take(10).orEmpty()
            box.addView(button(Lang.tr("WÈ ESTATISTIK MATCH LA"), NAVY) {
                try {
                    val q = android.net.Uri.encode("$home vs $away $day")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$q")))
                } catch (_: Exception) {
                    Toast.makeText(this, Lang.tr("Pa gen navigatè sou aparèy sa a."), Toast.LENGTH_SHORT).show()
                }
            }, lp(top = 12))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(str(s, "eventLabel") ?: Lang.tr("Match"))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(Lang.tr("Fèmen"), null)
            .show()
    }

    private fun confirmPay(code: String, amount: Double) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Peye tikè $code?")
            .setMessage("Ou pral bay kliyan an ${Money.format().format(amount)} HTG kach.")
            .setPositiveButton("WI, PEYE") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val res = ApiClient.getService(applicationContext).paryePay(code)
                        val msg = if (res.isSuccessful) str(res.body(), "message") ?: "Tikè peye." else errorOf(res.errorBody()?.string(), "Pa ka peye tikè a.")
                        Toast.makeText(this@ParyajActivity, msg, Toast.LENGTH_LONG).show()
                        verify(code)
                    } catch (e: Exception) {
                        Toast.makeText(this@ParyajActivity, "Pa gen koneksyon.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(Lang.tr("Non"), null)
            .show()
    }

    private fun confirmCancel(code: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Anile tikè $code?")
            .setMessage("Ou dwe remèt kliyan an kòb miz la. Sa pa ka defèt.")
            .setPositiveButton("WI, ANILE") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val res = ApiClient.getService(applicationContext).paryeCancel(code)
                        val msg = if (res.isSuccessful) str(res.body(), "message") ?: "Tikè anile." else errorOf(res.errorBody()?.string(), "Pa ka anile tikè a.")
                        Toast.makeText(this@ParyajActivity, msg, Toast.LENGTH_LONG).show()
                        verify(code)
                    } catch (e: Exception) {
                        Toast.makeText(this@ParyajActivity, "Pa gen koneksyon.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(Lang.tr("Non"), null)
            .show()
    }

    // ==================== PARI MWEN VANN (lis) ====================
    /** 0 = jodi a, 1 = yè, 7 = 7 dènye jou yo */
    private var betsDays = 0

    private fun loadMyBets(days: Int) {
        betsDays = days
        loading()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val (from, to) = when (days) {
            1 -> fmt.format(Date(now - day)) to fmt.format(Date(now - day))
            7 -> fmt.format(Date(now - 6 * day)) to fmt.format(Date(now))
            else -> fmt.format(Date(now)) to fmt.format(Date(now))
        }
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeBets(from, to)
                if (!res.isSuccessful) {
                    message("Erè", errorOf(res.errorBody()?.string(), "Pa ka chaje pari yo."))
                    return@launch
                }
                val list = arr(res.body(), "data") ?: JsonArray()
                if (tab != 4) return@launch
                renderMyBets(list)
            } catch (e: java.io.IOException) {
                message("Pa gen koneksyon", "Verifye entènèt la epi eseye ankò.")
            } catch (e: Exception) {
                message("Erè", "Pa ka montre pari yo: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun renderMyBets(list: JsonArray) {
        val money = Money.format()
        content.removeAllViews()

        // Chwa peryòd
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
        listOf(0 to "Jodi a", 1 to "Yè", 7 to "7 jou").forEachIndexed { i, (d, label) ->
            val on = d == betsDays
            chips.addView(text(Lang.tr(label), 14f, if (on) INK else Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                background = round(if (on) GOLD else NAVY, 10)
                setOnClickListener { loadMyBets(d) }
            }, lp(0, weight = 1f, end = if (i < 2) 6 else 0))
        }
        content.addView(chips)

        // Rezime
        var stake = 0.0; var n = 0; var cancelled = 0; var won = 0
        for (el in list) {
            val b = el.asJsonObject
            if (str(b, "status") == "cancelled") { cancelled++; continue }
            n++; stake += num(b, "stake")
            if (str(b, "status") == "won") won++
        }
        val sum = card()
        sum.addView(text(Lang.tr("Pari ou vann yo"), 17f, INK, true))
        sum.addView(text("$n tikè · ${money.format(stake)} HTG" +
            (if (won > 0) " · $won genyen" else "") +
            (if (cancelled > 0) " · $cancelled anile" else ""), 14f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(sum)

        if (list.size() == 0) {
            content.addView(card().apply { addView(text(Lang.tr("Pa gen pari pou peryòd sa a."), 15f, MUTED)) })
            return
        }

        val timeFmt = SimpleDateFormat(if (betsDays == 0) "HH:mm" else "dd/MM HH:mm", Locale.US).apply { timeZone = haiti }
        for (el in list) soldBetCard(el.asJsonObject, timeFmt)?.let { content.addView(it) }
        content.addView(text(Lang.tr("Klike sou yon tikè pou w wè detay li, peye l oswa anile l."), 12f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }


    /** Yon tikè ajan an vann (lis PARI ak RAPÒ). Klike → detay / peye / anile. */
    private fun soldBetCard(b: JsonObject, timeFmt: SimpleDateFormat): View? {
        val money = Money.format()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val code = str(b, "code") ?: return null
        val (st, color) = statusText(b)
        val placed = str(b, "placedAt")?.let { p -> runCatching { timeFmt.format(iso.parse(p.take(19))!!) }.getOrNull() } ?: ""
        val sels = arr(b, "selections")
        val first = sels?.firstOrNull()?.asJsonObject
        val c = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(code, 16f, INK, true), lp(0, weight = 1f))
        top.addView(text(placed, 13f, MUTED))
        c.addView(top)
        val what = when {
            sels == null || sels.size() == 0 -> ""
            sels.size() == 1 -> "${str(first, "label") ?: ""} · ${str(first, "eventLabel") ?: ""}"
            else -> "Konbine · ${sels.size()} match"
        }
        if (what.isNotBlank()) c.addView(text(what, 13f, MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0) })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        bottom.addView(text(st, 13f, color, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(0, weight = 1f))
        val st0 = str(b, "status")
        val right = if (st0 == "won" || st0 == "void") "${money.format(num(b, "payout"))} HTG" else "${money.format(num(b, "stake"))} HTG · ${String.format(Locale.US, "%.2f", num(b, "totalOdds"))}"
        bottom.addView(text(right, 14f, INK, true))
        c.addView(bottom)
        c.isClickable = true
        c.setOnClickListener { verify(code) }
        return c
    }

    // ==================== RAPÒ (separe de bolèt) ====================
    /** Peryòd rapò a (yyyy-MM-dd, lè Ayiti) + filtè tikè yo: all / won / lost / pending */
    private var repFrom: String? = null
    private var repTo: String? = null
    private var repFilter = "all"

    private fun ymd(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }.format(Date(ms))

    private fun pickReportDate(current: String, onPicked: (String) -> Unit) {
        val cal = java.util.Calendar.getInstance(haiti)
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }.parse(current) }.getOrNull()?.let { cal.time = it }
        android.app.DatePickerDialog(this, { _, y, m, d ->
            onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun loadReport() {
        loading()
        val today = ymd(System.currentTimeMillis())
        val from = repFrom ?: today
        val to = repTo ?: today
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.paryeReport(from, to)
                if (!res.isSuccessful) {
                    message("Erè", errorOf(res.errorBody()?.string(), "Pa ka chaje rapò a."))
                    return@launch
                }
                val bets = runCatching { api.paryeBets(from, to) }.getOrNull()
                    ?.takeIf { it.isSuccessful }?.let { arr(it.body(), "data") } ?: JsonArray()
                if (tab != 3) return@launch
                val r = obj(res.body(), "data") ?: JsonObject()
                val money = Money.format()
                val rows = listOf(
                    "Tikè vann" to "${num(r, "tickets").toInt()}",
                    "Tikè anile" to "${num(r, "cancelled").toInt()}",
                    "Vant paryaj" to "${money.format(num(r, "sales"))} HTG",
                    "Komisyon ou" to "${money.format(num(r, "commission"))} HTG",
                    "Ap tann" to "${num(r, "pending").toInt()} (${money.format(num(r, "pendingStake"))} HTG)",
                    "Tikè ki genyen" to "${num(r, "winners").toInt()}",
                    "Pou peye" to "${money.format(num(r, "toPay"))} HTG",
                    "Deja peye" to "${money.format(num(r, "paid"))} HTG",
                    "Poko peye" to "${money.format(num(r, "unpaid"))} HTG",
                    "Rezilta" to "${money.format(num(r, "net"))} HTG",
                )
                content.removeAllViews()
                val show = { x: String -> x.substring(8, 10) + "/" + x.substring(5, 7) + "/" + x.substring(0, 4) }
                val period = if (from == to) show(from) else "${show(from)} → ${show(to)}"
                val day = 86_400_000L
                val now = System.currentTimeMillis()

                // Chwa peryòd: Jodi a · Yè · 7 jou · 30 jou
                val chips = LinearLayout(this@ParyajActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(4)) }
                val presets = listOf(
                    Triple("Jodi a", today, today),
                    Triple("Yè", ymd(now - day), ymd(now - day)),
                    Triple("7 jou", ymd(now - 6 * day), today),
                    Triple("30 jou", ymd(now - 29 * day), today),
                )
                presets.forEachIndexed { i, (label, f0, t0) ->
                    val on = f0 == from && t0 == to
                    chips.addView(text(Lang.tr(label), 13f, if (on) INK else Color.WHITE, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(8), 0, dp(8))
                        maxLines = 1
                        background = round(if (on) GOLD else NAVY, 10)
                        setOnClickListener { repFrom = f0; repTo = t0; loadReport() }
                    }, lp(0, weight = 1f, end = if (i < presets.size - 1) 5 else 0))
                }
                content.addView(chips)

                // Dat egzak: Depi · Jiska
                val dates = LinearLayout(this@ParyajActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(6)) }
                fun dateBox(label: String, value: String, onPick: (String) -> Unit) = LinearLayout(this@ParyajActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = round(Color.WHITE, 10)
                    addView(text(Lang.tr(label), 11f, MUTED))
                    addView(text("📅 " + show(value), 15f, INK, true))
                    isClickable = true
                    setOnClickListener { pickReportDate(value, onPick) }
                }
                dates.addView(dateBox("Depi", from) { d -> repFrom = d; if ((repTo ?: to) < d) repTo = d; loadReport() }, lp(0, weight = 1f, end = 6))
                dates.addView(dateBox("Jiska", to) { d -> repTo = d; if ((repFrom ?: from) > d) repFrom = d; loadReport() }, lp(0, weight = 1f))
                content.addView(dates)

                val c = card()
                c.addView(text(Lang.tr("Rapò paryaj") + " — " + period, 17f, INK, true))
                c.addView(text("Separe de vant bolèt yo.", 13f, MUTED).apply { setPadding(0, dp(2), 0, dp(6)) })
                rows.forEach { (k, v) ->
                    val row = LinearLayout(this@ParyajActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
                    row.addView(text(k, 15f, MUTED), lp(0, weight = 1f))
                    row.addView(text(v, 15f, INK, true))
                    c.addView(row)
                }
                c.addView(button(Lang.tr("ENPRIME RAPÒ A"), NAVY) {
                    lifecycleScope.launch {
                        TenantBranding.ensureCached(applicationContext)
                        printer.connect {
                            runOnUiThread {
                                if (!printer.isReady()) {
                                    Toast.makeText(this@ParyajActivity, "Enprimant lan pa konekte.", Toast.LENGTH_SHORT).show()
                                    return@runOnUiThread
                                }
                                printer.printReportReceipt(
                                    companyName = brandName(),
                                    branchCode = "PARYE PLIS+",
                                    phone = TenantBranding.phone.orEmpty(),
                                    reportTitle = "RAPO PARYAJ",
                                    headerLines = if (from == to) listOf("Dat" to show(from)) else listOf("Depi" to show(from), "Jiska" to show(to)),
                                    bodyFields = rows,
                                    agent = TenantBranding.vendeur ?: "",
                                )
                            }
                        }
                    }
                }, lp(top = 12))
                content.addView(c)
                renderReportTickets(bets, from == to)
            } catch (e: java.io.IOException) {
                message("Pa gen koneksyon", "Verifye entènèt la epi eseye ankò.")
            } catch (e: Exception) {
                message("Erè", "Pa ka montre done yo: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Tikè peryòd la ak filtè: Tout · Genyen · Pèdi · Ap tann (klike → detay) */
    private fun renderReportTickets(bets: JsonArray, oneDay: Boolean) {
        val all = bets.mapNotNull { it?.takeIf { x -> x.isJsonObject }?.asJsonObject }
        fun cat(b: JsonObject): String = when (str(b, "status")) {
            "won" -> "won"; "lost" -> "lost"; "pending" -> "pending"; "cancelled" -> "cancelled"; else -> "other"
        }
        val counts = all.groupingBy { cat(it) }.eachCount()
        content.addView(text(Lang.tr("Tikè yo"), 16f, INK, true).apply { setPadding(dp(2), dp(14), 0, dp(4)) })
        val filters = listOf(
            Triple("all", "Tout", all.size),
            Triple("won", "Genyen", counts["won"] ?: 0),
            Triple("lost", "Pèdi", counts["lost"] ?: 0),
            Triple("pending", "Ap tann", counts["pending"] ?: 0),
        )
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
        filters.forEachIndexed { i, (key, label, n) ->
            val on = key == repFilter
            val bg = when { !on -> Color.WHITE; key == "won" -> GREEN; key == "lost" -> RED; key == "pending" -> BLUE; else -> NAVY }
            row.addView(text("${Lang.tr(label)} ($n)", 12f, if (on) Color.WHITE else INK, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                maxLines = 1
                background = round(bg, 10)
                setOnClickListener {
                    repFilter = key
                    // re-rann sèlman lis la (san rechaje rapò a)
                    val idx = content.indexOfChild(this@apply.parent as View) - 1
                    while (content.childCount > idx && idx >= 0) content.removeViewAt(idx)
                    renderReportTickets(bets, oneDay)
                }
            }, lp(0, weight = 1f, end = if (i < filters.size - 1) 5 else 0))
        }
        content.addView(row)
        val shown = all.filter { repFilter == "all" || cat(it) == repFilter }
        if (shown.isEmpty()) {
            content.addView(card().apply { addView(text(Lang.tr("Pa gen tikè pou chwa sa a."), 14f, MUTED)) })
            return
        }
        val timeFmt = SimpleDateFormat(if (oneDay) "HH:mm" else "dd/MM HH:mm", Locale.US).apply { timeZone = haiti }
        shown.forEach { b -> soldBetCard(b, timeFmt)?.let { content.addView(it) } }
        if (all.size >= 200) content.addView(text(Lang.tr("200 dènye tikè yo sèlman. Chwazi yon peryòd pi kout pou wè rès yo."), 12f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }
}
