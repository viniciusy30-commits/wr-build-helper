package com.wrbuildhelper.app

import android.content.Context
import android.graphics.Typeface
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Desenha os cartões de resultado (perfil, gank, build, dicas). Usado no app e no pop-up. */
object Renderer {

    private fun tag(ctx: Context, pal: Palette, s: String, sz: Float): TextView {
        val t = Ui.text(ctx, s, 12f * sz, pal.mu)
        t.setPadding(Ui.dp(ctx, 8f), Ui.dp(ctx, 3f), Ui.dp(ctx, 8f), Ui.dp(ctx, 3f))
        t.background = Ui.rounded(pal.bg, Ui.dp(ctx, 4f).toFloat(), pal.bd, Ui.dp(ctx, 1f))
        return t
    }

    private fun heading(ctx: Context, pal: Palette, s: String, sz: Float): TextView =
        Ui.title(ctx, s.uppercase(), 13.5f * sz, pal.ac)

    private fun addCard(ctx: Context, parent: LinearLayout, card: LinearLayout, idx: Int, compact: Boolean) {
        val p = Ui.lp()
        p.topMargin = Ui.dp(ctx, if (compact) 8f else 10f)
        parent.addView(card, p)
        Ui.fadeIn(card, idx * 60L)
    }

    fun render(ctx: Context, parent: LinearLayout, pal: Palette, compact: Boolean) {
        parent.removeAllViews()
        val sz = if (compact) 0.88f else 1f
        val r = Engine.calc(Session.en.toList(), Session.cls) ?: return
        val g = Engine.ganks(Session.en.toList())
        var idx = 0

        // ---- Perfil inimigo
        run {
            val card = Ui.card(ctx, pal)
            val head = LinearLayout(ctx)
            head.gravity = Gravity.CENTER_VERTICAL
            head.addView(heading(ctx, pal, "Perfil inimigo", sz))
            val tp = Ui.lp(); tp.leftMargin = Ui.dp(ctx, 8f)
            head.addView(tag(ctx, pal, "${r.n}/5", sz), tp)
            card.addView(head)

            val icons = FlowLayout(ctx, Ui.dp(ctx, 5f), Ui.dp(ctx, 5f))
            val isz = if (compact) 34f else 40f
            for (c in Session.en) {
                if (c != null) icons.addView(Icons.champ(ctx, c.n, isz), Icons.lp(ctx, isz))
            }
            val ip = Ui.lp(); ip.topMargin = Ui.dp(ctx, 10f)
            card.addView(icons, ip)

            val a = Math.round(r.p * 100).toInt()
            val bar = BarView(ctx, 0xFF8B5CF6.toInt(), 0xFFE5894D.toInt(), r.p.toFloat())
            val bp = Ui.lp(h = Ui.dp(ctx, 10f)); bp.topMargin = Ui.dp(ctx, 10f); bp.bottomMargin = Ui.dp(ctx, 4f)
            card.addView(bar, bp)
            val lab = LinearLayout(ctx)
            val l1 = Ui.text(ctx, "Físico $a%", 12f * sz, pal.mu)
            val l2 = Ui.text(ctx, "Mágico ${100 - a}%", 12f * sz, pal.mu)
            lab.addView(l1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l2.gravity = Gravity.END
            lab.addView(l2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(lab)
            val flow = FlowLayout(ctx, Ui.dp(ctx, 6f), Ui.dp(ctx, 6f))
            flow.addView(tag(ctx, pal, "Tanques: ${r.tk}", sz))
            flow.addView(tag(ctx, pal, "Curas: ${r.hl}", sz))
            flow.addView(tag(ctx, pal, "CC: ${r.cc}", sz))
            flow.addView(tag(ctx, pal, "Assassinos: ${r.asn}", sz))
            val fp = Ui.lp(); fp.topMargin = Ui.dp(ctx, 8f)
            card.addView(flow, fp)
            addCard(ctx, parent, card, idx++, compact)
        }

        // ---- Potencial de gank
        if (g.isNotEmpty()) {
            val card = Ui.card(ctx, pal)
            val head = LinearLayout(ctx)
            head.gravity = Gravity.CENTER_VERTICAL
            head.addView(heading(ctx, pal, "Potencial de gank", sz))
            val tp = Ui.lp(); tp.leftMargin = Ui.dp(ctx, 8f)
            head.addView(tag(ctx, pal, if (Session.lane == 1) "sua selva" else "rotação", sz), tp)
            card.addView(head)
            for (x in g) {
                val lb = if (x.s >= 7) "Alto" else if (x.s >= 5) "Médio" else "Baixo"
                val row = LinearLayout(ctx)
                row.gravity = Gravity.CENTER_VERTICAL
                val n = Ui.text(ctx, x.l, 14f * sz, pal.tx, true)
                row.addView(n, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Ui.text(ctx, lb + " · " + Engine.fmt(x.s) + "/10", 13f * sz, pal.tx))
                val rp = Ui.lp(); rp.topMargin = Ui.dp(ctx, 10f)
                card.addView(row, rp)
                val bar = BarView(ctx, pal.bd, pal.gn, (x.s / 10.0).toFloat())
                val bp = Ui.lp(h = Ui.dp(ctx, 10f)); bp.topMargin = Ui.dp(ctx, 6f); bp.bottomMargin = Ui.dp(ctx, 4f)
                card.addView(bar, bp)
                card.addView(Ui.text(ctx, x.c.joinToString(" | ") { Engine.why(it) }, 12.5f * sz, pal.mu))
            }
            addCard(ctx, parent, card, idx++, compact)
        }

        // ---- Build sugerida
        run {
            val card = Ui.card(ctx, pal)
            val head = LinearLayout(ctx)
            head.gravity = Gravity.CENTER_VERTICAL
            head.addView(heading(ctx, pal, "Build sugerida", sz))
            val tp = Ui.lp(); tp.leftMargin = Ui.dp(ctx, 8f)
            head.addView(tag(ctx, pal, Data.ROLE[Session.cls] ?: "", sz), tp)
            card.addView(head)
            val isz = if (compact) 38f else 44f
            r.items.forEachIndexed { i, it ->
                val row = LinearLayout(ctx)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, Ui.dp(ctx, 8f), 0, Ui.dp(ctx, 8f))
                val num = Ui.text(ctx, (i + 1).toString(), 12f * sz, pal.ac, true)
                num.gravity = Gravity.CENTER
                row.addView(num, Ui.lp(Ui.dp(ctx, 18f), Ui.dp(ctx, 24f)))
                val ic = Icons.item(ctx, it.n, isz)
                val icp = Icons.lp(ctx, isz); icp.rightMargin = Ui.dp(ctx, 10f)
                row.addView(ic, icp)
                val col = LinearLayout(ctx)
                col.orientation = LinearLayout.VERTICAL
                col.addView(Ui.text(ctx, Engine.ptName(it.n), 15f * sz, pal.tx, true))
                if (Data.PT.containsKey(it.n)) {
                    val en = Ui.text(ctx, it.n, 11.5f * sz, pal.mu)
                    en.setTypeface(en.typeface, Typeface.ITALIC)
                    col.addView(en)
                }
                val why = if (it.t != 0) "Situacional: " + it.why else it.why
                col.addView(Ui.text(ctx, why, 12.5f * sz, if (it.t != 0) pal.gn else pal.mu))
                row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(row)
                if (i < r.items.size - 1) {
                    val d = View(ctx)
                    d.setBackgroundColor(pal.bd)
                    card.addView(d, Ui.lp(h = Ui.dp(ctx, 1f)))
                }
            }
            val fl = FlowLayout(ctx, Ui.dp(ctx, 6f), Ui.dp(ctx, 6f))
            fl.addView(tag(ctx, pal, Data.TIP[Session.cls] ?: "", sz))
            val fp = Ui.lp(); fp.topMargin = Ui.dp(ctx, 8f)
            card.addView(fl, fp)
            addCard(ctx, parent, card, idx++, compact)
        }

        // ---- Como jogar
        run {
            val card = Ui.card(ctx, pal)
            card.addView(heading(ctx, pal, "Como jogar esta partida", sz))
            val ts = Engine.tips(Session.lane, Session.cls, r, g, Session.en.toList())
            ts.forEachIndexed { i, s ->
                val tv = Ui.text(ctx, "", 14f * sz, pal.tx)
                tv.text = Html.fromHtml(s, Html.FROM_HTML_MODE_COMPACT)
                tv.setPadding(0, Ui.dp(ctx, 9f), 0, Ui.dp(ctx, 9f))
                card.addView(tv)
                if (i < ts.size - 1) {
                    val d = View(ctx)
                    d.setBackgroundColor(pal.bd)
                    card.addView(d, Ui.lp(h = Ui.dp(ctx, 1f)))
                }
            }
            addCard(ctx, parent, card, idx, compact)
        }
    }
}
