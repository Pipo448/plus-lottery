package com.plusgroup.pos.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DecimalFormat

/**
 * PATAJE yon fich deyò APK a: WhatsApp, WhatsApp Business, SMS, Telegram,
 * imèl… Tèks la gen menm enfòmasyon ak resi enprime a (bolèt, nimewo fich,
 * dat, ajan, boul yo pa tiraj, total, mesaj anba fich la).
 * Sou WhatsApp, blòk boul yo parèt an ``monospace`` pou kolòn yo aliyen.
 */
object FicheShare {

    data class Line(
        val drawName: String?,
        val code: String,       // BO, L3, MA, L4 O1…
        val numero: String,
        val amount: Double,
        val free: Boolean = false, // maryaj gratis → "GRATIS"
    )

    private val money = DecimalFormat("#,##0.00")
    private const val WIDTH = 24

    private fun row(left: String, right: String): String {
        val space = (WIDTH - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(space) + right
    }

    /** Tèks fich la, pare pou WhatsApp (gra *...* ak blòk ```...```). */
    fun buildText(
        ficheNumber: String,
        dateText: String?,
        agent: String?,
        lines: List<Line>,
        total: Double,
        statusLabel: String? = null,
    ): String {
        val company = TenantBranding.companyName?.takeIf { it.isNotBlank() } ?: "PLUS GROUP LOTTERY"
        val sb = StringBuilder()
        sb.append("*").append(company.uppercase()).append("*\n")
        TenantBranding.phone?.takeIf { it.isNotBlank() }?.let { sb.append("Tel: ").append(it).append('\n') }
        TenantBranding.address?.takeIf { it.isNotBlank() }?.let { sb.append(it).append('\n') }
        sb.append('\n')
        sb.append("Ticket: *").append(ficheNumber).append("*\n")
        dateText?.takeIf { it.isNotBlank() }?.let { sb.append("Dat: ").append(it).append('\n') }
        agent?.takeIf { it.isNotBlank() }?.let { sb.append("Ajan: ").append(it).append('\n') }
        statusLabel?.takeIf { it.isNotBlank() }?.let { sb.append("Eta: ").append(it).append('\n') }

        sb.append("```\n")
        val byDraw = lines.groupBy { it.drawName?.takeIf { d -> d.isNotBlank() } ?: "—" }
        byDraw.entries.forEachIndexed { i, (draw, items) ->
            if (i > 0) sb.append('\n')
            sb.append(draw.uppercase().take(WIDTH)).append('\n')
            items.forEach { l ->
                sb.append(row("${l.code} ${l.numero}", if (l.free) FreeMariage.LABEL else money.format(l.amount))).append('\n')
            }
            if (byDraw.size > 1) sb.append(row("", "= " + money.format(items.sumOf { it.amount }))).append('\n')
        }
        sb.append("-".repeat(WIDTH)).append('\n')
        sb.append(row("TOTAL", money.format(total) + " HTG")).append('\n')
        sb.append("```\n")

        TenantBranding.message?.takeIf { it.isNotBlank() }?.let { sb.append('\n').append(it).append('\n') }
        sb.append("\n_Kenbe nimewo ticket la pou w ka touche si w genyen._")
        return sb.toString()
    }

    /** Montre chwa yo: WhatsApp, Lòt aplikasyon, Kopye. */
    fun share(activity: AppCompatActivity, text: String, title: String = "Pataje fich la") {
        val items = arrayOf("WhatsApp", "WhatsApp Business", "Lòt aplikasyon (SMS, Telegram…)", "Kopye tèks la")
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> sendTo(activity, text, "com.whatsapp")
                    1 -> sendTo(activity, text, "com.whatsapp.w4b")
                    2 -> chooser(activity, text)
                    else -> copy(activity, text)
                }
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    private fun baseIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun sendTo(activity: AppCompatActivity, text: String, pkg: String) {
        try {
            activity.startActivity(baseIntent(text).setPackage(pkg))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                if (pkg == "com.whatsapp") "WhatsApp pa enstale sou aparèy sa a" else "WhatsApp Business pa enstale",
                Toast.LENGTH_SHORT,
            ).show()
            chooser(activity, text)
        }
    }

    private fun chooser(activity: AppCompatActivity, text: String) {
        try {
            activity.startActivity(Intent.createChooser(baseIntent(text), "Pataje fich la avèk…"))
        } catch (_: ActivityNotFoundException) {
            copy(activity, text)
        }
    }

    private fun copy(activity: AppCompatActivity, text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Fich", text))
        Toast.makeText(activity, "Tèks fich la kopye", Toast.LENGTH_SHORT).show()
    }
}
