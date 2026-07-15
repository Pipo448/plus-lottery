package com.plusgroup.pos

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityMyFichesBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Ticket
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Fich Mwen Yo" — istwa tikè ajan an, ak filtè Debut/Fin.
 */
class MyFichesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyFichesBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

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

            // ticket.numbers se kounye a yon List<String> (backend retounen
            // yon vrè Array JSON) — nou jwenn yo ansanm ak vigil pou afichaj.
            val numerosText = ticket.numbers?.joinToString(", ") ?: ""

            val row = android.widget.TextView(this).apply {
                text = "${ticket.ticketNumber ?: "—"}   $numerosText   ${amount} HTG   (${ticket.status ?: "—"})"
                textSize = 14f
                setPadding(8, 8, 8, 8)
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
}