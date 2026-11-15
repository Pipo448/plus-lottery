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
        val names = listOf("MATCH", "LIVE", "VERIFYE", "RAPÒ")
        tabViews = names.mapIndexed { i, n ->
            text(Lang.tr(n), 14f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                setOnClickListener { selectTab(i) }
            }.also { tabsRow.addView(it, lp(0, weight = 1f, end = if (i < names.size - 1) 6 else 0)) }
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
        }
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

    private fun renderEvents() {
        content.removeAllViews()
        if (!active) content.addView(previewBanner())
        if (lastEvents.size() == 0) {
            message(if (tab == 1) "Pa gen match an dirèk" else "Pa gen match", "Tounen pita.")
            return
        }
        var lastLeague = ""
        for (el in lastEvents) {
            val e = el.asJsonObject
            val league = str(e, "league") ?: ""
            if (league != lastLeague) {
                lastLeague = league
                content.addView(text(league.uppercase(), 12f, MUTED, true).apply { setPadding(dp(4), dp(12), 0, 0) })
            }
            content.addView(eventCard(e))
        }
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

    private fun eventCard(e: JsonObject): View {
        val c = card().apply { layoutParams = lp(top = 6) }
        val id = str(e, "id") ?: return c
        val home = str(e, "home") ?: ""
        val away = str(e, "away") ?: ""
        val live = bool(e, "live")
        val score = obj(e, "score")
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(text(if (live) "● LIVE" else whenText(str(e, "commenceAt")), 12f, if (live) RED else MUTED, true), lp(0, weight = 1f))
        if (live && score != null) top.addView(text("${num(score, "home").toInt()} - ${num(score, "away").toInt()}", 14f, RED, true))
        c.addView(top)
        c.addView(text(home, 16f, INK, true).apply { setPadding(0, dp(4), 0, 0) })
        c.addView(text(away, 16f, INK, true))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val h = obj(e, "h2h")
        val evLabel = "$home - $away"
        val start = str(e, "commenceAt")
        fun add(outcome: String, top: String, label: String) {
            val odds = num(h, outcome)
            row.addView(oddButton(top, odds, isOn(id, "h2h", outcome, null)) {
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

    private fun oddButton(top: String, odds: Double, on: Boolean, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = if (on) round(GOLD, 12) else round(0xFFE8EEFF.toInt(), 12, 0xFFC9D8FA.toInt())
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isClickable = odds > 1.0
            if (odds > 1.0) setOnClickListener { onClick() }
        }
        box.addView(text(top, 11f, if (on) INK else MUTED, true).apply { gravity = Gravity.CENTER; maxLines = 1 })
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
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(8)) }
        var dialog: AlertDialog? = null

        fun opt(top: String, odds: Double, make: () -> Sel): Triple<String, Double, () -> Sel> = Triple(top, odds, make)
        fun section(title: String) {
            body.addView(text(Lang.tr(title), 14f, INK, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        }
        fun rowOf(vararg items: Triple<String, Double, () -> Sel>) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3)) }
            items.forEachIndexed { i, (top, odds, make) ->
                val probe = make()
                row.addView(oddButton(top, odds, isOn(probe.eventId, probe.market, probe.outcome, probe.point)) {
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
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(evLabel)
            .setView(sv)
            .setNegativeButton(Lang.tr("Fèmen"), null)
            .show()
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
            tvTotal.text = "${if (slip.size > 1) "Konbine ${slip.size} match" else "Pari senp"} · kòt ${String.format(Locale.US, "%.2f", odds)}"
            tvWin.text = "Gen posib: ${money.format(win)} HTG"
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
            quick.addView(button("$v", 0xFFE8EEFF.toInt(), INK) { et.setText(v.toString()); et.setSelection(et.text.length) }, lp(0, weight = 1f, end = if (i < 4) 4 else 0))
        }
        body.addView(quick, lp(top = 6))
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
                    "Gen posib: ${money.format(num(d, "potentialWin"))} HTG\n\n" +
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
            val company = TenantBranding.companyName?.uppercase() ?: "PLUS GROUP"
            val vendeur = TenantBranding.vendeur ?: "—"
            val phone = TenantBranding.phone.orEmpty()
            val code = str(d, "code") ?: "—"
            val dt = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
            val sels = arr(d, "selections") ?: JsonArray()
            val receipt = ParyajReceipt(
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
        if (str(b, "channel") == "online") {
            c.addView(text("Pari an liy: kòb la antre sou balans jwè a otomatikman.", 13f, MUTED))
        }
        val sels = arr(b, "selections") ?: JsonArray()
        for (el in sels) {
            val s = el.asJsonObject
            val res = when (str(s, "result")) { "won" -> "✓"; "lost" -> "✗"; "void" -> "—"; else -> "…" }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
            row.addView(text(res, 16f, when (str(s, "result")) { "won" -> GREEN; "lost" -> RED; else -> MUTED }, true).apply { setPadding(0, 0, dp(8), 0) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(str(s, "label") ?: "", 14f, INK, true))
            col.addView(text("${str(s, "eventLabel") ?: ""} · ${whenText(str(s, "commenceAt"))}", 12f, MUTED))
            row.addView(col, lp(0, weight = 1f))
            row.addView(text(String.format(Locale.US, "%.2f", num(s, "odds")), 14f, BLUE, true))
            c.addView(row)
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

    // ==================== RAPÒ (separe de bolèt) ====================
    private fun loadReport() {
        loading()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }.format(Date())
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).paryeReport(today, today)
                if (!res.isSuccessful) {
                    message("Erè", errorOf(res.errorBody()?.string(), "Pa ka chaje rapò a."))
                    return@launch
                }
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
                val c = card()
                c.addView(text("Rapò paryaj — jodi a", 17f, INK, true))
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
                                    companyName = TenantBranding.companyName?.uppercase() ?: "PLUS GROUP",
                                    branchCode = "PARYE PLIS+",
                                    phone = TenantBranding.phone.orEmpty(),
                                    reportTitle = "RAPO PARYAJ",
                                    headerLines = listOf("Dat" to today),
                                    bodyFields = rows,
                                    agent = TenantBranding.vendeur ?: "",
                                )
                            }
                        }
                    }
                }, lp(top = 12))
                content.addView(c)
            } catch (e: java.io.IOException) {
                message("Pa gen koneksyon", "Verifye entènèt la epi eseye ankò.")
            } catch (e: Exception) {
                message("Erè", "Pa ka montre done yo: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
