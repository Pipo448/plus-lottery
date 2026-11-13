package com.plusgroup.pos.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.plusgroup.pos.R
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.FinTirageReport
import com.plusgroup.pos.util.Dates
import com.plusgroup.pos.util.Lang
import com.plusgroup.pos.util.Money
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Paramèt → "Kont $" ak "Tiraj" (yo te make "Byento").
 *
 *  - AccountSheet: kont ajan an — balans, vant/komisyon/lo jodi a ak mwa sa a,
 *    sa ki deja peye, sa ki rete pou peye, depo/retrè, balans final.
 *  - DrawsSheet: tiraj JODI A yo — ouvè / poko louvri / fèmen / tire, lè
 *    yo louvri ak fèmen, konbyen tan ki rete, ak boul ki soti yo.
 *
 * Tout bagay bati an kòd (pa gen nouvo layout XML).
 */
object AgentSheets {

    // ---------- Zouti UI ----------
    private fun AppCompatActivity.dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun AppCompatActivity.col(id: Int) = ContextCompat.getColor(this, id)

    private fun AppCompatActivity.round(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        setCornerRadius(dp(radiusDp).toFloat())
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun AppCompatActivity.text(s: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private class Frame(val dialog: BottomSheetDialog, val body: LinearLayout, val subtitle: TextView)

    private fun AppCompatActivity.openSheet(title: String): Frame {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(col(R.color.pos_bg), 22)
            setPadding(dp(18), dp(14), dp(18), dp(18))
        }
        root.addView(View(this).apply {
            background = round(col(R.color.pos_line), 3)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12)
            }
        })
        root.addView(text(Lang.tr(title), 22f, col(R.color.pos_navy), true))
        val subtitle = text("", 14f, col(R.color.pos_muted))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            val h = (resources.displayMetrics.heightPixels * 0.88f).toInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = h }
            BottomSheetBehavior.from(sheet).apply {
                peekHeight = h; state = BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true
            }
        }
        body.addView(ProgressBar(this), LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(40)
        })
        dialog.show()
        return Frame(dialog, body, subtitle)
    }

    private fun AppCompatActivity.card(parent: LinearLayout, title: String? = null): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(col(R.color.pos_surface), 16, col(R.color.pos_line))
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }
        if (title != null) c.addView(text(Lang.tr(title), 16f, col(R.color.pos_navy), true),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return c
    }

    private fun AppCompatActivity.row(parent: LinearLayout, label: String, value: String, valueColor: Int? = null, strong: Boolean = false) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        r.addView(text(Lang.tr(label), 15f, col(R.color.pos_ink_soft)), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(text(value, if (strong) 17f else 15f, valueColor ?: col(R.color.pos_ink), true).apply {
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(-2, -2))
        parent.addView(r)
    }

    private fun AppCompatActivity.showError(f: Frame, msg: String) {
        f.body.removeAllViews()
        f.body.addView(text(msg, 15f, col(R.color.pos_red)).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30) })
    }

    private val money = Money.format()
    private fun htg(v: Double?) = "${money.format(v ?: 0.0)} HTG"
    private fun num(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun day(offset: Int = 0): String {
        val c = Calendar.getInstance(Dates.HAITI).apply { add(Calendar.DAY_OF_MONTH, offset) }
        return Dates.format(c.time, "yyyy-MM-dd")
    }

    // ==================== KONT $ ====================

    fun showAccount(activity: AppCompatActivity) = with(activity) {
        val f = openSheet("Kont mwen")
        val today = day()
        val monthStart = today.substring(0, 8) + "01"
        f.subtitle.text = Dates.format(Date(), "EEEE d MMMM yyyy", java.util.Locale.FRENCH)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val balA = async { runCatching { api.getBalance().body()?.data }.getOrNull() }
                val todayA = async { runCatching { api.getFinTirageReport(today, today, null).body()?.data }.getOrNull() }
                val monthA = async { runCatching { api.getFinTirageReport(monthStart, today, null).body()?.data }.getOrNull() }
                val bal = balA.await()
                val t = todayA.await()
                val m = monthA.await()
                if (bal == null && t == null && m == null) {
                    showError(f, Lang.tr("Pa t ka chaje kont lan. Verifye entènèt la."))
                    return@launch
                }
                f.body.removeAllViews()

                // Gwo kat balans
                val top = LinearLayout(this@with).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(col(R.color.pos_navy), col(R.color.pos_blue))).apply { setCornerRadius(dp(18).toFloat()) }
                    setPadding(dp(16), dp(14), dp(16), dp(16))
                }
                top.addView(text(Lang.tr("Balans ou"), 14f, col(R.color.pos_on_color_dim)))
                val balance = t?.balance ?: bal?.available
                top.addView(text(htg(balance), 28f, Color.WHITE, true))
                top.addView(text(Lang.tr("Vant total (depi kòmansman): ") + htg(bal?.lifetimeSales), 13f, col(R.color.pos_on_color_dim)))
                f.body.addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

                fun period(title: String, r: FinTirageReport?) {
                    val c = card(f.body, title)
                    if (r == null) { row(c, "—", Lang.tr("Pa disponib")); return }
                    val pp = r.profitPerte ?: 0.0
                    row(c, "Fich vann", "${r.ficheVendu ?: 0}")
                    row(c, "Vant", htg(r.vente))
                    row(c, "Komisyon ou", htg(r.commission), col(R.color.pos_green))
                    row(c, "Fich ganyan", "${r.ficheGagnant ?: 0}")
                    row(c, "Lo pou peye", htg(r.aPaye), col(R.color.pos_red))
                    if (r.dejaPeye != null) row(c, "Deja peye", htg(r.dejaPeye))
                    if (r.pokoPeye != null) row(c, "Rete pou peye", htg(r.pokoPeye), col(R.color.pos_amber_deep))
                    if (num(r.depot) != 0.0) row(c, "Depo", htg(num(r.depot)))
                    if (num(r.retrait) != 0.0) row(c, "Retrè", htg(num(r.retrait)))
                    row(c, if (pp >= 0) "Benefis" else "Pèt", htg(pp), col(if (pp >= 0) R.color.pos_green else R.color.pos_red), strong = true)
                }
                period("Jodi a", t)
                period("Mwa sa a", m)
                f.body.addView(text(Lang.tr("Pou detay: Rapò → F.TIRAGE / F.GAGNANT."), 13f, col(R.color.pos_muted)))
            } catch (e: Exception) {
                showError(f, Lang.tr("Pa t ka chaje kont lan. Verifye entènèt la."))
            }
        }
    }

    // ==================== TIRAJ ====================

    private fun hm(t: String?) = t?.take(5) ?: "—"

    /** Konbyen tan ki rete anvan lè sa a (jodi a, lè Ayiti). */
    private fun leftUntil(time: String?): String? {
        val hhmm = time?.take(5)?.split(":") ?: return null
        val h = hhmm.getOrNull(0)?.toIntOrNull() ?: return null
        val mi = hhmm.getOrNull(1)?.toIntOrNull() ?: return null
        val now = Calendar.getInstance(Dates.HAITI)
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1) // tiraj ki travèse minwi
        }
        val min = ((target.timeInMillis - now.timeInMillis) / 60000L).toInt()
        return if (min >= 60) "${min / 60}è ${min % 60}min" else "${min}min"
    }

    fun showDraws(activity: AppCompatActivity) = with(activity) {
        val f = openSheet("Tiraj jodi a")
        f.subtitle.text = Dates.format(Date(), "EEEE d MMMM yyyy", java.util.Locale.FRENCH)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getDraws(status = "")
                val draws: List<Draw> = res.body()?.data.orEmpty()
                if (!res.isSuccessful) { showError(f, Lang.tr("Pa t ka chaje tiraj yo. Verifye entènèt la.")); return@launch }
                f.body.removeAllViews()
                if (draws.isEmpty()) {
                    f.body.addView(text(Lang.tr("Pa gen tiraj jodi a. Kontakte administratè a."), 15f, col(R.color.pos_muted)).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30) })
                    return@launch
                }
                val order = mapOf("open" to 0, "scheduled" to 1, "drawn" to 2, "closed" to 3)
                val sorted = draws.sortedWith(compareBy({ order[it.status] ?: 4 }, { it.closeTime ?: "" }))
                val openCount = draws.count { it.status == "open" }
                f.subtitle.text = f.subtitle.text.toString() + "  ·  " +
                    Lang.tr("{n} ouvè").replace("{n}", openCount.toString())

                for (d in sorted) {
                    val c = card(f.body)
                    val head = LinearLayout(this@with).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    head.addView(text(d.name ?: "—", 17f, col(R.color.pos_navy), true), LinearLayout.LayoutParams(0, -2, 1f))
                    val (label, fg, bg) = when (d.status) {
                        "open" -> Triple("OUVÈ", col(R.color.pos_green), col(R.color.pos_tint_green))
                        "scheduled" -> Triple("POKO LOUVRI", col(R.color.pos_amber_deep), col(R.color.pos_tint_amber))
                        "drawn" -> Triple("TIRE", col(R.color.pos_blue), col(R.color.pos_tint_blue))
                        else -> Triple("FÈMEN", col(R.color.pos_red), col(R.color.pos_tint_red))
                    }
                    head.addView(text(Lang.tr(label), 12f, fg, true).apply {
                        background = round(bg, 20)
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                    })
                    c.addView(head)
                    val info = "${Lang.tr("Lè louvri")} ${hm(d.openTime)}  ·  ${Lang.tr("Lè fèmen")} ${hm(d.closeTime)}"
                    c.addView(text(info, 14f, col(R.color.pos_ink_soft)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    when (d.status) {
                        "open" -> leftUntil(d.closeTime)?.let {
                            c.addView(text(Lang.tr("Fèmen nan {t}").replace("{t}", it), 14f, col(R.color.pos_green), true))
                        }
                        "scheduled" -> leftUntil(d.openTime)?.let {
                            c.addView(text(Lang.tr("Ap louvri nan {t}").replace("{t}", it), 14f, col(R.color.pos_amber_deep), true))
                        }
                    }
                    if (!d.winningNumber1.isNullOrBlank()) {
                        val balls = listOfNotNull(d.winningNumber1, d.winningNumber2, d.winningNumber3).filter { it.isNotBlank() }
                        c.addView(text("${Lang.tr("Boul ki soti")}: " + balls.joinToString("  ·  "), 16f, col(R.color.pos_navy), true),
                            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
                    }
                }
            } catch (e: Exception) {
                showError(f, Lang.tr("Pa t ka chaje tiraj yo. Verifye entènèt la."))
            }
        }
    }
}
