package com.wrbuildhelper.app

import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Janela de busca de campeões. */
object Picker {
    private class VH(val row: LinearLayout, val name: TextView, val role: TextView) : RecyclerView.ViewHolder(row)

    private class Adapter(
        private val ctx: Context,
        private val pal: Palette,
        private val onPick: (Champ) -> Unit
    ) : RecyclerView.Adapter<VH>() {
        var items: List<Champ> = Engine.champs

        fun filter(q: String) {
            val f = q.trim().lowercase(Locale.ROOT)
            items = if (f.isEmpty()) Engine.champs else Engine.champs.filter { it.n.lowercase(Locale.ROOT).contains(f) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(ctx)
            row.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            val p = Ui.dp(ctx, 12f)
            row.setPadding(Ui.dp(ctx, 4f), p, Ui.dp(ctx, 4f), p)
            val n = Ui.text(ctx, "", 15f, pal.tx)
            val r = Ui.text(ctx, "", 12f, pal.mu)
            row.addView(n, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(r)
            return VH(row, n, r)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.name.text = c.n
            holder.role.text = Data.ROLE[c.k] ?: ""
            holder.row.setOnClickListener { onPick(c) }
        }

        override fun getItemCount(): Int = items.size
    }

    fun show(ctx: Context, onPick: (Champ) -> Unit) {
        val pal = Palette.of(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(ctx, 16f)
        box.setPadding(pad, pad / 2, pad, 0)
        val et = EditText(ctx)
        et.hint = "Buscar campeão..."
        et.setSingleLine(true)
        et.setTextColor(pal.tx)
        et.setHintTextColor(pal.mu)
        box.addView(et, Ui.lp())
        val rv = RecyclerView(ctx)
        rv.layoutManager = LinearLayoutManager(ctx)
        var dlgRef: androidx.appcompat.app.AlertDialog? = null
        val adapter = Adapter(ctx, pal) { c ->
            dlgRef?.dismiss()
            onPick(c)
        }
        rv.adapter = adapter
        box.addView(rv, Ui.lp(h = Ui.dp(ctx, 380f)))
        et.doAfterTextChanged { adapter.filter(it?.toString() ?: "") }
        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("Escolher campeão")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .create()
        dlgRef = dlg
        dlg.show()
    }
}
