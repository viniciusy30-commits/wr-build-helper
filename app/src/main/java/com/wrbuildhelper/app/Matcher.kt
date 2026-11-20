package com.wrbuildhelper.app

import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.util.Locale

/** Procura nomes de campeões no texto lido da tela (OCR). */
object Matcher {
    private val accents = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]")
    private val splitter = Regex("[^\\p{L}0-9]+")

    private fun norm(s: String): String {
        val d = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return nonAlnum.replace(accents.replace(d, ""), "")
    }

    private val table: List<Pair<String, Champ>> = Engine.champs.map { Pair(norm(it.n), it) }

    private val aliases: Map<String, String> = mapOf(
        "mestreyi" to "Master Yi",
        "nunu" to "Nunu & Willump",
        "nunue" to "Nunu & Willump",
        "willump" to "Nunu & Willump",
        "mundo" to "Dr. Mundo",
        "jarvan" to "Jarvan IV"
    )

    private fun dist(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    private fun matchText(raw: String, allowFuzzy: Boolean): Champ? {
        val x = norm(raw)
        if (x.length < 2) return null
        for ((k, c) in table) if (k == x) return c
        aliases[x]?.let { return Engine.find(it) }
        if (allowFuzzy && x.length >= 5) {
            var best: Champ? = null
            var bd = 2
            for ((k, c) in table) {
                if (k.length < 5 || Math.abs(k.length - x.length) > 1) continue
                val d = dist(k, x)
                if (d < bd) {
                    bd = d
                    best = c
                }
            }
            return best
        }
        return null
    }

    /** Retorna os campeões achados, na ordem em que aparecem na tela (de cima para baixo, esquerda para direita). */
    fun find(text: Text): List<Champ> {
        class Hit(val champ: Champ, val top: Int, val left: Int)

        val hits = mutableListOf<Hit>()
        for (b in text.textBlocks) {
            for (line in b.lines) {
                val box = line.boundingBox
                val top = box?.top ?: 0
                val left = box?.left ?: 0
                var c = matchText(line.text, true)
                if (c == null) {
                    for (tok in line.text.split(splitter)) {
                        if (tok.length >= 4) {
                            c = matchText(tok, false)
                            if (c != null) break
                        }
                    }
                }
                if (c != null) hits.add(Hit(c, top, left))
            }
        }
        val out = LinkedHashMap<String, Champ>()
        for (h in hits.sortedWith(compareBy<Hit>({ it.left / 300 }, { it.top }))) {
            if (!out.containsKey(h.champ.n)) out[h.champ.n] = h.champ
        }
        return out.values.toList()
    }
}
