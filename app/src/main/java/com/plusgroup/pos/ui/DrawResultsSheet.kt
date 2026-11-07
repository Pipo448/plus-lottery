package com.plusgroup.pos.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.plusgroup.pos.databinding.ItemDrawResultBinding
import com.plusgroup.pos.databinding.SheetDrawResultsBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.DrawResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * "Boul ki soti" — fèy anba ak yon kat pou chak tiraj:
 * non + sesyon (Maten / Midi / Swa) + lè, 3 boul an koulè
 * (1e lo oranj, 2e ble, 3e navy) ak liy bolèt la (2 dènye chif 1e lo).
 *
 * Itilizasyon: DrawResultsSheet.show(activity)
 */
object DrawResultsSheet {

    private enum class Range { TODAY, YESTERDAY, WEEK }

    private val haiti: TimeZone = TimeZone.getTimeZone("America/Port-au-Prince")

    fun show(activity: AppCompatActivity) {
        val dialog = BottomSheetDialog(activity)
        val b = SheetDrawResultsBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)
        com.plusgroup.pos.util.Lang.apply(b.root)

        // Fèy la pran prèske tout ekran an
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            val h = (activity.resources.displayMetrics.heightPixels * 0.88f).toInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = h }
            BottomSheetBehavior.from(sheet).apply {
                peekHeight = h
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }

        var all: List<DrawResult> = emptyList()
        var range = Range.TODAY
        val chips = mapOf(Range.TODAY to b.chipToday, Range.YESTERDAY to b.chipYesterday, Range.WEEK to b.chipWeek)

        fun render() {
            chips.forEach { (r, v) -> v.isSelected = r == range }
            val today = isoDay(0)
            val yesterday = isoDay(-1)
            val list = when (range) {
                Range.TODAY -> all.filter { it.drawDate == today }
                Range.YESTERDAY -> all.filter { it.drawDate == yesterday }
                Range.WEEK -> all
            }
            val rangeLabel = when (range) {
                Range.TODAY -> prettyDay(today)
                Range.YESTERDAY -> prettyDay(yesterday)
                Range.WEEK -> "7 dènye jou yo"
            }
            val countLabel = if (list.isNotEmpty()) "  ·  ${list.size} tiraj" else ""
            b.tvResultsSubtitle.text = rangeLabel + countLabel

            b.llResults.removeAllViews()
            b.tvResultsEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            b.tvResultsEmpty.text = when (range) {
                Range.TODAY -> "Poko gen rezilta pou jodi a.\nTiraj yo ap parèt isit la depi boul yo soti."
                Range.YESTERDAY -> "Pa gen rezilta pou yè."
                Range.WEEK -> "Pa gen rezilta pou 7 dènye jou yo."
            }

            var lastDay: String? = null
            list.forEach { r ->
                // Nan "7 jou": yon ti tit pou chak jou
                if (range == Range.WEEK && r.drawDate != lastDay) {
                    lastDay = r.drawDate
                    b.llResults.addView(dayHeader(activity, r.drawDate.orEmpty()))
                }
                b.llResults.addView(card(activity, b.llResults, r))
            }
            b.svResults.scrollTo(0, 0)
        }

        fun load() {
            b.pbResults.visibility = View.VISIBLE
            b.tvResultsEmpty.visibility = View.GONE
            activity.lifecycleScope.launch {
                try {
                    val api = ApiClient.getService(activity.applicationContext)
                    all = api.getDrawResults().body()?.data.orEmpty()
                        .filter { !it.winningNumber1.isNullOrBlank() }
                    // Si pa gen anyen jodi a men gen yè, montre yè dirèkteman
                    if (range == Range.TODAY && all.none { it.drawDate == isoDay(0) } && all.any { it.drawDate == isoDay(-1) }) {
                        range = Range.YESTERDAY
                    }
                    render()
                } catch (e: Exception) {
                    b.llResults.removeAllViews()
                    b.tvResultsEmpty.visibility = View.VISIBLE
                    b.tvResultsEmpty.text = "Pa t ka chaje rezilta yo.\nVerifye entènèt la epi peze ↻."
                } finally {
                    b.pbResults.visibility = View.GONE
                }
            }
        }

        chips.forEach { (r, v) -> v.setOnClickListener { range = r; render() } }
        b.btnResultsRefresh.setOnClickListener { load() }

        dialog.show()
        load()
    }

    // ==================== KAT YO ====================

    private fun card(activity: AppCompatActivity, parent: ViewGroup, r: DrawResult): View {
        val c = ItemDrawResultBinding.inflate(LayoutInflater.from(activity), parent, false)
        c.tvDrawName.text = r.name ?: "—"

        val (label, color) = session(r)
        c.tvSession.text = label
        c.tvSession.setTextColor(color)
        c.tvSession.backgroundTintList = ColorStateList.valueOf((color and 0x00FFFFFF) or 0x1F000000)

        c.tvDrawWhen.text = buildString {
            append(prettyDay(r.drawDate.orEmpty()))
            if (!r.closeTime.isNullOrBlank()) append("  ·  Fèmen a ").append(r.closeTime)
        }

        val n1 = r.winningNumber1?.trim().orEmpty()
        val n2 = r.winningNumber2?.trim().orEmpty()
        val n3 = r.winningNumber3?.trim().orEmpty()
        c.tvBall1.text = n1.ifEmpty { "—" }
        c.tvBall2.text = n2.ifEmpty { "—" }
        c.tvBall3.text = n3.ifEmpty { "—" }

        // Bolèt: 2 dènye chif 1e lo a, 2e lo, 3e lo
        val lot1 = if (n1.length >= 2) n1.takeLast(2) else n1
        val parts = listOf(lot1, n2, n3).filter { it.isNotEmpty() }
        c.tvBorlette.text = if (parts.isEmpty()) "—" else "Bolèt:  " + parts.joinToString("   ·   ")
        return c.root
    }

    private fun dayHeader(activity: AppCompatActivity, iso: String): View =
        TextView(activity).apply {
            text = prettyDay(iso).uppercase(Locale.FRENCH)
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            letterSpacing = 0.08f
            val d = resources.displayMetrics.density
            setPadding((6 * d).toInt(), (10 * d).toInt(), 0, (8 * d).toInt())
        }

    /** Maten / Midi / Swa apati non an, sinon apati lè tiraj la fèmen. */
    private fun session(r: DrawResult): Pair<String, Int> {
        val n = r.name.orEmpty().lowercase(Locale.ROOT)
        val hour = r.closeTime?.substringBefore(':')?.toIntOrNull()
        val key = when {
            "matin" in n || "maten" in n || "morning" in n -> 0
            "midi" in n || "mid" in n -> 1
            "soir" in n || "swa" in n || "night" in n || "evening" in n -> 2
            hour != null && hour < 12 -> 0
            hour != null && hour < 17 -> 1
            else -> 2
        }
        return when (key) {
            0 -> "Maten" to Color.parseColor("#1F63FF")
            1 -> "Midi" to Color.parseColor("#E89B00")
            else -> "Swa" to Color.parseColor("#123A9E")
        }
    }

    // ==================== DAT (lè Ayiti) ====================

    private fun isoDay(offsetDays: Int): String {
        val cal = Calendar.getInstance(haiti)
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }.format(cal.time)
    }

    private fun prettyDay(iso: String): String {
        if (iso.isBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = haiti }
            val date = parser.parse(iso) ?: return iso
            val label = when (iso) {
                isoDay(0) -> "Jodi a"
                isoDay(-1) -> "Yè"
                else -> null
            }
            val f = SimpleDateFormat("EEE d MMM yyyy", Locale.FRENCH).apply { timeZone = haiti }.format(date)
            if (label != null) "$label, $f" else f.replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            iso
        }
    }
}
