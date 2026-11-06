package com.plusgroup.pos.util

/**
 * Kalkil otomatik pou ekran vant lan (san Android — fasil pou teste):
 *
 *  • REVÈ: 35 → 53 (boul 2 chif; 33 pa gen revè).
 *  • MAuto: marye boul yo 2 pa 2. "Ak revè": chak boul marye ak revè lòt la
 *    tou (32,12 → 32*12, 32*21, 23*12, 23*21). Maryaj pa gen lòd: 12*32 = 32*12.
 *  • L4Auto: 4 chif ak chak pè boul. "Ak revè" + "Vire":
 *    32,12 → 3212, 3221, 2312, 2321 (revè) + 1232, 2132, 1223, 2123 (vire).
 */
object AutoCombos {

    private val TWO = Regex("^\\d{2}$")

    fun rev(n: String): String = n.reversed()

    /** Boul borlette (2 chif) diferan yo, nan lòd yo te antre a. */
    fun baseNumbers(numbers: List<String>): List<String> =
        numbers.map { it.trim() }.filter { TWO.matches(it) }.distinct()

    /** Revè ki manke pou boul yo (pa double, pa 33/44…). */
    fun reversesToAdd(numbers: List<String>): List<String> {
        val have = numbers.map { it.trim() }.toMutableSet()
        val out = mutableListOf<String>()
        for (n in baseNumbers(numbers)) {
            val r = rev(n)
            if (r != n && r !in have) {
                have += r
                out += r
            }
        }
        return out
    }

    /** Kle yon maryaj san lòd: "32*12" ak "12*32" → "12*32". */
    fun mariageKey(m: String): String {
        val parts = m.split("*").map { it.trim() }
        return if (parts.size == 2) parts.sorted().joinToString("*") else m.trim()
    }

    private fun variants(n: String, withRevers: Boolean): List<String> =
        if (withRevers) listOf(n, rev(n)).distinct() else listOf(n)

    /**
     * "Ak revè": yon boul ak revè l se YON SÈL fanmi (32 ak 23). Konsa, si
     * ajan an te deja klike Revè (32, 12, 23, 21), nou pa marye 32 ak 23 —
     * rezilta a menm jan ak 32, 12 sèlman.
     */
    private fun families(base: List<String>, withRevers: Boolean): List<String> =
        if (withRevers) base.distinctBy { minOf(it, rev(it)) } else base

    /** Tout maryaj yo (san sa ki deja sou fich la). */
    fun mariages(
        base: List<String>,
        withRevers: Boolean,
        existing: Collection<String> = emptyList(),
        includeSame: Boolean = false, // "Boul pa boul": 25*25
    ): List<String> {
        val skip = existing.map { mariageKey(it) }.toSet()
        val out = LinkedHashMap<String, String>()
        val base = families(base, withRevers)
        if (includeSame) {
            for (n in base) for (x in variants(n, withRevers)) {
                val key = mariageKey("$x*$x")
                if (key !in skip && key !in out) out[key] = "$x*$x"
            }
        }
        for (i in base.indices) for (j in i + 1 until base.size) {
            for (x in variants(base[i], withRevers)) for (y in variants(base[j], withRevers)) {
                if (x == y) continue
                val key = mariageKey("$x*$y")
                if (key in skip || key in out) continue
                out[key] = "$x*$y"
            }
        }
        return out.values.toList()
    }

    /** Tout nimewo 4 chif yo (san repetisyon). */
    fun loto4(base: List<String>, withRevers: Boolean, withVire: Boolean): List<String> {
        val out = LinkedHashSet<String>()
        val base = families(base, withRevers)
        for (i in base.indices) for (j in i + 1 until base.size) {
            val xs = variants(base[i], withRevers)
            val ys = variants(base[j], withRevers)
            for (x in xs) for (y in ys) out += x + y
            if (withVire) for (x in xs) for (y in ys) out += y + x
        }
        return out.toList()
    }
}
