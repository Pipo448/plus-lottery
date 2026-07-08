package com.plusgroup.pos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.plusgroup.pos.databinding.ActivityLoginBinding
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.LoginRequest
import com.plusgroup.pos.network.models.RegisterDeviceRequest
import com.plusgroup.pos.util.DeviceIdHelper
import com.plusgroup.pos.util.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // Si deja konekte, ale dirèkteman nan MainActivity
        if (session.isLoggedIn()) {
            goToMain()
            return
        }

        val deviceId = DeviceIdHelper.getDeviceId(this)
        binding.tvDeviceIdValue.text = deviceId

        binding.btnLogin.setOnClickListener {
            handleLogin(deviceId)
        }
    }

    private fun handleLogin(deviceId: String) {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            binding.tvError.text = "Antre idantifyan ak modpas ou."
            binding.tvError.visibility = android.view.View.VISIBLE
            return
        }

        binding.tvError.visibility = android.view.View.GONE
        setLoading(true)

        lifecycleScope.launch {
            try {
                val api = ApiClient.getService(applicationContext)
                val res = api.login(LoginRequest(username, password))

                if (!res.isSuccessful || res.body() == null) {
                    showError("Idantifyan oswa modpas pa kòrèk.")
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
                showError("Pa t ka konekte ak sèvè a. Verifye entènèt ou.")
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

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = android.view.View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Ap konekte..." else "KONEKTE"
    }

   private fun goToMain() {
    startActivity(Intent(this, DashboardActivity::class.java))
    finish()
}