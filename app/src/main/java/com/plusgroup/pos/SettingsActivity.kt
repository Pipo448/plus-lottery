package com.plusgroup.pos

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivitySettingsBinding
import com.plusgroup.pos.databinding.ItemSettingsRowBinding
import com.plusgroup.pos.databinding.ItemSettingsSectionBinding
import com.plusgroup.pos.network.ApiClient
import kotlinx.coroutines.launch

/**
 * "Paramèt" — konfigirasyon ajan + tenant.
 *
 * IMPÒTAN: Prime (Borlette/Loto3/4/5/Mariage) ak Boul bloke se REYÈLMAN
 * pa AJAN (chak ajan ka gen valè diferan). Men "Configuration Boule"
 * (kantite boul/mariaj/loto3/4/5) ak tout bouton ("Mariage automatique",
 * "Interval minute eliminer fiche", elatriye) soti nan `company_settings`
 * — sa yo se paramèt pou TOUT TENANT LAN, PA sèlman ajan sa a. Yo make
 * kòm "(tenan antye)" pou pa bay konfizyon.
 *
 * "Date expiration", "Limite gain", "Limite credit" pa AJAN PA EGZISTE
 * ankò nan backend Plus la — yo montre "n/a" pou kounye a.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnKont.setOnClickListener { showComingSoon() }
        binding.btnTiraj.setOnClickListener { showComingSoon() }
        binding.btnBoulKiSoti.setOnClickListener { showDrawResults() }
        binding.btnMizajou.setOnClickListener { showComingSoon() }

        loadAll()
    }

    private fun showComingSoon() {
        Toast.makeText(this, "Byento", Toast.LENGTH_SHORT).show()
    }

    private fun showDrawResults() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val results = api.getDrawResults().body()?.data ?: emptyList()

                if (results.isEmpty()) {
                    Toast.makeText(this@SettingsActivity, "Pa gen rezilta disponib kounye a", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val message = results.joinToString("\n\n") { r ->
                    "${r.name} (${r.drawDate})\n${r.winningNumber1 ?: "—"} - ${r.winningNumber2 ?: "—"} - ${r.winningNumber3 ?: "—"}"
                }

                androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Boul Ki Soti")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadAll() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)

                val profile = api.getProfile().body()?.data
                val prime = api.getPrime().body()?.data
                val blockedNumbers = api.getBlockedNumbers().body()?.data ?: emptyList()
                val settingsList = api.getCompanySettings().body()?.data ?: emptyList()
                val settingsMap = settingsList.associate { (it.key ?: "") to (it.value ?: "") }

                binding.llSettingsContainer.removeAllViews()

                // ---- Enfòmasyon jeneral ----
                addRow("Bank", profile?.tenantName ?: "—")
                addRow("Succursal", formatWithStatus(profile?.branchName, profile?.branchStatus))
                addRow("Agent", formatWithStatus(profile?.fullName, profile?.status))
                addRow("Addresse", profile?.zoneAddress ?: "—")
                addRow("Device id", profile?.deviceId ?: "—")
                addRow("Date expiration", "n/a")
                addRow("Boul bloquer", "${blockedNumbers.size}")
                addRow("Identifiant", profile?.username ?: "—")
                addRow("Pourcentage", "${profile?.commissionRate ?: 0} %")
                addRow("Limite gain", "n/a")
                addRow("Limite credit", "n/a")

                // ---- Paramèt tenant antye (company_settings) ----
                addRow("Mariage gratuit (tenan antye)", settingsMap["mariage_gratuit"] ?: "—")
                addRow("Mariage automatique (tenan antye)", settingsMap["mariage_automatique"] ?: "—")
                addRow("Loto4 automatique (tenan antye)", settingsMap["lotto4_automatique"] ?: "—")
                addRow("Interval minute eliminer fiche (tenan antye)", settingsMap["interval_minute_eliminer_fiche"] ?: "—")

                // ---- Prime (pa AJAN — agent_prime_general) ----
                addSection("Prime (pa ajan)")
                addRow("Borlette", prime?.borlette ?: "—")
                addRow("Lotto 3", prime?.loto3 ?: "—")
                addRow("Lotto 4 (O1/O2/O3)", "${prime?.l4o1 ?: "—"} / ${prime?.l4o2 ?: "—"} / ${prime?.l4o3 ?: "—"}")
                addRow("Lotto 5 (O1/O2/O3)", "${prime?.l5o1 ?: "—"} / ${prime?.l5o2 ?: "—"} / ${prime?.l5o3 ?: "—"}")
                addRow("Mariage", prime?.mariage ?: "—")

                // ---- Configuration Boule (tenan antye) ----
                addSection("Configuration Boule (tenan antye)")
                addRow("Kantite boul", settingsMap["kantite_boul"] ?: "—")
                addRow("Kantite mariaj", settingsMap["kantite_mariaj"] ?: "—")
                addRow("Kantite loto3", settingsMap["kantite_loto3"] ?: "—")
                addRow("Kantite loto4", settingsMap["kantite_loto4"] ?: "—")
                addRow("Kantite loto5", settingsMap["kantite_loto5"] ?: "—")
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Erè chaje paramèt yo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatWithStatus(name: String?, status: String?): String {
        if (name.isNullOrEmpty()) return "—"
        return if (!status.isNullOrEmpty()) "$name - ($status)" else name
    }

    private fun addRow(label: String, value: String) {
        val rowBinding = ItemSettingsRowBinding.inflate(
            LayoutInflater.from(this), binding.llSettingsContainer, false
        )
        rowBinding.tvLabel.text = label
        rowBinding.tvValue.text = value
        binding.llSettingsContainer.addView(rowBinding.root)
    }

    private fun addSection(title: String) {
        val sectionBinding = ItemSettingsSectionBinding.inflate(
            LayoutInflater.from(this), binding.llSettingsContainer, false
        )
        (sectionBinding.root as TextView).text = title
        binding.llSettingsContainer.addView(sectionBinding.root)
    }
}