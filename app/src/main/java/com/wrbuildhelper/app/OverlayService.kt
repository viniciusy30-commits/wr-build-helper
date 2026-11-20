package com.wrbuildhelper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.wrbuildhelper.app.STOP"
        private const val CHANNEL = "overlay"
        private const val NID = 42
        private const val AUTO_MS = 6000L
    }

    private class MaxHeightScroll(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
        }
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var capThread: HandlerThread? = null
    private var capHandler: Handler? = null

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var held: Image? = null
    private var curW = 0
    private var curH = 0
    private val lock = Any()

    private var bubble: View? = null
    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var panelOpen = false
    private var content: LinearLayout? = null
    private var results: LinearLayout? = null
    private var statusTv: TextView? = null
    private var autoChip: TextView? = null

    private var autoOn = false
    private var reading = false
    private var detected: List<Champ> = emptyList()
    private val pending = LinkedHashSet<String>()
    private var status = "Abra a seleção de campeões ou a tela de carregamento e toque em Ler tela."

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val listener: () -> Unit = { if (panelOpen) refresh() }

    private val autoLoop = object : Runnable {
        override fun run() {
            if (!autoOn) return
            readScreen(true)
            main.postDelayed(this, AUTO_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startAsForeground()
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = readProjectionData(intent)
        if (data == null || code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { shutdown() }
                }
            }, main)
            projection = proj
        } catch (e: Exception) {
            Toast.makeText(this, "Não consegui iniciar a captura de tela.", Toast.LENGTH_LONG).show()
            shutdown()
            return START_NOT_STICKY
        }

        Session.load(this)
        val t = HandlerThread("wr-capture")
        t.start()
        capThread = t
        capHandler = Handler(t.looper)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            buildBubble()
            buildPanel()
        } catch (e: Exception) {
            Toast.makeText(this, "Não consegui mostrar a bolha. Confira a permissão de sobreposição.", Toast.LENGTH_LONG).show()
            shutdown()
            return START_NOT_STICKY
        }
        Session.addListener(listener)
        running = true
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readProjectionData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra<Intent>(EXTRA_DATA)
        }
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Pop-up em jogo", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("WR Build Helper ativo")
            .setContentText("Toque na bolha sobre o jogo para ver a build.")
            .setOngoing(true)
            .addAction(0, "Parar", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NID, n)
        }
    }

    // ---------------- Janelas flutuantes ----------------

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun params(w: Int, h: Int, gravity: Int): WindowManager.LayoutParams {
        val type = overlayType()
        // FLAG_SECURE: o próprio pop-up não aparece na captura, então não "atrapalha" a leitura.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SECURE
        val p = WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT)
        p.gravity = gravity
        return p
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): IntArray {
        val dm = DisplayMetrics()
        val d = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        d.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }

    private fun buildBubble() {
        val size = Ui.dp(this, 52f)
        val fl = FrameLayout(this)
        fl.background = Ui.rounded(0xFF2F6BFF.toInt(), size / 2f, 0xFFFFFFFF.toInt(), Ui.dp(this, 2f))
        fl.elevation = Ui.dp(this, 6f).toFloat()
        val iv = ImageView(this)
        iv.setImageDrawable(Ui.icon(this, R.drawable.ic_bolt, 0xFFFFFFFF.toInt()))
        val pad = Ui.dp(this, 12f)
        iv.setPadding(pad, pad, pad, pad)
        fl.addView(iv, FrameLayout.LayoutParams(size, size))

        val lp = params(size, size, Gravity.TOP or Gravity.START)
        lp.x = Ui.dp(this, 8f)
        lp.y = Ui.dp(this, 90f)

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        fl.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try { wm.updateViewLayout(fl, lp) } catch (ex: Exception) { }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                }
            }
            true
        }
        wm.addView(fl, lp)
        bubble = fl
    }

    private fun buildPanel() {
        val pal = Palette.OVERLAY
        val sz = screenSize()
        val w = minOf(Ui.dp(this, 310f), (sz[0] * 0.6f).toInt())
        val maxScroll = (sz[1] * 0.9f).toInt() - Ui.dp(this, 150f)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = Ui.rounded(pal.bg, Ui.dp(this, 16f).toFloat(), pal.bd, Ui.dp(this, 1f))
        val p = Ui.dp(this, 12f)
        root.setPadding(p, Ui.dp(this, 10f), p, Ui.dp(this, 10f))

        // Cabeçalho
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.text(this, "WR Build Helper", 14f, pal.tx, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = ImageView(this)
        close.setImageDrawable(Ui.icon(this, R.drawable.ic_close, pal.mu))
        close.setPadding(Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f))
        close.setOnClickListener { togglePanel() }
        head.addView(close, LinearLayout.LayoutParams(Ui.dp(this, 32f), Ui.dp(this, 32f)))
        root.addView(head)

        // Controles
        val ctl = FlowLayout(this, Ui.dp(this, 6f), Ui.dp(this, 6f))
        val read = Ui.chip(this, pal, "Ler tela", true, true) { readScreen(false) }
        read.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_scan, 0xFFFFFFFF.toInt()), null, null, null)
        read.compoundDrawablePadding = Ui.dp(this, 6f)
        ctl.addView(read)
        val auto = Ui.chip(this, pal, "Auto: desligado", false, true) { toggleAuto() }
        autoChip = auto
        ctl.addView(auto)
        val stopChip = Ui.chip(this, pal, "Encerrar", false, true) { shutdown() }
        ctl.addView(stopChip)
        val cp = Ui.lp(); cp.topMargin = Ui.dp(this, 8f)
        root.addView(ctl, cp)

        val st = Ui.text(this, status, 11.5f, pal.mu)
        statusTv = st
        val sp = Ui.lp(); sp.topMargin = Ui.dp(this, 6f)
        root.addView(st, sp)

        // Conteúdo rolável
        val scroll = MaxHeightScroll(this, maxScroll)
        val ct = LinearLayout(this)
        ct.orientation = LinearLayout.VERTICAL
        scroll.addView(ct, Ui.lp())
        content = ct
        val scp = Ui.lp(); scp.topMargin = Ui.dp(this, 6f)
        root.addView(scroll, scp)

        val lp = params(w, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.x = Ui.dp(this, 8f)
        lp.y = Ui.dp(this, 8f)
        root.visibility = View.GONE
        wm.addView(root, lp)
        panel = root
        panelLp = lp
    }

    private fun togglePanel() {
        val pn = panel ?: return
        panelOpen = !panelOpen
        pn.visibility = if (panelOpen) View.VISIBLE else View.GONE
        if (panelOpen) refresh()
    }

    private fun setStatus(s: String) {
        status = s
        statusTv?.text = s
    }

    /** Recria a parte dinâmica do painel (lanes, detectados, inimigos e resultados). */
    private fun refresh() {
        val ct = content ?: return
        val pal = Palette.OVERLAY
        ct.removeAllViews()
        statusTv?.text = status
        autoChip?.text = if (autoOn) "Auto: ligado" else "Auto: desligado"

        // Lane
        val lanes = FlowLayout(this, Ui.dp(this, 5f), Ui.dp(this, 5f))
        Data.LANES.forEachIndexed { i, n ->
            lanes.addView(Ui.chip(this, pal, n, i == Session.lane, true) { Session.chooseLane(i) })
        }
        ct.addView(lanes, Ui.lp())

        // Campeões reconhecidos na tela
        if (detected.isNotEmpty()) {
            val t = Ui.text(this, "RECONHECIDOS NA TELA (toque nos inimigos)", 10.5f, pal.mu, true)
            val tp = Ui.lp(); tp.topMargin = Ui.dp(this, 10f); tp.bottomMargin = Ui.dp(this, 5f)
            ct.addView(t, tp)
            val fl = FlowLayout(this, Ui.dp(this, 5f), Ui.dp(this, 5f))
            for (c in detected) {
                val on = pending.contains(c.n)
                fl.addView(Ui.chip(this, pal, c.n, on, true) {
                    if (pending.contains(c.n)) {
                        pending.remove(c.n)
                    } else if (pending.size >= 5) {
                        setStatus("Máximo de 5 inimigos. Desmarque um antes.")
                        return@chip
                    } else {
                        pending.add(c.n)
                    }
                    refresh()
                })
            }
            ct.addView(fl, Ui.lp())
            val apply = Ui.chip(this, pal, "Usar como inimigos (" + pending.size + ")", pending.isNotEmpty(), true) { applyPending() }
            val ap = Ui.lp(); ap.topMargin = Ui.dp(this, 6f)
            ct.addView(apply, ap)
        }

        // Inimigos atuais
        val t2 = Ui.text(this, "INIMIGOS (toque para remover)", 10.5f, pal.mu, true)
        val t2p = Ui.lp(); t2p.topMargin = Ui.dp(this, 10f); t2p.bottomMargin = Ui.dp(this, 5f)
        ct.addView(t2, t2p)
        val slots = FlowLayout(this, Ui.dp(this, 5f), Ui.dp(this, 5f))
        for (i in 0 until 5) {
            val c = Session.en[i]
            val label = Data.LANES[i] + ": " + (c?.n ?: "—")
            slots.addView(Ui.chip(this, pal, label, c != null, true) {
                if (Session.en[i] != null) Session.setEnemy(i, null)
            })
        }
        ct.addView(slots, Ui.lp())

        // Resultados
        val res = LinearLayout(this)
        res.orientation = LinearLayout.VERTICAL
        ct.addView(res, Ui.lp())
        results = res
        Renderer.render(this, res, pal, true)
    }

    private fun applyPending() {
        val picks = detected.filter { pending.contains(it.n) }.take(5)
        if (picks.isEmpty()) {
            setStatus("Marque pelo menos um inimigo.")
            return
        }
        Session.setEnemies(Engine.assignLanes(picks))
        setStatus("Inimigos atualizados. Se alguma lane ficou trocada, remova e ajuste no app.")
    }

    private fun toggleAuto() {
        autoOn = !autoOn
        main.removeCallbacks(autoLoop)
        if (autoOn) {
            setStatus("Lendo a tela a cada " + (AUTO_MS / 1000) + "s...")
            main.post(autoLoop)
        } else {
            setStatus("Leitura automática desligada.")
        }
        if (panelOpen) refresh()
    }

    // ---------------- Captura + OCR ----------------

    private fun ensureCapture(w: Int, h: Int, dpi: Int) {
        synchronized(lock) {
            if (reader != null && curW == w && curH == h) return
            val proj = projection ?: return
            val nr = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            nr.setOnImageAvailableListener({ r ->
                val img = try { r.acquireLatestImage() } catch (e: Exception) { null }
                if (img != null) {
                    synchronized(lock) {
                        if (r === reader) {
                            held?.close()
                            held = img
                        } else {
                            img.close()
                        }
                    }
                }
            }, capHandler)
            held?.close()
            held = null
            val old = reader
            reader = nr
            val cur = vd
            if (cur == null) {
                vd = proj.createVirtualDisplay(
                    "wr-capture", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, nr.surface, null, capHandler
                )
            } else {
                cur.resize(w, h, dpi)
                cur.surface = nr.surface
            }
            try { old?.close() } catch (e: Exception) { }
            curW = w
            curH = h
        }
    }

    private fun toBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val buf = plane.buffer
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val full = Bitmap.createBitmap(img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888)
        buf.rewind()
        full.copyPixelsFromBuffer(buf)
        if (rowPadding == 0) return full
        val cropped = Bitmap.createBitmap(full, 0, 0, img.width, img.height)
        full.recycle()
        return cropped
    }

    /** Roda na thread de captura. Entrega null se não conseguir um quadro. */
    private fun grab(cb: (Bitmap?) -> Unit) {
        val h = capHandler ?: return
        try {
            val s = screenSize()
            ensureCapture(s[0], s[1], s[2])
        } catch (e: Exception) {
            cb(null)
            return
        }
        val deadline = SystemClock.uptimeMillis() + 2500
        val attempt = object : Runnable {
            override fun run() {
                var bmp: Bitmap? = null
                synchronized(lock) {
                    val img = held
                    if (img != null) {
                        try { bmp = toBitmap(img) } catch (e: Exception) { bmp = null }
                    }
                }
                if (bmp != null) {
                    cb(bmp)
                } else if (SystemClock.uptimeMillis() < deadline) {
                    h.postDelayed(this, 120)
                } else {
                    cb(null)
                }
            }
        }
        attempt.run()
    }

    private fun readScreen(auto: Boolean) {
        if (reading || projection == null) return
        val h = capHandler ?: return
        reading = true
        if (!auto) setStatus("Lendo a tela...")
        val hide = !auto && panelOpen
        if (hide) panel?.alpha = 0f
        h.postDelayed({
            grab { bmp ->
                main.post { if (hide) panel?.alpha = 1f }
                if (bmp == null) {
                    main.post {
                        reading = false
                        if (!auto) setStatus("Não consegui capturar a tela. Tente de novo.")
                        if (panelOpen) refresh()
                    }
                } else {
                    runOcr(bmp, auto)
                }
            }
        }, if (hide) 400L else 0L)
    }

    private fun runOcr(bmp: Bitmap, auto: Boolean) {
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { text ->
                val hits = Matcher.find(text)
                main.post { onDetected(hits, auto) }
            }
            .addOnFailureListener {
                main.post {
                    reading = false
                    if (!auto) setStatus("Falha ao ler o texto da tela.")
                    if (panelOpen) refresh()
                }
            }
            .addOnCompleteListener {
                try { bmp.recycle() } catch (e: Exception) { }
            }
    }

    private fun onDetected(hits: List<Champ>, auto: Boolean) {
        reading = false
        val mine = Session.me?.n
        val list = hits.filter { it.n != mine }
        val same = list.map { it.n } == detected.map { it.n }
        if (auto && same) return
        detected = list
        pending.clear()
        if (list.isEmpty()) {
            if (!auto) setStatus("Nenhum campeão reconhecido. Abra a seleção de campeões ou a tela de carregamento e tente de novo.")
        } else {
            if (list.size <= 5) list.forEach { pending.add(it.n) }
            setStatus("Achei " + list.size + " campeão(ões). Toque nos que são inimigos e em Usar como inimigos.")
            if (auto && list.size <= 5 && Session.en.all { it == null }) {
                applyPending()
                return
            }
        }
        if (panelOpen) refresh()
    }

    // ---------------- Encerramento ----------------

    private var closed = false

    private fun shutdown() {
        if (closed) return
        closed = true
        running = false
        autoOn = false
        main.removeCallbacksAndMessages(null)
        Session.removeListener(listener)
        try { bubble?.let { wm.removeView(it) } } catch (e: Exception) { }
        try { panel?.let { wm.removeView(it) } } catch (e: Exception) { }
        bubble = null
        panel = null
        synchronized(lock) {
            try { held?.close() } catch (e: Exception) { }
            held = null
            try { vd?.release() } catch (e: Exception) { }
            vd = null
            try { reader?.close() } catch (e: Exception) { }
            reader = null
        }
        try { projection?.stop() } catch (e: Exception) { }
        projection = null
        capThread?.quitSafely()
        capThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!closed) shutdown()
        running = false
        super.onDestroy()
    }
}
