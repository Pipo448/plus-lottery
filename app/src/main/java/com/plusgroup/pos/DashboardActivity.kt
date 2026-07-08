package com.plusgroup.pos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityDashboardBinding
import com.plusgroup.pos.network.ApiClient
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Ekran dashboard (paj akèy apre login). Estil sa a baze sou yon egzanp
 * yon lòt sistèm bolèt te montre kòm referans — pa sou yon ekran ki te
 * deja egziste nan app Plus la.
 *
 * "Bolèt" (anba a) = ekran sa a. "Paramèt" (anba a) ak ikòn enprimant
 * (anwo agoch) louvri MainActivity, ki deja gen konfigirasyon
 * enprimant + dekonekte.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val moneyFormat = DecimalFormat("#,##0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPrinter.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.btnMail.setOnClickListener {
            Toast.makeText(this, "Notifikasyon — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnMenu.setOnClickListener {
            Toast.makeText(this, "Meni — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnRefresh.setOnClickListener { loadProfileAndBalance() }

        binding.btnNouvelFich.setOnClickListener {
            startActivity(Intent(this, NewFicheActivity::class.java))
        }
        binding.btnChacheFich.setOnClickListener {
            Toast.makeText(this, "Chache Fich — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnFichMwenYo.setOnClickListener {
            Toast.makeText(this, "Fich Mwen Yo — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnRapo.setOnClickListener {
            Toast.makeText(this, "Rapò — byento", Toast.LENGTH_SHORT).show()
        }

        binding.tabBolet.setOnClickListener { /* Nou deja la */ }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        loadProfileAndBalance()
    }

    override fun onResume() {
        super.onResume()
        // Rafrechi balans lan chak fwa moun nan retounen sou ekran sa a
        // (egzanp: apre l fin vann yon fich).
        loadProfileAndBalance()
    }

    private fun loadProfileAndBalance() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)

                val profileRes = api.getProfile()
                val profile = profileRes.body()?.data
                val displayName = profile?.fullName ?: profile?.username ?: "Ajan"
                binding.tvSalut.text = "Salut $displayName"

                val balanceRes = api.getBalance()
                val balance = balanceRes.body()?.data

                binding.tvBalance.text = moneyFormat.format(balance?.available ?: 0.0)
                binding.tvCreditVente.text = profile?.creditVente?.let { moneyFormat.format(it) } ?: "Illimité"
                binding.tvLimiteGain.text = profile?.limiteGain?.let { moneyFormat.format(it) } ?: "Illimité"
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Pa t ka chaje pwofil: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
