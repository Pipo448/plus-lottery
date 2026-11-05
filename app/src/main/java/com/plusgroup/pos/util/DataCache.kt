package com.plusgroup.pos.util

import android.content.Context
import com.plusgroup.pos.network.ApiClient
import com.plusgroup.pos.network.models.Draw
import com.plusgroup.pos.network.models.LotteryGame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tiraj ouvè yo ak jwèt yo, sere nan memwa.
 *
 * "Nouvèl fich" montre yo TOUSWIT (pa tann rezo a), epi li rafrechi yo an
 * awyè plan. Akèy la prechaje yo chak fwa app la retounen devan, konsa lè
 * ajan an peze NOUVÈL FICH apre yon ti poz, tout bagay deja la.
 */
object DataCache {

    private const val MAX_AGE_MS = 10 * 60 * 1000L
    private val haiti: TimeZone = TimeZone.getTimeZone("America/Port-au-Prince")

    @Volatile private var draws: List<Draw> = emptyList()
    @Volatile private var games: List<LotteryGame> = emptyList()
    @Volatile private var loadedAt = 0L

    /** Tiraj ki toujou ouvè (lè fèmen yo poko pase, lè Ayiti). */
    fun openDraws(): List<Draw> {
        if (System.currentTimeMillis() - loadedAt > MAX_AGE_MS) return emptyList()
        val now = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = haiti }.format(Date())
        return draws.filter { d ->
            val close = d.closeTime ?: return@filter true
            val open = d.openTime
            // Tiraj ki travèse minwi (egz. 20:00 → 01:00): kite sèvè a deside
            if (open != null && close < open) true else now < close
        }
    }

    fun games(): List<LotteryGame> =
        if (System.currentTimeMillis() - loadedAt > MAX_AGE_MS) emptyList() else games

    fun put(newDraws: List<Draw>, newGames: List<LotteryGame>) {
        draws = newDraws
        if (newGames.isNotEmpty()) games = newGames
        loadedAt = System.currentTimeMillis()
    }

    /** Chaje tiraj ouvè + jwèt yo (rele l an awyè plan). */
    suspend fun refresh(context: Context): Boolean = try {
        val api = ApiClient.getService(context.applicationContext)
        val d = api.getDraws("open").body()?.data
        val g = api.getGames().body()?.data
        if (d != null) {
            put(d, g ?: games)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}
