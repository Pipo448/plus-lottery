package com.plusgroup.pos.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object DeviceIdHelper {

    private const val PREFS_NAME = "plus_pos_device"
    private const val KEY_DEVICE_ID = "device_id"

    /**
     * Jwenn Device ID aparèy la. Li kreye yon sèl fwa (UUID) epi konsève l
     * pou toujou nan SharedPreferences — menm ID la ap parèt chak fwa app
     * la louvri sou menm aparèy la, menm apre yon rebòte.
     */
    fun getDeviceId(context: Context): String {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrEmpty()) return existing

        val newId = UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}
