package com.plusgroup.pos.util

import android.content.Context
import com.plusgroup.pos.network.ApiClient
import java.text.DecimalFormat
import kotlin.random.Random

/**
 * Maryaj gratis (Konfigirasyon → Mariage gratuit, sql/016–018).
 *  • APK a chaje règ yo ("a pati de X HTG → N maryaj gratis") epi li montre
 *    maryaj gratis yo sou ekran vant lan, menm jan ak boul yo.
 *  • Lè fich la vann, li voye yo bay backend lan ki verifye yo (bloke,
 *    deja sou fich la…) epi anrejistre yo ak game_type = "mariage_gratuit".
 *  • Sou resi a yo parèt ak kòd "MG" epi "GRATIS" kote montan an ta ye.
 */
object FreeMariage {
    const val TYPE = "mariage_gratuit"
    const val CODE = "MG"
    const val LABEL = "GRATIS"

    data class Rule(val threshold: Double, val count: Int)

    @Volatile var enabled: Boolean = false
        private set
    @Volatile var rules: List<Rule> = emptyList()
        private set
    @Volatile private var loadedAt = 0L
    private const val REFRESH_MS = 2 * 60 * 1000L

    fun isFree(gameType: String?): Boolean = gameType?.trim()?.lowercase() == TYPE

    /** Montan pou afiche: "GRATIS" pou maryaj gratis, sinon montan an. */
    fun amountText(gameType: String?, amount: Double?, money: DecimalFormat): String =
        if (isFree(gameType)) LABEL else money.format(amount ?: 0.0)

    /** Chaje règ yo (maks yon fwa chak 2 minit, sof si force). Pa janm voye erè. */
    suspend fun load(context: Context, force: Boolean = false) {
        if (!force && System.currentTimeMillis() - loadedAt < REFRESH_MS) return
        try {
            val res = ApiClient.getService(context).getFreeMariageInfo()
            val info = res.body()?.data
            if (res.isSuccessful && info != null) {
                enabled = info.enabled == true
                rules = info.rules.orEmpty()
                    .mapNotNull { r ->
                        val t = r.thresholdAmount ?: return@mapNotNull null
                        val c = r.freeCount ?: return@mapNotNull null
                        if (t > 0 && c > 0) Rule(t, c) else null
                    }
                    .sortedBy { it.threshold }
                loadedAt = System.currentTimeMillis()
            }
        } catch (_: Exception) {
            // rezo koupe: kenbe sa nou te genyen an
        }
    }

    /** Konbyen maryaj gratis yon fich gen dwa (pi gwo règ ki ≤ total la). */
    fun countFor(total: Double): Int =
        if (!enabled || total <= 0) 0 else rules.lastOrNull { total + 1e-9 >= it.threshold }?.count ?: 0

    private fun rev(p: String): String = p.substringAfter('*') + "*" + p.substringBefore('*')

    /**
     * Tire [n] nouvo pè o aza: de boul diferan, pa sa ki nan [taken]
     * (maryaj ki deja sou fich la) ni nan [current].
     */
    fun pick(n: Int, taken: Collection<String>, current: Collection<String>, random: Random = Random.Default): List<String> {
        val avoid = HashSet<String>()
        (taken + current).forEach { avoid.add(it); avoid.add(rev(it)) }
        val out = ArrayList<String>()
        var tries = 0
        while (out.size < n && tries < 2000) {
            tries++
            val a = random.nextInt(100)
            val b = random.nextInt(100)
            if (a == b) continue
            val p = a.toString().padStart(2, '0') + "*" + b.toString().padStart(2, '0')
            if (p in avoid) continue
            avoid.add(p); avoid.add(rev(p))
            out.add(p)
        }
        return out
    }

    /**
     * Mete lis maryaj gratis la ajou pou yon total: kenbe sa ki la deja
     * (pou yo pa chanje chak fwa ajan an tape), retire sa ki vin sou fich la,
     * koupe si total la desann, ajoute si l monte.
     */
    fun sync(current: MutableList<String>, total: Double, taken: Collection<String>) {
        val n = countFor(total)
        val takenAll = taken.flatMap { listOf(it, rev(it)) }.toSet()
        current.removeAll { it in takenAll }
        while (current.size > n) current.removeAt(current.size - 1)
        if (current.size < n) current.addAll(pick(n - current.size, taken, current))
    }

    /** Ranplase youn (ajan an peye sou li) pa yon lòt pè o aza. */
    fun reroll(current: MutableList<String>, index: Int, taken: Collection<String>) {
        if (index !in current.indices) return
        val others = current.filterIndexed { i, _ -> i != index } + current[index]
        pick(1, taken, others).firstOrNull()?.let { current[index] = it }
    }

    /** Pou tès: mete règ yo dirèkteman. */
    internal fun setForTest(on: Boolean, list: List<Rule>) {
        enabled = on
        rules = list.sortedBy { it.threshold }
    }
}
