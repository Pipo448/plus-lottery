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
        "active" -> Color.parseColor("#1F63FF")
        "winner" -> Color.parseColor("#0F8A3A")
        "paid" -> Color.parseColor("#7C3AED")
        "lost" -> Color.parseColor("#64748B")
        "cancelled" -> Color.parseColor("#D80F22")
        else -> Color.parseColor("#071A4A")
    }

    /** Antèt la: vèsyon fonse koulè estati a */
    fun headerColor(status: String?): Int = when (status) {
        "winner" -> Color.parseColor("#0A6A2C")
        "paid" -> Color.parseColor("#5B21B6")
        "lost" -> Color.parseColor("#3F4B5E")
        "cancelled" -> Color.parseColor("#A50A19")
        else -> Color.parseColor("#071A4A")
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
        // Fich plizyè tiraj: yon ti tit pou chak tiraj (ak boul ki soti yo)
        val multi = lines.mapNotNull { it.drawName }.distinct().size > 1
        var lastDraw: String? = null
        lines.forEachIndexed { i, l ->
            if (multi && l.drawName != lastDraw) {
                lastDraw = l.drawName
                val res = l.drawResults.orEmpty().mapNotNull { it?.trim()?.takeIf { x -> x.isNotEmpty() } }
                b.llFicheLines.addView(TextView(activity).apply {
                    text = (l.drawName ?: "—") + if (res.isNotEmpty()) "   ·   " + res.joinToString(" - ") else ""
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(ContextCompat.getColor(activity, R.color.pos_navy))
                    val d = activity.resources.displayMetrics.density
                    setPadding((4 * d).toInt(), if (i == 0) 0 else (12 * d).toInt(), 0, (2 * d).toInt())
                })
            }
            b.llFicheLines.addView(lineRow(activity, l, i > 0 && !(multi && l.drawName != lines.getOrNull(i - 1)?.drawName)))
        }
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
            if (totalGain > 0) Color.parseColor("#0F8A3A") else ContextCompat.getColor(activity, R.color.pos_muted)
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
            b.llFicheActions.addView(actionTile(activity, "PEYE ${money.format(totalGain)}", R.drawable.ic_clipboard, "#0F8A3A", filled = true) {
                confirmPay(activity, ficheNo, totalGain) {
                    actions.onChanged?.invoke()
                    load(activity, dialog, b, ficheNo, actions)
                }
            }, LinearLayout.LayoutParams(0, dp(activity, 64), 2f).withMargins(activity))
        }
        actions.onReplay?.let { cb ->
            b.llFicheActions.addView(actionTile(activity, "Rejwe", R.drawable.ic_refresh, "#1F63FF") {
                dialog.dismiss(); cb(r)
            })
        }
        // Mon compte → "Ajan ka re-enprime fich"
        actions.onPrint?.takeIf { com.plusgroup.pos.util.TenantRules.canReprint }?.let { cb ->
            b.llFicheActions.addView(actionTile(activity, "Enprime", R.drawable.ic_print, "#071A4A") { cb(r) })
        }
        // Pataje fich la: WhatsApp, SMS, Telegram… (Mon compte → "Pataje fich sou WhatsApp")
        if (com.plusgroup.pos.util.TenantRules.showShare) {
            b.llFicheActions.addView(actionTile(activity, "Pataje", R.drawable.ic_share, "#0F8A3A") {
                shareFiche(activity, r)
            })
        }
        // Elimine: sèlman nan X premye minit yo (Mon compte → minit pou anile)
        if (status == "active" && withinCancelWindow(r.soldAt)) {
            b.llFicheActions.addView(actionTile(activity, "Elimine", R.drawable.ic_delete, "#D80F22") {
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
            setTextColor(if (won) Color.parseColor("#0A6A2C") else ContextCompat.getColor(activity, R.color.pos_navy))
            letterSpacing = 0.04f
            setPadding((10 * d).toInt(), 0, 0, 0)
            if (cancelled) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(activity).apply {
            text = com.plusgroup.pos.util.FreeMariage.amountText(l.gameType, l.amount, money)
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
                setTextColor(if (cancelled) Color.parseColor("#D80F22") else Color.parseColor("#0F8A3A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (won) sub.addView(TextView(activity).apply {
                text = "+${money.format(l.prize ?: 0.0)}"
                textSize = 15f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(Color.parseColor("#0F8A3A"))
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

    /** Fich la toujou nan tan pou anile (menm règ ak backend lan; 0 = 5 minit). */
    private fun withinCancelWindow(soldAt: String?): Boolean {
        if (soldAt.isNullOrBlank()) return true
        val minutes = com.plusgroup.pos.util.TenantRules.cancelMinutes.takeIf { it > 0 } ?: 5
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val sold = p.parse(soldAt.take(19))?.time ?: return true
            System.currentTimeMillis() - sold <= minutes * 60_000L
        } catch (_: Exception) {
            true // pa ka li dat la: kite backend lan deside
        }
    }

    private fun confirmCancel(activity: AppCompatActivity, ficheNo: String, lines: Int, onDone: () -> Unit) {
        // Mon compte → "Anile san konfimasyon"
        if (com.plusgroup.pos.util.TenantRules.cancelWithoutConfirm) {
            doCancel(activity, ficheNo, onDone)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Elimine fich la?")
            .setMessage("Tout $lines liy fich #$ficheNo ap anile. Aksyon sa a pa ka defèt.")
            .setPositiveButton("Wi, elimine l") { _, _ -> doCancel(activity, ficheNo, onDone) }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun doCancel(activity: AppCompatActivity, ficheNo: String, onDone: () -> Unit) {
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

    private fun shareFiche(activity: AppCompatActivity, r: VerifyTicketResult) {
        val shareLines = r.lines.orEmpty().map { l ->
            com.plusgroup.pos.util.FicheShare.Line(
                drawName = l.drawName ?: r.drawName,
                code = gameCode(l.gameType, l.numero, l.option),
                numero = l.numero ?: "",
                amount = l.amount ?: 0.0,
                free = com.plusgroup.pos.util.FreeMariage.isFree(l.gameType),
            )
        }
        val text = com.plusgroup.pos.util.FicheShare.buildText(
            ficheNumber = formatFicheNo(r.ficheNumber ?: r.ticketNumber ?: "—"),
            dateText = prettyDateTime(r.soldAt),
            agent = r.agent,
            lines = shareLines,
            total = r.betAmount ?: shareLines.sumOf { it.amount },
            statusLabel = FicheStatus.label(r.status),
        )
        com.plusgroup.pos.util.FicheShare.share(activity, text)
    }

    // ==================== Ti zouti ====================

    fun gameCode(gameType: String?, numero: String?, option: String?): String {
        val base = when (gameType?.lowercase(Locale.ROOT)) {
            "borlette" -> "BO"
            "loto3" -> "L3"
            "mariage" -> "MA"
            com.plusgroup.pos.util.FreeMariage.TYPE -> com.plusgroup.pos.util.FreeMariage.CODE
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
        "BO" -> Color.parseColor("#1F63FF")
        "MA" -> Color.parseColor("#C2570A")
        "MG" -> Color.parseColor("#DB2777")
        "L3" -> Color.parseColor("#7C3AED")
        "L4" -> Color.parseColor("#0F766E")
        "L5" -> Color.parseColor("#BE123C")
        else -> Color.parseColor("#071A4A")
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
