package com.plusgroup.pos

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivitySearchFicheBinding
import com.plusgroup.pos.network.ApiClient
import kotlinx.coroutines.launch

/**
 * "Chache Fich" — chèche yon tikè pa nimewo.
 *
 * NOTE: Bouton "SCAN" se yon estòb kounye a ("byento") — pou l vrèman li
 * yon kòd-ba ak kamera a, ta bezwen ajoute yon librè tankou ML Kit oswa
 * ZXing ak pèmisyon kamera; se yon pwochen etap apa.
 */
class SearchFicheActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchFicheBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchFicheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnScan.setOnClickListener {
            Toast.makeText(this, "Eskane kòd-ba — byento", Toast.LENGTH_SHORT).show()
        }

        binding.etTicket.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                searchTicket()
                true
            } else {
                false
            }
        }
        binding.btnSearchIcon.setOnClickListener { searchTicket() }
    }

    private fun searchTicket() {
        val ticketNumber = binding.etTicket.text.toString().trim()
        if (ticketNumber.isEmpty()) {
            Toast.makeText(this, "Antre yon nimewo tikè", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvResult.text = ""
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.verifyTicket(ticketNumber)
                val result = res.body()?.data

                if (!res.isSuccessful || result == null) {
                    binding.tvResult.text = "Tikè '$ticketNumber' pa jwenn."
                    return@launch
                }

                val statusLabel = when (result.status) {
                    "sold" -> "Vandi"
                    "cancelled" -> "Anile"
                    else -> result.status ?: "—"
                }
                val gagnanLabel = if (result.isWinner == true) "WI ✅ (${result.prizeAmount ?: 0} HTG)" else "Non"

                // result.numbers se yon List<String> (backend retounen yon
                // vrè Array JSON) — nou jwenn yo ansanm ak vigil pou afichaj
                // pwòp, olye montre fòma lis Kotlin brit tankou "[25, 32]".
                val numerosText = result.numbers?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "—"

                binding.tvResult.text = buildString {
                    appendLine("Tikè No: ${result.ticketNumber ?: ticketNumber}")
                    appendLine("Statut: $statusLabel")
                    appendLine("Nimewo: $numerosText")
                    appendLine("Montan: ${result.betAmount ?: 0} HTG")
                    appendLine("Gen: $gagnanLabel")
                    appendLine("Vandi: ${result.soldAt ?: "—"}")
                }
            } catch (e: Exception) {
                binding.tvResult.text = "Erè: ${e.message}"
            }
        }
    }
}