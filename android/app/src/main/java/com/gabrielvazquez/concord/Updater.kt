package com.gabrielvazquez.concord

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Atualização com um toque: confere a versão publicada no GitHub, baixa o APK dentro do app
 * e abre a confirmação do Android ("Atualizar"). Nada se perde.
 */
object Updater {
    private const val VERSION_URL = "https://github.com/gabrielvazquez/concord/releases/download/android-latest/version.json"
    private const val APK_URL = "https://github.com/gabrielvazquez/concord/releases/download/android-latest/CONCORD.apk"
    const val ACTION = "com.gabrielvazquez.concord.INSTALL_STATUS"

    var available by mutableStateOf<String?>(null)      // nome da versão nova
    var progress by mutableFloatStateOf(-1f)            // -1 = parado; 0..1 baixando; 2 = instalando
    var checking by mutableStateOf(false)
    private var lastCheck = 0L

    fun check(c: Concord, force: Boolean = false, done: ((Boolean) -> Unit)? = null) {
        if (!force && System.currentTimeMillis() - lastCheck < 30 * 60_000) return
        lastCheck = System.currentTimeMillis(); checking = true
        c.scope.launch {
            val newer = try {
                withContext(Dispatchers.IO) {
                    c.supa.http.newCall(Request.Builder().url("$VERSION_URL?t=${System.currentTimeMillis()}").build()).execute().use { r ->
                        if (!r.isSuccessful) null else JSONObject(r.body!!.string()).let { o -> if (o.optInt("versionCode") > BuildConfig.VERSION_CODE) o.optString("versionName") else null }
                    }
                }
            } catch (_: Exception) { null }
            available = newer; checking = false
            done?.invoke(newer != null)
        }
    }

    /** baixa e instala; se o Android ainda não deixou o CONCORD instalar atualizações, abre essa permissão primeiro */
    fun install(act: Activity, scope: CoroutineScope, say: (String) -> Unit) {
        if (progress >= 0f) return
        if (!act.packageManager.canRequestPackageInstalls()) {
            // algumas TVs não têm essa tela direta: tenta a do app e, se não der, a geral de segurança
            val tries = listOf(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")),
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), Intent(Settings.ACTION_SECURITY_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}")))
            val opened = tries.any { i -> try { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false } }
            say(if (opened) "Permita \"instalar apps desconhecidos\" para o CONCORD e aperte Atualizar de novo."
                else "Em Configurações › Apps › Acesso especial › Instalar apps desconhecidos, permita o CONCORD e aperte Atualizar de novo.")
            return
        }
        progress = 0f
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pi = act.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    params.setAppPackageName(act.packageName)
                    if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    val id = pi.createSession(params)
                    pi.openSession(id).use { s ->
                        Concord.I.supa.http.newCall(Request.Builder().url(APK_URL).build()).execute().use { r ->
                            if (!r.isSuccessful) throw IllegalStateException("download ${r.code}")
                            val body = r.body!!; val total = body.contentLength().coerceAtLeast(1)
                            s.openWrite("CONCORD.apk", 0, body.contentLength()).use { out ->
                                body.byteStream().use { inp ->
                                    val buf = ByteArray(64 * 1024); var n: Int; var got = 0L
                                    while (inp.read(buf).also { n = it } > 0) { out.write(buf, 0, n); got += n; progress = (got.toFloat() / total).coerceIn(0f, 1f) }
                                }
                                s.fsync(out)
                            }
                        }
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                        val pending = PendingIntent.getBroadcast(act, 7, Intent(act, InstallReceiver::class.java).setAction(ACTION), flags)
                        progress = 2f
                        s.commit(pending.intentSender)
                    }
                }
            } catch (e: Exception) {
                progress = -1f; say("Não foi possível atualizar agora. Tente de novo.")
            }
        }
    }
}

/** Resposta do instalador do Android: pede a confirmação da pessoa ou avisa se falhou */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.progress = -1f
            else -> { Updater.progress = -1f; if (Concord.isReady()) Concord.I.say("A atualização foi cancelada.") }
        }
    }
}
