package com.plusgroup.pos

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityNewFicheBinding
import com.plusgroup.pos.databinding.ItemFicheLineBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import com.plusgroup.pos.network.models.SellTicketRequest
import com.plusgroup.pos.util.DeviceIdHelper
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
 * Soumèt final la rele `POST agent/tickets` YON FWA POU CHAK LIY, youn
 * apre lòt (pa gen wout "batch" nan backend la kounye a).
 */
class NewFicheActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewFicheBinding

    private var openDraws: List<Draw> = emptyList()
    private var selectedDraw: Draw? = null

    private var activeGames: List<LotteryGame> = emptyList()
    private var selectedGame: LotteryGame? = null

    private data class FicheLine(
        val drawName: String,
        val numero: String,
        var price: Double,
    )

    private val lines = mutableListOf<FicheLine>()

    private val moneyFormat = DecimalFormat("#,##0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewFicheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearAll.setOnClickListener {
            lines.clear()
            refreshLinesList()
            refreshTotal()
        }
        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "Pataje — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnPrint.setOnClickListener {
            Toast.makeText(this, "Enprime — byento", Toast.LENGTH_SHORT).show()
        }
        binding.btnEditGameLabel.setOnClickListener { showGamePicker() }

        binding.tvChwaziTiraj.setOnClickListener { showDrawPicker() }

        // Bouton rapid pou chak kategori pari
        binding.btnBPaire.setOnClickListener { addQuickCategory(QuickCategory.B_PAIRE) }
        binding.btnGrap.setOnClickListener { addQuickCategory(QuickCategory.GRAP) }
        binding.btnP0.setOnClickListener { addQuickCategory(QuickCategory.pick(0)) }
        binding.btnP1.setOnClickListener { addQuickCategory(QuickCategory.pick(1)) }
        binding.btnP2.setOnClickListener { addQuickCategory(QuickCategory.pick(2)) }
        binding.btnP3.setOnClickListener { addQuickCategory(QuickCategory.pick(3)) }
        binding.btnP4.setOnClickListener { addQuickCategory(QuickCategory.pick(4)) }

        binding.btnAntre.setOnClickListener { addManualLine() }
        binding.btnSoumet.setOnClickListener { submitFiche() }

        loadDrawsAndGames()
        refreshTotal()
    }

    // ==================== CHAJE TIRAJ AK JWÈT ====================

    private fun loadDrawsAndGames() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)

                val drawsRes = api.getDraws("open")
                openDraws = drawsRes.body()?.data ?: emptyList()

                val gamesRes = api.getGames()
                activeGames = gamesRes.body()?.data ?: emptyList()
                selectedGame = activeGames.firstOrNull()
                binding.tvFicheLabel.text = (selectedGame?.name ?: "BORLETTE").uppercase()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NewFicheActivity,
                    "Pa t ka chaje tiraj/jwèt yo: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun showDrawPicker() {
        if (openDraws.isEmpty()) {
            Toast.makeText(this, "Pa gen tiraj ouvè kounye a", Toast.LENGTH_SHORT).show()
            return
        }
        val names = openDraws.map { it.name ?: it.id }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("CHWAZI TIRAJ")
            .setSingleChoiceItems(names, -1) { dialog, which ->
                selectedDraw = openDraws[which]
                binding.tvChwaziTiraj.text = selectedDraw?.name ?: "CHWAZI TIRAJ"
                dialog.dismiss()
            }
            .setNegativeButton("Anile", null)
            .show()
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

    /**
     * Jenere 10 nimewo pou kategori a, apre l mande yon pri, epi ajoute
     * 10 liy (menm pri chak) nan Fich la.
     */
    private fun addQuickCategory(category: QuickCategory) {
        val numbers: List<String> = when (category) {
            is QuickCategory.BPaire -> (0..9).map { "$it$it" }
            is QuickCategory.Grap -> (0..9).map { "$it$it$it" }
            is QuickCategory.Pick -> (0..9).map { d -> "$d${category.digit}" }
        }
        promptForPrice { price ->
            val drawName = selectedDraw?.name ?: "—"
            numbers.forEach { num -> lines.add(FicheLine(drawName, num, price)) }
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

    // ==================== ANTRE MANYÈL ====================

    private fun addManualLine() {
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

        val drawName = selectedDraw?.name ?: "—"
        lines.add(FicheLine(drawName, numero, montant))
        binding.etBoulLa.text?.clear()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== LIS LIY (chak liy: kòbèy + kreyon apa) ====================

    private fun refreshLinesList() {
        binding.llLinesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        lines.forEachIndexed { index, line ->
            val rowBinding = ItemFicheLineBinding.inflate(inflater, binding.llLinesContainer, false)
            rowBinding.tvLineTiraj.text = line.drawName
            rowBinding.tvLineBoul.text = line.numero
            rowBinding.tvLineKob.text = moneyFormat.format(line.price)

            rowBinding.btnDeleteLine.setOnClickListener {
                lines.removeAt(index)
                refreshLinesList()
                refreshTotal()
            }
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

    private fun refreshTotal() {
        val total = lines.sumOf { it.price }
        binding.tvTotal.text = "Total: ${moneyFormat.format(total)}"
    }

    // ==================== SOUMÈT FICH LA (yon apèl pou chak liy) ====================

    private fun submitFiche() {
        val draw = selectedDraw
        val game = selectedGame

        if (draw == null) {
            Toast.makeText(this, "Chwazi yon tiraj anvan", Toast.LENGTH_SHORT).show()
            return
        }
        if (game == null) {
            Toast.makeText(this, "Pa gen jwèt disponib", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.isEmpty()) {
            Toast.makeText(this, "Ajoute omwen yon liy anvan w soumèt", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceId = DeviceIdHelper.getDeviceId(this)
        binding.btnSoumet.isEnabled = false

        lifecycleScope.launch {
            val api = ApiClient.getService(applicationContext)
            var successCount = 0
            var lastErrorMsg: String? = null

            val linesToSubmit = lines.toList()

            for (line in linesToSubmit) {
                try {
                    val res = api.sellTicket(
                        SellTicketRequest(
                            drawId = draw.id,
                            gameId = game.id,
                            numbers = listOf(line.numero),
                            betAmount = line.price,
                            posDeviceId = deviceId,
                        )
                    )
                    if (res.isSuccessful) {
                        successCount++
                    } else {
                        lastErrorMsg = "Liy '${line.numero}': ${res.errorBody()?.string() ?: "erè enkoni"}"
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Liy '${line.numero}': ${e.message}"
                }
            }

            binding.btnSoumet.isEnabled = true

            if (successCount == linesToSubmit.size) {
                Toast.makeText(
                    this@NewFicheActivity,
                    "Fich soumèt avèk siksè ($successCount liy)",
                    Toast.LENGTH_LONG,
                ).show()
                lines.clear()
                refreshLinesList()
                refreshTotal()
            } else {
                Toast.makeText(
                    this@NewFicheActivity,
                    "$successCount/${linesToSubmit.size} liy pase. Dènye erè: $lastErrorMsg",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}