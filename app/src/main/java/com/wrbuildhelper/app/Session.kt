package com.wrbuildhelper.app

import android.content.Context

/** Estado compartilhado entre o app e o pop-up flutuante. */
object Session {
    var lane: Int = 1
    var cls: Char = 'F'
    var me: Champ? = null
    val en: Array<Champ?> = arrayOfNulls(5)

    private var loaded = false
    private var prefsCtx: Context? = null
    private val listeners = mutableListOf<() -> Unit>()

    fun load(ctx: Context) {
        prefsCtx = ctx.applicationContext
        Builds.init(ctx)
        if (loaded) return
        loaded = true
        val sp = ctx.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
        lane = sp.getInt("lane", 1).coerceIn(0, 4)
        cls = (sp.getString("cls", "F") ?: "F").firstOrNull() ?: 'F'
        me = Engine.find(sp.getString("me", "") ?: "")
        val parts = (sp.getString("en", "") ?: "").split("|")
        for (i in 0 until 5) {
            en[i] = parts.getOrNull(i)?.let { Engine.find(it) }
        }
    }

    private fun save() {
        val c = prefsCtx ?: return
        c.getSharedPreferences("session", Context.MODE_PRIVATE).edit()
            .putInt("lane", lane)
            .putString("cls", cls.toString())
            .putString("me", me?.n ?: "")
            .putString("en", en.joinToString("|") { it?.n ?: "" })
            .apply()
    }

    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun refresh() {
        for (l in listeners.toList()) l()
    }

    private fun changed() {
        save()
        for (l in listeners.toList()) l()
    }

    fun chooseLane(i: Int) {
        lane = i
        if (me == null) cls = Data.DEFAULT_CLASS[i]
        changed()
    }

    fun setClass(k: Char) {
        cls = k
        me = null
        changed()
    }

    fun chooseMe(c: Champ) {
        me = c
        cls = c.k
        changed()
    }

    fun setEnemy(i: Int, c: Champ?) {
        en[i] = c
        changed()
    }

    fun setEnemies(arr: Array<Champ?>) {
        for (i in 0 until 5) en[i] = arr.getOrNull(i)
        changed()
    }

    fun clearEnemies() {
        for (i in 0 until 5) en[i] = null
        changed()
    }
}
