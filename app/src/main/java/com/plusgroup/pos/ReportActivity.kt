package com.plusgroup.pos

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityReportBinding
import com.plusgroup.pos.network.ApiClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Rapò" — kounye a, sèlman "PARTIEL" (Rapò Pasyèl) konplètman fonksyonèl.
 * F.TIRAGE, F.GAGNANT, TRASACT, ak F.ELIMINER se estòb ("byento") — chak
 * youn ta bezwen pwòp lojik/wout backend apa.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPartiel.setOnClickListener { loadPartialReport() }
        binding.btnFTirage.setOnClickListener { showComingSoon() }
        binding.btnFGagnant.setOnClickListener { showComingSoon() }
        binding.btnTrasact.setOnClickListener { showComingSoon() }
        binding.btnFEliminer.setOnClickListener { showComingSoon() }

        binding.tvDate.text = dateFormat.format(selectedDate.time)
        binding.tvDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate.set(year, month, day)
                    binding.tvDate.text = dateFormat.format(selectedDate.time)
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
            ).show()
        }
        binding.btnSearch.setOnClickListener { loadPartialReport() }

        binding.tabBolet.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        loadPartialReport()
    }

    private fun showComingSoon() {
        Toast.makeText(this, "Byento", Toast.LENGTH_SHORT).show()
    }

    private fun loadPartialReport() {
        val date = apiDateFormat.format(selectedDate.time)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getPartialReport(date)
                val report = res.body()?.data

                binding.tvTirageValue.text = report?.tirage ?: "—"
                binding.tvDateValue.text = report?.date ?: "—"
                binding.tvFicheVenduValue.text = "${report?.ficheVendu ?: 0}"
                binding.tvVenteValue.text = "${report?.vente ?: 0.0}"
                binding.tvCommissionValue.text = "${report?.commission ?: 0}"
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
