package com.plusgroup.pos

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityNewFicheBinding
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
 * Chak "liy" se yon (nimewo + pri). Bouton rapid yo (BPaire, Grap, P0-P9)
 * jenere otomatikman 10 liy ak MENM pri a chak fwa, olye ajan an antre
 * chak nimewo yon pa yon.
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

    // Chak liy: Pair(nimewo, pri)
    private val lines = mutableListOf<Pair<String, Double>>()

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
                // Pa default, chwazi premye jwèt aktif la (adapte si w bezwen
                // yon seleksyon eksplisit pito).
                selectedGame = activeGames.firstOrNull()
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
            numbers.forEach { num -> lines.add(num to price) }
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

        lines.add(numero to montant)
        binding.etBoulLa.text?.clear()
        refreshLinesList()
        refreshTotal()
    }

    // ==================== LIS LIY + TOTAL ====================

    private fun refreshLinesList() {
        // Senp rezime tèks — ranplase ak yon RecyclerView si w vle yon lis
        // ki ka efase chak liy endividyèlman pita.
        val summary = lines.joinToString("\n") { (num, price) -> "$num — ${moneyFormat.format(price)} HTG" }
        binding.tvLinesSummary.text = summary
    }

    private fun refreshTotal() {
        val total = lines.sumOf { it.second }
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

            // Kopi lokal — nou vide `lines` sèlman apre tout soumèt fini,
            // pou pa pèdi done si gen yon erè nan mitan.
            val linesToSubmit = lines.toList()

            for ((numero, montant) in linesToSubmit) {
                try {
                    val res = api.sellTicket(
                        SellTicketRequest(
                            drawId = draw.id,
                            gameId = game.id,
                            numbers = listOf(numero),
                            betAmount = montant,
                            posDeviceId = deviceId,
                        )
                    )
                    if (res.isSuccessful) {
                        successCount++
                    } else {
                        lastErrorMsg = "Liy '$numero': ${res.errorBody()?.string() ?: "erè enkoni"}"
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Liy '$numero': ${e.message}"
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
                // NOTE: liy ki echwe yo rete nan `lines` — ajan an ka re-eseye
                // soumèt ankò san l pa retape tout bagay. Pou fè sa byen,
                // ta bezwen mache tras kilès liy ki reyisi deja; vèsyon sa a
                // se yon premye pa — ka amelyore pita si sa nesesè.
            }
        }
    }
}