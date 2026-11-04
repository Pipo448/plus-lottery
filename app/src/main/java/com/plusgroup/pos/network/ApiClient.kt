package com.plusgroup.pos.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.plusgroup.pos.network.models.RefreshRequest
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

    // Chanje URL sa a si backend ou deplwaye sou yon lòt adrès.
    private const val BASE_URL = "https://plusgroup-lottery-api.onrender.com/api/v1/"

    private var retrofit: Retrofit? = null

    fun getService(context: Context): ApiService {
        if (retrofit == null) {
            val session = SessionManager(context.applicationContext)

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
                .authenticator(TokenRefreshAuthenticator(session))
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }

    /**
     * Lè backend lan reponn 401 (token ekspire), nou mande yon nouvo token
     * ak refresh token an, epi nou re-voye MENM demann lan otomatikman.
     * Si refresh la echwe tou, nou efase sesyon an — pwochen ekran an ap
     * mande ajan an rekonekte.
     */
    private class TokenRefreshAuthenticator(
        private val session: SessionManager,
    ) : Authenticator {

        private val plainClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.contains("/auth/")) return null          // pa refresh sou login/refresh
            if (responseCount(response) >= 2) return null       // pa boukle

            synchronized(this) {
                val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                val current = session.getToken()

                // Yon lòt demann te deja renouvle token an pandan nou t ap tann
                if (!current.isNullOrEmpty() && current != usedToken) {
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $current")
                        .build()
                }

                val refresh = session.getRefreshToken() ?: return null
                return when (val result = refreshTokens(refresh)) {
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

        private sealed class RefreshResult {
            data class Ok(val accessToken: String) : RefreshResult()
            object Rejected : RefreshResult()
            object NetworkError : RefreshResult()
        }

        private fun refreshTokens(refreshToken: String): RefreshResult = try {
            val body = Gson().toJson(RefreshRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BASE_URL + "auth/refresh")
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
