package com.plusgroup.pos

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.plusgroup.pos.databinding.ActivityDashboardBinding
import com.plusgroup.pos.databinding.DialogInfoListBinding
import com.plusgroup.pos.databinding.ItemSettingsRowBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.util.SessionManager
import com.plusgroup.pos.util.TenantBranding
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Ekran dashboard (paj akèy apre login).
 *
 * "Bolèt" (anba a) = ekran sa a. "Paramèt" (anba a) louvri SettingsActivity.
 * Ikòn enprimant (anwo agoch) louvri MainActivity (konfigirasyon enprimant).
 *
 * Bouton 3 pwen an (anwo adwat) louvri yon meni:
 *   - Tiraj disponib → lis tiraj ki ouvè; klike youn louvri Nouvèl Fich ak tiraj sa a deja chwazi
 *   - Boul ki sòti   → dènye nimewo ki soti yo
 *   - Kont           → enfòmasyon ajan an + balans
 *   - Dekonekte      → konfime, efase sesyon an, retounen sou Login
 */
class DashboardActivity : BaseActivity() {

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
        binding.btnMenu.setOnClickListener { showMainMenu() }
        binding.btnRefresh.setOnClickListener { loadProfileAndBalance(forceBranding = true) }

        binding.btnNouvelFich.setOnClickListener {
            startActivity(Intent(this, NewFicheActivity::class.java))
        }
        binding.btnChacheFich.setOnClickListener {
            startActivity(Intent(this, SearchFicheActivity::class.java))
        }
        binding.btnFichMwenYo.setOnClickListener {
            startActivity(Intent(this, MyFichesActivity::class.java))
        }
        binding.btnRapo.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        binding.tabBolet.setOnClickListener { /* Nou deja la */ }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadProfileAndBalance()
    }

    override fun onResume() {
        super.onResume()
        // Rafrechi balans lan chak fwa moun nan retounen sou ekran sa a
        // (egzanp: apre l fin vann yon fich).
        loadProfileAndBalance()
    }

    // Logo tenant lan (Mon compte → Paramètres). San logo: logo Plus la rete.
    private fun showTenantLogo() {
        val logo = TenantBranding.logo(this)
        if (logo != null) binding.ivLogo.setImageBitmap(logo)
        else binding.ivLogo.setImageResource(R.drawable.ic_launcher)
    }

    private fun loadProfileAndBalance(forceBranding: Boolean = false) {
        showTenantLogo() // touswit ak sa ki sere sou aparèy la
        lifecycleScope.launch {
            TenantBranding.load(applicationContext, force = forceBranding)
            showTenantLogo()
        }
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

    // ==================== MENI 3 PWEN ====================

    private fun showMainMenu() {
        val popup = PopupMenu(this, binding.btnMenu, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_dashboard, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuTirajDisponib -> showTirajDisponib()
                R.id.menuBoulKiSoti -> showBoulKiSoti()
                R.id.menuKont -> showKont()
                R.id.menuGwoseEkran -> com.plusgroup.pos.util.UiScale.showPicker(this)
                R.id.menuDekonekte -> confirmLogout()
            }
            true
        }
        popup.show()
    }

    /** Lis tiraj ki ouvè kounye a. Klike youn → Nouvèl Fich ak tiraj la deja chwazi. */
    private fun showTirajDisponib() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val draws = api.getDraws("open").body()?.data.orEmpty()
                    .filter { !it.name.isNullOrBlank() }

                if (draws.isEmpty()) {
                    MaterialAlertDialogBuilder(this@DashboardActivity)
                        .setTitle("Tiraj disponib")
                        .setMessage("Pa gen tiraj ouvè kounye a.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                val names = draws.map { it.name!! }.toTypedArray()
                MaterialAlertDialogBuilder(this@DashboardActivity)
                    .setTitle("Tiraj disponib (${draws.size})")
                    .setItems(names) { _, which ->
                        val intent = Intent(this@DashboardActivity, NewFicheActivity::class.java)
                            .putExtra("PREFILL_DRAW_NAME", names[which])
                        startActivity(intent)
                    }
                    .setNegativeButton("Fèmen", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Erè chaje tiraj yo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Dènye boul ki soti pou chak tiraj ki gen rezilta. */
    private fun showBoulKiSoti() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val results = api.getDrawResults().body()?.data.orEmpty()

                if (results.isEmpty()) {
                    MaterialAlertDialogBuilder(this@DashboardActivity)
                        .setTitle("Boul ki sòti")
                        .setMessage("Pa gen rezilta disponib kounye a.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }

                val rows = results.map { r ->
                    val label = buildString {
                        append(r.name ?: "—")
                        if (!r.drawDate.isNullOrBlank()) append("\n").append(r.drawDate)
                    }
                    val numbers = listOf(r.winningNumber1, r.winningNumber2, r.winningNumber3)
                        .joinToString("  ·  ") { it ?: "—" }
                    label to numbers
                }
                showInfoDialog("Boul ki sòti", rows)
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Enfòmasyon kont ajan an + balans. */
    private fun showKont() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val profile = api.getProfile().body()?.data
                val balance = api.getBalance().body()?.data

                val rows = listOf(
                    "Non" to (profile?.fullName ?: "—"),
                    "Idantifyan" to (profile?.username ?: "—"),
                    "Bank" to (profile?.tenantName ?: "—"),
                    "Succursal" to (profile?.branchName ?: "—"),
                    "Estati" to (profile?.status ?: "—"),
                    "Balance" to moneyFormat.format(balance?.available ?: 0.0),
                    "Total vant" to moneyFormat.format(balance?.lifetimeSales ?: 0.0),
                    "Crédit de vente" to (profile?.creditVente?.let { moneyFormat.format(it) } ?: "Illimité"),
                    "Limite gain" to (profile?.limiteGain?.let { moneyFormat.format(it) } ?: "Illimité"),
                    "Pourcentage" to "${profile?.commissionRate ?: 0} %",
                    "Device id" to (profile?.deviceId ?: "—"),
                )
                showInfoDialog("Kont", rows)
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Pa t ka chaje kont lan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Dekonekte")
            .setMessage("Ou vle dekonekte sou aparèy sa a?")
            .setPositiveButton("Wi, dekonekte") { _, _ ->
                SessionManager(this).clear()
                val loginIntent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(loginIntent)
                finish()
            }
            .setNegativeButton("Non", null)
            .show()
    }

    /** Dyalòg ak yon lis liy "etikèt → valè" (menm estil ak ekran Paramèt la). */
    private fun showInfoDialog(title: String, rows: List<Pair<String, String>>) {
        val inflater = LayoutInflater.from(this)
        val body = DialogInfoListBinding.inflate(inflater)
        rows.forEach { (label, value) ->
            val row = ItemSettingsRowBinding.inflate(inflater, body.llInfoRows, false)
            row.tvLabel.text = label
            row.tvValue.text = value
            body.llInfoRows.addView(row.root)
        }
        // Retire dènye separatè a pou dyalòg la fini pwòp
        (body.llInfoRows.getChildAt(body.llInfoRows.childCount - 1) as? LinearLayout)
            ?.let { last -> if (last.childCount > 1) last.getChildAt(1).visibility = android.view.View.GONE }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(body.root)
            .setPositiveButton("Fèmen", null)
            .show()
    }
}
