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
class SearchFicheActivity : BaseActivity() {

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
            Toast.makeText(this, "Antre nimewo fich la (oswa nimewo tikè a)", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvResult.text = ""
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.verifyTicket(ticketNumber)
                val result = res.body()?.data

                if (!res.isSuccessful || result == null) {
                    binding.tvResult.text = com.plusgroup.pos.util.ApiErrors.message(res, "Fich '$ticketNumber' pa jwenn.")
                    return@launch
                }

                val statusLabel = when (result.status) {
                    "active" -> "Aktif (rezilta poko soti)"
                    "cancelled" -> "Anile"
                    "winner" -> "GENYEN ✅ — poko peye"
                    "paid" -> "Genyen — deja PEYE"
                    "lost" -> "Pèdi"
                    else -> result.status ?: "—"
                }
                val money = java.text.DecimalFormat("#,##0.00")
                val boulKiSoti = result.results?.filterNotNull()?.joinToString(" - ")?.takeIf { it.isNotBlank() }

                binding.tvResult.text = buildString {
                    appendLine("Fich No : ${result.ficheNumber ?: result.ticketNumber ?: ticketNumber}")
                    appendLine("Tiraj   : ${result.drawName ?: "—"} ${result.drawDate ?: ""}")
                    if (boulKiSoti != null) appendLine("Boul    : $boulKiSoti")
                    appendLine("Ajan    : ${result.agent ?: "—"}")
                    appendLine("Statut  : $statusLabel")
                    appendLine("--------------------------------")
                    result.lines.orEmpty().forEach { l ->
                        val opt = if (!l.option.isNullOrBlank()) " O${l.option.trimStart('0')}" else ""
                        val gain = when (l.status) {
                            "winner", "paid" -> "+" + money.format(l.prize ?: 0.0)
                            "cancelled" -> "anile"
                            "lost" -> "-"
                            else -> "..."
                        }
                        appendLine(String.format("%-9s %8s  %10s", (l.numero ?: "") + opt, money.format(l.amount ?: 0.0), gain))
                    }
                    appendLine("--------------------------------")
                    appendLine("Total mise : ${money.format(result.betAmount ?: 0.0)} HTG")
                    appendLine("Total gain : ${money.format(result.prizeAmount ?: 0.0)} HTG")
                    appendLine("Vandi      : ${result.soldAt ?: "—"}")
                }
            } catch (e: Exception) {
                binding.tvResult.text = "Erè: ${e.message}"
            }
        }
    }
}