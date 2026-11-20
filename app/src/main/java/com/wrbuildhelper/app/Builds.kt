package com.wrbuildhelper.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Build do meta de um campeão numa lane. */
class MetaBuild(val boots: String, val bootsUp: String?, val items: List<String>, val lane: String, val tag: String = "", val core: Int = 99, val runes: List<String> = emptyList())

/**
 * Builds do meta por campeão e por lane (arquivo assets/builds.json).
 * O app também tenta baixar a versão mais nova do GitHub ao abrir; assim, novos lotes de
 * campeões chegam só com o commit do arquivo, sem precisar instalar outro APK.
 */
object Builds {
    private const val REMOTE =
        "https://raw.githubusercontent.com/viniciusy30-commits/wr-build-helper/main/app/src/main/assets/builds.json"

    @Volatile
    private var root: JSONObject? = null
    private var appCtx: Context? = null
    private val remoteOk = ConcurrentHashMap<String, List<MetaBuild>>()
    private val remoteFail = ConcurrentHashMap<String, Long>()
    private val inflight: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())
    private val ui = Handler(Looper.getMainLooper())
    @Volatile
    private var started = false

    @Volatile
    var patch: String = Data.PATCH
        private set

    fun init(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        appCtx = app
        var best: JSONObject? = null
        try {
            val txt = app.assets.open("builds.json").bufferedReader().use { it.readText() }
            best = JSONObject(txt)
        } catch (e: Exception) { }
        try {
            val f = File(app.filesDir, "builds-remote.json")
            if (f.exists()) {
                val r = JSONObject(f.readText())
                if (best == null || r.optInt("ver", 0) > best.optInt("ver", 0)) best = r
            }
        } catch (e: Exception) { }
        apply(best)
        Thread {
            try {
                val con = URL(REMOTE).openConnection() as HttpURLConnection
                con.connectTimeout = 8000
                con.readTimeout = 12000
                if (con.responseCode == 200) {
                    val txt = con.inputStream.bufferedReader().use { it.readText() }
                    val r = JSONObject(txt)
                    val cur = root
                    if (cur == null || r.optInt("ver", 0) > cur.optInt("ver", 0)) {
                        File(app.filesDir, "builds-remote.json").writeText(txt)
                        apply(r)
                    }
                }
            } catch (e: Exception) { }
        }.start()
    }

    private fun apply(r: JSONObject?) {
        if (r == null) return
        root = r
        patch = r.optString("patch", Data.PATCH)
    }

    fun count(): Int = root?.optJSONObject("champs")?.length() ?: 0

    /** Build do campeão para a lane; se não houver para essa lane, usa a lane que existir. */
    fun get(champ: String?, lane: Int): MetaBuild? {
        if (champ == null) return null
        val l = local(champ, lane)
        if (l != null) {
            if (l.runes.isNotEmpty()) return l
            // build local sem runas: completa com as runas da página (se já baixadas)
            val rr = remote(champ)
            val rn = (rr?.firstOrNull { it.lane == l.lane } ?: rr?.firstOrNull())?.runes
            return if (rn.isNullOrEmpty()) l else MetaBuild(l.boots, l.bootsUp, l.items, l.lane, l.tag, l.core, rn)
        }
        val r = remote(champ) ?: return null
        val want = Data.LANES.getOrNull(lane)
        return r.firstOrNull { it.lane == want } ?: r.firstOrNull()
    }

    private fun local(champ: String, lane: Int): MetaBuild? {
        val ch = root?.optJSONObject("champs")?.optJSONObject(champ) ?: return null
        var name = Data.LANES.getOrNull(lane) ?: return null
        var b = ch.optJSONObject(name)
        if (b == null) {
            val k = ch.keys()
            if (!k.hasNext()) return null
            name = k.next()
            b = ch.optJSONObject(name) ?: return null
        }
        val arr = b.optJSONArray("items") ?: return null
        val items = ArrayList<String>()
        for (i in 0 until arr.length()) items.add(arr.getString(i))
        if (items.isEmpty()) return null
        val boots = b.optString("boots", "")
        if (boots.isEmpty()) return null
        val up = b.optString("bootsUp", "")
        return MetaBuild(boots, if (up.isEmpty()) null else up, items, name, "Meta " + patch)
    }

    // ---------- Busca no celular (riftpatchnotes.com: núcleo mais popular no Diamante+) ----------

    private fun slugs(n: String): List<String> {
        val base = n.lowercase(java.util.Locale.ROOT)
        val hy = base.replace("&", " ").replace(Regex("[’'.,]"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val out = LinkedHashSet<String>()
        out.add(hy)
        out.add(hy.replace("-", ""))
        if (base.contains("&")) out.add(base.replace("&", "and").replace(Regex("[’'.,]"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-'))
        val first = hy.substringBefore('-')
        if (first.length >= 3) out.add(first)
        return out.toList()
    }

    private fun unescape(s: String): String = s.replace("&#x27;", "’").replace("&#39;", "’")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("'", "’").trim()

    private fun roleToLane(r: String): String = when (r.trim().lowercase(java.util.Locale.ROOT)) {
        "adc", "dragon", "duo" -> "Dragão"
        "support", "sup" -> "Suporte"
        "mid", "middle" -> "Meio"
        "jungle", "jungler" -> "Selva"
        "top", "baron", "solo" -> "Topo"
        else -> r.trim()
    }

    private fun itemsIn(seg: String): List<String> {
        val out = ArrayList<String>()
        val img = Regex("<img[^>]*>")
        val alt = Regex("alt=\"([^\"]*)\"")
        for (m in img.findAll(seg)) {
            val tag = m.value
            if (!tag.contains("/items/")) continue
            val a = alt.find(tag)?.groupValues?.get(1) ?: continue
            val name = unescape(a)
            if (name.isNotEmpty() && !out.contains(name)) out.add(name)
        }
        return out
    }

    private fun runesIn(seg: String): List<String> {
        val out = ArrayList<String>()
        val names = HashSet<String>()
        val img = Regex("<img[^>]*>")
        val alt = Regex("alt=\"([^\"]*)\"")
        val src = Regex("src=\"([^\"]*)\"")
        for (m in img.findAll(seg)) {
            val tag = m.value
            if (!tag.contains("/runes/") && !tag.contains("/perks/") && !tag.contains("runes%2F")) continue
            var raw = src.find(tag)?.groupValues?.get(1)?.replace("&amp;", "&") ?: ""
            if (raw.contains("url=")) {
                try {
                    raw = java.net.URLDecoder.decode(raw.substringAfter("url=").substringBefore("&"), "UTF-8")
                } catch (e: Exception) { }
            }
            val url = when {
                raw.isEmpty() -> ""
                raw.startsWith("http") -> raw
                raw.startsWith("//") -> "https:" + raw
                raw.startsWith("/") -> "https://www.riftpatchnotes.com" + raw
                else -> "https://www.riftpatchnotes.com/" + raw
            }
            var name = alt.find(tag)?.groupValues?.get(1)?.let { unescape(it) } ?: ""
            if (name.isEmpty()) {
                if (raw.isEmpty()) continue
                name = raw.substringAfterLast('/').substringBefore('.').substringBefore('?')
                    .replace(Regex("[-_]+"), " ").trim()
                    .split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
            }
            name = name.replace("|", " ")
            if (name.isNotEmpty() && names.add(name)) out.add(if (url.isEmpty()) name else "$name|$url")
        }
        return out.take(9)
    }

    /** Papéis na ordem das abas da página (a primeira é a mais jogada). */
    private fun roleList(html: String): List<String> {
        val m = Regex("Build Guide \\(([A-Za-z ]{2,20})\\)").find(html) ?: return emptyList()
        val first = m.groupValues[1]
        val before = html.substring(maxOf(0, m.range.first - 3000), m.range.first)
        val text = before.replace(Regex("<[^>]+>"), " ")
        val out = ArrayList<String>()
        for (w in Regex("\\b(Top|Baron|Solo|Jungle|Mid|ADC|Dragon|Duo|Support)\\b").findAll(text)) {
            val lane = roleToLane(w.value)
            if (!out.contains(lane)) out.add(lane)
        }
        val fl = roleToLane(first)
        out.remove(fl)
        out.add(0, fl)
        return out
    }

    private fun parse(html: String): List<MetaBuild> {
        val bIdx = ArrayList<Int>()
        var i = html.indexOf("Most popular boots")
        while (i >= 0) {
            bIdx.add(i)
            i = html.indexOf("Most popular boots", i + 10)
        }
        if (bIdx.isEmpty()) return emptyList()
        val roles = roleList(html)
        val out = ArrayList<MetaBuild>()
        val fc = html.indexOf("Most popular core")
        var start = if (fc in 0 until bIdx[0]) fc else maxOf(0, bIdx[0] - 7000)
        for ((n, b) in bIdx.withIndex()) {
            val nextB = if (n + 1 < bIdx.size) bIdx[n + 1] else html.length
            val ab = html.indexOf("Alternative boots", b)
            val bootsEnd = minOf(if (ab in (b + 1) until nextB) ab else b + 3000, nextB)
            val boots = itemsIn(html.substring(b, bootsEnd)).firstOrNull()
            val items = itemsIn(html.substring(start, b))
            val panelStart = start
            val c1 = html.indexOf("Most popular core", bootsEnd)
            val c2 = html.indexOf("Alternative core", bootsEnd)
            val nc = listOf(c1, c2).filter { it >= 0 }.minOrNull()
            start = if (nc != null && nc < nextB) nc else bootsEnd
            if (boots == null || items.size < 3) continue
            val lane = roles.getOrNull(n) ?: "mais jogada"
            val runes = runesIn(html.substring(panelStart, maxOf(panelStart, minOf(start, nextB))))
            out.add(MetaBuild(boots, null, items.take(5), lane, "Meta Diamante+ (15/11)", 3, runes))
        }
        return out
    }

    private fun toJson(list: List<MetaBuild>): JSONObject {
        val j = JSONObject()
        j.put("t", System.currentTimeMillis())
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("lane", m.lane)
            o.put("boots", m.boots)
            o.put("items", JSONArray(m.items))
            o.put("tag", m.tag)
            o.put("core", m.core)
            o.put("runes", JSONArray(m.runes))
            arr.put(o)
        }
        j.put("panels", arr)
        return j
    }

    private fun fromJson(j: JSONObject): List<MetaBuild> {
        val out = ArrayList<MetaBuild>()
        val arr = j.getJSONArray("panels")
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val a = o.getJSONArray("items")
            val list = ArrayList<String>()
            for (x in 0 until a.length()) list.add(a.getString(x))
            val rl = ArrayList<String>()
            val ra = o.optJSONArray("runes")
            if (ra != null) for (x in 0 until ra.length()) rl.add(ra.getString(x))
            out.add(MetaBuild(o.getString("boots"), null, list, o.optString("lane", ""), o.optString("tag", "Meta Diamante+"), o.optInt("core", 3), rl))
        }
        return out
    }

    private fun remote(champ: String): List<MetaBuild>? {
        remoteOk[champ]?.let { return it }
        val ctx = appCtx ?: return null
        val last = remoteFail[champ]
        if (last != null && System.currentTimeMillis() - last < 10 * 60 * 1000L) return null
        val cands = slugs(champ)
        // cache em disco (válido por 12 horas)
        try {
            val f = File(File(ctx.filesDir, "rpn4"), cands[0] + ".json")
            if (f.exists()) {
                val j = JSONObject(f.readText())
                if (System.currentTimeMillis() - j.optLong("t", 0) < 12 * 3600 * 1000L) {
                    val l = fromJson(j)
                    if (l.isNotEmpty()) {
                        remoteOk[champ] = l
                        return l
                    }
                }
            }
        } catch (e: Exception) { }
        if (!inflight.add(champ)) return null
        Thread {
            var ok = false
            for (sl in cands) {
                try {
                    val con = URL("https://www.riftpatchnotes.com/champion/" + sl).openConnection() as HttpURLConnection
                    con.connectTimeout = 8000
                    con.readTimeout = 15000
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 WRBuildHelper")
                    if (con.responseCode != 200) continue
                    val html = con.inputStream.bufferedReader().use { it.readText() }
                    val l = parse(html)
                    if (l.isEmpty()) continue
                    val dir = File(ctx.filesDir, "rpn4")
                    dir.mkdirs()
                    File(dir, cands[0] + ".json").writeText(toJson(l).toString())
                    remoteOk[champ] = l
                    ok = true
                    break
                } catch (e: Exception) { }
            }
            if (!ok) remoteFail[champ] = System.currentTimeMillis()
            inflight.remove(champ)
            if (ok) ui.post { Session.refresh() }
        }.start()
        return null
    }
}
