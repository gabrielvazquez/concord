package com.gabrielvazquez.concord.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Cfg {
    const val URL = "https://tfzloojivxgeshjijfee.supabase.co"
    const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRmemxvb2ppdnhnZXNoamlqZmVlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEzODg5NzMsImV4cCI6MjEwNjk2NDk3M30._wNiXS3dhNaKBls2YJrzobnI5LJYB5ktbIt7eFbFr5k"
    const val SITE = "https://gabrielvazquez.github.io/concord/"
}

class ApiError(val status: Int, msg: String) : Exception(msg)

data class Session(val access: String, val refresh: String, val expiresAt: Long, val uid: String, val email: String)

/** Conta salva no aparelho (entrar com um toque) */
data class SavedAccount(
    val id: String, val email: String, val name: String, val color: String, val avatar: String?,
    val refresh: String, val last: Long,
)

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Cliente do Supabase feito à mão: Auth, banco (PostgREST), funções e arquivos. */
class Supa(private val ctx: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val prefs = ctx.getSharedPreferences("concord", Context.MODE_PRIVATE)
    private val json = "application/json".toMediaType()
    private val refreshLock = Mutex()

    @Volatile var session: Session? = null
        private set
    var onSession: ((Session?) -> Unit)? = null

    /* ---------- Contas salvas ---------- */
    fun accounts(): List<SavedAccount> {
        val a = JSONArray(prefs.getString("accounts", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            SavedAccount(it.getString("id"), it.optString("email"), it.optString("name"), it.optString("color", "#6366f1"),
                it.optString("avatar").ifEmpty { null }, it.optString("refresh"), it.optLong("last"))
        }.sortedByDescending { it.last }
    }

    private fun saveAccounts(list: List<SavedAccount>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("email", it.email).put("name", it.name).put("color", it.color)
                .put("avatar", it.avatar ?: "").put("refresh", it.refresh).put("last", it.last))
        }
        prefs.edit().putString("accounts", a.toString()).apply()
    }

    fun rememberAccount(name: String? = null, color: String? = null, avatar: String? = null) {
        val s = session ?: return
        val list = accounts().toMutableList()
        val old = list.find { it.id == s.uid }
        list.removeAll { it.id == s.uid }
        list.add(SavedAccount(s.uid, s.email, name ?: old?.name ?: s.email, color ?: old?.color ?: "#6366f1",
            avatar ?: old?.avatar, s.refresh, System.currentTimeMillis()))
        saveAccounts(list)
        prefs.edit().putString("active", s.uid).apply()
    }

    fun forgetAccount(id: String) { saveAccounts(accounts().filter { it.id != id }) }
    fun activeId(): String? = prefs.getString("active", null)
    fun clearActive() { prefs.edit().remove("active").apply(); session = null; onSession?.invoke(null) }

    /* ---------- Auth ---------- */
    private fun parseSession(o: JSONObject): Session {
        val user = o.getJSONObject("user")
        return Session(o.getString("access_token"), o.getString("refresh_token"),
            System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000, user.getString("id"), user.optString("email"))
    }

    private fun setSession(s: Session) {
        session = s
        val list = accounts().toMutableList()
        val i = list.indexOfFirst { it.id == s.uid }
        if (i >= 0) { list[i] = list[i].copy(refresh = s.refresh); saveAccounts(list) }
        onSession?.invoke(s)
    }

    suspend fun signIn(email: String, password: String): Session {
        val o = call("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), auth = false) as JSONObject
        return parseSession(o).also { setSession(it) }
    }

    suspend fun signUp(email: String, password: String, name: String, color: String): Session? {
        val body = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("app", "concord").put("display_name", name).put("color", color))
        val o = call("POST", "/auth/v1/signup?redirect_to=" + enc(Cfg.SITE), body, auth = false) as JSONObject
        if (!o.has("access_token")) return null
        return parseSession(o).also { setSession(it) }
    }

    suspend fun resume(refresh: String): Session = refreshLock.withLock {
        val o = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), auth = false) as JSONObject
        parseSession(o).also { setSession(it) }
    }

    suspend fun recover(email: String) {
        call("POST", "/auth/v1/recover?redirect_to=" + enc(Cfg.SITE), JSONObject().put("email", email), auth = false)
    }

    suspend fun token(): String {
        val s = session ?: throw ApiError(401, "Sem sessão")
        if (s.expiresAt - System.currentTimeMillis() > 90_000) return s.access
        return refreshLock.withLock {
            val cur = session!!
            if (cur.expiresAt - System.currentTimeMillis() > 90_000) cur.access
            else {
                val o = call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", cur.refresh), auth = false) as JSONObject
                parseSession(o).also { setSession(it) }.access
            }
        }
    }

    suspend fun signOutEverywhere() {
        try { call("POST", "/auth/v1/logout?scope=local", JSONObject()) } catch (_: Exception) {}
    }

    /* ---------- Banco ---------- */
    suspend fun select(table: String, query: String): JSONArray = call("GET", "/rest/v1/$table?$query") as JSONArray

    suspend fun insert(table: String, row: JSONObject): JSONObject {
        val r = call("POST", "/rest/v1/$table", row, prefer = "return=representation")
        return (r as? JSONArray)?.optJSONObject(0) ?: JSONObject()
    }

    suspend fun update(table: String, query: String, patch: JSONObject): JSONArray =
        (call("PATCH", "/rest/v1/$table?$query", patch, prefer = "return=representation") as? JSONArray) ?: JSONArray()

    suspend fun delete(table: String, query: String) { call("DELETE", "/rest/v1/$table?$query") }

    /** dados privados da conta (user_metadata), usados para o código da TV vinculada */
    suspend fun userMeta(): JSONObject = (call("GET", "/auth/v1/user") as? JSONObject)?.optJSONObject("user_metadata") ?: JSONObject()
    suspend fun setUserMeta(key: String, value: String) { call("PUT", "/auth/v1/user", JSONObject().put("data", JSONObject().put(key, value))) }
    suspend fun rpc(fn: String, args: JSONObject): Any? = call("POST", "/rest/v1/rpc/$fn", args)

    suspend fun upload(path: String, bytes: ByteArray, mime: String): String {
        val t = token()
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("${Cfg.URL}/storage/v1/object/concord/$path")
                .header("apikey", Cfg.KEY).header("Authorization", "Bearer $t").header("x-upsert", "true")
                .post(bytes.toRequestBody(mime.toMediaType())).build()
            http.newCall(req).execute().use { if (!it.isSuccessful) throw ApiError(it.code, it.body?.string() ?: "Falha no envio") }
        }
        return "${Cfg.URL}/storage/v1/object/public/concord/$path"
    }

    private suspend fun call(method: String, path: String, body: Any? = null, auth: Boolean = true, prefer: String? = null): Any? {
        val bearer = if (auth) token() else Cfg.KEY
        return withContext(Dispatchers.IO) {
            val b = Request.Builder().url(Cfg.URL + path).header("apikey", Cfg.KEY).header("Authorization", "Bearer $bearer")
            if (prefer != null) b.header("Prefer", prefer)
            val rb = body?.toString()?.toRequestBody(json)
            when (method) {
                "GET" -> b.get()
                "DELETE" -> b.delete(rb)
                else -> b.method(method, rb ?: "{}".toRequestBody(json))
            }
            http.newCall(b.build()).execute().use { res ->
                val txt = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = try { JSONObject(txt).let { it.optString("msg").ifEmpty { it.optString("message").ifEmpty { it.optString("error_description").ifEmpty { it.optString("error") } } } } } catch (_: Exception) { txt }
                    throw ApiError(res.code, msg.ifEmpty { "Erro ${res.code}" })
                }
                when {
                    txt.isBlank() -> null
                    txt.trimStart().startsWith("[") -> JSONArray(txt)
                    txt.trimStart().startsWith("{") -> JSONObject(txt)
                    else -> txt
                }
            }
        }
    }
}

fun authMessage(e: Throwable): String {
    val m = e.message ?: ""
    return when {
        m.contains("Invalid login", true) -> "Email ou senha incorretos."
        m.contains("Email not confirmed", true) -> "Confirme seu email antes de entrar (veja a caixa de entrada)."
        m.contains("already registered", true) -> "Já existe uma conta com esse email."
        m.contains("Password should", true) -> "A senha precisa ter pelo menos 6 caracteres."
        m.contains("rate limit", true) -> "Muitas tentativas. Espere um pouco e tente de novo."
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "Sem conexão com a internet."
        else -> m.ifEmpty { "Algo deu errado." }
    }
}
