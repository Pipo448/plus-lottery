package com.plusgroup.pos

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.plusgroup.pos.databinding.ActivitySearchFicheBinding
import com.plusgroup.pos.ui.FicheDetailSheet

/**
 * "Chache Fich" — pa nimewo, oswa pa SCAN:
 *  • Bouton SCAN → kamera a li QR/kòd-ba ki sou fich enprime a.
 *  • POS SUNMI ki gen eskanè entegre: eskanè a "tape" nimewo a nan chan an
 *    epi peze ENTER → rechèch la fèt otomatikman.
 * Rezilta a parèt nan yon bèl fèy detay ak bouton PEYE / ELIMINE.
 */
class SearchFicheActivity : BaseActivity() {

    companion object {
        const val EXTRA_FICHE = "EXTRA_FICHE"
        const val EXTRA_SCAN = "EXTRA_SCAN"
    }

    private lateinit var binding: ActivitySearchFicheBinding

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result?.contents?.trim()
        if (code.isNullOrEmpty()) return@registerForActivityResult
        val fiche = extractFicheNumber(code)
        binding.etTicket.setText(fiche)
        openFiche(fiche)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchFicheBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnScan.setOnClickListener { startScan() }

        binding.etTicket.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                searchTicket()
                true
            } else {
                false
            }
        }
        binding.btnSearchIcon.setOnClickListener { searchTicket() }
        binding.tvResult.text = "Eskane QR ki sou fich la, oswa tape nimewo fich la."

        intent.getStringExtra(EXTRA_FICHE)?.let {
            binding.etTicket.setText(it)
            openFiche(it)
        }
        if (intent.getBooleanExtra(EXTRA_SCAN, false) && savedInstanceState == null) startScan()
    }

    private fun startScan() {
        val options = ScanOptions()
            .setPrompt("Mete QR ki sou fich la nan kare a")
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .setCaptureActivity(ScanActivity::class.java)
        scanLauncher.launch(options)
    }

    private fun searchTicket() {
        val raw = binding.etTicket.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, com.plusgroup.pos.util.Lang.tr("Antre nimewo fich la, oswa peze SCAN"), Toast.LENGTH_SHORT).show()
            return
        }
        openFiche(extractFicheNumber(raw))
    }

    private fun openFiche(ficheNumber: String) {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.etTicket.windowToken, 0)
        binding.tvResult.text = "Fich #${FicheDetailSheet.formatFicheNo(ficheNumber)}"
        FicheDetailSheet.show(this, ficheNumber)
    }

    /**
     * QR ki sou fich la gen nimewo a AK tirè yo ("7939-1006-5118"), jan l
     * anrejistre a. Avan, nou te retire tirè yo → fich la pa t jwenn.
     * Kounye a: 12 chif (ak oswa san tirè/espas) → fòma "XXXX-XXXX-XXXX".
     */
    private fun extractFicheNumber(code: String): String {
        val trimmed = code.trim()
        val digits = trimmed.filter { it.isDigit() }
        val looksNumeric = trimmed.matches(Regex("[0-9 -]+"))
        val candidate = when {
            looksNumeric -> digits
            else -> Regex("\\d{4}-?\\d{4}-?\\d{4}").find(trimmed)?.value?.filter { it.isDigit() } ?: return trimmed
        }
        return if (candidate.length == 12) {
            "${candidate.substring(0, 4)}-${candidate.substring(4, 8)}-${candidate.substring(8)}"
        } else {
            trimmed
        }
    }
}
