package com.plusgroup.pos.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

object DeviceIdHelper {

    private const val PREFS_NAME = "plus_pos_device"
    private const val KEY_DEVICE_ID = "device_id"

    // Valè "factice" byen koni ke kèk aparèy Android trè ansyen/defektye
    // retounen pou TOUT moun kòm ANDROID_ID — si nou wè sa, nou pa ka fè
    // l konfyans e nou dwe tonbe sou yon UUID owaza pito.
    private const val KNOWN_BROKEN_ANDROID_ID = "9774d56d682e549c"

    /**
     * Jwenn Device ID aparèy la.
     *
     * Depi vèsyon sa a: ID la baze sou ANDROID_ID aparèy la, ki rete STAB
     * menm apre w DEZENSTALE + REENSTALE (oswa senpleman rebati/voye yon
     * nouvo vèsyon) app la sou menm telefòn nan — kontrèman ak yon UUID
     * owaza ki te konsève SÈLMAN nan SharedPreferences (e ki te disparèt
     * definitivman lè w dezenstale app la).
     *
     * ANDROID_ID chanje SÈLMAN si telefòn nan fè yon "factory reset", oswa
     * si w chanje kle siyati (keystore) w itilize pou bati APK a.
     *
     * IMPÒTAN — kontinwite ak aparèy ki DEJA anrejistre anvan chanjman sa a:
     * Si yon ansyen valè (UUID owaza, ansyen sistèm nan) deja sove lokalman
     * nan SharedPreferences, nou kontinye itilize l pou kounye a — konsa
     * telefòn ki poko dezenstale app la pa pèdi apèrman li san avètisman.
     * Se sèlman apre PWOCHÈN dezenstalasyon/reenstalasyon konplè (fenèt sa
     * a sèlman, yon SÈL fwa) ke aparèy la ap chanje pou nouvo sistèm
     * ANDROID_ID la — apre sa, li ap rete estab pou tout tan, menm apre
     * lòt reenstalasyon/miz ajou.
     */
    fun getDeviceId(context: Context): String {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrEmpty()) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        val newId = if (!androidId.isNullOrEmpty() && androidId != KNOWN_BROKEN_ANDROID_ID) {
            androidId.take(16)
        } else {
            UUID.randomUUID().toString().replace("-", "").take(16)
        }

        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}