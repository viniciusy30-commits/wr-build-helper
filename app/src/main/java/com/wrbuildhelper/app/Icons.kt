package com.wrbuildhelper.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Ícones de campeões e itens, baixados da CDN pública da Riot (Data Dragon) e guardados no
 * celular. Se algum ícone não existir ou estiver sem internet, aparece um quadradinho com a inicial.
 */
object Icons {
    private const val BASE = "https://ddragon.leagueoflegends.com"
    private val mem = LruCache<String, Bitmap>(400)
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
    private val metaLock = Any()
    private var version: String? = null
    private var champIds: Map<String, String> = emptyMap()
    private var itemIds: Map<String, String> = emptyMap()
    private var metaReady = false
    private var lastMetaFail = 0L
    private val nonAlnum = Regex("[^a-z0-9]")

    private fun norm(s: String): String = nonAlnum.replace(s.lowercase(Locale.ROOT), "")

    class PlaceholderDrawable(private val label: String, private val fg: Int, private val bgc: Int) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            p.color = bgc
            canvas.drawRect(b, p)
            p.color = fg
            p.textAlign = Paint.Align.CENTER
            p.textSize = b.height() * 0.42f
            p.typeface = Typeface.create("serif", Typeface.BOLD)
            val y = b.exactCenterY() - (p.descent() + p.ascent()) / 2f
            canvas.drawText(label, b.exactCenterX(), y, p)
        }

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    fun lp(ctx: Context, sizeDp: Float): LinearLayout.LayoutParams {
        val s = Ui.dp(ctx, sizeDp)
        return LinearLayout.LayoutParams(s, s)
    }

    fun champ(ctx: Context, name: String, sizeDp: Float): ImageView = make(ctx, "c", name)

    fun item(ctx: Context, name: String, sizeDp: Float): ImageView = make(ctx, "i", name)

    private fun make(ctx: Context, kind: String, name: String): ImageView {
        val pal = Palette.WR
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = Ui.rounded(pal.card, Ui.dp(ctx, 4f).toFloat(), pal.ac, Ui.dp(ctx, 2f))
        val pad = Ui.dp(ctx, 2f)
        iv.setPadding(pad, pad, pad, pad)
        iv.outlineProvider = ViewOutlineProvider.BACKGROUND
        iv.clipToOutline = true
        load(ctx, kind, name, iv)
        return iv
    }

    private fun load(ctx: Context, kind: String, name: String, iv: ImageView) {
        val key = "$kind:$name"
        iv.tag = key
        val cached = mem.get(key)
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        val pal = Palette.WR
        val letter = name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "?"
        iv.setImageDrawable(PlaceholderDrawable(letter, pal.ac, pal.card))
        if (missing.contains(key)) return
        val app = ctx.applicationContext
        pool.execute {
            val bmp = try { fetch(app, kind, name) } catch (e: Exception) { null }
            if (bmp != null) {
                mem.put(key, bmp)
                main.post { if (iv.tag == key) iv.setImageBitmap(bmp) }
            }
        }
    }

    private fun download(url: String, dest: File): Boolean {
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 10000
            con.readTimeout = 20000
            con.setRequestProperty("User-Agent", "WRBuildHelper")
            if (con.responseCode != 200) return false
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".tmp")
            con.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    private fun meta(ctx: Context): Boolean {
        synchronized(metaLock) {
            if (metaReady) return true
            if (System.currentTimeMillis() - lastMetaFail < 60000L) return false
            try {
                val dir = File(ctx.filesDir, "dd")
                dir.mkdirs()
                val vf = File(dir, "version.txt")
                val vtmp = File(dir, "versions.json")
                var ver: String? = null
                if (download("$BASE/api/versions.json", vtmp)) {
                    val arr = org.json.JSONArray(vtmp.readText())
                    ver = arr.getString(0)
                    vf.writeText(ver)
                } else if (vf.exists()) {
                    ver = vf.readText().trim()
                }
                if (ver.isNullOrEmpty()) {
                    lastMetaFail = System.currentTimeMillis()
                    return false
                }
                val vs: String = ver ?: return false
                val cf = File(dir, "champion-$vs.json")
                val itf = File(dir, "item-$vs.json")
                if (!cf.exists() && !download("$BASE/cdn/$vs/data/en_US/champion.json", cf)) {
                    lastMetaFail = System.currentTimeMillis()
                    return false
                }
                if (!itf.exists() && !download("$BASE/cdn/$vs/data/en_US/item.json", itf)) {
                    lastMetaFail = System.currentTimeMillis()
                    return false
                }
                dir.listFiles()?.forEach {
                    if ((it.name.startsWith("champion-") || it.name.startsWith("item-")) && !it.name.contains(vs)) it.delete()
                }
                val cm = HashMap<String, String>()
                val cd = JSONObject(cf.readText()).getJSONObject("data")
                val ck = cd.keys()
                while (ck.hasNext()) {
                    val k = ck.next()
                    val o = cd.getJSONObject(k)
                    val id = o.optString("id", k)
                    cm[norm(o.optString("name", id))] = id
                    cm[norm(id)] = id
                }
                val im = HashMap<String, String>()
                val idata = JSONObject(itf.readText()).getJSONObject("data")
                val ik = idata.keys()
                while (ik.hasNext()) {
                    val k = ik.next()
                    val o = idata.getJSONObject(k)
                    val n = norm(o.optString("name", ""))
                    if (n.isEmpty()) continue
                    val onRift = o.optJSONObject("maps")?.optBoolean("11", false) ?: false
                    if (!im.containsKey(n) || onRift) im[n] = k
                }
                version = vs
                champIds = cm
                itemIds = im
                metaReady = cm.isNotEmpty() && im.isNotEmpty()
                if (!metaReady) lastMetaFail = System.currentTimeMillis()
                return metaReady
            } catch (e: Exception) {
                lastMetaFail = System.currentTimeMillis()
                return false
            }
        }
    }

    private fun fetch(ctx: Context, kind: String, name: String): Bitmap? {
        val file = File(File(ctx.filesDir, "icons"), kind + "-" + norm(name) + ".png")
        if (file.exists()) {
            val b = BitmapFactory.decodeFile(file.path)
            if (b != null) return b
            file.delete()
        }
        if (!meta(ctx)) return null
        val id = if (kind == "c") champIds[norm(name)] else itemIds[norm(name)]
        if (id == null) {
            if (kind == "i") {
                // Itens que só existem no Wild Rift: ícone do próprio site de meta.
                val sl = name.lowercase(Locale.ROOT).replace(Regex("[’'.,]"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-')
                if (download("https://www.riftpatchnotes.com/items/" + sl + ".png", file)) {
                    val b = BitmapFactory.decodeFile(file.path)
                    if (b != null) return b
                    file.delete()
                }
            }
            missing.add("$kind:$name")
            return null
        }
        val ver = version ?: return null
        val folder = if (kind == "c") "champion" else "item"
        if (!download("$BASE/cdn/$ver/img/$folder/$id.png", file)) return null
        return BitmapFactory.decodeFile(file.path)
    }
}
