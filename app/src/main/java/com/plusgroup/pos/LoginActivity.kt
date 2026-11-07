package com.plusgroup.pos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.plusgroup.pos.databinding.ActivityLoginBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import com.plusgroup.pos.util.DeviceIdHelper
import com.plusgroup.pos.util.ServerConfig
import com.plusgroup.pos.util.SessionManager
import com.plusgroup.pos.util.TenantBranding
import kotlinx.coroutines.launch

/**
 * Ekran login — menm pwosesis ak SGL:
 *  1. "CHAJE SÈVÈ" pran lis SÈVÈ yo sou sèvè santral la; ajan an chwazi youn
 *     (pa default: SÈVÈ 1). Tout demann yo ale sou adrès sèvè sa a.
 *  2. APK a voye DEVICE ID a bay sèvè a: si aparèy la mare ak yon ajan,
 *     logo ak non BOLÈT la parèt touswit, menm anvan login.
 *  3. Login + verifikasyon aparèy la (yon lòt telefòn pa ka itilize kont lan).
 */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Si deja konekte, ale dirèkteman sou akèy la
        if (session.isLoggedIn()) {
            goToMain()
            return
        }

        deviceId = DeviceIdHelper.getDeviceId(this)
        binding.tvDeviceIdValue.text = deviceId
        binding.tvVersion.text = "Version ${appVersion()} @PlusGroup"

        binding.btnLogin.setOnClickListener { handleLogin() }
        binding.btnLoadServers.setOnClickListener { loadServers(openPicker = true) }
        // Lang: Kreyòl / Français (sere sou aparèy la)
        bindLangSwitch()
        binding.btnChooseServer.setOnClickListener {
            if (ServerConfig.cachedList(this).isEmpty()) loadServers(openPicker = true) else showServerPicker()
        }

        // Montre sa ki te sere a touswit (san tann rezo a), epi verifye aparèy la
        TenantBranding.ensureCached(applicationContext)
        showBranding()
        checkDevice()
    }

    private fun bindLangSwitch() {
        val fr = com.plusgroup.pos.util.Lang.isFr(this)
        val on = androidx.core.content.ContextCompat.getColor(this, R.color.pl_ink)
        val off = androidx.core.content.ContextCompat.getColor(this, R.color.pl_white)
        binding.btnLangKr.setBackgroundResource(if (!fr) R.drawable.pl_bg_lang_on else 0)
        binding.btnLangFr.setBackgroundResource(if (fr) R.drawable.pl_bg_lang_on else 0)
        binding.btnLangKr.setTextColor(if (!fr) on else off)
        binding.btnLangFr.setTextColor(if (fr) on else off)
        binding.btnLangKr.setOnClickListener { com.plusgroup.pos.util.Lang.set(this, "ht") }
        binding.btnLangFr.setOnClickListener { com.plusgroup.pos.util.Lang.set(this, "fr") }
    }

    // ==================== SÈVÈ ====================

    private fun showSelectedServer() {
        binding.tvServerSelected.text = ServerConfig.current(this).name
    }

    /** Logo + non bolèt la (si aparèy la rekonèt), sinon logo Plus Group. */
    private fun showBranding() {
        showSelectedServer()
        val known = ServerConfig.deviceTenant(this) != null
        val logo = if (known) TenantBranding.logo(this) else null
        if (logo != null) {
            // Logo bolèt la nan yon ti kare blan sou antèt ble a
            val pad = (10 * resources.displayMetrics.density).toInt()
            binding.ivServerLogo.setBackgroundResource(R.drawable.bg_logo_tile)
            binding.ivServerLogo.setPadding(pad, pad, pad, pad)
            binding.ivServerLogo.setImageBitmap(logo)
        } else {
            binding.ivServerLogo.background = null
            binding.ivServerLogo.setPadding(0, 0, 0, 0)
            binding.ivServerLogo.setImageResource(R.drawable.pl_logo_plus_lotterie)
        }
        if (known && !TenantBranding.companyName.isNullOrBlank()) {
            binding.tvServerName.text = TenantBranding.companyName
            binding.tvServerHint.text = "PLUS LOTTERIE"
        } else {
            binding.tvServerName.text = "Jwe, Tcheke, Genyen !"
            binding.tvServerHint.text = "PLUS GROUP"
        }
    }

    /** Pran lis sèvè yo sou sèvè santral la (CHAJE SÈVÈ). */
    private fun loadServers(openPicker: Boolean) {
        binding.btnLoadServers.isEnabled = false
        binding.btnLoadServers.text = "AP CHAJE..."
        lifecycleScope.launch {
            val list: List<ServerConfig.Server>? = try {
                val res = ApiClient.getService(applicationContext)
                    .getAppServers(ServerConfig.CENTRAL_API + "public/app-servers")
                if (res.isSuccessful) {
                    res.body()?.data.orEmpty().mapNotNull { s ->
                        val code = s.code?.trim().orEmpty()
                        val api = ServerConfig.normalizeApiUrl(s.apiUrl)
                        if (code.isEmpty() || api == null) null
                        else ServerConfig.Server(code, s.name?.takeIf { it.isNotBlank() } ?: code, api)
                    }
                } else null
            } catch (_: Exception) {
                null
            }
            binding.btnLoadServers.isEnabled = true
            binding.btnLoadServers.text = "CHAJE SÈVÈ"

            if (list == null || list.isEmpty()) {
                Toast.makeText(
                    this@LoginActivity,
                    com.plusgroup.pos.util.Lang.tr("Pa t ka chaje lis sèvè yo. Verifye entènèt la epi eseye ankò."),
                    Toast.LENGTH_LONG,
                ).show()
                if (openPicker && ServerConfig.cachedList(this@LoginActivity).isNotEmpty()) showServerPicker()
                return@launch
            }
            ServerConfig.saveList(this@LoginActivity, list)
            Toast.makeText(this@LoginActivity, "${list.size} sèvè chaje", Toast.LENGTH_SHORT).show()

            // Sèvè ajan an te chwazi a pa nan lis la ankò (efase / kache)?
            val current = ServerConfig.current(this@LoginActivity)
            val stillThere = list.firstOrNull { it.code == current.code }
            when {
                stillThere != null && stillThere.apiUrl != current.apiUrl -> selectServer(stillThere) // adrès li chanje
                stillThere != null && stillThere.name != current.name -> { ServerConfig.select(this@LoginActivity, stillThere); showSelectedServer() }
                list.size == 1 && stillThere == null -> selectServer(list[0])
            }
            if (openPicker && list.size > 1) showServerPicker()
        }
    }

    private fun showServerPicker() {
        val list = ServerConfig.cachedList(this)
        if (list.isEmpty()) return
        val current = ServerConfig.current(this)
        val names = list.map { it.name }.toTypedArray()
        var picked = list.indexOfFirst { it.code == current.code }
        MaterialAlertDialogBuilder(this)
            .setTitle(com.plusgroup.pos.util.Lang.tr("Chwazi sèvè ou"))
            .setSingleChoiceItems(names, picked) { _, which -> picked = which }
            .setPositiveButton(com.plusgroup.pos.util.Lang.tr("OK")) { _, _ -> list.getOrNull(picked)?.let { selectServer(it) } }
            .setNegativeButton(com.plusgroup.pos.util.Lang.tr("Anile"), null)
            .show()
    }

    private fun selectServer(server: ServerConfig.Server) {
        val changed = ServerConfig.select(applicationContext, server)
        if (changed) {
            // Yon lòt sèvè = yon lòt baz done: ansyen token ak ansyen bolèt la pa valab ankò
            session.clear()
            ApiClient.reset()
            lifecycleScope.launch {
                TenantBranding.setServer(applicationContext, null, null, newTenant = true)
                showBranding()
            }
        }
        binding.tvError.visibility = View.GONE
        showBranding()
        checkDevice()
    }

    // ==================== DEVICE ID → BOLÈT ====================

    /** Mande sèvè a ak ki bolèt aparèy sa a mare. Logo a parèt anvan login. */
    private fun checkDevice() {
        lifecycleScope.launch {
            try {
                val res = ApiClient.getService(applicationContext).lookupDevice(deviceId)
                val info = res.body()?.data
                if (!res.isSuccessful || info == null) {
                    setDeviceStatus("Pa t ka verifye aparèy la sou sèvè sa a", R.color.pos_muted)
                    return@launch
                }
                if (info.registered == true && !info.tenantSlug.isNullOrBlank()) {
                    val isNew = info.tenantSlug != ServerConfig.deviceTenant(applicationContext)
                    ServerConfig.saveDeviceTenant(applicationContext, info.tenantSlug)
                    TenantBranding.setServer(applicationContext, info.tenantName, info.logo, newTenant = isNew)
                    showBranding()
                    if (info.active == false) {
                        setDeviceStatus("Kont bolèt sa a sispann. Kontakte administratè a.", R.color.pos_red)
                    } else {
                        setDeviceStatus("✓ Aparèy rekonèt — ${info.tenantName ?: ""}".trimEnd(' ', '—'), R.color.pos_green)
                    }
                } else {
                    if (ServerConfig.deviceTenant(applicationContext) != null) {
                        ServerConfig.saveDeviceTenant(applicationContext, null)
                        TenantBranding.setServer(applicationContext, null, null, newTenant = true)
                    }
                    showBranding()
                    setDeviceStatus("Aparèy sa a poko mare ak okenn ajan sou ${ServerConfig.current(applicationContext).name}.\nBay administratè a DEVICE ID sa a.", R.color.pos_muted)
                }
            } catch (_: Exception) {
                // San entènèt: kenbe logo ki te sere a
                setDeviceStatus("Pa gen koneksyon — verifye entènèt la", R.color.pos_muted)
            }
        }
    }

    private fun setDeviceStatus(text: String, colorRes: Int) {
        binding.tvDeviceStatus.text = text
        binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvDeviceStatus.visibility = View.VISIBLE
    }

    // ==================== LOGIN ====================

    private fun handleLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            showError("Antre idantifyan ak modpas ou.")
            return
        }

        binding.tvError.visibility = View.GONE
        setLoading(true)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.login(LoginRequest(username, password))

                if (!res.isSuccessful || res.body() == null) {
                    showError(
                        extractErrorMessage(res.errorBody()?.string())
                            ?: "Idantifyan oswa modpas pa kòrèk sou ${ServerConfig.current(applicationContext).name}."
                    )
                    return@launch
                }

                val token = res.body()!!.resolveToken()
                if (token.isNullOrEmpty()) {
                    showError("Pa gen token. Repons: ${res.body()}")
                    return@launch
                }

                // Sove token tanporèman — nesesè pou apèl registerDevice pi ba a,
                // paske entèsèptè a mete l nan header Authorization la.
                session.saveToken(token)
                // Pou APK a ka renouvle token an otomatikman (pa rekonekte chak 15 minit)
                session.saveRefreshToken(res.body()!!.data?.refreshToken)

                // Marye/verifye Device ID la ak ajan sa a. Si sa echwe (aparèy pa
                // otorize, oswa pwoblèm rezo), koneksyon an DWE anile — se pwen
                // sa a ki anpeche yon lòt telefòn konekte sou menm kont ajan an.
                try {
                    val deviceRes = api.registerDevice(RegisterDeviceRequest(deviceId))
                    if (!deviceRes.isSuccessful) {
                        val errMsg = extractErrorMessage(deviceRes.errorBody()?.string())
                        session.clear()
                        showError(errMsg ?: "Aparèy sa a pa otorize pou kont sa a.")
                        return@launch
                    }
                } catch (e: Exception) {
                    session.clear()
                    showError("Pa t ka verifye aparèy la. Verifye entènèt ou epi eseye ankò.")
                    return@launch
                }

                goToMain()
            } catch (e: Exception) {
                showError("Pa t ka konekte ak ${ServerConfig.current(applicationContext).name}. Verifye entènèt ou.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun extractErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrEmpty()) return null
        return try {
            val json = com.google.gson.JsonParser.parseString(errorBody).asJsonObject
            when {
                json.has("message") && json.get("message").isJsonArray ->
                    json.getAsJsonArray("message").joinToString("\n") { it.asString }
                json.has("message") -> json.get("message").asString
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Ap konekte..." else "KONEKTE"
    }

    private fun goToMain() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
