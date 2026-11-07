package com.plusgroup.pos

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plusgroup.pos.databinding.ActivityNewFicheBinding
import com.plusgroup.pos.databinding.ItemFicheLineBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.util.TenantBranding
import com.plusgroup.pos.util.DataCache
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.SellFicheLine
import com.plusgroup.pos.network.models.SellFicheRequest
import com.plusgroup.pos.network.models.SellFicheResult
import com.plusgroup.pos.util.ApiErrors
import com.plusgroup.pos.printer.PrinterManager
import com.plusgroup.pos.util.DeviceIdHelper
import com.plusgroup.pos.util.AutoCombos
import com.plusgroup.pos.util.FicheShare
import com.plusgroup.pos.util.TenantRules
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Ekran "Nouvèl Fich" — vann tikè.
 *
 * Chak "liy" se yon (tiraj + nimewo + pri). Bouton rapid yo (BPaire, Grap,
 * P0-P9) jenere otomatikman 10 liy ak MENM pri a chak fwa, olye ajan an
 * antre chak nimewo yon pa yon.
 *
 * Chak liy ka EFASE (kòbèy) oswa MODIFYE pri li (kreyon) apa, san l pa
 * afekte lòt liy yo.
 *
 * Yon SÈL bouton (ikòn enprime anlè a) fè tout bagay: voye TOUT fich la
 * bay backend la nan YON SÈL demann (POST agent/fiches). Backend lan vann
 * tout liy yo ansanm oswa ayen (tiraj fèmen, limit, boul bloke…), epi
 * si l pase, resi a enprime otomatikman.
 *
 * REQUEST ID: si rezo a koupe pandan vant lan, ajan an ka peze enprime
 * ankò san danje — nou re-voye MENM requestId la, backend lan rekonèt li
 * epi li pa vann fich la de fwa.
 *
 * FICHE_NUMBER: tout liy yon menm fich jenere e voye AK MENM `ficheNumber`
 * la, pou backend lan ka gwoupe yo ansanm nan "Mes fiches" (olye chak liy
 * parèt kòm yon fich apa).
 *
 * PREFILL (REJWE): si ekran sa a louvri ak "PREFILL_LINES_JSON" nan
 * Intent la (soti nan MyFichesActivity, bouton "Rejwe"), nou chaje boul
 * yo tou prepli, editab, epi ajan an ka ajoute/retire/chanje tiraj anvan
 * l soumèt kòm yon NOUVO vant (nouvo fiche_number, pa yon modifikasyon
 * ansyen fich la).
 */
class NewFicheActivity : BaseActivity() {

    private lateinit var binding: ActivityNewFicheBinding

    private var openDraws: List<Draw> = emptyList()
    // Ajan an ka chwazi PLIZYÈ tiraj: menm boul yo vann sou chak tiraj,
    // men se YON SÈL fich (yon nimewo, yon QR, yon resi) → total × tiraj.
    private val selectedDraws = mutableListOf<Draw>()
    private val selectedDraw: Draw? get() = selectedDraws.firstOrNull()

    private var activeGames: List<LotteryGame> = emptyList()
    private var selectedGame: LotteryGame? = null

    private data class FicheLine(
        var drawName: String,
        val numero: String,
        var price: Double,
        val category: String,
        val optionLabel: String? = null,
    )

    private data class PrefillLine(val numero: String, val price: Double)

    private val lines = mutableListOf<FicheLine>()

    // Dènye demann ki pa t konfime (rezo koupe). Si ajan an re-eseye ak
    // EGZAKTEMAN menm fich la, nou re-voye menm ID yo → pa gen doub vant.
    private var pendingRequestId: String? = null
    private var pendingFicheNumber: String? = null
    private var pendingSignature: String? = null

    // Dènye fich ki VANN (pou bouton Pataje a: WhatsApp, SMS…)
    private var lastSold: SellFicheResult? = null
    private var lastSoldAt: String? = null

    private val moneyFormat = DecimalFormat("#,##0.00")
    private val printerManager: PrinterManager by lazy { PrinterManager(applicationContext) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewFicheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Prepare enprimant lan ak enfòmasyon fich la DEPI ekran an louvri,
        // konsa lè ajan an peze ENPRIME, resi a soti touswit apre vant lan.
        printerManager.connect()
        TenantBranding.ensureCached(applicationContext)
        lifecycleScope.launch { TenantBranding.load(applicationContext) }

        binding.btnClearAll.setOnClickListener {
            lines.clear()
            refreshLinesList()
            refreshTotal()
        }
        binding.btnShare.setOnClickListener { shareLastFiche() }
        binding.btnPrint.setOnClickListener { submitFiche() }
        binding.btnEditGameLabel.setOnClickListener { showGamePicker() }

        binding.tvChwaziTiraj.setOnClickListener { showDrawPicker() }

        binding.btnRevers.setOnClickListener { doRevers() }
        binding.btnMAuto.setOnClickListener { showMAutoDialog() }
        binding.btnL4Auto.setOnClickListener { showL4AutoDialog() }
        binding.btnBPaire.setOnClickListener { addQuickCategory(QuickCategory.B_PAIRE) }
        binding.btnGrap.setOnClickListener { addQuickCategory(QuickCategory.GRAP) }
        binding.btnP0.setOnClickListener { addQuickCategory(QuickCategory.pick(0)) }
        binding.btnP1.setOnClickListener { addQuickCategory(QuickCategory.pick(1)) }
        binding.btnP2.setOnClickListener { addQuickCategory(QuickCategory.pick(2)) }
        binding.btnP3.setOnClickListener { addQuickCategory(QuickCategory.pick(3)) }
        binding.btnP4.setOnClickListener { addQuickCategory(QuickCategory.pick(4)) }
        binding.btnP5.setOnClickListener { addQuickCategory(QuickCategory.pick(5)) }
        binding.btnP6.setOnClickListener { addQuickCategory(QuickCategory.pick(6)) }
        binding.btnP7.setOnClickListener { addQuickCategory(QuickCategory.pick(7)) }
        binding.btnP8.setOnClickListener { addQuickCategory(QuickCategory.pick(8)) }
        binding.btnP9.setOnClickListener { addQuickCategory(QuickCategory.pick(9)) }
        binding.btnMariage.setOnClickListener { addMariageLine() }
        binding.btnLoto4.setOnClickListener { showLoto4OptionsDialog() }
        binding.btnLoto5.setOnClickListener { showLoto5OptionsDialog() }

        // 4 / 5 chif: yon sèl klik → Maryaj oswa opsyon 1, 2, 3 (oswa tou 3)
        binding.btnChooseMariage.setOnClickListener { addMariageLine() }
        binding.btnOpt1.setOnClickListener { addLotoOptions(listOf(1)) }
        binding.btnOpt2.setOnClickListener { addLotoOptions(listOf(2)) }
        binding.btnOpt3.setOnClickListener { addLotoOptions(listOf(3)) }
        binding.btnOptAll.setOnClickListener { addLotoOptions(listOf(1, 2, 3)) }

        binding.etBoulLa.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateQuickChooser(s?.toString().orEmpty().trim())
            }
        })

        binding.btnAntre.setOnClickListener { addManualLine() }
        // Klavye a: "Fini" sou chan pri a = ANTRE
        binding.etMontant.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addManualLine()
                true
            } else false
        }

        loadDrawsAndGames()
        refreshTotal()
    }

    // ==================== CHAJE TIRAJ AK JWÈT ====================

    private var prefillApplied = false

    private fun loadDrawsAndGames() {
        // 1) TOUSWIT: sa ki deja nan memwa (akèy la prechaje yo)
        val cachedDraws = DataCache.openDraws()
        val cachedGames = DataCache.games()
        if (cachedDraws.isNotEmpty() && cachedGames.isNotEmpty()) {
            applyDrawsAndGames(cachedDraws, cachedGames)
        }

        // 2) An awyè plan: dènye vèsyon sèvè a
        lifecycleScope.launch {
            val ok = DataCache.refresh(applicationContext)
            if (ok) {
                applyDrawsAndGames(DataCache.openDraws(), DataCache.games())
            } else if (openDraws.isEmpty()) {
                Toast.makeText(
                    this@NewFicheActivity,
                    "Pa t ka chaje tiraj yo. Verifye entènèt la.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun applyDrawsAndGames(draws: List<Draw>, games: List<LotteryGame>) {
        openDraws = draws
        if (games.isNotEmpty()) {
            activeGames = games
            if (selectedGame == null || activeGames.none { it.id == selectedGame?.id }) {
                selectedGame = activeGames.firstOrNull()
            }
            binding.tvFicheLabel.text = (selectedGame?.name ?: "BORLETTE").uppercase()
        }
        // Tiraj ajan an te chwazi a fèmen pandan l t ap ekri fich la
        if (selectedDraws.removeAll { d -> openDraws.none { it.id == d.id } }) {
            onDrawsChanged()
        }
        if (!prefillApplied) {
            prefillApplied = true
            applyPrefillIfAny()
        }
    }

    // ==================== PREFILL (soti nan "Rejwe" nan Mes fiches) ====================

    private fun applyPrefillIfAny() {
        val prefillDrawName = intent.getStringExtra("PREFILL_DRAW_NAME")
        val prefillLinesJson = intent.getStringExtra("PREFILL_LINES_JSON")

        if (prefillDrawName != null) {
            val match = openDraws.firstOrNull { it.name == prefillDrawName }
            if (match != null) {
                selectedDraws.clear()
                selectedDraws.add(match)
                onDrawsChanged()
            } else {
                Toast.makeText(
                    this,
                    "Tiraj '$prefillDrawName' pa ouvè kounye a — chwazi yon lòt tiraj",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        if (!prefillLinesJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawLines: List<Map<String, Any>> = Gson().fromJson(prefillLinesJson, type)
                val drawName = if (selectedDraws.isNotEmpty()) drawsLabel() else prefillDrawName ?: "—"

                rawLines.forEach { raw ->
                    val numero = (raw["numero"] as? String)?.trim() ?: return@forEach
                    val price = (raw["price"] as? Double) ?: 0.0
                    if (numero.isEmpty() || price <= 0) return@forEach
                    addOrMerge(FicheLine(drawName, numero, price, categoryForNumero(numero)))
                }
                refreshLinesList()
                refreshTotal()

                if (rawLines.isNotEmpty()) {
                    Toast.makeText(this, "Fich chaje — ajoute/retire liy si w vle, epi enprime", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erè chaje ansyen fich la: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun categoryForNumero(numero: String): String = when {
        numero.contains("*") -> "MARIAGE"
        numero.length == 2 -> "BORLETTE"
        numero.length == 3 -> "LOTO3"
        numero.length == 4 -> "LOTO4"
        numero.length == 5 -> "LOTO5"
        else -> "BORLETTE"
    }

    private fun showDrawPicker() {
        if (openDraws.isEmpty()) {
            Toast.makeText(this, "Pa gen tiraj ouvè kounye a", Toast.LENGTH_SHORT).show()
            return
        }
        val names = openDraws.map { it.name ?: it.id }.toTypedArray()
        if (!TenantRules.doubleDraw) {
            // Mon compte → "Double tiraj" fèmen: yon sèl tiraj pa fich
            val current = openDraws.indexOfFirst { d -> selectedDraws.firstOrNull()?.id == d.id }
            AlertDialog.Builder(this)
                .setTitle("CHWAZI TIRAJ")
                .setSingleChoiceItems(names, current) { dlg, which ->
                    selectedDraws.clear()
                    selectedDraws.add(openDraws[which])
                    onDrawsChanged()
                    dlg.dismiss()
                }
                .setNegativeButton("Anile", null)
                .show()
            return
        }
        val checked = BooleanArray(openDraws.size) { i -> selectedDraws.any { it.id == openDraws[i].id } }
        AlertDialog.Builder(this)
            .setTitle("CHWAZI TIRAJ (youn oswa plizyè)")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("OK") { _, _ ->
                selectedDraws.clear()
                openDraws.forEachIndexed { i, d -> if (checked[i]) selectedDraws.add(d) }
                onDrawsChanged()
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    /**
     * Non tiraj yo jan yo parèt devan chak boul. Plizyè tiraj: youn anba
     * lòt ("Georgia-Soir\nFlorida Soir") pou tout non yo parèt nèt.
     */
    private fun drawsLabel(): String = when (selectedDraws.size) {
        0 -> "—"
        1 -> selectedDraws[0].name ?: "—"
        else -> selectedDraws.joinToString("\n") { it.name ?: "—" }
    }

    /**
     * Tiraj yo chanje (ajan an chwazi lòt, oswa youn fèmen): tout boul ki
     * deja sou fich la pran non NOUVO tiraj yo (egz. apre "Rejwe"), epi
     * total la rekalkile (× kantite tiraj).
     */
    private fun onDrawsChanged() {
        binding.tvChwaziTiraj.text = when (selectedDraws.size) {
            0 -> "CHWAZI TIRAJ"
            1 -> selectedDraws[0].name ?: "CHWAZI TIRAJ"
            else -> "${selectedDraws.size} tiraj: " + selectedDraws.joinToString(", ") { it.name ?: "—" }
        }
        val label = drawsLabel()
        lines.forEach { it.drawName = label }
        refreshLinesList()
        refreshTotal()
    }

    private fun showGamePicker() {
        if (activeGames.isEmpty()) {
            Toast.makeText(this, "Pa gen kategori jwèt disponib", Toast.LENGTH_SHORT).show()
            return
        }
        val names = activeGames.map { it.name ?: it.id }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("CHWAZI KATEGORI")
            .setSingleChoiceItems(names, -1) { dialog, which ->
                selectedGame = activeGames[which]
                binding.tvFicheLabel.text = (selectedGame?.name ?: "BORLETTE").uppercase()
                dialog.dismiss()
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    // ==================== KATEGORI RAPID (BPaire / Grap / Pick N) ====================

    private sealed class QuickCategory {
        object BPaire : QuickCategory()
        object Grap : QuickCategory()
        data class Pick(val digit: Int) : QuickCategory()

        companion object {
            val B_PAIRE = BPaire
            val GRAP = Grap
            fun pick(digit: Int) = Pick(digit)
        }
    }

    private fun addQuickCategory(category: QuickCategory) {
        if (!requireDrawSelected()) return

        val numbers: List<String> = when (category) {
            is QuickCategory.BPaire -> (0..9).map { "$it$it" }
            is QuickCategory.Grap -> (0..9).map { "$it$it$it" }
            is QuickCategory.Pick -> (0..9).map { d -> "$d${category.digit}" }
        }
        // Grap = 000, 111… (3 chif) → Loto3; lòt yo → Borlette
        val cat = if (category is QuickCategory.Grap) "LOTO3" else "BORLETTE"
        if (!fits(cat, countNew(cat, numbers))) return
        promptForPrice { price ->
            val drawName = drawsLabel()
            numbers.forEach { num -> addOrMerge(FicheLine(drawName, num, price, cat)) }
            refreshLinesList()
            refreshTotal()
        }
    }

    private fun promptForPrice(onPrice: (Double) -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        AlertDialog.Builder(this)
            .setTitle("Pri pou chak nimewo")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val price = input.text.toString().toDoubleOrNull()
                if (price == null || price <= 0) {
                    Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
                } else {
                    onPrice(price)
                }
            }
            .setNegativeButton("Anile", null)
            .show()
    }

    // ==================== ANTRE MANYÈL (oto-deteksyon selon konbyen chif) ====================

    private fun addManualLine() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val montantText = binding.etMontant.text.toString().trim()

        if (numero.isEmpty()) {
            Toast.makeText(this, "Antre yon nimewo", Toast.LENGTH_SHORT).show()
            return
        }
        val montant = montantText.toDoubleOrNull()
        if (montant == null || montant <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val category = when (numero.length) {
            2 -> "BORLETTE"
            3 -> "LOTO3"
            4 -> {
                Toast.makeText(this, "4 chif: klike \"Maryaj\" oswa yon opsyon Loto4 (O1, O2, O3) anba a", Toast.LENGTH_LONG).show()
                return
            }
            5 -> {
                Toast.makeText(this, "5 chif: klike yon opsyon Loto5 (O1, O2, O3) anba a", Toast.LENGTH_LONG).show()
                return
            }
            else -> {
                Toast.makeText(this, "Nimewo dwe 2, 3, 4, oswa 5 chif", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (!fits(category, countNew(category, listOf(numero)))) return
        val drawName = drawsLabel()
        val before = findLine(drawName, numero, category, null)?.price
        addOrMerge(FicheLine(drawName, numero, montant, category))
        if (before != null) {
            Toast.makeText(this, "$numero: ${moneyFormat.format(before)} + ${moneyFormat.format(montant)} = ${moneyFormat.format(before + montant)}", Toast.LENGTH_SHORT).show()
        }
        binding.etBoulLa.text?.clear()
        binding.etBoulLa.requestFocus()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== CHWA RAPID 4 / 5 CHIF ====================

    /** 4 chif → [Maryaj] L4 [O1][O2][O3][1·2·3]  ·  5 chif → L5 [O1][O2][O3][1·2·3] */
    private fun updateQuickChooser(typed: String) {
        val digits = typed.all { it.isDigit() }
        when {
            digits && typed.length == 4 -> {
                binding.llMariageLoto4Chooser.visibility = View.VISIBLE
                binding.btnChooseMariage.visibility = View.VISIBLE
                binding.tvQuickPrefix.text = "L4"
            }
            digits && typed.length == 5 -> {
                binding.llMariageLoto4Chooser.visibility = View.VISIBLE
                binding.btnChooseMariage.visibility = View.GONE
                binding.tvQuickPrefix.text = "L5"
            }
            else -> binding.llMariageLoto4Chooser.visibility = View.GONE
        }
    }

    /** Ajoute boul 4 oswa 5 chif la ak opsyon yo chwazi a (menm pri a pou chak). */
    private fun addLotoOptions(options: List<Int>) {
        if (!requireDrawSelected()) return
        val numero = binding.etBoulLa.text.toString().trim()
        val category = when {
            numero.matches(Regex("^\\d{4}$")) -> "LOTO4"
            numero.matches(Regex("^\\d{5}$")) -> "LOTO5"
            else -> {
                Toast.makeText(this, "Tape 4 chif (Loto4) oswa 5 chif (Loto5)", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val price = currentPrice() ?: return
        if (!fits(category, options.count { o -> findLine(drawsLabel(), numero, category, "0$o") == null })) return
        val drawName = drawsLabel()
        options.forEach { o -> addOrMerge(FicheLine(drawName, numero, price, category, "0$o")) }
        binding.etBoulLa.text?.clear()
        binding.etBoulLa.requestFocus()
        refreshLinesList()
        refreshTotal()
    }

    /** Pri ki nan chan "Pri" a, oswa null (ak mesaj) si l pa bon. */
    private fun currentPrice(): Double? {
        val price = binding.etMontant.text.toString().trim().toDoubleOrNull()
        if (price == null || price <= 0) {
            Toast.makeText(this, "Antre pri a anvan", Toast.LENGTH_SHORT).show()
            binding.etMontant.requestFocus()
            return null
        }
        return price
    }

    // ==================== MENM BOUL = MENM LIY ====================

    /** Liy ki deja sou fich la pou menm tiraj, menm boul, menm jwèt, menm opsyon. */
    private fun findLine(drawName: String, numero: String, category: String, option: String?): FicheLine? =
        lines.firstOrNull {
            it.drawName == drawName && it.numero == numero &&
                it.category.equals(category, ignoreCase = true) &&
                (it.optionLabel ?: "") == (option ?: "")
        }

    /**
     * Si boul la deja sou fich la (menm tiraj / jwèt / opsyon), ajoute kòb la
     * sou MENM liy lan (25 + 25 = 50) olye pou l parèt de fwa. Sinon, nouvo liy.
     * Retounen true si se te yon fizyon.
     */
    private fun addOrMerge(line: FicheLine): Boolean {
        val existing = findLine(line.drawName, line.numero, line.category, line.optionLabel)
        if (existing != null) {
            existing.price = Math.round((existing.price + line.price) * 100) / 100.0
            return true
        }
        lines.add(line)
        return false
    }

    /** Konbyen nan boul sa yo ki ta fè NOUVO liy (lòt yo jis ogmante kòb yo). */
    private fun countNew(category: String, numeros: List<String>, option: String? = null): Int {
        val drawName = drawsLabel()
        return numeros.distinct().count { findLine(drawName, it, category, option) == null }
    }

    // ==================== RÈG MON COMPTE ====================

    /**
     * Kantite maksimòm pa fich (Mon compte → Kantite maksimòm). 0 = pa gen limit.
     * Retounen false (ak mesaj) si ajoute `adding` liy `category` ta depase.
     */
    private fun fits(category: String, adding: Int): Boolean {
        val max = TenantRules.maxPerFiche(category)
        if (max <= 0 || adding <= 0) return true
        val have = lines.count { it.category.equals(category, ignoreCase = true) }
        if (have + adding <= max) return true
        val left = (max - have).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Twòp ${TenantRules.categoryLabel(category)}")
            .setMessage(
                "Yon fich pa ka gen plis pase $max ${TenantRules.categoryLabel(category)}.\n" +
                    "Gen $have deja; ou ka ajoute $left ankò.\n\nFè yon lòt fich pou rès yo."
            )
            .setPositiveButton("OK", null)
            .show()
        return false
    }

    /** Montre / kache bouton yo dapre Mon compte. Rele l chak fwa ekran an parèt. */
    private fun applyRules() {
        fun vis(on: Boolean) = if (on) View.VISIBLE else View.GONE
        binding.btnRevers.visibility = vis(TenantRules.showRevers)
        binding.btnBPaire.visibility = vis(TenantRules.showPairGrap)
        binding.btnGrap.visibility = vis(TenantRules.showPairGrap)
        listOf(
            binding.btnP0, binding.btnP1, binding.btnP2, binding.btnP3, binding.btnP4,
            binding.btnP5, binding.btnP6, binding.btnP7, binding.btnP8, binding.btnP9,
        ).forEach { it.visibility = vis(TenantRules.showPwent) }
        binding.btnShare.visibility = vis(TenantRules.showShare)

        // Double tiraj fèmen: kenbe yon sèl tiraj
        if (!TenantRules.doubleDraw && selectedDraws.size > 1) {
            val first = selectedDraws.first()
            selectedDraws.clear()
            selectedDraws.add(first)
            onDrawsChanged()
            Toast.makeText(this, "Yon sèl tiraj pa fich (Double tiraj fèmen)", Toast.LENGTH_SHORT).show()
        }
        refreshLinesList() // kreyon pri + MAuto / L4Auto
    }

    override fun onResume() {
        super.onResume()
        TenantBranding.ensureCached(applicationContext)
        applyRules()
        // Paramèt yo ka chanje sou web la: rafrechi (chak 2 minit maks) epi reaplike
        lifecycleScope.launch {
            TenantBranding.load(applicationContext)
            applyRules()
        }
    }

    // ==================== REVÈ ====================

    /**
     * REVÈ: si "Boul la" gen 2 chif (35) → 35 ak 53 ak pri a.
     * Si chan an vid → revè tout boul borlette ki deja sou fich la, chak ak
     * MENM pri ak boul pa l la. Pa double, pa revè pou 00, 11… Apre sa ajan
     * an ka chanje pri chak liy ak kreyon an.
     */
    private fun doRevers() {
        if (!requireDrawSelected()) return
        val typed = binding.etBoulLa.text.toString().trim()
        val drawName = drawsLabel()

        if (typed.isNotEmpty()) {
            if (!typed.matches(Regex("^\\d{2}$"))) {
                Toast.makeText(this, "Revè: boul 2 chif sèlman (egz. 35 → 53)", Toast.LENGTH_SHORT).show()
                return
            }
            val price = currentPrice() ?: return
            val r = AutoCombos.rev(typed)
            if (!fits("BORLETTE", countNew("BORLETTE", listOf(typed, r).distinct()))) return
            addOrMerge(FicheLine(drawName, typed, price, "BORLETTE"))
            val added = if (r != typed) {
                addOrMerge(FicheLine(drawName, r, price, "BORLETTE")); true
            } else false
            binding.etBoulLa.text?.clear()
            binding.etBoulLa.requestFocus()
            refreshLinesList()
            refreshTotal()
            Toast.makeText(this, if (added) "$typed + $r" else "$typed (pa gen lòt revè)", Toast.LENGTH_SHORT).show()
            return
        }

        val borlette = lines.filter { it.category == "BORLETTE" }
        val toAdd = AutoCombos.reversesToAdd(borlette.map { it.numero })
        if (toAdd.isEmpty()) {
            Toast.makeText(this, "Pa gen revè pou ajoute (tape yon boul 2 chif, oswa ajoute boul borlette anvan)", Toast.LENGTH_LONG).show()
            return
        }
        if (!fits("BORLETTE", toAdd.size)) return
        toAdd.forEach { r ->
            val src = borlette.first { AutoCombos.rev(it.numero) == r }
            addOrMerge(FicheLine(src.drawName, r, src.price, "BORLETTE"))
        }
        refreshLinesList()
        refreshTotal()
        Toast.makeText(this, "${toAdd.size} revè ajoute: ${toAdd.joinToString(", ")}", Toast.LENGTH_SHORT).show()
    }

    // ==================== MAuto / L4Auto ====================

    /** Boul borlette (2 chif) diferan ki sou fich la. */
    private fun baseBorlette(): List<String> =
        AutoCombos.baseNumbers(lines.filter { it.category == "BORLETTE" }.map { it.numero })

    /** MAuto / L4Auto parèt sèlman lè gen 2 boul oswa plis. */
    private fun updateAutoRow() {
        val enough = baseBorlette().size >= 2
        binding.btnMAuto.visibility = if (TenantRules.showMAuto) View.VISIBLE else View.GONE
        binding.btnL4Auto.visibility = if (TenantRules.showL4Auto) View.VISIBLE else View.GONE
        binding.llAutoRow.visibility =
            if (enough && (TenantRules.showMAuto || TenantRules.showL4Auto)) View.VISIBLE else View.GONE
    }

    private fun defaultAutoPrice(): String =
        binding.etMontant.text.toString().trim().takeIf { (it.toDoubleOrNull() ?: 0.0) > 0 }
            ?: lines.lastOrNull { it.category == "BORLETTE" }?.price?.let { moneyFormat.format(it).replace(",", "") }
            ?: ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * Dyalòg komen MAuto / L4Auto: pri + chwa yo + apèsi (kantite, total,
     * premye nimewo yo). "Ajoute" aktif sèlman si gen omwen 1 liy.
     */
    private fun showAutoDialog(
        title: String,
        toggles: List<Pair<String, Boolean>>,
        withOptions: Boolean,
        defaultOptions: Set<Int> = setOf(1, 2, 3),
        generate: (List<Boolean>) -> List<String>,
        onAdd: (numbers: List<String>, options: List<Int>, price: Double) -> Unit,
    ) {
        if (!requireDrawSelected()) return
        val base = baseBorlette()
        if (base.size < 2) {
            Toast.makeText(this, "Ajoute omwen 2 boul borlette anvan", Toast.LENGTH_SHORT).show()
            return
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        box.addView(TextView(this).apply {
            text = "Boul yo: ${base.joinToString("  ")}"
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.pos_ink_soft))
        })
        val priceInput = android.widget.EditText(this).apply {
            hint = "Pri pou chak liy"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(defaultAutoPrice())
            textSize = 18f
        }
        box.addView(priceInput)
        val switches = toggles.map { (label, on) ->
            com.google.android.material.switchmaterial.SwitchMaterial(this).apply {
                text = label
                isChecked = on
                textSize = 15f
            }.also { box.addView(it) }
        }
        val optionBoxes = if (withOptions) {
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            val cbs = (1..3).map { o ->
                android.widget.CheckBox(this).apply {
                    text = "O$o"
                    isChecked = o in defaultOptions
                    textSize = 15f
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }.also { row.addView(it) }
            }
            box.addView(row)
            cbs
        } else emptyList()
        val preview = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, dp(4))
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.pos_navy))
        }
        box.addView(preview)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton("Ajoute", null)
            .setNegativeButton("Anile", null)
            .create()

        fun state(): Triple<List<String>, List<Int>, Double?> {
            val nums = generate(switches.map { it.isChecked })
            val opts = if (withOptions) optionBoxes.mapIndexedNotNull { i, cb -> if (cb.isChecked) i + 1 else null } else listOf(0)
            return Triple(nums, opts, priceInput.text.toString().trim().toDoubleOrNull()?.takeIf { it > 0 })
        }
        fun refresh() {
            val (nums, opts, price) = state()
            val count = nums.size * opts.size
            val sample = nums.take(12).joinToString("  ") + if (nums.size > 12) "  …" else ""
            preview.text = if (count == 0) "Okenn liy — chanje chwa yo." else buildString {
                append("$count liy")
                if (withOptions) append(" (${nums.size} nimewo × ${opts.size} opsyon)")
                if (price != null) append("  ·  Total ${moneyFormat.format(count * price)}")
                append("\n").append(sample)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = count in 1..500 && price != null
            if (count > 500) preview.append("\nTwòp liy (500 maksimòm) — retire kèk chwa.")
        }
        switches.forEach { it.setOnCheckedChangeListener { _, _ -> refresh() } }
        optionBoxes.forEach { it.setOnCheckedChangeListener { _, _ -> refresh() } }
        priceInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
        })
        dialog.setOnShowListener {
            refresh()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val (nums, opts, price) = state()
                if (price == null || nums.isEmpty() || opts.isEmpty()) return@setOnClickListener
                onAdd(nums, opts, price)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** MAuto: marye boul yo 2 pa 2 (ak revè yo si l aktive). */
    private fun showMAutoDialog() {
        val base = baseBorlette()
        val existing = lines.filter { it.category == "MARIAGE" }.map { it.numero }
        showAutoDialog(
            title = "Maryaj otomatik",
            toggles = listOf("Ak revè yo (32*12 → 32*21, 23*12, 23*21)" to true),
            withOptions = false,
            generate = { t -> AutoCombos.mariages(base, withRevers = t[0], existing = existing, includeSame = TenantRules.sameMariage) },
        ) { nums, _, price ->
            if (!fits("MARIAGE", nums.size)) return@showAutoDialog
            val drawName = drawsLabel()
            nums.forEach { m -> addOrMerge(FicheLine(drawName, m, price, "MARIAGE")) }
            refreshLinesList()
            refreshTotal()
            Toast.makeText(this, "${nums.size} maryaj ajoute — modifye oswa efase sa w pa vle", Toast.LENGTH_SHORT).show()
        }
    }

    /** L4Auto: 4 chif ak chak pè boul (revè + vire), nan opsyon yo chwazi yo. */
    private fun showL4AutoDialog() {
        val base = baseBorlette()
        val existing = lines.filter { it.category == "LOTO4" }.map { "${it.numero}:${it.optionLabel}" }.toSet()
        showAutoDialog(
            title = "Loto4 otomatik",
            toggles = listOf(
                "Ak revè yo (3212 → 3221, 2312, 2321)" to true,
                "Vire (3212 → 1232…)" to true,
            ),
            withOptions = true,
            defaultOptions = TenantRules.l4AutoOptions,
            generate = { t -> AutoCombos.loto4(base, withRevers = t[0], withVire = t[1]) },
        ) { nums, opts, price ->
            val fresh = nums.sumOf { num -> opts.count { o -> "$num:0$o" !in existing } }
            if (!fits("LOTO4", fresh)) return@showAutoDialog
            val drawName = drawsLabel()
            var n = 0
            for (num in nums) for (o in opts) {
                if ("$num:0$o" in existing) continue
                addOrMerge(FicheLine(drawName, num, price, "LOTO4", "0$o"))
                n++
            }
            refreshLinesList()
            refreshTotal()
            Toast.makeText(this, "$n liy Loto4 ajoute — modifye oswa efase sa w pa vle", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== PATAJE (WhatsApp, SMS…) ====================

    private fun shareLastFiche() {
        val sold = lastSold
        if (sold == null) {
            Toast.makeText(
                this,
                if (lines.isEmpty()) "Vann yon fich anvan pou w ka pataje l" else "Enprime (vann) fich la anvan, epi pataje l",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val fallbackDraw = sold.drawName
        val shareLines = sold.lines.orEmpty().map { l ->
            FicheShare.Line(
                drawName = l.drawName ?: fallbackDraw,
                code = codeFor(l.gameType) + (l.option?.trimStart('0')?.takeIf { it.isNotEmpty() }?.let { " O$it" } ?: ""),
                numero = l.numero ?: "",
                amount = l.amount ?: 0.0,
            )
        }
        val text = FicheShare.buildText(
            ficheNumber = sold.ficheNumber ?: "—",
            dateText = lastSoldAt,
            agent = TenantBranding.vendeur,
            lines = shareLines,
            total = sold.total ?: shareLines.sumOf { it.amount },
        )
        FicheShare.share(this, text)
    }

    // ==================== MARIAGE ====================
    private fun addMariageLine() {
        if (!requireDrawSelected()) return

        val rawInput = binding.etBoulLa.text.toString().trim()
        val montant = binding.etMontant.text.toString().trim().toDoubleOrNull()

        val numero = when {
            rawInput.matches(Regex("^\\d{4}$")) -> rawInput.substring(0, 2) + "*" + rawInput.substring(2, 4)
            rawInput.matches(Regex("^\\d{2}\\*\\d{2}$")) -> rawInput
            else -> {
                Toast.makeText(this, "Antre 4 chif (egzanp 2532) pou Mariage", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (montant == null || montant <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val parts = numero.split("*")
        if (parts.size == 2 && parts[0] == parts[1] && !TenantRules.sameMariage) {
            Toast.makeText(this, "Maryaj ${numero} pa pèmèt: \"Boul pa boul\" fèmen nan Mon compte", Toast.LENGTH_LONG).show()
            return
        }
        if (!fits("MARIAGE", countNew("MARIAGE", listOf(numero)))) return
        val drawName = drawsLabel()
        addOrMerge(FicheLine(drawName, numero, montant, "MARIAGE"))
        binding.etBoulLa.text?.clear()
        binding.etBoulLa.requestFocus()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== LOTO4 ====================
    private fun showLoto4OptionsDialog() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val price = binding.etMontant.text.toString().trim().toDoubleOrNull()

        if (!numero.matches(Regex("^\\d{4}$"))) {
            Toast.makeText(this, "Antre yon nimewo 4 chif pou Loto4", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val vire = numero.substring(2, 4) + numero.substring(0, 2)

        val options = listOf(
            Triple("L4O1", numero, "01"),
            Triple("L4O1", vire, "01"),
            Triple("L4O2", numero, "02"),
            Triple("L4O2", vire, "02"),
            Triple("L4O3", numero, "03"),
            Triple("L4O3", vire, "03"),
        )

        showOptionsCheckboxDialog("OPTIONS LOTO 4", options, price, "LOTO4")
    }

    // ==================== LOTO5 ====================
    private fun showLoto5OptionsDialog() {
        if (!requireDrawSelected()) return

        val numero = binding.etBoulLa.text.toString().trim()
        val price = binding.etMontant.text.toString().trim().toDoubleOrNull()

        if (!numero.matches(Regex("^\\d{5}$"))) {
            Toast.makeText(this, "Antre yon nimewo 5 chif pou Loto5", Toast.LENGTH_SHORT).show()
            return
        }
        if (price == null || price <= 0) {
            Toast.makeText(this, "Antre yon pri valab", Toast.LENGTH_SHORT).show()
            return
        }

        val options = listOf(
            Triple("Option 1", numero, "01"),
            Triple("Option 2", numero, "02"),
            Triple("Option 3", numero, "03"),
        )

        showOptionsCheckboxDialog("OPTIONS LOTO 5", options, price, "LOTO5")
    }

    private fun showOptionsCheckboxDialog(
        title: String,
        options: List<Triple<String, String, String>>,
        price: Double,
        category: String,
    ) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }
        val checkBoxes = mutableListOf<android.widget.CheckBox>()

        for ((label, numero, _) in options) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val checkBox = android.widget.CheckBox(this).apply {
                text = "$label -> $numero"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val priceLabel = TextView(this).apply {
                text = moneyFormat.format(price)
            }
            checkBoxes.add(checkBox)
            row.addView(checkBox)
            row.addView(priceLabel)
            container.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("VALIDER") { _, _ ->
                val newOnes = checkBoxes.indices.count { i ->
                    checkBoxes[i].isChecked && findLine(drawsLabel(), options[i].second, category, options[i].third) == null
                }
                if (!fits(category, newOnes)) return@setPositiveButton
                val drawName = drawsLabel()
                var addedCount = 0
                checkBoxes.forEachIndexed { i, cb ->
                    if (cb.isChecked) {
                        val (_, numero, optionLabel) = options[i]
                        addOrMerge(FicheLine(drawName, numero, price, category, optionLabel))
                        addedCount++
                    }
                }
                if (addedCount == 0) {
                    Toast.makeText(this, "Ou pa koche okenn opsyon", Toast.LENGTH_SHORT).show()
                } else {
                    binding.etBoulLa.text?.clear()
                    refreshLinesList()
                    refreshTotal()
                }
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    private fun requireDrawSelected(): Boolean {
        if (selectedDraws.isEmpty()) {
            Toast.makeText(this, "Chwazi yon Tiraj anvan ou ka vann", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // ==================== JENERE NIMEWO FICH ====================
    // Yon SÈL nimewo pou TOUT fich la — jenere nan `submitFiche()`, voye
    // ak demann lan, epi enprime sou resi a (QR kòd la tou).
    private var lastFicheMillis = 0L

    private fun generateFicheNumber(): String {
        // 12 chif soti nan lè a an milisegond (pa repete avan ~30 ane).
        // Plizyè tiraj an menm tan → chak fich gen pwòp nimewo l.
        var ms = System.currentTimeMillis()
        if (ms <= lastFicheMillis) ms = lastFicheMillis + 1
        lastFicheMillis = ms
        val raw = (ms % 1_000_000_000_000L).toString().padStart(12, '0')
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}-${raw.substring(8, 12)}"
    }

    private fun gameTypeFor(category: String): String = when (category) {
        "BORLETTE" -> "borlette"
        "LOTO3" -> "loto3"
        "LOTO4" -> "loto4"
        "LOTO5" -> "loto5"
        "MARIAGE" -> "mariage"
        else -> category.lowercase()
    }

    private fun codeFor(gameType: String?): String = when (gameType?.lowercase()) {
        "borlette" -> "BO"
        "loto3" -> "LT"
        "loto4" -> "L4"
        "loto5" -> "L5"
        "mariage" -> "MA"
        else -> (gameType ?: "").take(2).uppercase()
    }

    /** Siyati fich la: si li chanje, se yon NOUVO vant (nouvo requestId). */
    private fun currentSignature(gameId: String): String =
        selectedDraws.joinToString(",") { it.id } + "|" + gameId + "|" + lines.joinToString(";") { "${it.category}:${it.numero}:${it.optionLabel}:${it.price}" }

    // ==================== ENPRIME FICH LA ====================
    private fun printCurrentFiche(result: SellFicheResult) {
        val soldLines = result.lines.orEmpty()
        if (soldLines.isEmpty()) {
            Toast.makeText(this, "Pa gen liy pou enprime", Toast.LENGTH_SHORT).show()
            return
        }
        val ficheNumber = result.ficheNumber ?: "—"
        val drawName = result.drawName ?: selectedDraw?.name ?: "—"
        val total = result.total ?: soldLines.sumOf { it.amount ?: 0.0 }

        // Fich plizyè tiraj: yon seksyon pou chak tiraj sou MENM resi a
        fun toPrint(l: com.plusgroup.pos.network.models.SoldLine): Triple<String, String, String> {
            val numero = if (!l.option.isNullOrBlank()) "${l.numero} (O${l.option.trimStart('0')})" else (l.numero ?: "")
            return Triple(codeFor(l.gameType), numero, moneyFormat.format(l.amount ?: 0.0))
        }
        val sections = result.draws?.takeIf { it.size > 1 }?.map { part ->
            com.plusgroup.pos.printer.ReceiptSection(
                drawName = part.drawName ?: "—",
                drawTotal = moneyFormat.format(part.total ?: part.lines.orEmpty().sumOf { it.amount ?: 0.0 }),
                lines = part.lines.orEmpty().map { toPrint(it) },
            )
        }

        val dateFormat = java.text.SimpleDateFormat("dd/MMM/yyyy hh:mm a", java.util.Locale.US)
        val dateTimeText = dateFormat.format(java.util.Date())

        // Resi a enprime SA BACKEND LAN ANREJISTRE (pa sa ki te sou ekran an)
        val printLines: List<Triple<String, String, String>> = soldLines.map { line ->
            val numero = if (!line.option.isNullOrBlank()) "${line.numero} (O${line.option.trimStart('0')})" else (line.numero ?: "")
            Triple(codeFor(line.gameType), numero, moneyFormat.format(line.amount ?: 0.0))
        }

        lifecycleScope.launch {
            // Non, telefòn, adrès, mesaj ak logo: Mon compte → Paramètres.
            // Nou pran sa ki deja sere a pou enprime TOUSWIT (pa tann rezo a);
            // rafrechisman an fèt an awyè plan (onCreate + apre chak enpresyon).
            TenantBranding.ensureCached(applicationContext)

            val companyName = TenantBranding.companyName?.uppercase() ?: "PLUS GROUP"
            val vendeur = TenantBranding.vendeur ?: "—"
            val footerMessage = TenantBranding.message?.takeIf { it.isNotBlank() } ?: "Mèsi pou konfyans ou!"
            val phone = TenantBranding.phone.orEmpty()
            val address = TenantBranding.address.orEmpty()

            printerManager.connect {
                runOnUiThread {
                    if (!printerManager.isReady()) {
                        Toast.makeText(
                            this@NewFicheActivity,
                            "Fich la VANN, men enprimant lan pa konekte. Konekte l epi reenprime fich la nan \"Fich mwen yo\".",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@runOnUiThread
                    }
                    printerManager.printFicheReceipt(
                        companyName = companyName,
                        promoLine = address,
                        phone = phone,
                        vendeur = vendeur,
                        dateTimeText = dateTimeText,
                        ficheNumber = ficheNumber,
                        drawName = drawName,
                        drawTotal = moneyFormat.format(total),
                        lines = printLines,
                        grandTotal = moneyFormat.format(total),
                        footerMessage = footerMessage,
                        qrData = ficheNumber,
                        sections = sections,
                    )
                    Toast.makeText(this@NewFicheActivity, "Fich enprime avèk siksè!", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch { TenantBranding.load(applicationContext) }
                }
            }
        }
    }

    // ==================== LIS LIY ====================

    private fun refreshLinesList() {
        binding.llLinesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val categoriesInOrder = LinkedHashSet<String>()
        lines.forEach { categoriesInOrder.add(it.category) }

        for (category in categoriesInOrder) {
            val headerText = TextView(this).apply {
                text = category
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 14f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(0, 16, 0, 4)
            }
            binding.llLinesContainer.addView(headerText)

            lines.forEachIndexed { index, line ->
                if (line.category != category) return@forEachIndexed

                val rowBinding = ItemFicheLineBinding.inflate(inflater, binding.llLinesContainer, false)
                rowBinding.tvLineTiraj.text = line.drawName
                // Plizyè tiraj: tout non yo, youn anba lòt, yon ti jan pi piti
                val nDraws = line.drawName.count { it == '\n' } + 1
                rowBinding.tvLineTiraj.maxLines = nDraws.coerceAtMost(6)
                rowBinding.tvLineTiraj.textSize = if (nDraws > 1) 13f else 16f
                rowBinding.tvLineBoul.text = line.numero
                rowBinding.tvLineOption.text = line.optionLabel ?: ""
                rowBinding.tvLineKob.text = moneyFormat.format(line.price)

                rowBinding.btnDeleteLine.setOnClickListener {
                    lines.removeAt(index)
                    refreshLinesList()
                    refreshTotal()
                }
                // Mon compte → "Ajan ka modifye lo"
                rowBinding.btnEditLinePrice.visibility = if (TenantRules.canEditPrice) View.VISIBLE else View.GONE
                rowBinding.btnEditLinePrice.setOnClickListener {
                    promptForPrice { newPrice ->
                        line.price = newPrice
                        refreshLinesList()
                        refreshTotal()
                    }
                }

                binding.llLinesContainer.addView(rowBinding.root)
            }
        }
        updateAutoRow()
    }

    private fun refreshTotal() {
        val total = lines.sumOf { it.price }
        val n = selectedDraws.size
        binding.tvTotal.text = if (n > 1) {
            "Total: ${moneyFormat.format(total)} × $n tiraj = ${moneyFormat.format(total * n)}"
        } else {
            "Total: ${moneyFormat.format(total)}"
        }
    }

    // ==================== SOUMÈT + ENPRIME (AN PARALÈL) ====================

    private fun submitFiche(isRetryAfterCollision: Boolean = false) {
        val draws = selectedDraws.toList()
        val game = selectedGame

        if (draws.isEmpty()) {
            Toast.makeText(this, "Chwazi yon tiraj anvan", Toast.LENGTH_SHORT).show()
            return
        }
        if (game == null) {
            Toast.makeText(this, "Pa gen jwèt disponib", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Ajoute omwen yon liy anvan w enprime", Toast.LENGTH_SHORT).show()
            return
        }

        // Menm fich ak dènye eseye ki pa t konfime → re-itilize menm ID yo
        val signature = currentSignature(game.id)
        if (signature != pendingSignature || pendingRequestId == null) {
            pendingRequestId = java.util.UUID.randomUUID().toString()
            pendingFicheNumber = generateFicheNumber()
            pendingSignature = signature
        }

        // YON SÈL fich: plizyè tiraj → drawIds (backend lan vann boul yo sou
        // chak tiraj nan menm tranzaksyon an: tout oswa anyen)
        val request = SellFicheRequest(
            requestId = pendingRequestId!!,
            ficheNumber = pendingFicheNumber!!,
            drawId = draws.first().id,
            drawIds = if (draws.size > 1) draws.map { it.id } else null,
            gameId = game.id,
            deviceCode = DeviceIdHelper.getDeviceId(this),
            lines = lines.map { line ->
                SellFicheLine(
                    numero = line.numero,
                    gameType = gameTypeFor(line.category),
                    option = if (line.category == "LOTO4" || line.category == "LOTO5") line.optionLabel else null,
                    amount = line.price,
                )
            },
        )

        binding.btnPrint.isEnabled = false
        binding.btnPrint.alpha = 0.45f // siy touswit: klik la pran

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.sellFiche(request)
                val result = res.body()?.data

                if (res.isSuccessful && result != null) {
                    pendingRequestId = null
                    pendingFicheNumber = null
                    pendingSignature = null

                    lastSold = result
                    lastSoldAt = java.text.SimpleDateFormat("dd/MMM/yyyy hh:mm a", java.util.Locale.US).format(java.util.Date())
                    printCurrentFiche(result)
                    val bar = Snackbar.make(binding.root, "Fich ${result.ficheNumber ?: ""} vann", Snackbar.LENGTH_LONG)
                    if (TenantRules.showShare) bar.setAction("PATAJE") { shareLastFiche() }
                    bar.show()
                    // Entèripsyon limit = non: kèk boul pa t vann (limit) → di ajan an
                    val skipped = result.skipped.orEmpty()
                    if (skipped.isNotEmpty()) {
                        MaterialAlertDialogBuilder(this@NewFicheActivity)
                            .setTitle("${skipped.map { it.numero }.distinct().size} boul pa vann")
                            .setMessage(
                                "Boul sa yo rive nan limit yo, yo retire sou fich la:\n\n" +
                                    skipped.joinToString("\n") { "• ${it.numero} — ${moneyFormat.format(it.amount ?: 0.0)}" } +
                                    "\n\nRès fich la vann nòmalman."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    lines.clear()
                    binding.etBoulLa.text?.clear()
                    binding.etMontant.text?.clear()
                    refreshLinesList()
                    refreshTotal()
                    if (result.replay == true) {
                        Toast.makeText(this@NewFicheActivity, "Fich sa a te deja vann — n ap enprime l.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val raw = res.errorBody()?.string()
                val message = ApiErrors.parse(raw) ?: "Vant lan pa pase. Eseye ankò."

                // Nimewo fich la te deja itilize (ra anpil) → nouvo nimewo, yon sèl fwa
                if (ApiErrors.code(raw) == "FICHE_EXISTS" && !isRetryAfterCollision) {
                    pendingRequestId = null
                    pendingSignature = null
                    binding.btnPrint.isEnabled = true
                    submitFiche(isRetryAfterCollision = true)
                    return@launch
                }

                // Refize (tiraj fèmen, limit, boul bloke…): ANYEN pa vann,
                // menm pou lòt tiraj yo. Ajan an korije epi re-eseye.
                pendingRequestId = null
                pendingSignature = null
                showSaleError(message)
            } catch (e: Exception) {
                showSaleError(
                    "Koneksyon an koupe. Nou pa konnen si fich la pase.\n\n" +
                        "Peze ENPRIME ankò san chanje anyen: si li te deja vann, " +
                        "li p ap vann de fwa — l ap jis enprime."
                )
            } finally {
                binding.btnPrint.isEnabled = true
                binding.btnPrint.alpha = 1f
            }
        }
    }

    private fun showSaleError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Fich la pa vann")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
