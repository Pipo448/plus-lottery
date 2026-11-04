package com.plusgroup.pos.util

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("plus_pos_session", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    // Refresh token — pèmèt APK a jwenn yon nouvo token otomatikman lè
    // ansyen an ekspire, san ajan an pa bezwen rekonekte.
    fun saveRefreshToken(token: String?) {
        prefs.edit().putString(KEY_REFRESH, token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_REFRESH).apply()
    }

    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH = "refresh_token"
    }
}
