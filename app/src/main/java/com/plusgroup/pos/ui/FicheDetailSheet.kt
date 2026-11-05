package com.plusgroup.pos.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.plusgroup.pos.R
import com.plusgroup.pos.databinding.ItemFicheCardBinding
import com.plusgroup.pos.databinding.SheetFicheDetailBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.VerifyLine
import com.plusgroup.pos.network.models.VerifyTicketResult
import com.plusgroup.pos.util.ApiErrors
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Koulè ak non chak estati fich (menm toupatou nan app la). */
object FicheStatus {
    fun label(status: String?): String = when (status) {
        "active" -> "An atant"
        "winner" -> "Genyen"
        "paid" -> "Peye"
        "lost" -> "Pèdi"
        "cancelled" -> "Elimine"
        else -> status ?: "—"
    }

    fun color(status: String?): Int = when (status) {
        "active" -> Color.parseColor("#2563EB")
        "winner" -> Color.parseColor("#16A34A")
        "paid" -> Color.parseColor("#0B8FD8")
        "lost" -> Color.parseColor("#64748B")
        "cancelled" -> Color.parseColor("#E11D48")
        else -> Color.parseColor("#1B0E52")
    }

    /** Antèt la: vèsyon fonse koulè estati a */
    fun headerColor(status: String?): Int = when (status) {
        "winner" -> Color.parseColor("#0F7A36")
        "paid" -> Color.parseColor("#086AA3")
        "lost" -> Color.parseColor("#3F4B5E")
        "cancelled" -> Color.parseColor("#9F1239")
        else -> Color.parseColor("#1B0E52")
    }

    fun tint(color: Int, alpha: Int = 0x22): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Pilil estati: tèks koulè + fon klè */
    fun stylePill(view: TextView, status: String?, onDark: Boolean = false) {
        val c = color(status)
        view.text = label(status)
        if (onDark) {
            view.setTextColor(headerColor(status))
            view.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            view.setTextColor(c)
            view.backgroundTintList = ColorStateList.valueOf(tint(c))
        }
    }

    /** Ranpli yon kat fich (item_fiche_card) */
    fun bindCard(
        c: ItemFicheCardBinding,
        status: String?,
        drawName: String?,
        meta: String,
        balls: String?,
        mise: String,
        gain: String?,
    ) {
        c.vCardStrip.backgroundTintList = ColorStateList.valueOf(color(status))
        c.tvCardDraw.text = drawName ?: "—"
        stylePill(c.tvCardStatus, status)
        c.tvCardMeta.text = meta
        c.tvCardBalls.text = balls.orEmpty()
        c.tvCardBalls.visibility = if (balls.isNullOrBlank()) View.GONE else View.VISIBLE
        c.tvCardMise.text = mise
        if (gain.isNullOrBlank()) {
            c.tvCardGain.visibility = View.GONE
        } else {
            c.tvCardGain.visibility = View.VISIBLE
            c.tvCardGain.text = gain
        }
    }
}

/**
 * Detay yon fich (fèy anba): estati, boul ki soti, CHAK LIY ak boul ki
 * genyen an an vèt, total mise/gain, epi aksyon yo:
 *   • Peye (fich ki genyen, poko peye)
 *   • Elimine (fich an atant)
 *   • Rejwe / Enprime (si ekran ki louvri l la bay yo)
 */
object FicheDetailSheet {

    class Actions(
        val onReplay: ((VerifyTicketResult) -> Unit)? = null,
        val onPrint: ((VerifyTicketResult) -> Unit)? = null,
        val onChanged: (() -> Unit)? = null,
    )

    private val money = DecimalFormat("#,##0.00")

    fun show(activity: AppCompatActivity, ficheNumber: String, actions: Actions = Actions()) {
        val dialog = BottomSheetDialog(activity)
        val b = SheetFicheDetailBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            val h = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = h }
            BottomSheetBehavior.from(sheet).apply {
                peekHeight = h
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }

        b.tvFicheDraw.text = "Fich"
        b.tvFicheNumber.text = "#$ficheNumber"
        dialog.show()
        load(activity, dialog, b, ficheNumber, actions)
    }

    private fun load(
        activity: AppCompatActivity,
        dialog: BottomSheetDialog,
        b: SheetFicheDetailBinding,
        ficheNumber: String,
        actions: Actions,
    ) {
        b.pbFiche.visibility = View.VISIBLE
        activity.lifecycleScope.launch {
            try {
                val res = ApiClient.getService(activity.applicationContext).verifyTicket(ficheNumber)
                val r = res.body()?.data
                if (!res.isSuccessful || r == null) {
                    showError(b, ApiErrors.message(res, "Fich #$ficheNumber pa jwenn."))
                    return@launch
                }
                render(activity, dialog, b, r, actions)
            } catch (e: Exception) {
                showError(b, "Pa t ka chaje fich la. Verifye entènèt la.")
            } finally {
                b.pbFiche.visibility = View.GONE
            }
        }
    }

    private fun showError(b: SheetFicheDetailBinding, msg: String) {
        b.tvFicheDraw.text = "Fich pa jwenn"
        b.tvFicheMeta.text = msg
        b.llFicheLines.visibility = View.GONE
        b.tvFicheTotalMise.text = "—"
        b.tvFicheTotalGain.text = "—"
        b.llFicheActions.removeAllViews()
    }

    private fun render(
        activity: AppCompatActivity,
        dialog: BottomSheetDialog,
        b: SheetFicheDetailBinding,
        r: VerifyTicketResult,
        actions: Actions,
    ) {
        val ficheNo = r.ficheNumber ?: r.ticketNumber ?: "—"
        val status = r.status

        // ----- Antèt -----
        b.llFicheHeader.backgroundTintList = ColorStateList.valueOf(FicheStatus.headerColor(status))
        b.tvFicheDraw.text = r.drawName ?: "—"
        FicheStatus.stylePill(b.tvFicheStatus, status, onDark = true)
        b.tvFicheNumber.text = "#${formatFicheNo(ficheNo)}"
        b.tvFicheMeta.text = listOfNotNull(
            prettyDateTime(r.soldAt),
            r.agent?.let { "Ajan $it" },
        ).joinToString("  ·  ")

        val results = r.results.orEmpty().mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (results.isNotEmpty()) {
            b.llFicheBalls.visibility = View.VISIBLE
            b.tvFicheBall1.text = results.getOrNull(0) ?: "—"
            b.tvFicheBall2.text = results.getOrNull(1) ?: "—"
            b.tvFicheBall3.text = results.getOrNull(2) ?: "—"
            b.tvFicheBall1.textSize = if ((results.getOrNull(0)?.length ?: 0) >= 3) 12f else 14f
        } else {
            b.llFicheBalls.visibility = View.GONE
        }

        // ----- Liy yo -----
        b.llFicheLines.visibility = View.VISIBLE
        b.llFicheLines.removeAllViews()
        val lines = r.lines.orEmpty()
        lines.forEachIndexed { i, l -> b.llFicheLines.addView(lineRow(activity, l, i > 0)) }
        if (lines.isEmpty()) {
            b.llFicheLines.addView(TextView(activity).apply {
                text = "Pa gen liy."
                setTextColor(ContextCompat.getColor(activity, R.color.pos_muted))
            })
        }

        // ----- Total -----
        val totalMise = r.betAmount ?: lines.sumOf { it.amount ?: 0.0 }
        val totalGain = r.prizeAmount ?: lines.filter { it.status == "winner" || it.status == "paid" }.sumOf { it.prize ?: 0.0 }
        b.tvFicheTotalMise.text = "${money.format(totalMise)} HTG"
        b.tvFicheTotalGain.text = if (totalGain > 0) "+${money.format(totalGain)}" else money.format(0.0)
        b.tvFicheTotalGain.setTextColor(
            if (totalGain > 0) Color.parseColor("#16A34A") else ContextCompat.getColor(activity, R.color.pos_muted)
        )

        b.tvFicheNote.visibility = View.VISIBLE
        b.tvFicheNote.text = when (status) {
            "winner" -> "Fich sa a genyen. Peye kliyan an epi peze PEYE pou anrejistre l."
            "paid" -> "Fich sa a deja peye — li pa ka peye yon dezyèm fwa."
            "active" -> "Rezilta tiraj la poko soti."
            "lost" -> "Fich sa a pa genyen."
            "cancelled" -> "Fich sa a te elimine."
            else -> ""
        }

        // ----- Aksyon -----
        b.llFicheActions.removeAllViews()
        if (status == "winner") {
            b.llFicheActions.addView(actionTile(activity, "PEYE ${money.format(totalGain)}", R.drawable.ic_clipboard, "#16A34A", filled = true) {
                confirmPay(activity, ficheNo, totalGain) {
                    actions.onChanged?.invoke()
                    load(activity, dialog, b, ficheNo, actions)
                }
            }, LinearLayout.LayoutParams(0, dp(activity, 64), 2f).withMargins(activity))
        }
        actions.onReplay?.let { cb ->
            b.llFicheActions.addView(actionTile(activity, "Rejwe", R.drawable.ic_refresh, "#2563EB") {
                dialog.dismiss(); cb(r)
            })
        }
        actions.onPrint?.let { cb ->
            b.llFicheActions.addView(actionTile(activity, "Enprime", R.drawable.ic_print, "#1B0E52") { cb(r) })
        }
        if (status == "active") {
            b.llFicheActions.addView(actionTile(activity, "Elimine", R.drawable.ic_delete, "#E11D48") {
                confirmCancel(activity, ficheNo, lines.size) {
                    dialog.dismiss()
                    actions.onChanged?.invoke()
                }
            })
        }
        b.llFicheActions.visibility = if (b.llFicheActions.childCount == 0) View.GONE else View.VISIBLE
    }

    // Yon liy: [BO] 23 ................ 50,00
    //           Genyen 1e lo                 +500,00
    private fun lineRow(activity: AppCompatActivity, l: VerifyLine, withTopMargin: Boolean): View {
        val won = l.status == "winner" || l.status == "paid"
        val cancelled = l.status == "cancelled"
        val d = activity.resources.displayMetrics.density

        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (won) R.drawable.bg_line_win else R.drawable.bg_line_plain)
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { if (withTopMargin) topMargin = (6 * d).toInt() }
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gameColor = gameColor(l.gameType, l.numero)
        row.addView(TextView(activity).apply {
            text = gameCode(l.gameType, l.numero, l.option)
            textSize = 11f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(gameColor)
            setBackgroundResource(R.drawable.bg_pill_solid)
            backgroundTintList = ColorStateList.valueOf(FicheStatus.tint(gameColor))
            setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
        })
        row.addView(TextView(activity).apply {
            text = l.numero ?: "—"
            textSize = 19f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(if (won) Color.parseColor("#0F7A36") else ContextCompat.getColor(activity, R.color.pos_navy))
            letterSpacing = 0.04f
            setPadding((10 * d).toInt(), 0, 0, 0)
            if (cancelled) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(activity).apply {
            text = money.format(l.amount ?: 0.0)
            textSize = 15f
            setTextColor(ContextCompat.getColor(activity, R.color.pos_ink_soft))
        })
        wrap.addView(row)

        if (won || cancelled) {
            val sub = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (4 * d).toInt(), 0, 0)
            }
            sub.addView(TextView(activity).apply {
                text = when {
                    cancelled -> "Anile"
                    l.winningPosition != null && l.winningPosition > 0 -> "Genyen ${l.winningPosition}e lo" + if (l.status == "paid") " · peye" else ""
                    else -> "Genyen" + if (l.status == "paid") " · peye" else ""
                }
                textSize = 12f
                setTextColor(if (cancelled) Color.parseColor("#E11D48") else Color.parseColor("#16A34A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (won) sub.addView(TextView(activity).apply {
                text = "+${money.format(l.prize ?: 0.0)}"
                textSize = 15f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(Color.parseColor("#16A34A"))
            })
            wrap.addView(sub)
        }
        return wrap
    }

    private fun actionTile(
        activity: AppCompatActivity,
        label: String,
        icon: Int,
        colorHex: String,
        filled: Boolean = false,
        onClick: () -> Unit,
    ): TextView {
        val c = Color.parseColor(colorHex)
        return TextView(activity, null, 0, R.style.Widget_PlusPOS_ActionTile).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, dp(activity, 64), 1f).withMargins(activity)
            val dr = ContextCompat.getDrawable(activity, icon)?.mutate()
            dr?.setTint(if (filled) Color.WHITE else c)
            setCompoundDrawablesWithIntrinsicBounds(null, dr, null, null)
            setTextColor(if (filled) Color.WHITE else c)
            backgroundTintList = ColorStateList.valueOf(if (filled) c else FicheStatus.tint(c, 0x1A))
            setOnClickListener { onClick() }
        }
    }

    private fun confirmPay(activity: AppCompatActivity, ficheNo: String, amount: Double, onDone: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Peye fich la?")
            .setMessage("Ou pral peye ${money.format(amount)} HTG pou fich #$ficheNo.\nFich la pa ka peye yon dezyèm fwa.")
            .setPositiveButton("Wi, peye") { _, _ ->
                activity.lifecycleScope.launch {
                    try {
                        val res = ApiClient.getService(activity.applicationContext).payFiche(ficheNo)
                        if (res.isSuccessful) {
                            val paid = res.body()?.data?.totalPaid ?: amount
                            Toast.makeText(activity, "Fich peye: ${money.format(paid)} HTG", Toast.LENGTH_LONG).show()
                            onDone()
                        } else {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle("Fich la pa ka peye")
                                .setMessage(ApiErrors.message(res, "Pa t ka peye fich la."))
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Erè rezo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun confirmCancel(activity: AppCompatActivity, ficheNo: String, lines: Int, onDone: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Elimine fich la?")
            .setMessage("Tout $lines liy fich #$ficheNo ap anile. Aksyon sa a pa ka defèt.")
            .setPositiveButton("Wi, elimine l") { _, _ ->
                activity.lifecycleScope.launch {
                    try {
                        val res = ApiClient.getService(activity.applicationContext).cancelFiche(ficheNo)
                        if (res.isSuccessful) {
                            Toast.makeText(activity, "Fich elimine", Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle("Fich la pa ka elimine")
                                .setMessage(ApiErrors.message(res, "Pa t ka anile fich la."))
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Erè rezo: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Non", null)
            .show()
    }

    // ==================== Ti zouti ====================

    fun gameCode(gameType: String?, numero: String?, option: String?): String {
        val base = when (gameType?.lowercase(Locale.ROOT)) {
            "borlette" -> "BO"
            "loto3" -> "L3"
            "mariage" -> "MA"
            "loto4" -> "L4"
            "loto5" -> "L5"
            else -> when {
                numero.orEmpty().contains("*") -> "MA"
                numero.orEmpty().length == 3 -> "L3"
                numero.orEmpty().length == 4 -> "L4"
                numero.orEmpty().length == 5 -> "L5"
                else -> "BO"
            }
        }
        val opt = option?.trimStart('0')?.takeIf { it.isNotEmpty() }
        return if (opt != null) "$base O$opt" else base
    }

    private fun gameColor(gameType: String?, numero: String?): Int = when (gameCode(gameType, numero, null)) {
        "BO" -> Color.parseColor("#2563EB")
        "MA" -> Color.parseColor("#C2570A")
        "L3" -> Color.parseColor("#7C3AED")
        "L4" -> Color.parseColor("#0F766E")
        "L5" -> Color.parseColor("#BE123C")
        else -> Color.parseColor("#1B0E52")
    }

    fun formatFicheNo(n: String): String =
        if (n.length == 12 && n.all { it.isDigit() }) "${n.substring(0, 4)}-${n.substring(4, 8)}-${n.substring(8)}" else n

    fun prettyDateTime(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val d = p.parse(iso.take(19)) ?: return iso
            SimpleDateFormat("d MMM yyyy · HH:mm", Locale.FRENCH).format(d)
        } catch (_: Exception) {
            iso
        }
    }

    private fun dp(activity: AppCompatActivity, v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun LinearLayout.LayoutParams.withMargins(activity: AppCompatActivity) = apply {
        val m = dp(activity, 4)
        setMargins(m, 0, m, 0)
    }
}
