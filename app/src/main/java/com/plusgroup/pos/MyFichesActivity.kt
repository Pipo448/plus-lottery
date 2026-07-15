package com.plusgroup.pos

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityMyFichesBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Ticket
import com.plusgroup.pos.printer.PrinterManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Fich Mwen Yo" — istwa tikè ajan an, ak filtè Debut/Fin.
 *
 * Klike sou nenpòt tikè nan lis la louvri yon dyalòg ak detay li, ansanm
 * ak 2 aksyon: "Rejwe" (reenprime resi a) ak "Elimine" (anile tikè a).
 */
class MyFichesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyFichesBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    // Ti kach lokal pou non konpayi/vandè — evite refè menm rekèt la chak
    // fwa ajan an "Rejwe" yon tikè.
    private var cachedCompanyName: String? = null
    private var cachedVendeur: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyFichesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.tvDebut.text = dateFormat.format(startDate.time)
        binding.tvFin.text = dateFormat.format(endDate.time)

        binding.tvDebut.setOnClickListener { pickDate(startDate) { binding.tvDebut.text = dateFormat.format(startDate.time) } }
        binding.tvFin.setOnClickListener { pickDate(endDate) { binding.tvFin.text = dateFormat.format(endDate.time) } }

        binding.btnSearch.setOnClickListener { loadFiches() }

        loadFiches()
    }

    private fun pickDate(target: Calendar, onPicked: () -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                target.set(year, month, day)
                onPicked()
            },
            target.get(Calendar.YEAR),
            target.get(Calendar.MONTH),
            target.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun loadFiches() {
        val start = apiDateFormat.format(startDate.time)
        val end = apiDateFormat.format(endDate.time)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getMyTickets(start, end)
                val tickets = res.body()?.data ?: emptyList()
                renderTickets(tickets)
            } catch (e: Exception) {
                Toast.makeText(this@MyFichesActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderTickets(tickets: List<Ticket>) {
        binding.llResultContainer.removeAllViews()

        if (tickets.isEmpty()) {
            addTotalRow(0.0)
            return
        }

        var total = 0.0

        for (ticket in tickets) {
            val amount = ticket.betAmount ?: 0.0
            if (ticket.status != "cancelled") total += amount

            // ticket.numbers se yon List<String> (backend retounen yon vrè
            // Array JSON) — nou jwenn yo ansanm ak vigil pou afichaj.
            val numerosText = ticket.numbers?.joinToString(", ") ?: ""

            val row = android.widget.TextView(this).apply {
                text = "${ticket.ticketNumber ?: "—"}   $numerosText   ${amount} HTG   (${ticket.status ?: "—"})"
                textSize = 14f
                setPadding(8, 8, 8, 8)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { showTicketDetailDialog(ticket) }
            }
            binding.llResultContainer.addView(row)
        }

        addTotalRow(total)
    }

    private fun addTotalRow(total: Double) {
        val totalView = android.widget.TextView(this).apply {
            text = "TOTAL : ${total}"
            setTextColor(android.graphics.Color.parseColor("#8B0000"))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }
        binding.llResultContainer.addView(totalView, 0)
    }

    // ==================== DYALÒG DETAY TIKÈ (Rejwe / Elimine) ====================

    private fun showTicketDetailDialog(ticket: Ticket) {
        val numerosText = ticket.numbers?.joinToString(", ") ?: "—"
        val amount = ticket.betAmount ?: 0.0
        val statusLabel = when (ticket.status) {
            "active" -> "Aktif"
            "cancelled" -> "Anile"
            "winner" -> "Genyen"
            "lost" -> "Pèdi"
            "paid" -> "Peye"
            else -> ticket.status ?: "—"
        }

        val message = buildString {
            appendLine("Tikè No: ${ticket.ticketNumber ?: "—"}")
            appendLine("Nimewo: $numerosText")
            appendLine("Montan: $amount HTG")
            appendLine("Statut: $statusLabel")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Detay Tikè")
            .setMessage(message)
            .setNeutralButton("Fèmen", null)
            .create()

        // Sèlman tikè "active" ka anile — pa gen sans "Elimine" yon tikè
        // ki deja anile, genyen, pèdi, oswa peye.
        if (ticket.status == "active") {
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Elimine") { _, _ ->
                confirmCancelTicket(ticket)
            }
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Rejwe") { _, _ ->
            reprintTicket(ticket)
        }

        dialog.show()
    }

    private fun confirmCancelTicket(ticket: Ticket) {
        AlertDialog.Builder(this)
            .setTitle("Konfime")
            .setMessage("Ou sèten ou vle anile tikè '${ticket.ticketNumber}' la? Aksyon sa a pa ka defèt.")
            .setPositiveButton("Wi, anile l") { _, _ -> cancelTicket(ticket) }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun cancelTicket(ticket: Ticket) {
        val id = ticket.id ?: run {
            Toast.makeText(this, "Erè: ID tikè a manke", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.cancelTicket(id)
                if (res.isSuccessful) {
                    Toast.makeText(this@MyFichesActivity, "Tikè anile avèk siksè", Toast.LENGTH_SHORT).show()
                    loadFiches() // reload lis la pou montre nouvo statut la
                } else {
                    Toast.makeText(
                        this@MyFichesActivity,
                        "Erè: ${res.errorBody()?.string() ?: "pa t ka anile"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MyFichesActivity, "Erè rezo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== REJWE (reenprime resi a) ====================

    private fun reprintTicket(ticket: Ticket) {
        val numerosText = ticket.numbers?.joinToString(", ") ?: "—"
        val amount = ticket.betAmount ?: 0.0

        lifecycleScope.launch {
            if (cachedCompanyName == null) {
                try {
                    val api = ApiClient.getService(applicationContext)
                    val profile = api.getProfile().body()?.data
                    cachedCompanyName = profile?.tenantName?.uppercase()
                    cachedVendeur = profile?.branchName ?: profile?.fullName
                } catch (_: Exception) {
                    // Kontinye ak valè default si sa echwe.
                }
            }

            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(
                            this@MyFichesActivity,
                            "Enprimant pa konekte.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@runOnUiThread
                    }
                    printerManager.printTicketReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        drawName = "—",
                        ticketNumber = ticket.ticketNumber ?: "—",
                        numbers = numerosText,
                        betAmount = amount.toString(),
                        footerMessage = "Kopi rejwe — ${cachedVendeur ?: ""}",
                    )
                    Toast.makeText(this@MyFichesActivity, "Rejwe voye bay enprimant lan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}