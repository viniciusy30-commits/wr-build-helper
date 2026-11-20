package com.wrbuildhelper.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "viniciusy30-commits/wr-build-helper"
    private const val APK_NAME = "WRBuildHelper.apk"
    private const val SIX_HOURS = 6L * 60 * 60 * 1000

    private val main = Handler(Looper.getMainLooper())

    class Info(val tag: Long, val url: String)

    sealed class Result {
        object NoNet : Result()
        object Latest : Result()
        class Available(val info: Info) : Result()
        class Fail(val msg: String) : Result()
    }

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pi)
    }

    fun installedName(ctx: Context): String {
        return ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }

    private fun fetchLatest(ctx: Context, cb: (Result) -> Unit) {
        val have = installedCode(ctx)
        Thread {
            val res: Result = try {
                val con = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                con.connectTimeout = 10000
                con.readTimeout = 15000
                con.setRequestProperty("Accept", "application/vnd.github+json")
                con.setRequestProperty("User-Agent", "WRBuildHelper")
                val code = con.responseCode
                if (code == 404) {
                    Result.Latest
                } else if (code != 200) {
                    Result.Fail("GitHub respondeu $code")
                } else {
                    val body = con.inputStream.bufferedReader().use { it.readText() }
                    val j = JSONObject(body)
                    val tag = j.optString("tag_name", "").removePrefix("v").toLongOrNull() ?: 0L
                    var url = ""
                    val assets = j.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk")) {
                                url = a.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    if (tag > have && url.isNotEmpty()) Result.Available(Info(tag, url)) else Result.Latest
                }
            } catch (e: java.net.UnknownHostException) {
                Result.NoNet
            } catch (e: java.net.SocketTimeoutException) {
                Result.NoNet
            } catch (e: java.io.IOException) {
                Result.NoNet
            } catch (e: Exception) {
                Result.Fail(e.message ?: "erro desconhecido")
            }
            main.post { cb(res) }
        }.start()
    }

    /** Ao abrir o app: no máximo a cada 6 horas, avisa só se houver versão nova. */
    fun autoCheck(act: Activity) {
        val sp = act.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val last = sp.getLong("last", 0L)
        if (System.currentTimeMillis() - last < SIX_HOURS) return
        fetchLatest(act) { r ->
            if (act.isFinishing || act.isDestroyed) return@fetchLatest
            if (r is Result.Available) {
                sp.edit().putLong("last", System.currentTimeMillis()).apply()
                showAvailable(act, r.info)
            } else if (r is Result.Latest) {
                sp.edit().putLong("last", System.currentTimeMillis()).apply()
            }
        }
    }

    /** Botão "Verificar atualização". */
    fun manualCheck(act: Activity, status: (String) -> Unit) {
        status("Procurando atualização...")
        fetchLatest(act) { r ->
            if (act.isFinishing || act.isDestroyed) return@fetchLatest
            when (r) {
                is Result.NoNet -> status("Sem internet. Tente de novo mais tarde.")
                is Result.Latest -> status("Você já está na versão mais nova.")
                is Result.Fail -> status("Não deu para verificar agora (" + r.msg + ").")
                is Result.Available -> {
                    status("Nova versão encontrada: v" + r.info.tag)
                    showAvailable(act, r.info)
                }
            }
        }
    }

    private fun showAvailable(act: Activity, info: Info) {
        MaterialAlertDialogBuilder(act)
            .setTitle("Nova versão disponível")
            .setMessage("A versão v" + info.tag + " está pronta (você tem a v" + installedCode(act) + "). Deseja atualizar agora?")
            .setPositiveButton("Atualizar") { _, _ -> startUpdate(act, info) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun startUpdate(act: Activity, info: Info) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder(act)
                .setTitle("Permissão necessária")
                .setMessage("Para instalar a atualização, o Android precisa que você permita \"Instalar apps desconhecidos\" para o WR Build Helper. Toque em Abrir configuração, ative a opção e volte para o app.")
                .setPositiveButton("Abrir configuração") { _, _ ->
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))
                    act.startActivity(i)
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        download(act, info)
    }

    private fun download(act: Activity, info: Info) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(act, 24f)
        box.setPadding(pad, pad, pad, pad / 2)
        val label = TextView(act)
        label.text = "Baixando... 0%"
        label.gravity = Gravity.CENTER
        box.addView(label)
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.isIndeterminate = false
        val bp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        bp.topMargin = Ui.dp(act, 12f)
        box.addView(bar, bp)
        val dlg: AlertDialog = MaterialAlertDialogBuilder(act)
            .setTitle("Atualizando")
            .setView(box)
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            var err: String? = null
            var file: File? = null
            try {
                val dir = File(act.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, APK_NAME)
                val con = URL(info.url).openConnection() as HttpURLConnection
                con.connectTimeout = 15000
                con.readTimeout = 30000
                con.instanceFollowRedirects = true
                con.setRequestProperty("User-Agent", "WRBuildHelper")
                val total = con.contentLengthLong
                con.inputStream.use { input ->
                    f.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    main.post {
                                        bar.progress = pct
                                        label.text = "Baixando... $pct%"
                                    }
                                }
                            }
                        }
                    }
                }
                file = f
            } catch (e: Exception) {
                err = e.message ?: "falha no download"
            }
            val fin = file
            val er = err
            main.post {
                dlg.dismiss()
                if (fin != null && !act.isFinishing) {
                    install(act, fin)
                } else {
                    Toast.makeText(act, "Não consegui baixar a atualização (" + (er ?: "?") + ")", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(act: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(act, "Não consegui abrir o instalador: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
}
