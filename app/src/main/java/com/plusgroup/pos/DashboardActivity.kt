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
    private val moneyFormat = com.plusgroup.pos.util.Money.format()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPrinter.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        // Bwat mesaj: mesaj admin bank lan voye bay ajan yo
        binding.btnMail.setOnClickListener {
            com.plusgroup.pos.ui.MessagesSheet.show(this) { setMailBadge(0) }
        }
        binding.btnMenu.setOnClickListener { showMainMenu() }
        // Konekte enprimant lan depi app la louvri: premye fich la soti touswit
        com.plusgroup.pos.printer.PrinterManager(applicationContext).connect()

        binding.btnRefresh.setOnClickListener { v ->
            v.animate().rotationBy(360f).setDuration(500).start()
            loadProfileAndBalance(forceBranding = true)
        }

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

        // Tab aktif la (Bolèt) parèt an lò ak ti liy anba l
        binding.tabBolet.isSelected = true
        binding.tabParamet.isSelected = false
        binding.tabBolet.setOnClickListener { /* Nou deja la */ }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // (loadProfileAndBalance fèt nan onResume, ki toujou vini apre onCreate)
    }

    override fun onResume() {
        // Nouvo vèsyon APK sou sit la? (maks 1 fwa chak 3 è)
        com.plusgroup.pos.util.AppUpdate.check(this, lifecycleScope)
        super.onResume()
        applyBoxRule()
        // Retou apre yon ti poz: renouvle token an DAVANS epi prechaje tiraj
        // yo, pou NOUVÈL FICH louvri touswit san ajan an pa rekonekte.
        lifecycleScope.launch {
            com.plusgroup.pos.network.ApiClient.keepTokenFresh(applicationContext)
            loadProfileAndBalance()
            launch { com.plusgroup.pos.util.DataCache.refresh(applicationContext) }
            // Vèsyon esè 7 jou: avètisman davans (1 fwa pa jou, 2 dènye jou yo)
            launch { com.plusgroup.pos.util.TrialNotice.check(this@DashboardActivity) }
            if (com.plusgroup.pos.util.TenantRules.showBox) {
                val unread = com.plusgroup.pos.ui.MessagesSheet.unreadCount(this@DashboardActivity)
                if (unread >= 0) setMailBadge(unread)
            }
        }
    }

    /** Mon compte → "Box": bwat mesaj (anvlòp) sou akèy la. */
    private fun applyBoxRule() {
        val on = com.plusgroup.pos.util.TenantRules.showBox
        binding.btnMail.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
        if (!on) binding.tvMailBadge.visibility = android.view.View.GONE
    }

    private fun setMailBadge(count: Int) {
        binding.tvMailBadge.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvMailBadge.text = if (count > 9) "9+" else count.toString()
    }

    // Logo tenant lan (Mon compte → Paramètres) parèt nan yon ti kare blan
    // sou antèt ble a. San logo: logo Plus Lotterie a rete.
    private fun showTenantLogo() {
        val logo = TenantBranding.logo(this)
        if (logo != null) {
            val pad = (8 * resources.displayMetrics.density).toInt()
            binding.ivLogo.setBackgroundResource(R.drawable.bg_logo_tile)
            binding.ivLogo.setPadding(pad, pad, pad, pad)
            binding.ivLogo.setImageBitmap(logo)
        } else {
            binding.ivLogo.background = null
            binding.ivLogo.setPadding(0, 0, 0, 0)
            binding.ivLogo.setImageResource(R.drawable.pl_logo_plus_lotterie)
        }
    }

    private fun loadProfileAndBalance(forceBranding: Boolean = false) {
        showTenantLogo() // touswit ak sa ki sere sou aparèy la
        lifecycleScope.launch {
            TenantBranding.load(applicationContext, force = forceBranding)
            applyBoxRule()
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
        com.plusgroup.pos.util.Lang.menu(this, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuTirajDisponib -> showTirajDisponib()
                R.id.menuBoulKiSoti -> showBoulKiSoti()
                R.id.menuKont -> showKont()
                R.id.menuGwoseEkran -> com.plusgroup.pos.util.UiScale.showPicker(this)
                R.id.menuLang -> showLangPicker()
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
                        .setTitle(com.plusgroup.pos.util.Lang.tr("Tiraj disponib"))
                        .setMessage(com.plusgroup.pos.util.Lang.tr("Pa gen tiraj ouvè kounye a."))
                        .setPositiveButton(com.plusgroup.pos.util.Lang.tr("OK"), null)
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
                    .setNegativeButton(com.plusgroup.pos.util.Lang.tr("Fèmen"), null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Erè chaje tiraj yo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Dènye boul ki soti pou chak tiraj ki gen rezilta. */
    private fun showBoulKiSoti() {
        com.plusgroup.pos.ui.DrawResultsSheet.show(this)
    }

    /** Enfòmasyon kont ajan an + balans. */
    private fun showKont() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val profile = api.getProfile().body()?.data
                val balance = api.getBalance().body()?.data

                val rows = listOf(
                    "Non konplè" to (profile?.fullName ?: "—"),
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

    /** Kreyòl / Français — tout APK a chanje touswit. */
    private fun showLangPicker() {
        val codes = arrayOf("ht", "fr")
        val names = arrayOf("Kreyòl", "Français")
        val cur = codes.indexOf(com.plusgroup.pos.util.Lang.get(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(com.plusgroup.pos.util.Lang.tr("Lang / Langue"))
            .setSingleChoiceItems(names, cur) { d, which ->
                d.dismiss()
                com.plusgroup.pos.util.Lang.set(this, codes[which])
            }
            .setNegativeButton(com.plusgroup.pos.util.Lang.tr("Fèmen"), null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(com.plusgroup.pos.util.Lang.tr("Dekonekte"))
            .setMessage(com.plusgroup.pos.util.Lang.tr("Ou vle dekonekte sou aparèy sa a?"))
            .setPositiveButton(com.plusgroup.pos.util.Lang.tr("Wi, dekonekte")) { _, _ ->
                SessionManager(this).clear()
                val loginIntent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(loginIntent)
                finish()
            }
            .setNegativeButton(com.plusgroup.pos.util.Lang.tr("Non"), null)
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

        com.plusgroup.pos.util.Lang.apply(body.root)
        MaterialAlertDialogBuilder(this)
            .setTitle(com.plusgroup.pos.util.Lang.tr(title))
            .setView(body.root)
            .setPositiveButton(com.plusgroup.pos.util.Lang.tr("Fèmen"), null)
            .show()
    }
}
