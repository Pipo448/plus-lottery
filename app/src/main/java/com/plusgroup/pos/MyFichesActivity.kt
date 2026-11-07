package com.plusgroup.pos

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.plusgroup.pos.databinding.ActivityMyFichesBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.util.TenantBranding
import com.plusgroup.pos.network.models.FicheSummary
import com.plusgroup.pos.printer.PrinterManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * "Fich Mwen Yo" — istwa fich ajan an, ak filtè Debut/Fin.
 *
 * Chak KAT reprezante YON FICH KONPLÈ (pa yon sèl boul) — jan yon vrè
 * fich bolèt ta dwe parèt (10 boul nan menm fich la = 1 kat, pa 10 kat).
 * Klike sou yon kat louvri dyalòg detay ak 3 aksyon: REJOUER, IMPRIMER,
 * ELIMINÉ (jan modèl DVN Lotto a fè l).
 */
class MyFichesActivity : BaseActivity() {

    private lateinit var binding: ActivityMyFichesBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val moneyFormat = DecimalFormat("#,##0.00")

    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()

    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }

    private var cachedCompanyName: String? = null
    private var cachedVendeur: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyFichesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        printerManager.connect() // pare pou re-enprime touswit

        binding.btnBack.setOnClickListener { finish() }

        binding.tvDebut.text = dateFormat.format(startDate.time)
        binding.tvFin.text = dateFormat.format(endDate.time)

        binding.tvDebut.setOnClickListener { pickDate(startDate) { binding.tvDebut.text = dateFormat.format(startDate.time) } }
        binding.tvFin.setOnClickListener { pickDate(endDate) { binding.tvFin.text = dateFormat.format(endDate.time) } }

        binding.btnSearch.setOnClickListener { loadFiches() }
        binding.swipeRefresh.setOnRefreshListener { loadFiches() }

        loadFiches()
    }

    override fun onResume() {
        super.onResume()
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
                // Fich ki ANILE pa dwe parèt ditou nan "Mes fiches" — sèl
                // kote yo dwe vizib se panèl admin (sekte "Elimine").
                val fiches = (res.body()?.data ?: emptyList()).filter { it.status != "cancelled" }
                renderFiches(fiches)
            } catch (e: Exception) {
                Toast.makeText(this@MyFichesActivity, "Erè: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    // ==================== AFICHAJ: yon kat pou chak fich ====================
    // Anlè: yon ti rezime (kantite fich, total mise, total gain). Anba: yon
    // kat pou chak fich ak koulè estati li. Klike → fèy detay la ak bouton
    // REJWE / ENPRIME / ELIMINE / PEYE.

    private fun renderFiches(fiches: List<FicheSummary>) {
        val box = binding.llResultContainer
        box.removeAllViews()

        val totalMise = fiches.sumOf { it.totalMise ?: 0.0 }
        val totalGain = fiches.filter { it.status == "winner" || it.status == "paid" }.sumOf { it.totalGain ?: 0.0 }
        val toPay = fiches.count { it.status == "winner" }
        box.addView(summaryCard(fiches.size, totalMise, totalGain, toPay))

        if (fiches.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "Pa gen fich pou peryòd sa a."
                gravity = android.view.Gravity.CENTER
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MyFichesActivity, R.color.pos_muted))
                setPadding(0, 48, 0, 48)
            })
            return
        }

        for (fiche in fiches) {
            val c = com.plusgroup.pos.databinding.ItemFicheCardBinding.inflate(layoutInflater, box, false)
            val ficheNo = fiche.ficheNumber ?: fiche.ticketNumber
            val nLines = fiche.lines?.size ?: 0
            com.plusgroup.pos.ui.FicheStatus.bindCard(
                c,
                status = fiche.status,
                drawName = fiche.drawName,
                meta = listOfNotNull(
                    ficheNo?.let { "#" + com.plusgroup.pos.ui.FicheDetailSheet.formatFicheNo(it) },
                    com.plusgroup.pos.ui.FicheDetailSheet.prettyDateTime(fiche.soldAt),
                ).joinToString("  ·  "),
                balls = fiche.lines.orEmpty().mapNotNull { it.numero }.take(6).joinToString("  ")
                    .let { if (nLines > 6) "$it  +${nLines - 6}" else it },
                mise = moneyFormat.format(fiche.totalMise ?: 0.0),
                gain = if ((fiche.totalGain ?: 0.0) > 0) "+" + moneyFormat.format(fiche.totalGain) else null,
            )
            c.root.setOnClickListener { openFiche(fiche) }
            box.addView(c.root)
        }
    }

    private fun summaryCard(count: Int, mise: Double, gain: Double, toPay: Int): android.view.View {
        val d = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_results_header)
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (12 * d).toInt() }
            elevation = 3 * d
        }
        fun stat(value: String, label: String, color: Int = android.graphics.Color.WHITE) =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MyFichesActivity).apply {
                    text = value
                    textSize = 18f
                    typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
                    setTextColor(color)
                })
                addView(TextView(this@MyFichesActivity).apply {
                    text = label
                    textSize = 11f
                    letterSpacing = 0.06f
                    setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))
                })
            }
        card.addView(stat("$count", "FICH"))
        card.addView(stat(moneyFormat.format(mise), "MISE"))
        card.addView(stat(moneyFormat.format(gain), if (toPay > 0) "GAIN · $toPay POU PEYE" else "GAIN",
            android.graphics.Color.parseColor("#86EFAC")))
        return card
    }

    private fun openFiche(fiche: FicheSummary) {
        val ficheNo = fiche.ficheNumber ?: fiche.ticketNumber ?: return
        com.plusgroup.pos.ui.FicheDetailSheet.show(
            this,
            ficheNo,
            com.plusgroup.pos.ui.FicheDetailSheet.Actions(
                onReplay = { replayFiche(fiche) },
                onPrint = { printFicheSummary(fiche) },
                onChanged = { loadFiches() },
            ),
        )
    }

    private fun formatDateTime(soldAt: String?): String {
        if (soldAt.isNullOrBlank()) return "—"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(soldAt.take(19))
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date!!)
        } catch (_: Exception) {
            soldAt
        }
    }

    private fun categoryLabelFor(numero: String): String {
        return when {
            numero.contains("*") -> "MARIAGE"
            numero.length == 2 -> "BORLETTE"
            numero.length == 3 -> "LOTO3"
            numero.length == 4 -> "LOTO4"
            numero.length == 5 -> "LOTO5"
            else -> "BORLETTE"
        }
    }

    // ==================== IMPRIME (reenprime menm fich la egzakteman) ====================

    private fun printFicheSummary(fiche: FicheSummary) {
        fun codeOf(l: com.plusgroup.pos.network.models.FicheLineSummary): String =
            if (com.plusgroup.pos.util.FreeMariage.isFree(l.gameType)) com.plusgroup.pos.util.FreeMariage.CODE
            else shortCodeFor(categoryLabelFor(l.numero ?: ""))
        fun amountOf(l: com.plusgroup.pos.network.models.FicheLineSummary): String =
            com.plusgroup.pos.util.FreeMariage.amountText(l.gameType, l.betAmount, moneyFormat)

        val printLines = fiche.lines?.map { line ->
            Triple(codeOf(line), line.numero ?: "—", amountOf(line))
        } ?: emptyList()

        // Fich plizyè tiraj: yon seksyon pou chak tiraj (menm jan ak premye resi a)
        val byDraw = fiche.lines.orEmpty().groupBy { it.drawName ?: fiche.drawName ?: "—" }
        val sections = if (byDraw.size > 1) byDraw.map { (name, ls) ->
            com.plusgroup.pos.printer.ReceiptSection(
                drawName = name,
                drawTotal = moneyFormat.format(ls.sumOf { it.betAmount ?: 0.0 }),
                lines = ls.map { l ->
                    Triple(codeOf(l), l.numero ?: "—", amountOf(l))
                },
            )
        } else null

        lifecycleScope.launch {
            ensureCompanyInfoCached()

            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(this@MyFichesActivity, com.plusgroup.pos.util.Lang.tr("Enprimant pa konekte."), Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = cachedCompanyName ?: "PLUS GROUP",
                        promoLine = TenantBranding.address.orEmpty(),
                        phone = TenantBranding.phone.orEmpty(),
                        vendeur = cachedVendeur ?: "—",
                        dateTimeText = formatDateTime(fiche.soldAt),
                        ficheNumber = fiche.ficheNumber ?: fiche.ticketNumber ?: "—",
                        drawName = fiche.drawName ?: "—",
                        drawTotal = moneyFormat.format(fiche.totalMise ?: 0.0),
                        lines = printLines,
                        grandTotal = moneyFormat.format(fiche.totalMise ?: 0.0),
                        footerMessage = "Kopi fich",
                        qrData = fiche.ficheNumber ?: fiche.ticketNumber,
                        sections = sections,
                    )
                    Toast.makeText(this@MyFichesActivity, com.plusgroup.pos.util.Lang.tr("Fich voye bay enprimant lan"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shortCodeFor(category: String): String = when (category) {
        "BORLETTE" -> "BO"
        "LOTO3" -> "LT"
        "LOTO4" -> "L4"
        "LOTO5" -> "L5"
        "MARIAGE" -> "MA"
        else -> category.take(2)
    }

    private suspend fun ensureCompanyInfoCached() {
        // Non/logo/telefòn tenant lan (Mon compte → Paramètres): sa ki sere a
        // pou enprime touswit; rafrechi an awyè plan
        TenantBranding.ensureCached(applicationContext)
        lifecycleScope.launch { TenantBranding.load(applicationContext) }
        TenantBranding.companyName?.let { cachedCompanyName = it.uppercase() }
        TenantBranding.vendeur?.let { cachedVendeur = it }
        if (cachedCompanyName != null) return
        try {
            val api = ApiClient.getService(applicationContext)
            val profile = api.getProfile().body()?.data
            cachedCompanyName = profile?.tenantName?.uppercase()
            cachedVendeur = profile?.branchName ?: profile?.fullName
        } catch (_: Exception) {
            // Kontinye ak valè default si sa echwe.
        }
    }

    // ==================== REJWE (chaje boul yo editab nan Nouvèl Fich) ====================

    private fun replayFiche(fiche: FicheSummary) {
        val prefillLines = fiche.lines?.map {
            mapOf("numero" to (it.numero ?: ""), "price" to (it.betAmount ?: 0.0))
        } ?: emptyList()

        val intent = Intent(this, NewFicheActivity::class.java).apply {
            putExtra("PREFILL_DRAW_NAME", fiche.drawName)
            putExtra("PREFILL_LINES_JSON", Gson().toJson(prefillLines))
        }
        startActivity(intent)
    }
}
