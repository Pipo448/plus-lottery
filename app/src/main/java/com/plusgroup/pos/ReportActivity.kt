package com.plusgroup.pos

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityReportBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.util.TenantBranding
import com.plusgroup.pos.network.models.EliminatedLine
import com.plusgroup.pos.network.models.GagnantLine
import com.plusgroup.pos.network.models.TransactionLine
import com.plusgroup.pos.printer.PrinterManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Rapò" — PARTIEL, F.TIRAGE, F.GAGNANT, TRASACT, ak F.ELIMINER tout
 * fonksyonèl, chak youn ak filtè De/A + Tiraj pataje, epi yon bouton
 * print ki enprime rapò aktif la.
 */
class ReportActivity : BaseActivity() {

    private lateinit var binding: ActivityReportBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val moneyFormat = com.plusgroup.pos.util.Money.format()

    private var fromDate: Calendar = Calendar.getInstance()
    private var toDate: Calendar = Calendar.getInstance()

    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    private enum class ReportMode { PARTIEL, FIN_TIRAGE, GAGNANT, TRASACT, ELIMINE }
    private var currentMode = ReportMode.PARTIEL

    private var drawNames: List<String> = listOf("Tout")

    // Done pou dènye rapò chaje a — kenbe pou enpresyon (fòma
    // printReportReceipt: antèt + kò + pye paj opsyonèl).
    private var printReportTitle = "Rapport partiel"
    private var printHeaderLines: List<Pair<String, String>> = emptyList()
    private var printBodyFields: List<Pair<String, String>> = emptyList()
    private var printFooterFields: List<Pair<String, String>> = emptyList()

    private var cachedCompanyName: String? = null
    private var cachedVendeur: String? = null
    private var cachedPhone: String? = null
    private var cachedBranchCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPartiel.setOnClickListener { switchMode(ReportMode.PARTIEL) }
        binding.btnFTirage.setOnClickListener { switchMode(ReportMode.FIN_TIRAGE) }
        binding.btnFGagnant.setOnClickListener { switchMode(ReportMode.GAGNANT) }
        // Mon compte → "Ajan wè fich ki genyen"
        com.plusgroup.pos.util.TenantBranding.ensureCached(applicationContext)
        binding.btnFGagnant.visibility =
            if (com.plusgroup.pos.util.TenantRules.seeWinners) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnTrasact.setOnClickListener { switchMode(ReportMode.TRASACT) }
        binding.btnFEliminer.setOnClickListener { switchMode(ReportMode.ELIMINE) }

        binding.btnPrintReport.setOnClickListener { printCurrentReport() }

        binding.tvDateFrom.text = dateFormat.format(fromDate.time)
        binding.tvDateTo.text = dateFormat.format(toDate.time)
        binding.tvDateFrom.setOnClickListener {
            pickDate(fromDate) { binding.tvDateFrom.text = dateFormat.format(fromDate.time) }
        }
        binding.tvDateTo.setOnClickListener {
            pickDate(toDate) { binding.tvDateTo.text = dateFormat.format(toDate.time) }
        }

        binding.btnSearch.setOnClickListener { loadCurrentReport() }

        binding.tabBolet.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
        binding.tabParamet.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadDrawNamesForSpinner()
        switchMode(ReportMode.PARTIEL)
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

    private fun loadDrawNamesForSpinner() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                // TOUT tiraj yo (menm sa ki fèmen): ajan an ka wè rapò Texas apre l fèmen
                val res = api.getDrawNames()
                var names = (res.body()?.data ?: emptyList()).mapNotNull { it.name?.trim() }
                    .filter { it.isNotEmpty() }.distinct()
                if (names.isEmpty()) {
                    names = (api.getDraws(status = "").body()?.data ?: emptyList())
                        .mapNotNull { it.name }.distinct()
                }
                drawNames = listOf("Tout") + names
                binding.spinnerTirage.adapter = ArrayAdapter(
                    this@ReportActivity, android.R.layout.simple_spinner_dropdown_item, drawNames
                )
            } catch (_: Exception) {
                drawNames = listOf("Tout")
                binding.spinnerTirage.adapter = ArrayAdapter(
                    this@ReportActivity, android.R.layout.simple_spinner_dropdown_item, drawNames
                )
            }
        }
    }

    private fun selectedDrawNameOrNull(): String? {
        val pos = binding.spinnerTirage.selectedItemPosition
        if (pos <= 0) return null // "Tout" oswa pa gen seleksyon
        return drawNames.getOrNull(pos)
    }

    private fun switchMode(mode: ReportMode) {
        currentMode = mode
        loadCurrentReport()
    }

    private fun loadCurrentReport() {
        when (currentMode) {
            ReportMode.PARTIEL -> loadPartialReport()
            ReportMode.FIN_TIRAGE -> loadFinTirageReport()
            ReportMode.GAGNANT -> loadGagnantReport()
            ReportMode.TRASACT -> loadTransactionsReport()
            ReportMode.ELIMINE -> loadEliminatedReport()
        }
    }

    private fun showSummaryMode() {
        binding.llSummaryResult.visibility = View.VISIBLE
        binding.llListResult.visibility = View.GONE
    }

    private fun showListMode() {
        binding.llSummaryResult.visibility = View.GONE
        binding.llListResult.visibility = View.VISIBLE
    }

    private fun addSummaryRow(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(this).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        binding.llSummaryRows.addView(row)
    }

    // ==================== PARTIEL ====================

    private fun loadPartialReport() {
        showSummaryMode()
        binding.tvSummaryTitle.text = "Rapport partiel"
        binding.tvBalanceValue.visibility = View.GONE
        binding.tvBalanceLabel.visibility = View.GONE

        val date = apiDateFormat.format(fromDate.time)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getPartialReport(date)
                val report = res.body()?.data

                binding.llSummaryRows.removeAllViews()
                addSummaryRow("Tirage", report?.tirage ?: "—")
                addSummaryRow("Date", report?.date ?: "—")
                addSummaryRow("Fiche vendu", "${report?.ficheVendu ?: 0}")
                addSummaryRow("Vente", moneyFormat.format(report?.vente ?: 0.0))
                addSummaryRow("Commission", moneyFormat.format(report?.commission ?: 0.0))

                printReportTitle = "Rapport partiel"
                printHeaderLines = listOf(
                    "Date" to (report?.date ?: "—"),
                    "Vendeur" to (cachedVendeur ?: "—"),
                )
                printBodyFields = listOf(
                    "Tirage" to (report?.tirage ?: "—"),
                    "Fiche vendu" to "${report?.ficheVendu ?: 0}",
                    "Vente" to moneyFormat.format(report?.vente ?: 0.0),
                    "Commission" to moneyFormat.format(report?.commission ?: 0.0),
                )
                printFooterFields = emptyList()
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== F.TIRAGE ====================

    private fun loadFinTirageReport() {
        showSummaryMode()
        binding.tvSummaryTitle.text = "Rapport fin tirage"
        binding.tvBalanceValue.visibility = View.VISIBLE
        binding.tvBalanceLabel.visibility = View.VISIBLE

        val from = apiDateFormat.format(fromDate.time)
        val to = apiDateFormat.format(toDate.time)
        val drawName = selectedDrawNameOrNull()

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getFinTirageReport(from, to, drawName)
                val r = res.body()?.data

                // Balans = sa ajan an dwe remèt pwopriyetè a / sipèvizè a
                // (vant − komisyon − gany + depo − retrè)
                val bal = r?.balance ?: 0.0
                binding.tvBalanceValue.text = moneyFormat.format(bal)
                binding.tvBalanceValue.setTextColor(
                    Color.parseColor(if (bal >= 0) "#071A4A" else "#D80F22")
                )
                binding.tvBalanceLabel.text = if (bal >= 0) "BALANS POU REMÈT" else "BANK LAN DWE AJAN AN"

                binding.llSummaryRows.removeAllViews()
                addSummaryRow("Tirage", r?.tirage ?: "tout")
                addSummaryRow("Date", "${r?.dateFrom ?: "—"} / ${r?.dateTo ?: "—"}")
                addSummaryRow("Date impression", formatDateTime(r?.datePrinted))
                addSummaryRow("Vendeur", r?.vendeur ?: "—")
                addSummaryRow("Succursal", r?.succursal ?: "—")
                addSummaryRow("Fiche vendu", "${r?.ficheVendu ?: 0}")
                addSummaryRow("Fiche gagnant", "${r?.ficheGagnant ?: 0}")
                addSummaryRow("Vente", moneyFormat.format(r?.vente ?: 0.0))
                addSummaryRow("Commission", moneyFormat.format(r?.commission ?: 0.0))
                addSummaryRow("Paiement (gany)", moneyFormat.format(r?.aPaye ?: 0.0))
                if (r?.pokoPeye != null && r.pokoPeye > 0) addSummaryRow("  • Poko peye", moneyFormat.format(r.pokoPeye))
                addSummaryRow("Profit / Perte", moneyFormat.format(r?.profitPerte ?: 0.0))
                addSummaryRow("Depot", formatMaybeNa(r?.depot))
                addSummaryRow("Retrait", formatMaybeNa(r?.retrait))
                addSummaryRow("Balance (pou remèt)", moneyFormat.format(r?.balance ?: 0.0))

                printReportTitle = "Rapport de fin tirage"
                printHeaderLines = listOf(
                    "Date 1" to (r?.dateFrom ?: "—"),
                    "Date 2" to (r?.dateTo ?: "—"),
                    "D. Impression" to formatDateTime(r?.datePrinted),
                    "Vendeur" to (r?.vendeur ?: "—"),
                    "Succursal" to (r?.succursal ?: "—"),
                )
                printBodyFields = listOf(
                    "Tirage" to (r?.tirage ?: "tout"),
                    "#Fiche vendues" to "${r?.ficheVendu ?: 0}",
                    "#Fiche gagnantes" to "${r?.ficheGagnant ?: 0}",
                    "Ventes" to moneyFormat.format(r?.vente ?: 0.0),
                    "Commission" to moneyFormat.format(r?.commission ?: 0.0),
                    "A paye" to moneyFormat.format(r?.aPaye ?: 0.0),
                    "Profit/Perte" to moneyFormat.format(r?.profitPerte ?: 0.0),
                )
                printFooterFields = listOf(
                    "Depot" to formatMaybeNa(r?.depot),
                    "Retrait" to formatMaybeNa(r?.retrait),
                    "Balance" to moneyFormat.format(r?.balance ?: 0.0),
                )
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun formatMaybeNa(value: Any?): String {
        if (value == null || value == "n/a") return "n/a"
        val d = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
        return if (d != null) moneyFormat.format(d) else "n/a"
    }

    // ==================== F.GAGNANT ====================

    private fun loadGagnantReport() {
        showListMode()
        binding.tvListTitle.text = "Fiche gagnant"

        val from = apiDateFormat.format(fromDate.time)
        val to = apiDateFormat.format(toDate.time)
        val drawName = selectedDrawNameOrNull()

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getGagnantReport(from, to, drawName)
                val report = res.body()?.data
                val lines = report?.lines ?: emptyList()

                val poko = report?.pokoPeye ?: 0.0
                binding.tvListSummary.text =
                    "${report?.ficheGagnant ?: lines.size} fich genyen — Total: ${moneyFormat.format(report?.totalPrize ?: 0.0)} HTG" +
                        if (poko > 0) "\nPoko peye: ${moneyFormat.format(poko)} HTG · klike sou yon fich pou peye l" else ""

                renderGagnantLines(lines)

                printReportTitle = "Rapport fiche gagnant"
                printHeaderLines = listOf(
                    "Date" to "$from / $to",
                    "Agent" to (cachedVendeur ?: "—"),
                )
                printBodyFields = lines.map {
                    (it.ficheNumber ?: it.ticketNumber ?: "—") to moneyFormat.format(it.prizeAmount ?: 0.0)
                } + listOf("Total" to moneyFormat.format(report?.totalPrize ?: 0.0))
                printFooterFields = emptyList()
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderGagnantLines(lines: List<GagnantLine>) {
        binding.llListContainer.removeAllViews()
        if (lines.isEmpty()) {
            binding.llListContainer.addView(emptyStateView("Pa gen fich genyen pou peryòd sa a."))
            return
        }
        // Yon kat pou chak fich; klike → detay (boul ki genyen) + PEYE
        for (line in lines) {
            val c = com.plusgroup.pos.databinding.ItemFicheCardBinding.inflate(layoutInflater, binding.llListContainer, false)
            val status = if (line.paid == true) "paid" else "winner"
            val ficheNo = line.ficheNumber ?: line.ticketNumber
            com.plusgroup.pos.ui.FicheStatus.bindCard(
                c,
                status = status,
                drawName = line.drawName,
                meta = listOfNotNull(
                    ficheNo?.let { "#" + com.plusgroup.pos.ui.FicheDetailSheet.formatFicheNo(it) },
                    com.plusgroup.pos.ui.FicheDetailSheet.prettyDateTime(line.soldAt),
                ).joinToString("  ·  "),
                balls = line.numero?.let { "Boul ki genyen: $it" },
                mise = moneyFormat.format(line.vente ?: 0.0),
                gain = "+" + moneyFormat.format(line.prizeAmount ?: 0.0),
            )
            if (ficheNo != null) {
                c.root.setOnClickListener {
                    com.plusgroup.pos.ui.FicheDetailSheet.show(
                        this, ficheNo,
                        com.plusgroup.pos.ui.FicheDetailSheet.Actions(onChanged = { loadGagnantReport() }),
                    )
                }
            }
            binding.llListContainer.addView(c.root)
        }
    }

    // ==================== TRASACT ====================

    private fun loadTransactionsReport() {
        showListMode()
        binding.tvListTitle.text = "Transactions"

        val from = apiDateFormat.format(fromDate.time)
        val to = apiDateFormat.format(toDate.time)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getTransactionsReport(from, to)
                val lines = res.body()?.data ?: emptyList()

                val total = lines.sumOf { it.montant ?: 0.0 }
                binding.tvListSummary.text = "${lines.size} transaksyon — Total: ${moneyFormat.format(total)} HTG"

                renderTransactionLines(lines)

                printReportTitle = "Rapport transactions"
                printHeaderLines = listOf("Date" to "$from / $to")
                printBodyFields = lines.map {
                    "${it.type ?: "—"} (${formatDateTime(it.date)})" to moneyFormat.format(it.montant ?: 0.0)
                } + listOf("Total" to moneyFormat.format(total))
                printFooterFields = emptyList()
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderTransactionLines(lines: List<TransactionLine>) {
        binding.llListContainer.removeAllViews()
        if (lines.isEmpty()) {
            binding.llListContainer.addView(emptyStateView("Pa gen transaksyon pou peryòd sa a."))
            return
        }
        for (line in lines) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 12, 16, 12)
            }
            row.addView(TextView(this).apply {
                text = formatDateTime(line.date)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = line.type ?: "—"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = moneyFormat.format(line.montant ?: 0.0)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            binding.llListContainer.addView(row)
            binding.llListContainer.addView(divider())
        }
    }

    // ==================== F.ELIMINER ====================

    private fun loadEliminatedReport() {
        showListMode()
        binding.tvListTitle.text = "Fich Elimine"

        // F.ELIMINER backend la itilize yon sèl "date" — nou itilize "De"
        // a kòm dat rapò a pou kounye a.
        val date = apiDateFormat.format(fromDate.time)
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.getEliminatedReport(date)
                val report = res.body()?.data
                val lines = report?.lines ?: emptyList()

                binding.tvListSummary.text =
                    "${report?.ficheElimine ?: lines.size} fich elimine — Total: ${moneyFormat.format(report?.totalVente ?: 0.0)} HTG"

                renderEliminatedLines(lines)

                printReportTitle = "Rapport fich elimine"
                printHeaderLines = listOf(
                    "Date" to date,
                    "Vendeur" to (cachedVendeur ?: "—"),
                )
                printBodyFields = lines.map {
                    (it.ficheNumber ?: it.ticketNumber ?: "—") to moneyFormat.format(it.vente ?: 0.0)
                } + listOf("Total" to moneyFormat.format(report?.totalVente ?: 0.0))
                printFooterFields = emptyList()
            } catch (e: Exception) {
                Toast.makeText(this@ReportActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderEliminatedLines(lines: List<EliminatedLine>) {
        binding.llListContainer.removeAllViews()
        if (lines.isEmpty()) {
            binding.llListContainer.addView(emptyStateView("Pa gen fich elimine pou dat sa a."))
            return
        }
        for (line in lines) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            card.addView(TextView(this).apply {
                text = "${line.drawName ?: "—"}  •  #${line.ficheNumber ?: line.ticketNumber ?: "—"}"
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
            })
            card.addView(TextView(this).apply {
                text = "Vente: ${moneyFormat.format(line.vente ?: 0.0)}  /  Rezon: ${line.cancelReason ?: "—"}"
                textSize = 12f
                setTextColor(Color.DKGRAY)
            })
            card.addView(TextView(this).apply {
                text = formatDateTime(line.cancelledAt)
                textSize = 11f
                setTextColor(Color.GRAY)
            })
            binding.llListContainer.addView(card)
            binding.llListContainer.addView(divider())
        }
    }

    // ==================== YOUTIL AFICHAJ ====================

    private fun emptyStateView(message: String): TextView = TextView(this).apply {
        text = message
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, 24, 0, 24)
        setTextColor(Color.DKGRAY)
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
        setBackgroundColor(Color.parseColor("#E0E0E0"))
    }

    private fun formatDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(raw.take(19))
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date!!)
        } catch (_: Exception) {
            raw
        }
    }

    // ==================== ENPRIME ====================
    // Itilize `printerManager.printReportReceipt()` — fòma DVN Lotto:
    // antèt konpayi santre, liy kontèks (Date/Vendeur...), yon liy
    // "----", chan "Etikèt: Valè", epi yon dezyèm seksyon opsyonèl
    // (Depot/Retrait/Balance pou F.TIRAGE).

    private fun printCurrentReport() {
        if (printBodyFields.isEmpty()) {
            Toast.makeText(this, com.plusgroup.pos.util.Lang.tr("Pa gen anyen pou enprime"), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            ensureCompanyInfoCached()
            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(this@ReportActivity, com.plusgroup.pos.util.Lang.tr("Enprimant pa konekte."), Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    // Ajan an enprime anlè (menm jan ak fich yo): pa repete l anba
                    val headers = printHeaderLines.filterNot { (k, _) ->
                        k.equals("Vendeur", true) || k.equals("Agent", true) || k.equals("Ajan", true)
                    }
                    printerManager.printReportReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        branchCode = cachedBranchCode ?: "",
                        phone = cachedPhone ?: "",
                        reportTitle = printReportTitle,
                        headerLines = listOfNotNull(
                            TenantBranding.branch?.takeIf { it.isNotBlank() }?.let { "Sikisal" to it },
                        ) + headers,
                        bodyFields = printBodyFields,
                        footerFields = printFooterFields,
                        agent = cachedVendeur ?: "",
                    )
                    Toast.makeText(this@ReportActivity, com.plusgroup.pos.util.Lang.tr("Rapò voye bay enprimant lan"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun ensureCompanyInfoCached() {
        // Non, logo, telefòn, adrès tenant lan (Mon compte → Paramètres) +
        // non ajan an — menm sous ak fich yo. Sa ki sere a sèvi touswit.
        TenantBranding.ensureCached(applicationContext)
        if (TenantBranding.companyName == null) TenantBranding.load(applicationContext)
        else lifecycleScope.launch { TenantBranding.load(applicationContext) }
        cachedCompanyName = TenantBranding.companyName?.uppercase() ?: cachedCompanyName
        cachedVendeur = TenantBranding.vendeur ?: cachedVendeur
        cachedPhone = TenantBranding.phone
        cachedBranchCode = TenantBranding.address
        if (cachedCompanyName != null) return
        try {
            val profile = ApiClient.getService(applicationContext).getProfile().body()?.data
            cachedCompanyName = profile?.tenantName?.uppercase()
            cachedVendeur = profile?.username ?: profile?.fullName
        } catch (_: Exception) {
            // Kontinye ak valè default si sa echwe.
        }
    }

}
