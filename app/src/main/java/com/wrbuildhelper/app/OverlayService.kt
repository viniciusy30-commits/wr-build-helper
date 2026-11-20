package com.wrbuildhelper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/** Bolha flutuante + painel por cima do jogo. Tudo é escolhido manualmente pelo usuário. */
class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "com.wrbuildhelper.app.STOP"
        private const val CHANNEL = "overlay"
        private const val NID = 42
        private const val NONE = -2
        private const val ME = -1
        private val SHORT_LANES = listOf("Topo", "Selva", "Meio", "Dragão", "Sup.")
    }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: LinearLayout? = null
    private var panelOpen = false
    private var content: LinearLayout? = null
    private var scrollMax = 0
    private var panelWidth = 0
    private var pickTarget = NONE
    private var closed = false

    private val listener: () -> Unit = { if (panelOpen) refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (running) return START_NOT_STICKY
        Session.load(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            buildPanel()
            buildBubble()
        } catch (e: Exception) {
            Toast.makeText(this, "Não consegui mostrar a bolha. Confira a permissão de sobreposição.", Toast.LENGTH_LONG).show()
            shutdown()
            return START_NOT_STICKY
        }
        Session.addListener(listener)
        running = true
        return START_NOT_STICKY
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val p = WindowManager.LayoutParams(w, h, overlayType(), flags, PixelFormat.TRANSLUCENT)
        p.gravity = gravity
        return p
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): IntArray {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun buildBubble() {
        val pal = Palette.OVERLAY
        val size = Ui.dp(this, 52f)
        val fl = FrameLayout(this)
        fl.background = Ui.rounded(pal.bg or 0xFF000000.toInt(), size / 2f, pal.ac, Ui.dp(this, 2f))
        fl.elevation = Ui.dp(this, 6f).toFloat()
        val iv = ImageView(this)
        iv.setImageDrawable(Ui.icon(this, R.drawable.ic_bolt, pal.ac))
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
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
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
        panelWidth = minOf(Ui.dp(this, 320f), (sz[0] * 0.62f).toInt())
        scrollMax = (sz[1] * 0.9f).toInt() - Ui.dp(this, 64f)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = Ui.rounded(pal.bg, Ui.dp(this, 8f).toFloat(), pal.ac, Ui.dp(this, 1f))
        val p = Ui.dp(this, 12f)
        root.setPadding(p, Ui.dp(this, 8f), p, Ui.dp(this, 10f))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.title(this, "WR BUILD HELPER", 13f, pal.ac), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val stopT = Ui.text(this, "Encerrar", 11.5f, pal.rd)
        stopT.setPadding(Ui.dp(this, 8f), Ui.dp(this, 6f), Ui.dp(this, 8f), Ui.dp(this, 6f))
        stopT.setOnClickListener { shutdown() }
        head.addView(stopT)
        val close = ImageView(this)
        close.setImageDrawable(Ui.icon(this, R.drawable.ic_close, pal.mu))
        close.setPadding(Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f), Ui.dp(this, 6f))
        close.setOnClickListener { togglePanel() }
        head.addView(close, LinearLayout.LayoutParams(Ui.dp(this, 32f), Ui.dp(this, 32f)))
        root.addView(head)

        val scroll = MaxHeightScroll(this, scrollMax)
        val ct = LinearLayout(this)
        ct.orientation = LinearLayout.VERTICAL
        scroll.addView(ct, Ui.lp())
        content = ct
        root.addView(scroll, Ui.lp())

        val lp = params(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.x = Ui.dp(this, 8f)
        lp.y = Ui.dp(this, 8f)
        root.visibility = View.GONE
        wm.addView(root, lp)
        panel = root
    }

    private fun togglePanel() {
        val pn = panel ?: return
        panelOpen = !panelOpen
        pn.visibility = if (panelOpen) View.VISIBLE else View.GONE
        if (panelOpen) {
            pickTarget = NONE
            refresh()
        }
    }

    private fun refresh() {
        val ct = content ?: return
        ct.removeAllViews()
        if (pickTarget == NONE) buildMain(ct) else buildPick(ct)
    }

    private fun label(text: String, top: Float): TextView {
        val pal = Palette.OVERLAY
        val t = Ui.title(this, text.uppercase(), 10.5f, pal.mu)
        val lp = Ui.lp()
        lp.topMargin = Ui.dp(this, top)
        lp.bottomMargin = Ui.dp(this, 5f)
        t.layoutParams = lp
        return t
    }

    private fun buildMain(ct: LinearLayout) {
        val pal = Palette.OVERLAY

        ct.addView(label("Minha lane", 4f))
        val lanes = FlowLayout(this, Ui.dp(this, 5f), Ui.dp(this, 5f))
        Data.LANES.forEachIndexed { i, n ->
            lanes.addView(Ui.chip(this, pal, n, i == Session.lane, true) { Session.chooseLane(i) })
        }
        ct.addView(lanes, Ui.lp())

        ct.addView(label("Classe / meu campeão", 10f))
        val classes = FlowLayout(this, Ui.dp(this, 5f), Ui.dp(this, 5f))
        for ((k, l) in Data.ROLE) {
            classes.addView(Ui.chip(this, pal, l, k == Session.cls, true) { Session.setClass(k) })
        }
        ct.addView(classes, Ui.lp())

        val me = Session.me
        val meRow = LinearLayout(this)
        meRow.gravity = Gravity.CENTER_VERTICAL
        meRow.background = Ui.rounded(pal.card, Ui.dp(this, 6f).toFloat(), pal.bd, Ui.dp(this, 1f))
        val mp = Ui.dp(this, 6f)
        meRow.setPadding(mp, mp, mp, mp)
        if (me != null) meRow.addView(Icons.champ(this, me.n, 36f), Icons.lp(this, 36f))
        else meRow.addView(Ui.emptyBox(this, pal, 36f), Icons.lp(this, 36f))
        val mt = Ui.text(this, if (me != null) me.n + " — trocar" else "Escolher meu campeão (opcional)", 13f, pal.tx, me != null)
        mt.setPadding(Ui.dp(this, 10f), 0, 0, 0)
        meRow.addView(mt)
        meRow.setOnClickListener {
            pickTarget = ME
            refresh()
        }
        Ui.pressable(meRow)
        val mrp = Ui.lp()
        mrp.topMargin = Ui.dp(this, 6f)
        ct.addView(meRow, mrp)

        ct.addView(label("Inimigos (toque p/ escolher, segure p/ remover)", 12f))
        val row = LinearLayout(this)
        for (i in 0 until 5) {
            val c = Session.en[i]
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER_HORIZONTAL
            val isz = 46f
            if (c != null) tile.addView(Icons.champ(this, c.n, isz), Icons.lp(this, isz))
            else tile.addView(Ui.emptyBox(this, pal, isz), Icons.lp(this, isz))
            val lt = Ui.text(this, SHORT_LANES[i], 10f, pal.ac, true)
            lt.gravity = Gravity.CENTER
            tile.addView(lt, Ui.lp())
            tile.setOnClickListener {
                pickTarget = i
                refresh()
            }
            tile.setOnLongClickListener {
                Session.setEnemy(i, null)
                true
            }
            Ui.pressable(tile)
            row.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        ct.addView(row, Ui.lp())

        val clear = Ui.text(this, "Limpar inimigos", 11.5f, pal.rd)
        clear.setPadding(0, Ui.dp(this, 8f), 0, Ui.dp(this, 4f))
        clear.setOnClickListener { Session.clearEnemies() }
        ct.addView(clear)

        val res = LinearLayout(this)
        res.orientation = LinearLayout.VERTICAL
        ct.addView(res, Ui.lp())
        Renderer.render(this, res, pal, true)
    }

    private fun buildPick(ct: LinearLayout) {
        val pal = Palette.OVERLAY
        val target = pickTarget
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val name = if (target == ME) "Meu campeão" else "Inimigo — " + Data.LANES[target]
        head.addView(Ui.title(this, name.uppercase(), 11.5f, pal.ac), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val hasValue = if (target == ME) Session.me != null else Session.en[target] != null
        if (target != ME && hasValue) {
            val rm = Ui.chip(this, pal, "Remover", false, true) {
                Session.setEnemy(target, null)
                pickTarget = NONE
                refresh()
            }
            head.addView(rm)
        }
        val back = Ui.chip(this, pal, "Voltar", false, true) {
            pickTarget = NONE
            refresh()
        }
        val bp = Ui.lp()
        bp.leftMargin = Ui.dp(this, 6f)
        head.addView(back, bp)
        ct.addView(head, Ui.lp())

        val grid = Picker.build(this, pal, 50f, false, 0) { c ->
            pickTarget = NONE
            if (target == ME) Session.chooseMe(c) else Session.setEnemy(target, c)
            refresh()
        }
        val gp = Ui.lp()
        gp.topMargin = Ui.dp(this, 8f)
        ct.addView(grid, gp)
    }

    // ---------------- Encerramento ----------------

    private fun shutdown() {
        if (closed) return
        closed = true
        running = false
        Session.removeListener(listener)
        try { bubble?.let { wm.removeView(it) } } catch (e: Exception) { }
        try { panel?.let { wm.removeView(it) } } catch (e: Exception) { }
        bubble = null
        panel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!closed) shutdown()
        running = false
        super.onDestroy()
    }
}
