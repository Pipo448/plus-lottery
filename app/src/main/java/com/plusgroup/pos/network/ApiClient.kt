package com.plusgroup.pos.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.plusgroup.pos.network.models.RefreshRequest
import com.plusgroup.pos.util.ServerConfig
import com.plusgroup.pos.util.SessionManager
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Adrès la soti nan SÈVÈ ajan an chwazi a (ServerConfig). Pa default:
    // SÈVÈ 1 (sèvè santral la). Lè ajan an chanje sèvè, Retrofit rebati.
    @Volatile private var retrofit: Retrofit? = null
    @Volatile private var currentBase: String? = null

    private fun baseUrl(context: Context): String = currentBase ?: ServerConfig.apiUrl(context)

    /** Rele l apre ajan an chanje sèvè: pwochen demann yo ale sou nouvo adrès la. */
    fun reset() {
        retrofit = null
        currentBase = null
    }

    fun getService(context: Context): ApiService {
        val wanted = ServerConfig.apiUrl(context.applicationContext)
        if (retrofit == null || wanted != currentBase) {
            val session = SessionManager(context.applicationContext)

            val appContext = context.applicationContext
            val authInterceptor = Interceptor { chain ->
                val token = session.getToken()
                val request = chain.request().newBuilder()
                if (!token.isNullOrEmpty()) {
                    request.header("Authorization", "Bearer $token")
                }
                chain.proceed(request.build())
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                // Render ka pran tan pou l leve (cold start) — pa koupe twò vit.
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .authenticator(TokenRefreshAuthenticator(session, appContext))
                .build()

            currentBase = wanted
            retrofit = Retrofit.Builder()
                .baseUrl(wanted)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }

    // ==================== TOKEN TOUJOU FRÈ ====================
    // Lè ajan an kite POS la yon ti tan, token an (15 min) ekspire. Avan,
    // premye demann lan te resevwa 401 → refresh → re-voye: sa te pran tan
    // (e Render te ka ap dòmi tou). Kounye a, depi app la retounen devan
    // (onResume), nou renouvle token an DAVANS si l prèske fini.

    private val refreshLock = Any()
    private val plainClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    internal sealed class RefreshResult {
        data class Ok(val accessToken: String) : RefreshResult()
        object Rejected : RefreshResult()
        object NetworkError : RefreshResult()
    }

    internal fun refreshTokens(session: SessionManager, refreshToken: String, context: Context): RefreshResult = try {
        val body = Gson().toJson(RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl(context) + "auth/refresh")
            .post(body)
            .build()
        plainClient.newCall(request).execute().use { res ->
            if (res.code == 401 || res.code == 403) return RefreshResult.Rejected
            if (!res.isSuccessful) return RefreshResult.NetworkError
            val raw = res.body?.string() ?: return RefreshResult.NetworkError
            val json = JsonParser.parseString(raw).asJsonObject
            val data = if (json.has("data")) json.getAsJsonObject("data") else json
            val access = data.get("accessToken")?.asString ?: return RefreshResult.Rejected
            session.saveToken(access)
            data.get("refreshToken")?.asString?.let { session.saveRefreshToken(it) }
            RefreshResult.Ok(access)
        }
    } catch (e: Exception) {
        RefreshResult.NetworkError
    }

    /** Konbyen segonn ki rete anvan token an ekspire (null = pa ka li l). */
    private fun secondsLeft(token: String): Long? = try {
        val payload = token.split(".")[1]
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
        val exp = JsonParser.parseString(json).asJsonObject.get("exp")?.asLong
        exp?.let { it - System.currentTimeMillis() / 1000 }
    } catch (_: Exception) {
        null
    }

    /**
     * Rele l nan onResume: si token an ap fini nan mwens pase 3 minit (oswa
     * li fini deja), renouvle l an awyè plan. Pa bloke ekran an.
     */
    suspend fun keepTokenFresh(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val session = SessionManager(context.applicationContext)
        val token = session.getToken() ?: return@withContext
        val left = secondsLeft(token)
        if (left != null && left > 180) return@withContext
        synchronized(refreshLock) {
            // Yon lòt apèl te ka deja renouvle l
            val current = session.getToken() ?: return@synchronized
            if (current != token) return@synchronized
            val refresh = session.getRefreshToken() ?: return@synchronized
            if (refreshTokens(session, refresh, context.applicationContext) == RefreshResult.Rejected) session.clear()
        }
    }

    /**
     * Lè backend lan reponn 401 (token ekspire), nou mande yon nouvo token
     * ak refresh token an, epi nou re-voye MENM demann lan otomatikman.
     * Si refresh la echwe tou, nou efase sesyon an — pwochen ekran an ap
     * mande ajan an rekonekte.
     */
    private class TokenRefreshAuthenticator(
        private val session: SessionManager,
        private val context: Context,
    ) : Authenticator {

        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.contains("/auth/")) return null          // pa refresh sou login/refresh
            if (responseCount(response) >= 2) return null       // pa boukle

            synchronized(refreshLock) {
                val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                val current = session.getToken()

                // Yon lòt demann te deja renouvle token an pandan nou t ap tann
                if (!current.isNullOrEmpty() && current != usedToken) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }

                val refresh = session.getRefreshToken() ?: return null
                return when (val result = refreshTokens(session, refresh, context)) {
                    is RefreshResult.Ok -> response.request.newBuilder()
                        .header("Authorization", "Bearer ${result.accessToken}")
                        .build()
                    RefreshResult.Rejected -> {
                        // Refresh token an pa bon ankò (ekspire / kont bloke)
                        session.clear()
                        null
                    }
                    RefreshResult.NetworkError -> null // pa dekonekte ajan an pou yon pwoblèm rezo
                }
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }
}
