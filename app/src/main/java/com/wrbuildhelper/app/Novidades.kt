package com.wrbuildhelper.app

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object Novidades {
    class Section(val id: String, val titulo: String, val itens: List<String>)

    fun parse(ctx: Context): List<Section> {
        val text = try {
            ctx.assets.open("novidades.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<Section>()
        var id: String? = null
        var titulo = ""
        var itens = mutableListOf<String>()
        fun flush() {
            val cur = id
            if (cur != null) out.add(Section(cur, titulo, itens))
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("#id ")) {
                flush()
                id = line.removePrefix("#id ").trim()
                titulo = ""
                itens = mutableListOf()
            } else if (line.startsWith("#titulo ")) {
                titulo = line.removePrefix("#titulo ").trim()
            } else if (line.startsWith("-")) {
                itens.add(line.removePrefix("-").trim())
            }
        }
        flush()
        return out
    }

    private fun body(s: Section): String = s.itens.joinToString("\n") { "• $it" }

    /** Mostra a seção mais nova na primeira vez que o app abre depois de atualizar. */
    fun showIfNew(ctx: Context) {
        val list = parse(ctx)
        val top = list.firstOrNull() ?: return
        val sp = ctx.getSharedPreferences("novidades", Context.MODE_PRIVATE)
        val seen = sp.getString("seen", null)
        sp.edit().putString("seen", top.id).apply()
        if (seen == null || seen == top.id) return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("O que mudou: " + top.titulo)
            .setMessage(body(top))
            .setPositiveButton("Legal!", null)
            .show()
    }

    fun showAll(ctx: Context) {
        val list = parse(ctx)
        val msg = if (list.isEmpty()) "Sem novidades registradas." else
            list.joinToString("\n\n") { it.titulo + "\n" + body(it) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("O que mudou")
            .setMessage(msg)
            .setPositiveButton("Fechar", null)
            .show()
    }
}
