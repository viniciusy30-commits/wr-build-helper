package com.wrbuildhelper.app

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Grade de campeões com ícones (estilo tela de seleção do Wild Rift). */
object Picker {

    private fun cell(ctx: Context, pal: Palette, c: Champ, sizeDp: Float, onPick: (Champ) -> Unit): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.addView(Icons.champ(ctx, c.n, sizeDp), Icons.lp(ctx, sizeDp))
        val t: TextView = Ui.text(ctx, c.n, 10f, pal.tx)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setSingleLine(true)
        t.ellipsize = TextUtils.TruncateAt.END
        box.addView(t, LinearLayout.LayoutParams(Ui.dp(ctx, sizeDp + 4f), LinearLayout.LayoutParams.WRAP_CONTENT))
        box.setOnClickListener { onPick(c) }
        Ui.pressable(box)
        return box
    }

    /**
     * Monta a grade. scrollMaxPx > 0: a grade tem rolagem própria (diálogo do app).
     * scrollMaxPx = 0: sem rolagem própria (o pop-up já rola por fora).
     */
    fun build(
        ctx: Context, pal: Palette, cellDp: Float, withSearch: Boolean,
        scrollMaxPx: Int, onPick: (Champ) -> Unit
    ): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        var filter: Char? = null
        var query = ""
        val grid = FlowLayout(ctx, Ui.dp(ctx, 6f), Ui.dp(ctx, 8f))
        val chips = FlowLayout(ctx, Ui.dp(ctx, 5f), Ui.dp(ctx, 5f))

        fun rebuildGrid() {
            grid.removeAllViews()
            val q = query.trim().lowercase(Locale.ROOT)
            for (c in Engine.champs) {
                if (filter != null && c.k != filter) continue
                if (q.isNotEmpty() && !c.n.lowercase(Locale.ROOT).contains(q)) continue
                grid.addView(cell(ctx, pal, c, cellDp, onPick))
            }
        }

        fun rebuildChips() {
            chips.removeAllViews()
            chips.addView(Ui.chip(ctx, pal, "Todos", filter == null, true) {
                filter = null
                rebuildChips()
                rebuildGrid()
            })
            for ((k, label) in Data.ROLE) {
                chips.addView(Ui.chip(ctx, pal, label, filter == k, true) {
                    filter = k
                    rebuildChips()
                    rebuildGrid()
                })
            }
        }

        if (withSearch) {
            val et = EditText(ctx)
            et.hint = "Buscar campeão..."
            et.setSingleLine(true)
            et.setTextColor(pal.tx)
            et.setHintTextColor(pal.mu)
            et.textSize = 14f
            val p = Ui.dp(ctx, 10f)
            et.setPadding(p, p, p, p)
            et.background = Ui.rounded(pal.card, Ui.dp(ctx, 4f).toFloat(), pal.bd, Ui.dp(ctx, 1f))
            et.doAfterTextChanged {
                query = it?.toString() ?: ""
                rebuildGrid()
            }
            box.addView(et, Ui.lp())
        }
        val cp = Ui.lp()
        cp.topMargin = Ui.dp(ctx, 8f)
        cp.bottomMargin = Ui.dp(ctx, 8f)
        box.addView(chips, cp)
        rebuildChips()
        rebuildGrid()
        if (scrollMaxPx > 0) {
            val sc = MaxHeightScroll(ctx, scrollMaxPx)
            sc.addView(grid, Ui.lp())
            box.addView(sc, Ui.lp())
        } else {
            box.addView(grid, Ui.lp())
        }
        return box
    }

    fun show(ctx: Context, onPick: (Champ) -> Unit) {
        val pal = Palette.of(ctx)
        var dlgRef: androidx.appcompat.app.AlertDialog? = null
        val h = (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val box = build(ctx, pal, 56f, true, h) { c ->
            dlgRef?.dismiss()
            onPick(c)
        }
        val pad = Ui.dp(ctx, 16f)
        box.setPadding(pad, Ui.dp(ctx, 8f), pad, 0)
        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("Escolher campeão")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .create()
        dlgRef = dlg
        dlg.show()
    }
}
