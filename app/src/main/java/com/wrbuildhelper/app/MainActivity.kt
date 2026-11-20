package com.wrbuildhelper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var pal: Palette
    private lateinit var lanesBox: FlowLayout
    private lateinit var classBox: FlowLayout
    private lateinit var meBox: LinearLayout
    private lateinit var slotsBox: LinearLayout
    private lateinit var outBox: LinearLayout
    private lateinit var assistBtn: TextView
    private val main = Handler(Looper.getMainLooper())

    private val listener: () -> Unit = { redraw() }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.load(this)
        pal = Palette.of(this)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(pal.bg)
        scroll.isFillViewport = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 16f)
        root.setPadding(pad, pad, pad, Ui.dp(this, 40f))
        scroll.addView(root, Ui.lp())
        setContentView(scroll)

        // Cabeçalho
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Ui.title(this, "WR BUILD HELPER", 21f, pal.ac), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gear = TextView(this)
        gear.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_tune, pal.ac), null, null, null)
        gear.setPadding(Ui.dp(this, 10f), Ui.dp(this, 10f), Ui.dp(this, 10f), Ui.dp(this, 10f))
        gear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        Ui.pressable(gear)
        head.addView(gear)
        root.addView(head)
        val line = View(this)
        line.setBackgroundColor(pal.bd)
        root.addView(line, Ui.lp(h = Ui.dp(this, 1f)))

        val sub = Ui.text(this, "Escolha os 5 inimigos e receba a build ideal. Patch ${Data.PATCH} · ${Data.PATCH_DATE}", 12.5f, pal.mu)
        val subp = Ui.lp(); subp.topMargin = Ui.dp(this, 8f)
        root.addView(sub, subp)

        // Assistente em jogo
        val ac = Ui.card(this, pal)
        ac.addView(Ui.title(this, "ASSISTENTE EM JOGO", 13.5f, pal.ac))
        val info = Ui.text(this, "Mostra uma bolha por cima do Wild Rift. Toque nela para escolher a lane, o seu campeão e os 5 inimigos e ver a build na hora.", 12.5f, pal.mu)
        info.setPadding(0, Ui.dp(this, 6f), 0, Ui.dp(this, 10f))
        ac.addView(info)
        assistBtn = Ui.text(this, "", 14f, pal.onAc, true)
        assistBtn.gravity = Gravity.CENTER
        assistBtn.setPadding(0, Ui.dp(this, 12f), 0, Ui.dp(this, 12f))
        assistBtn.setOnClickListener { onAssistClick() }
        Ui.pressable(assistBtn)
        ac.addView(assistBtn, Ui.lp())
        val acp = Ui.lp(); acp.topMargin = Ui.dp(this, 14f)
        root.addView(ac, acp)
        Ui.fadeIn(ac)

        section(root, "Minha lane")
        lanesBox = FlowLayout(this, Ui.dp(this, 6f), Ui.dp(this, 6f))
        root.addView(lanesBox, Ui.lp())

        section(root, "Meu campeão / classe")
        classBox = FlowLayout(this, Ui.dp(this, 6f), Ui.dp(this, 6f))
        root.addView(classBox, Ui.lp())
        meBox = LinearLayout(this)
        meBox.gravity = Gravity.CENTER_VERTICAL
        val mp = Ui.lp(); mp.topMargin = Ui.dp(this, 8f)
        root.addView(meBox, mp)

        section(root, "Time inimigo (por lane)")
        slotsBox = LinearLayout(this)
        slotsBox.orientation = LinearLayout.VERTICAL
        root.addView(slotsBox, Ui.lp())
        val clear = Ui.text(this, "Limpar inimigos", 12.5f, pal.rd)
        clear.setPadding(0, Ui.dp(this, 10f), 0, Ui.dp(this, 4f))
        clear.setOnClickListener { Session.clearEnemies() }
        root.addView(clear)

        outBox = LinearLayout(this)
        outBox.orientation = LinearLayout.VERTICAL
        root.addView(outBox, Ui.lp())

        val foot = Ui.text(this, "Atualizado para o patch ${Data.PATCH} (${Data.PATCH_DATE}). Heurística baseada em classe, dano, CC e cura dos inimigos; os nomes em português seguem a tradução do LoL e podem variar um pouco no seu cliente (o nome em inglês aparece embaixo). Novos patches podem mudar itens e campeões. Ícones: Riot Games (Data Dragon). Este app não é afiliado à Riot Games.", 11.5f, pal.mu)
        val fp = Ui.lp(); fp.topMargin = Ui.dp(this, 18f)
        root.addView(foot, fp)

        redraw()
        updateAssist()

        Updater.autoCheck(this)
        Novidades.showIfNew(this)
    }

    private fun section(root: LinearLayout, s: String) {
        val t = Ui.title(this, s.uppercase(), 12f, pal.mu)
        val p = Ui.lp(); p.topMargin = Ui.dp(this, 18f); p.bottomMargin = Ui.dp(this, 8f)
        root.addView(t, p)
    }

    override fun onResume() {
        super.onResume()
        Session.addListener(listener)
        redraw()
        updateAssist()
    }

    override fun onPause() {
        Session.removeListener(listener)
        super.onPause()
    }

    private fun redraw() {
        lanesBox.removeAllViews()
        Data.LANES.forEachIndexed { i, n ->
            lanesBox.addView(Ui.chip(this, pal, n, i == Session.lane) { Session.chooseLane(i) })
        }
        classBox.removeAllViews()
        for ((k, label) in Data.ROLE) {
            classBox.addView(Ui.chip(this, pal, label, k == Session.cls) { Session.setClass(k) })
        }

        meBox.removeAllViews()
        val me = Session.me
        val isz = 44f
        if (me != null) {
            meBox.addView(Icons.champ(this, me.n, isz), Icons.lp(this, isz))
        } else {
            meBox.addView(Ui.emptyBox(this, pal, isz), Icons.lp(this, isz))
        }
        val mt = Ui.text(this, if (me != null) me.n + " (" + (Data.ROLE[me.k] ?: "") + ") — trocar" else "Escolher campeão (opcional)", 14f, pal.tx, me != null)
        mt.setPadding(Ui.dp(this, 12f), 0, 0, 0)
        meBox.addView(mt)
        meBox.setOnClickListener { Picker.show(this) { c -> Session.chooseMe(c) } }
        Ui.pressable(meBox)

        slotsBox.removeAllViews()
        for (i in 0 until 5) {
            val c = Session.en[i]
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this, 10f)
            row.setPadding(p, p, p, p)
            row.background = Ui.rounded(pal.card, Ui.dp(this, 6f).toFloat(), pal.bd, Ui.dp(this, 1f), c == null)
            val sz = 44f
            if (c != null) row.addView(Icons.champ(this, c.n, sz), Icons.lp(this, sz))
            else row.addView(Ui.emptyBox(this, pal, sz), Icons.lp(this, sz))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(Ui.dp(this, 12f), 0, 0, 0)
            col.addView(Ui.text(this, Data.LANES[i].uppercase(), 11f, pal.ac, true))
            if (c != null) {
                col.addView(Ui.text(this, c.n, 15f, pal.tx, true))
                col.addView(Ui.text(this, (Data.ROLE[c.k] ?: "") + (if (c.h) " · cura" else "") + (if (c.c) " · CC" else ""), 12f, pal.mu))
            } else {
                col.addView(Ui.text(this, "+ escolher", 14f, pal.mu))
            }
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { Picker.show(this) { ch -> Session.setEnemy(i, ch) } }
            Ui.pressable(row)
            val lp = Ui.lp()
            lp.bottomMargin = Ui.dp(this, 8f)
            slotsBox.addView(row, lp)
        }
        Renderer.render(this, outBox, pal, false)
    }

    // ---------- Assistente em jogo ----------

    private fun updateAssist() {
        val on = OverlayService.running
        assistBtn.text = if (on) "Parar pop-up" else "Iniciar pop-up em jogo"
        assistBtn.setTextColor(if (on) pal.tx else pal.onAc)
        assistBtn.background = Ui.rounded(if (on) pal.rd else pal.ac, Ui.dp(this, 6f).toFloat())
    }

    private fun onAssistClick() {
        if (OverlayService.running) {
            stopService(Intent(this, OverlayService::class.java))
            main.postDelayed({ updateAssist() }, 400)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permitir bolha sobre outros apps")
                .setMessage("Para mostrar o pop-up por cima do Wild Rift, o Android precisa da permissão \"Sobrepor a outros apps\". Toque em Abrir configuração, ative para o WR Build Helper e volte.")
                .setPositiveButton("Abrir configuração") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startOverlay()
    }

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Pop-up ativo! Abra o Wild Rift e toque na bolha.", Toast.LENGTH_LONG).show()
        main.postDelayed({ updateAssist() }, 600)
        main.postDelayed({ moveTaskToBack(true) }, 900)
    }
}
