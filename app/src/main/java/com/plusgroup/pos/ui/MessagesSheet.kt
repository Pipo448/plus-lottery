package com.plusgroup.pos.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.plusgroup.pos.R
import com.plusgroup.pos.databinding.SheetMessagesBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.AgentMessage
import com.plusgroup.pos.network.models.MarkReadRequest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Bwat mesaj ajan an (bouton anvlòp la sou akèy la): mesaj admin bank lan
 * voye yo. Mesaj enpòtan yo an wouj; sa ki poko li yo gen yon ti pwen ble.
 * Lè fèy la louvri, tout mesaj yo make "li".
 */
object MessagesSheet {

    /** Kantite mesaj ki poko li (pou ti badj wouj la). */
    suspend fun unreadCount(activity: AppCompatActivity): Int = try {
        ApiClient.getService(activity.applicationContext).getMessages().body()?.data?.unread ?: 0
    } catch (_: Exception) {
        -1 // pa chanje badj la si pa gen rezo
    }

    fun show(activity: AppCompatActivity, onClosed: () -> Unit = {}) {
        val dialog = BottomSheetDialog(activity)
        val b = SheetMessagesBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(b.root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            val h = (activity.resources.displayMetrics.heightPixels * 0.85f).toInt()
            sheet.layoutParams = sheet.layoutParams.apply { height = h }
            BottomSheetBehavior.from(sheet).apply {
                peekHeight = h
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
        dialog.setOnDismissListener { onClosed() }
        dialog.show()

        activity.lifecycleScope.launch {
            try {
                val api = ApiClient.getService(activity.applicationContext)
                val inbox = api.getMessages().body()?.data
                val list = inbox?.messages.orEmpty()
                val unread = inbox?.unread ?: 0
                b.tvMessagesSubtitle.text = when {
                    list.isEmpty() -> "Pa gen mesaj"
                    unread > 0 -> "${list.size} mesaj · $unread nouvo"
                    else -> "${list.size} mesaj"
                }
                b.llMessages.removeAllViews()
                list.forEach { b.llMessages.addView(card(activity, it)) }
                b.tvMessagesEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                if (unread > 0) {
                    try { api.markMessagesRead(MarkReadRequest(null)) } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                b.tvMessagesEmpty.visibility = View.VISIBLE
                b.tvMessagesEmpty.text = "Pa t ka chaje mesaj yo.\nVerifye entènèt la."
            } finally {
                b.pbMessages.visibility = View.GONE
            }
        }
    }

    private fun card(activity: AppCompatActivity, m: AgentMessage): View {
        val d = activity.resources.displayMetrics.density
        val important = m.important == true
        val accent = if (important) Color.parseColor("#D80F22") else Color.parseColor("#1F63FF")

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_result_card)
            if (important) backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF5F7"))
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (10 * d).toInt() }
            elevation = 1.5f * d
        }

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        if (m.read != true) {
            top.addView(View(activity).apply {
                setBackgroundResource(R.drawable.bg_pill_solid)
                backgroundTintList = ColorStateList.valueOf(accent)
                layoutParams = LinearLayout.LayoutParams((9 * d).toInt(), (9 * d).toInt()).apply { marginEnd = (8 * d).toInt() }
            })
        }
        top.addView(TextView(activity).apply {
            text = m.title ?: "—"
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (important) Color.parseColor("#A50A19") else ContextCompat.getColor(activity, R.color.pos_navy))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (important) {
            top.addView(TextView(activity).apply {
                text = "ENPÒTAN"
                textSize = 10f
                letterSpacing = 0.08f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_pill_solid)
                backgroundTintList = ColorStateList.valueOf(accent)
                setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
            })
        }
        card.addView(top)

        card.addView(TextView(activity).apply {
            text = m.body.orEmpty()
            textSize = 14f
            setLineSpacing(0f, 1.15f)
            setTextColor(ContextCompat.getColor(activity, R.color.pos_ink_soft))
            setPadding(0, (6 * d).toInt(), 0, 0)
        })
        card.addView(TextView(activity).apply {
            text = prettyDate(m.createdAt)
            textSize = 11f
            setTextColor(ContextCompat.getColor(activity, R.color.pos_muted))
            setPadding(0, (8 * d).toInt(), 0, 0)
        })
        return card
    }

    private fun prettyDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = p.parse(iso.take(19)) ?: return iso
            SimpleDateFormat("EEE d MMM · HH:mm", Locale.FRENCH)
                .apply { timeZone = TimeZone.getTimeZone("America/Port-au-Prince") }
                .format(date)
        } catch (_: Exception) {
            iso
        }
    }
}
