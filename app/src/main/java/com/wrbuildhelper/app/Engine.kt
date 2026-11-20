package com.wrbuildhelper.app

import java.util.Locale

data class Champ(val n: String, val k: Char, val h: Boolean, val c: Boolean)

data class Item(val n: String, val why: String, val t: Int)

data class Profile(
    val p: Double,
    val tk: Int,
    val hl: Int,
    val cc: Int,
    val asn: Int,
    val items: List<Item>,
    val n: Int
)

data class Gank(val l: String, val s: Double, val c: List<Champ>)

object Engine {

    val champs: List<Champ> = Data.RAW.split(",").map { s ->
        val parts = s.split(":")
        val n = parts[0]
        val c = parts[1]
        Champ(n, c[0], c.contains('h'), c.contains('c'))
    }.sortedBy { it.n.lowercase(Locale.ROOT) }

    private val byName: Map<String, Champ> = champs.associateBy { it.n }

    fun find(name: String): Champ? = byName[name]

    fun ptName(n: String): String = Data.PT[n] ?: n

    fun calc(en: List<Champ?>, cls: Char): Profile? {
        val e = en.filterNotNull()
        if (e.isEmpty()) return null
        var ad = 0.0
        var ap = 0.0
        var tk = 0
        var hl = 0
        var cc = 0
        var asn = 0
        for (c in e) {
            if ("ASFO".indexOf(c.k) >= 0) {
                ad += 1.0
            } else if ("MPE".indexOf(c.k) >= 0) {
                ap += 1.0
            } else {
                ad += 0.5
                ap += 0.5
                tk++
            }
            if (c.h) hl++
            if (c.c) cc++
            if ("SP".indexOf(c.k) >= 0) asn++
        }
        val p = ad / (ad + ap)

        class Sit(val n: String, val why: String, val sc: Double)

        val sits = mutableListOf<Sit>()
        fun add(n: String?, why: String, sc: Double) {
            if (n != null && sits.none { it.n == n }) sits.add(Sit(n, why, sc))
        }

        if (p >= 0.6) add(Data.ARM[cls], Math.round(p * 100).toString() + "% do dano inimigo é físico", p * 10)
        if (p <= 0.4) add(Data.MR[cls], Math.round((1 - p) * 100).toString() + "% do dano inimigo é mágico", (1 - p) * 10)
        if (p > 0.4 && p < 0.6) {
            add(Data.ARM[cls], "dano misto: defesa física", 5.0)
            add(Data.MR[cls], "dano misto: resistência mágica", 5.0)
        }
        if (tk >= 2) add(Data.PEN[cls], "$tk tanques no time inimigo", 8.0)
        if (hl >= 2) add(Data.HEAL[cls], "$hl inimigos com cura", 7.5)
        if (asn >= 2 && cls != 'T') add(Data.BURST[cls], "$asn assassinos: proteção contra burst", 6.0)

        var boots = Data.BOOT[cls] ?: "Berserker’s Greaves"
        var bw = "padrão da classe"
        if (cc >= 3 || p <= 0.35) {
            boots = "Mercury’s Treads"
            bw = if (cc >= 3) "$cc inimigos com CC pesado" else "time majoritariamente mágico"
        } else if (p >= 0.65 && e.size >= 3 && cls != 'E') {
            boots = "Plated Steelcaps"
            bw = "time majoritariamente físico"
        }

        val sorted = sits.sortedByDescending { it.sc }
        val items = mutableListOf(Item(boots, bw, 1))
        val core = (Data.CORE[cls] ?: emptyList()).map { Item(it, "Core da classe", 0) }
        val sit = sorted.map { Item(it.n, it.why, 2) }
        val order = listOfNotNull(
            core.getOrNull(0), core.getOrNull(1),
            sit.getOrNull(0), sit.getOrNull(1),
            core.getOrNull(2), sit.getOrNull(2),
            core.getOrNull(3)
        )
        for (x in order) {
            if (items.size < 6 && items.none { it.n == x.n }) items.add(x)
        }
        return Profile(p, tk, hl, cc, asn, items, e.size)
    }

    private fun gankScore(c: Champ): Double {
        var s = when (c.k) {
            'A', 'O', 'M', 'E' -> 7.0
            'P' -> 5.0
            'S', 'F' -> 4.0
            else -> 3.0
        }
        s += if (Data.MOBILE.contains(c.n)) -2.0 else 1.5
        if (c.c) s -= 1.0
        return maxOf(0.0, minOf(10.0, s))
    }

    fun why(c: Champ): String {
        val a = mutableListOf<String>()
        a.add(if (Data.MOBILE.contains(c.n)) "tem fuga/dash" else "sem fuga")
        if ("AOME".indexOf(c.k) >= 0) a.add("frágil")
        if (c.k == 'T') a.add("tanque, difícil de matar")
        if (c.c) a.add("CC pode punir o gank")
        return c.n + ": " + a.joinToString(", ")
    }

    fun ganks(en: List<Champ?>): List<Gank> {
        val g = mutableListOf<Gank>()
        for (i in intArrayOf(0, 2)) {
            val c = en.getOrNull(i)
            if (c != null) g.add(Gank(Data.LANES[i], gankScore(c), listOf(c)))
        }
        val cs = listOfNotNull(en.getOrNull(3), en.getOrNull(4))
        if (cs.isNotEmpty()) {
            val avg = cs.sumOf { gankScore(it) } / cs.size
            g.add(Gank("Dragão", maxOf(0.0, avg - 0.5), cs))
        }
        return g.sortedByDescending { it.s }
    }

    fun fmt(d: Double): String = String.format(Locale("pt", "BR"), "%.1f", d)

    fun tips(lane: Int, cls: Char, r: Profile?, g: List<Gank>, en: List<Champ?>): List<String> {
        val t = mutableListOf<String>()
        val jungler = lane == 1
        if (jungler && g.isNotEmpty()) {
            var s = "Gank: comece por <b>" + g[0].l + "</b> (" + fmt(g[0].s) + "/10)"
            if (g.size > 1 && g[1].s >= 5) s += ", depois <b>" + g[1].l + "</b>"
            s += ". Evite gankar tanque com CC."
            t.add(s)
        }
        t.add(Data.LANE_TIPS[lane])
        val j = en.getOrNull(1)
        if (j != null && lane != 1) {
            if ("SP".indexOf(j.k) >= 0) {
                t.add("Selva inimigo (" + j.n + ") é assassino: cuidado com ganks, mantenha visão nos arbustos.")
            } else if ("TF".indexOf(j.k) >= 0) {
                t.add("Selva inimigo (" + j.n + ") é forte em luta e objetivo: não duele perto dele.")
            }
        }
        if (j != null && jungler) {
            if ("SP".indexOf(j.k) >= 0) {
                t.add(j.n + " inimigo é assassino: cuidado com invade; fique perto do seu time no início.")
            } else {
                t.add("Selva inimigo (" + j.n + "): disputar os objetivos com Smite pronto é mais seguro do que invadir.")
            }
        }
        if (r != null) {
            if (r.asn >= 2) t.add("Há " + r.asn + " assassinos inimigos: não ande sozinho e fique perto do time quando eles sumirem do mapa.")
            if (r.cc >= 3) t.add("Muito CC inimigo (" + r.cc + "): não seja o primeiro a entrar; use botas com tenacidade e espere o CC ser gasto.")
            if (r.tk >= 2) t.add("Muitos tanques: foque o atirador/mago frágil e evite trocar de frente com a frontline.")
            if (r.hl >= 2) t.add("Há " + r.hl + " inimigos com cura: foque o curandeiro e compre anti-cura cedo.")
            if (r.p >= 0.65) {
                t.add("Time inimigo é muito físico: priorize armadura e lute perto da sua torre.")
            } else if (r.p <= 0.35) {
                t.add("Time inimigo é muito mágico: priorize resistência mágica e cuidado com o burst.")
            }
        }
        t.add(Data.CT[cls] ?: "")
        return t
    }

    // Pontuação de quanto cada classe combina com cada lane (usada para distribuir
    // os campeões lidos da tela nos 5 espaços de lane).
    private val laneFit: List<Map<Char, Int>> = listOf(
        mapOf('F' to 3, 'T' to 3, 'P' to 1, 'S' to 1, 'M' to 1),
        mapOf('S' to 3, 'F' to 2, 'T' to 2, 'P' to 2),
        mapOf('M' to 3, 'P' to 3, 'S' to 2, 'F' to 1),
        mapOf('A' to 3, 'O' to 3),
        mapOf('E' to 3, 'T' to 2, 'M' to 1)
    )

    fun assignLanes(picks: List<Champ>): Array<Champ?> {
        val best = arrayOfNulls<Champ>(5)
        var bestScore = -1
        val cur = arrayOfNulls<Champ>(5)
        fun rec(i: Int, score: Int) {
            if (i == picks.size) {
                if (score > bestScore) {
                    bestScore = score
                    for (x in 0 until 5) best[x] = cur[x]
                }
                return
            }
            for (lane in 0 until 5) {
                if (cur[lane] == null) {
                    cur[lane] = picks[i]
                    rec(i + 1, score + (laneFit[lane][picks[i].k] ?: 0))
                    cur[lane] = null
                }
            }
        }
        rec(0, 0)
        return best
    }
}
