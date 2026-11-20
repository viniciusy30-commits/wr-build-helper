package com.wrbuildhelper.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private fun button(pal: Palette, label: String, filled: Boolean, onClick: () -> Unit): TextView {
        val b = Ui.text(this, label, 14f, if (filled) 0xFFFFFFFF.toInt() else pal.tx, true)
        b.gravity = Gravity.CENTER
        b.setPadding(0, Ui.dp(this, 12f), 0, Ui.dp(this, 12f))
        b.background = if (filled) Ui.rounded(pal.ac, Ui.dp(this, 12f).toFloat())
        else Ui.rounded(pal.card, Ui.dp(this, 12f).toFloat(), pal.bd, Ui.dp(this, 1f))
        b.setOnClickListener { onClick() }
        Ui.pressable(b)
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pal = Palette.of(this)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(pal.bg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 16f)
        root.setPadding(pad, pad, pad, pad * 2)
        scroll.addView(root, Ui.lp())
        setContentView(scroll)

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val back = TextView(this)
        back.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_back, pal.tx), null, null, null)
        back.setPadding(0, Ui.dp(this, 10f), Ui.dp(this, 14f), Ui.dp(this, 10f))
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(Ui.text(this, "Configurações", 20f, pal.tx, true))
        root.addView(head)

        // Atualização
        val up = Ui.card(this, pal)
        up.addView(Ui.text(this, "Atualização do app", 15f, pal.tx, true))
        val ver = Ui.text(this, "Versão instalada: " + Updater.installedName(this) + " (build " + Updater.installedCode(this) + ")", 12.5f, pal.mu)
        ver.setPadding(0, Ui.dp(this, 6f), 0, Ui.dp(this, 10f))
        up.addView(ver)
        val status = Ui.text(this, "", 13f, pal.mu)
        status.setPadding(0, 0, 0, Ui.dp(this, 10f))
        up.addView(button(pal, "Verificar atualização", true) {
            Updater.manualCheck(this) { msg -> status.text = msg }
        }, Ui.lp())
        val sp = Ui.lp(); sp.topMargin = Ui.dp(this, 10f)
        up.addView(status, sp)
        val upp = Ui.lp(); upp.topMargin = Ui.dp(this, 16f)
        root.addView(up, upp)

        // Novidades
        val nv = Ui.card(this, pal)
        nv.addView(Ui.text(this, "O que mudou", 15f, pal.tx, true))
        val nvb = Ui.lp(); nvb.topMargin = Ui.dp(this, 10f)
        nv.addView(button(pal, "Ver novidades", false) { Novidades.showAll(this) }, nvb)
        val nvp = Ui.lp(); nvp.topMargin = Ui.dp(this, 12f)
        root.addView(nv, nvp)

        // Pop-up
        val pp = Ui.card(this, pal)
        pp.addView(Ui.text(this, "Pop-up em jogo", 15f, pal.tx, true))
        val d = Ui.text(this, "Se o Android bloquear a bolha, confira se \"Sobrepor a outros apps\" está ativo para o WR Build Helper.", 12.5f, pal.mu)
        d.setPadding(0, Ui.dp(this, 6f), 0, Ui.dp(this, 10f))
        pp.addView(d)
        pp.addView(button(pal, "Abrir permissão de sobreposição", false) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, Ui.lp())
        val ppp = Ui.lp(); ppp.topMargin = Ui.dp(this, 12f)
        root.addView(pp, ppp)
    }
}
