package com.gabrielvazquez.concord

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gabrielvazquez.concord.net.PgSub
import com.gabrielvazquez.concord.net.Realtime
import com.gabrielvazquez.concord.net.RtChannel
import com.gabrielvazquez.concord.net.SavedAccount
import com.gabrielvazquez.concord.net.Supa
import com.gabrielvazquez.concord.net.authMessage
import com.gabrielvazquez.concord.net.enc
import com.gabrielvazquez.concord.voice.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

sealed class Screen {
    object Loading : Screen()
    object Accounts : Screen()
    data class Login(val signup: Boolean = false, val email: String = "", val msg: String? = null) : Screen()
    object NewProfile : Screen()
    object Main : Screen()
}

sealed class View {
    data class Text(val ch: Channel) : View()
    data class Voice(val ch: Channel) : View()
    data class DmChat(val dm: Dm) : View()
    object Home : View()
}

data class IncomingCall(val dm: Dm, val from: String)

/** Estado e regras do app (equivalente ao "S" do site). Tudo é observável pelo Compose. */
class Concord(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val supa = Supa(app)
    val rt = Realtime(supa, scope)
    val sid: String = UUID.randomUUID().toString()
    val voice = Voice(this)
    val since = System.currentTimeMillis()

    var screen by mutableStateOf<Screen>(Screen.Loading)
    var meId by mutableStateOf<String?>(null)
    var isAdmin by mutableStateOf(false)
    val admins = mutableStateListOf<String>()
    val profiles = mutableStateMapOf<String, Profile>()
    val nicks = mutableStateMapOf<String, String>()
    val servers = mutableStateListOf<Server>()
    var server by mutableStateOf<Server?>(null)
    val channels = mutableStateListOf<Channel>()
    val members = mutableStateListOf<String>()
    val online = mutableStateListOf<Online>()
    var view by mutableStateOf<View?>(null)
    var home by mutableStateOf(false)
    val messages = mutableStateListOf<Msg>()
    var loadingMsgs by mutableStateOf(false)
    val unread = mutableStateListOf<String>()
    val dms = mutableStateListOf<Dm>()
    val typing = mutableStateMapOf<String, Pair<String, Long>>()   // sid -> (nome, até)
    var toast by mutableStateOf<String?>(null)
    var incoming by mutableStateOf<IncomingCall?>(null)
    var autoIdle by mutableStateOf(false)
    var connected by mutableStateOf(true)
    /** pede a permissão do microfone antes de entrar numa sala (definido pela tela) */
    var askMic: ((() -> Unit) -> Unit)? = null

    private var srvRt: RtChannel? = null
    private var msgRt: RtChannel? = null
    private var dmRt: RtChannel? = null
    private var userRt: RtChannel? = null
    var visible = true

    val me: Profile? get() = meId?.let { profiles[it] }

    init {
        supa.onSession = { }
        rt.onStatus = { connected = it }
    }

    fun say(s: String) { toast = s }

    /* ===================== Entrada ===================== */
    fun boot() {
        val acc = supa.accounts()
        val active = supa.activeId()?.let { id -> acc.find { it.id == id } }
        if (active != null) scope.launch {
            try { supa.resume(active.refresh); afterLogin() } catch (e: Exception) {
                if (e is java.io.IOException) { screen = Screen.Accounts; say("Sem conexão. Tente de novo.") } else screen = Screen.Accounts
            }
        } else screen = if (acc.isNotEmpty()) Screen.Accounts else Screen.Login()
    }

    fun accounts(): List<SavedAccount> = supa.accounts()

    fun enterAccount(a: SavedAccount, onFail: (String) -> Unit) = scope.launch {
        try { supa.resume(a.refresh); afterLogin() } catch (e: Exception) {
            if (e is java.io.IOException) onFail("Sem conexão com a internet.") else screen = Screen.Login(email = a.email, msg = "Sessão expirada. Digite sua senha.")
        }
    }

    fun forget(a: SavedAccount) { supa.forgetAccount(a.id); if (supa.accounts().isEmpty()) screen = Screen.Login() else screen = Screen.Accounts }

    fun signIn(email: String, pass: String, onErr: (String) -> Unit) = scope.launch {
        try { supa.signIn(email.trim(), pass); afterLogin() } catch (e: Exception) { onErr(authMessage(e)) }
    }

    fun signUp(name: String, email: String, pass: String, color: String, onErr: (String) -> Unit, onConfirm: () -> Unit) = scope.launch {
        try { val s = supa.signUp(email.trim(), pass, name.trim().take(24), color); if (s == null) onConfirm() else afterLogin() } catch (e: Exception) { onErr(authMessage(e)) }
    }

    fun recover(email: String, done: (String) -> Unit) = scope.launch {
        try { supa.recover(email.trim()); done("Se existir conta com esse email, o link chega em instantes.") } catch (e: Exception) { done(authMessage(e)) }
    }

    fun createProfile(name: String, color: String, onErr: (String) -> Unit) = scope.launch {
        try { supa.insert("dc_profiles", JSONObject().put("id", supa.session!!.uid).put("display_name", name.trim().take(24)).put("color", color)); afterLogin() } catch (e: Exception) { onErr(authMessage(e)) }
    }

    private suspend fun afterLogin() {
        val s = supa.session ?: return
        val rows = supa.select("dc_profiles", "select=*&id=eq.${s.uid}")
        if (rows.length() == 0) { screen = Screen.NewProfile; return }
        val p = Profile.from(rows.getJSONObject(0))
        profiles[p.id] = p
        meId = p.id
        supa.rememberAccount(p.name, p.color, p.avatar)
        try {
            val a = supa.select("dc_admins", "select=user_id")
            admins.clear(); (0 until a.length()).forEach { admins.add(a.getJSONObject(it).getString("user_id")) }
            isAdmin = admins.contains(p.id)
        } catch (_: Exception) {}
        screen = Screen.Main
        rt.connect()
        startPersonalChannels()
        loadTvLink()
        presenceJob?.cancel(); presenceJob = scope.launch { while (true) { delay(30_000); if (meId != null) trackServer() } }
        loadServers()
        loadDms()
    }

    fun logout() = scope.launch {
        presenceJob?.cancel(); presenceJob = null
        voice.leave()
        rt.disconnect()
        srvRt = null; msgRt = null; dmRt = null; userRt = null; tvLinkRt = null; tvLink = null
        servers.clear(); channels.clear(); messages.clear(); dms.clear(); online.clear(); members.clear(); unread.clear()
        server = null; view = null; meId = null; home = false
        supa.clearActive()
        screen = Screen.Accounts
    }

    /* ===================== Servidores ===================== */
    suspend fun loadServers(selectId: String? = null) {
        val me = meId ?: return
        try {
            val mem = supa.select("dc_members", "select=server_id,joined_at&user_id=eq.$me&order=joined_at")
            val ids = (0 until mem.length()).map { mem.getJSONObject(it).getString("server_id") }
            servers.clear()
            if (ids.isNotEmpty()) {
                val data = supa.select("dc_servers", "select=*&id=in.(${ids.joinToString(",")})")
                val list = (0 until data.length()).map { Server.from(data.getJSONObject(it)) }
                ids.mapNotNull { id -> list.find { it.id == id } }.forEach { servers.add(it) }
            }
            val last = app.getSharedPreferences("concord", 0).getString("last_server", null)
            val pick = servers.find { it.id == selectId } ?: servers.find { it.id == last } ?: servers.firstOrNull()
            if (pick != null) selectServer(pick) else { server = null; openHome() }
        } catch (e: Exception) { say("Erro ao carregar servidores: ${authMessage(e)}") }
    }

    fun joinByCode(code: String, done: (Boolean) -> Unit = {}) = scope.launch {
        try {
            val r = supa.rpc("dc_join", JSONObject().put("p_code", code.trim().uppercase().removePrefix("#")))
            val o = r as? JSONObject
            if (o == null || !o.has("id") || o.isNull("id")) { say("Código não encontrado ou convites fechados."); done(false); return@launch }
            loadServers(o.getString("id")); done(true)
        } catch (e: Exception) { say("Erro: ${authMessage(e)}"); done(false) }
    }

    fun selectServer(s: Server) {
        if (server?.id == s.id && !home) return
        home = false
        if (server?.id == s.id) { val f = channels.firstOrNull { !it.isVoice }; if (f != null) openText(f); return }
        srvRt?.leave(); msgRt?.leave(); srvRt = null; msgRt = null
        server = s; channels.clear(); messages.clear(); online.clear(); members.clear(); nicks.clear()
        app.getSharedPreferences("concord", 0).edit().putString("last_server", s.id).apply()
        scope.launch {
            loadChannels(); loadMembers()
            val ch = rt.channel("dc-srv-${s.id}", sid, listOf(
                PgSub("dc_channels", filter = "server_id=eq.${s.id}"), PgSub("dc_members", filter = "server_id=eq.${s.id}"),
                PgSub("dc_servers", "UPDATE", "id=eq.${s.id}"), PgSub("dc_profiles", "UPDATE")))
            srvRt = ch
            ch.onPresence = { rebuildOnline() }
            ch.onJoined = { trackServer() }
            ch.on("typing") { p -> val n = p.optString("name"); typing[p.optString("sid")] = n to (System.currentTimeMillis() + 3500); scope.launch { delay(3600); cleanTyping() } ; lastTypingCh = p.optString("ch") }
            ch.on("profile") { p -> p.optString("id").takeIf { it.isNotEmpty() }?.let { id -> scope.launch { refreshProfile(id) } } }
            ch.onChange = { table, type, rec, _ ->
                when (table) {
                    "dc_channels" -> scope.launch { loadChannels() }
                    "dc_members" -> scope.launch { loadMembers() }
                    "dc_servers" -> { val i = servers.indexOfFirst { it.id == rec.optString("id") }; if (i >= 0 && type == "UPDATE") { val ns = Server.from(rec); servers[i] = ns; if (server?.id == ns.id) server = ns } }
                    "dc_profiles" -> if (rec.has("id") && profiles.containsKey(rec.optString("id"))) profiles[rec.getString("id")] = Profile.from(rec)
                }
            }
            val first = channels.firstOrNull { !it.isVoice }
            if (first != null) openText(first) else view = null
        }
    }

    var lastTypingCh by mutableStateOf<String?>(null)
    private fun cleanTyping() { val now = System.currentTimeMillis(); typing.keys.toList().forEach { if ((typing[it]?.second ?: 0) < now) typing.remove(it) } }

    private suspend fun refreshProfile(id: String) {
        try { val r = supa.select("dc_profiles", "select=*&id=eq.$id"); if (r.length() > 0) profiles[id] = Profile.from(r.getJSONObject(0)) } catch (_: Exception) {}
    }

    suspend fun loadChannels() {
        val s = server ?: return
        try {
            val r = supa.select("dc_channels", "select=*&server_id=eq.${s.id}&order=position,created_at")
            val list = (0 until r.length()).map { Channel.from(r.getJSONObject(it)) }
            channels.clear(); channels.addAll(list)
            subscribeMessages()
        } catch (e: Exception) { say("Erro ao carregar canais: ${authMessage(e)}") }
    }

    suspend fun loadMembers() {
        val s = server ?: return
        try {
            val r = supa.select("dc_members", "select=user_id,nickname,joined_at&server_id=eq.${s.id}")
            val ids = (0 until r.length()).map { r.getJSONObject(it) }
            members.clear(); members.addAll(ids.map { it.getString("user_id") })
            nicks.clear(); ids.forEach { o -> if (!o.isNull("nickname") && o.optString("nickname").isNotEmpty()) nicks[o.getString("user_id")] = o.getString("nickname") }
            loadProfiles(members.toList())
            if (meId != null && !members.contains(meId)) { say("Você saiu de ${s.name}."); servers.removeAll { it.id == s.id }; server = null; servers.firstOrNull()?.let { selectServer(it) } ?: openHome() }
        } catch (_: Exception) {}
    }

    suspend fun loadProfiles(ids: List<String>) {
        val miss = ids.filter { it.isNotEmpty() }.distinct()
        if (miss.isEmpty()) return
        try {
            val p = supa.select("dc_profiles", "select=*&id=in.(${miss.joinToString(",")})")
            (0 until p.length()).forEach { val pr = Profile.from(p.getJSONObject(it)); profiles[pr.id] = pr }
        } catch (_: Exception) {}
    }

    private fun subscribeMessages() {
        msgRt?.leave(); msgRt = null
        val s = server ?: return
        val ids = channels.filter { !it.isVoice }.map { it.id }
        if (ids.isEmpty()) return
        val ch = rt.channel("dc-msgs-${s.id}-${System.currentTimeMillis()}", null, listOf(
            PgSub("dc_messages", "INSERT", "channel_id=in.(${ids.joinToString(",")})"), PgSub("dc_messages", "DELETE")))
        msgRt = ch
        ch.onChange = { _, type, rec, old ->
            if (type == "INSERT") onMessage(Msg.from(rec), rec.optString("channel_id"))
            else if (type == "DELETE") { val id = old.optLong("id"); messages.removeAll { it.id == id } }
        }
    }

    private fun onMessage(m: Msg, chId: String) {
        val v = view
        if (v is View.Text && v.ch.id == chId) { if (messages.none { it.id == m.id }) messages.add(m) }
        else if (!unread.contains(chId)) unread.add(chId)
        if (m.userId != meId && myStatus() != "dnd" && (!visible || !(v is View.Text && v.ch.id == chId))) {
            val ch = channels.find { it.id == chId } ?: return
            Notifier.message(app, "${name(m.userId, m.author)} em #${ch.name}", m.content, "ch-$chId", m.content.contains("@" + (me?.name ?: "\u0000"), true))
        }
    }

    fun trackServer() {
        val ch = srvRt ?: return
        val st = myStatus()
        val o = JSONObject().put("uid", meId).put("name", name(meId)).put("color", me?.color ?: "#6366f1")
            .put("voice", voice.channel?.id ?: JSONObject.NULL).put("muted", voice.muted).put("deaf", voice.deaf)
            .put("cam", voice.camOn).put("screen", voice.screenOn).put("since", since).put("inv", st == "invisible").put("st", st)
            .put("act", JSONObject.NULL).put("ts", System.currentTimeMillis()).put("mobile", true)
        ch.track(o)
    }

    private fun rebuildOnline() {
        val ch = srvRt ?: return
        val out = LinkedHashMap<String, Online>()
        ch.presence.forEach { (k, metas) ->
            val best = metas.maxByOrNull { it.optLong("ts") } ?: return@forEach
            val o = Online.from(k, best)
            if (o.inv && o.uid != meId && o.voice == null) return@forEach
            val ex = out[o.uid]
            if (ex == null || (o.voice != null && ex.voice == null) || (o.ts > ex.ts && (ex.voice == null) == (o.voice == null))) out[o.uid] = o
        }
        online.clear(); online.addAll(out.values)
        voice.onServerPresence()
    }

    /** quem está na sala: presença do servidor + a própria sala de voz (quando estou nela), para ninguém sumir da lista */
    fun voiceUsers(chId: String): List<Online> {
        val list = online.filter { it.voice == chId }.toMutableList()
        if (voice.channel?.id == chId && !voice.viewer) {
            val have = list.map { it.uid }.toMutableSet()
            voice.meta.forEach { (k, m) ->
                val u = m.optString("uid"); if (m.optBoolean("tv") || u.isEmpty() || !have.add(u)) return@forEach
                list.add(Online(k, u, m.optString("name"), m.optString("color", "#6366f1"), chId, m.optBoolean("muted"), m.optBoolean("deaf"),
                    m.optString("camId").let { it.isNotEmpty() && it != "null" }, m.optString("screenId").let { it.isNotEmpty() && it != "null" }, "online", null, 0L, 0L, false))
            }
        }
        return list
    }
    private var presenceJob: kotlinx.coroutines.Job? = null

    fun statusOf(uid: String?): String {
        if (uid == null) return "offline"
        if (uid == meId) return myStatus()
        val o = online.find { it.uid == uid } ?: return "offline"
        return if (o.st == "invisible") "offline" else o.st
    }

    fun myStatus(): String {
        val ps = me?.presence ?: "online"
        if (ps == "invisible") return "invisible"
        if (ps == "online" && autoIdle) return "idle"
        return ps
    }

    fun setStatus(st: String) = scope.launch {
        val p = me ?: return@launch
        profiles[p.id] = p.copy(presence = st); autoIdle = false; trackServer()
        try { supa.update("dc_profiles", "id=eq.${p.id}", JSONObject().put("presence_status", st)) } catch (e: Exception) { say(authMessage(e)) }
    }

    fun name(uid: String?, fb: String? = null): String = uid?.let { nicks[it] ?: profiles[it]?.name } ?: fb ?: "…"

    /* ===================== Chat ===================== */
    fun openText(ch: Channel) {
        view = View.Text(ch); unread.remove(ch.id); messages.clear(); loadingMsgs = true
        scope.launch {
            try {
                val r = supa.select("dc_messages", "select=*&channel_id=eq.${ch.id}&order=created_at.desc&limit=80")
                val list = (0 until r.length()).map { Msg.from(r.getJSONObject(it)) }.reversed()
                if ((view as? View.Text)?.ch?.id == ch.id) { messages.clear(); messages.addAll(list) }
                loadProfiles(list.mapNotNull { it.userId }.filter { !profiles.containsKey(it) })
            } catch (e: Exception) { say("Erro ao carregar mensagens: ${authMessage(e)}") }
            loadingMsgs = false
        }
    }

    private var lastSent = HashMap<String, Long>()
    private var typingSent = 0L

    fun typingNow() {
        val v = view as? View.Text ?: return
        if (System.currentTimeMillis() - typingSent < 2500) return
        typingSent = System.currentTimeMillis()
        srvRt?.send("typing", JSONObject().put("ch", v.ch.id).put("name", name(meId)).put("sid", sid))
    }

    fun send(text: String, onFail: (String) -> Unit) {
        val content = text.trim().take(2000); if (content.isEmpty()) return
        when (val v = view) {
            is View.Text -> {
                val ch = v.ch
                if (ch.readonly && !isAdmin) { say("Canal somente leitura."); onFail(content); return }
                if (ch.slowmode > 0 && !isAdmin) {
                    val wait = ch.slowmode * 1000 - (System.currentTimeMillis() - (lastSent[ch.id] ?: 0))
                    if (wait > 0) { say("Modo lento: aguarde ${(wait + 999) / 1000}s."); onFail(content); return }
                    lastSent[ch.id] = System.currentTimeMillis()
                }
                scope.launch {
                    try {
                        val p = me
                        val row = supa.insert("dc_messages", JSONObject().put("channel_id", ch.id).put("user_id", meId).put("author", p?.name ?: "").put("author_color", p?.color ?: "#6366f1").put("content", content))
                        val m = Msg.from(row); if ((view as? View.Text)?.ch?.id == ch.id && messages.none { it.id == m.id }) messages.add(m)
                    } catch (e: Exception) { say("Não foi possível enviar: ${authMessage(e)}"); onFail(content) }
                }
            }
            is View.DmChat -> scope.launch {
                try {
                    val row = supa.insert("dc_dm_messages", JSONObject().put("dm_id", v.dm.id).put("user_id", meId).put("content", content))
                    val m = Msg.from(row); if ((view as? View.DmChat)?.dm?.id == v.dm.id && messages.none { it.id == m.id }) messages.add(m)
                    bumpDm(v.dm.id, m.at)
                } catch (e: Exception) { say("Não foi possível enviar: ${authMessage(e)}"); onFail(content) }
            }
            else -> {}
        }
    }

    fun deleteMessage(m: Msg) = scope.launch {
        try { supa.delete("dc_messages", "id=eq.${m.id}"); messages.removeAll { it.id == m.id } } catch (e: Exception) { say(authMessage(e)) }
    }

    /* ===================== Mensagens diretas e chamadas ===================== */
    private fun startPersonalChannels() {
        val me = meId ?: return
        dmRt = rt.channel("dc-dmrt-$me-${System.currentTimeMillis()}", null, listOf(PgSub("dc_dm_messages", "INSERT"), PgSub("dc_dms")))
        dmRt!!.onChange = { table, _, rec, _ ->
            if (table == "dc_dm_messages") onDmMessage(Msg.from(rec), rec.optString("dm_id")) else scope.launch { loadDms() }
        }
        userRt = rt.channel("dc-user-$me", null)
        userRt!!.on("call") { p -> onCallSignal(p) }
    }

    suspend fun loadDms() {
        val me = meId ?: return
        try {
            val r = supa.select("dc_dms", "select=*&or=(user_a.eq.$me,user_b.eq.$me)&order=last_at.desc")
            val list = (0 until r.length()).map { Dm.from(r.getJSONObject(it)) }
            dms.clear(); dms.addAll(list)
            loadProfiles(list.map { it.other(me) }.filter { !profiles.containsKey(it) })
        } catch (_: Exception) {}
    }

    private fun bumpDm(id: String, at: Long) {
        val i = dms.indexOfFirst { it.id == id }; if (i < 0) return
        val d = dms.removeAt(i).copy(lastAt = at); dms.add(0, d)
    }

    private fun onDmMessage(m: Msg, dmId: String) {
        val d = dms.find { it.id == dmId }
        if (d == null) { scope.launch { loadDms(); if (dms.any { it.id == dmId }) onDmMessage(m, dmId) }; return }
        bumpDm(dmId, m.at)
        val v = view
        if (v is View.DmChat && v.dm.id == dmId) { if (messages.none { it.id == m.id }) messages.add(m) }
        else if (m.userId != meId && !unread.contains(dmId)) unread.add(dmId)
        if (m.userId != meId && myStatus() != "dnd" && (!visible || !(v is View.DmChat && v.dm.id == dmId)))
            Notifier.message(app, name(m.userId), m.content, "dm-$dmId", true)
    }

    fun openHome() { home = true; if (view !is View.DmChat) view = View.Home }

    fun openDm(d: Dm) {
        home = true; view = View.DmChat(d); unread.remove(d.id); messages.clear(); loadingMsgs = true
        Notifier.cancel(app, "dm-${d.id}")
        scope.launch {
            try {
                val r = supa.select("dc_dm_messages", "select=*&dm_id=eq.${d.id}&order=created_at.desc&limit=80")
                val list = (0 until r.length()).map { Msg.from(r.getJSONObject(it)) }.reversed()
                if ((view as? View.DmChat)?.dm?.id == d.id) { messages.clear(); messages.addAll(list) }
            } catch (e: Exception) { say(authMessage(e)) }
            loadingMsgs = false
        }
    }

    fun openDmWith(uid: String, call: Boolean = false) = scope.launch {
        var d = dms.find { it.other(meId!!) == uid }
        if (d == null) {
            try {
                val r = supa.rpc("dc_open_dm", JSONObject().put("p_other", uid))
                val o = (r as? JSONObject) ?: (r as? org.json.JSONArray)?.optJSONObject(0)
                if (o != null) { d = Dm.from(o); if (dms.none { it.id == d.id }) dms.add(0, d) }
            } catch (e: Exception) { say("Não foi possível abrir a conversa: ${authMessage(e)}"); return@launch }
        }
        val dm = d ?: return@launch
        openDm(dm)
        if (call) startCall(dm)
    }

    fun dmChannel(d: Dm) = Channel("dm-${d.id}", "", name(d.other(meId!!)), "voice", 0, null, null, null, false, 0, 0, dmId = d.id)

    private fun sendToUser(uid: String, payload: JSONObject) {
        if (uid == meId) { userRt?.send("call", payload); return }
        val ch = rt.channel("dc-user-$uid", null)
        ch.onJoined = { ch.send("call", payload); scope.launch { delay(1500); ch.leave() } }
    }

    fun startCall(d: Dm) {
        val other = d.other(meId!!)
        if (voice.channel?.dmId == d.id) { view = View.Voice(voice.channel!!); return }
        voice.join(dmChannel(d))
        view = View.Voice(dmChannel(d))
        sendToUser(other, JSONObject().put("t", "ring").put("dm", d.id).put("from", meId).put("name", name(meId)))
        say("Chamando ${name(other)}…")
        scope.launch {
            delay(35_000)
            if (voice.channel?.dmId == d.id && voice.peerCount() == 0) {
                sendToUser(other, JSONObject().put("t", "cancel").put("dm", d.id).put("from", meId))
                say("${name(other)} não atendeu."); voice.leave()
            }
        }
    }

    private fun onCallSignal(p: JSONObject) {
        val t = p.optString("t"); val dmId = p.optString("dm"); val from = p.optString("from")
        if (t == "answered") { if (p.optString("sid") != sid && incoming?.dm?.id == dmId) { incoming = null; Notifier.cancelCall(app) }; return }
        if (from == meId || dmId.isEmpty()) return
        scope.launch {
            var d = dms.find { it.id == dmId }
            if (d == null) { loadDms(); d = dms.find { it.id == dmId } }
            val dm = d ?: return@launch
            if (dm.other(meId!!) != from) return@launch
            when (t) {
                "ring" -> if (voice.channel?.dmId != dm.id) {
                    loadProfiles(listOf(from))
                    incoming = IncomingCall(dm, from)
                    if (myStatus() != "dnd") Notifier.incomingCall(app, name(from), dm.id)
                    delay(40_000); if (incoming?.dm?.id == dm.id) { incoming = null; Notifier.cancelCall(app) }
                }
                "cancel" -> if (incoming?.dm?.id == dm.id) { incoming = null; Notifier.cancelCall(app); say("Chamada perdida de ${name(from)}.") }
                "decline" -> if (voice.channel?.dmId == dm.id && voice.peerCount() == 0) { say("${name(from)} recusou a chamada."); voice.leave() }
            }
        }
    }

    fun answer(accept: Boolean) {
        val c = incoming ?: return
        incoming = null; Notifier.cancelCall(app)
        sendToUser(meId!!, JSONObject().put("t", "answered").put("dm", c.dm.id).put("sid", sid))
        if (accept) { openDm(c.dm); voice.join(dmChannel(c.dm)); view = View.Voice(dmChannel(c.dm)) }
        else sendToUser(c.from, JSONObject().put("t", "decline").put("dm", c.dm.id).put("from", meId))
    }

    /* ===================== TV ===================== */
    fun tvsInRoom() = voice.meta.values.count { it.optBoolean("tv") && it.optString("uid") == meId }

    fun pairTv(code: String, unpair: Boolean = false, done: (Boolean) -> Unit) {
        val ch = voice.channel
        if (ch == null && !unpair) { done(false); return }
        val r = rt.channel("dc-tv-$code", null)
        var fin = false
        val finish = { ok: Boolean -> if (!fin) { fin = true; done(ok); scope.launch { delay(500); r.leave() } } }
        r.on("paired") { finish(true) }
        r.onJoined = {
            if (unpair) { r.send("unpair", JSONObject()); finish(true) }
            else r.send("pair", JSONObject().put("room", ch!!.id).put("roomName", ch.name).put("uid", meId).put("name", name(meId)))
        }
        app.getSharedPreferences("concord", 0).edit().putString("tv_code", code).apply()
        scope.launch { delay(7000); finish(false) }
    }
    /** QR da TV lido fora de uma sala: conecta assim que a pessoa entrar numa sala de voz */
    var pendingTv: Pair<String, Long>? = null
    fun tvFromQr(code: String) {
        if (voice.channel != null) pairTv(code) { ok -> say(if (ok) "TV conectada. O som continua aqui." else "Não achei a TV. Confira se ela está na tela do código.") }
        else { pendingTv = code to System.currentTimeMillis(); say("TV encontrada! Entre numa sala de voz e ela conecta sozinha.") }
    }
    /* TV vinculada à conta: um código fixo da conta; a TV com esse código segue a pessoa nas salas de voz */
    var tvLink by mutableStateOf<String?>(null)
    private var tvLinkRt: RtChannel? = null
    fun loadTvLink(create: Boolean = false, regen: Boolean = false) = scope.launch {
        try {
            var code = if (regen) null else supa.userMeta().optString("tv_link").ifEmpty { null }
            if (code == null && (create || regen)) { code = (100000 + (Math.random() * 900000).toInt()).toString(); supa.setUserMeta("tv_link", code) }
            if (code != null) useTvLink(code)
            if (regen) say("Código novo. TVs vinculadas antes foram desligadas.")
        } catch (_: Exception) { if (create || regen) say("Não consegui gerar o código agora.") }
    }
    private fun useTvLink(code: String) {
        if (tvLink == code && tvLinkRt != null) return
        tvLinkRt?.leave(); tvLink = code
        val r = rt.channel("dc-tvlink-$code", null); tvLinkRt = r
        r.on("hello") { tvLinkAnnounce() }
    }
    fun tvLinkAnnounce() {
        val r = tvLinkRt ?: return
        val ch = voice.channel
        if (ch != null && ch.dmId == null && !voice.viewer) r.send("pair", JSONObject().put("room", ch.id).put("roomName", ch.name).put("uid", meId).put("name", name(meId)))
        else r.send("linked", JSONObject().put("uid", meId).put("name", name(meId)))
    }
    fun tvLinkLeft(roomId: String) { tvLinkRt?.send("unpair", JSONObject().put("room", roomId)) }

    fun onVoiceJoined() {
        if (voice.viewer) return
        if (voice.channel?.dmId == null) tvLinkAnnounce()
        val p = pendingTv ?: return; pendingTv = null
        if (System.currentTimeMillis() - p.second < 15 * 60_000 && voice.channel?.dmId == null) scope.launch { delay(800); tvFromQr(p.first) }
    }
    fun lastTvCode(): String = app.getSharedPreferences("concord", 0).getString("tv_code", "") ?: ""

    /* ===================== Voz ===================== */
    fun openVoice(ch: Channel) {
        view = View.Voice(ch)
        if (voice.channel?.id == ch.id) return
        if (voice.channel == null) {
            val lim = ch.userLimit
            if (lim > 0 && !isAdmin && voiceUsers(ch.id).count { it.uid != meId } >= lim) { say("Sala cheia ($lim)."); return }
            voice.join(ch)
        }
    }

    /* ===================== Perfil ===================== */
    fun saveProfile(name: String, statusText: String, bio: String, pronouns: String, done: () -> Unit) = scope.launch {
        val p = me ?: return@launch
        try {
            val patch = JSONObject().put("display_name", name.trim().take(24).ifEmpty { p.name })
                .put("status_text", statusText.trim().ifEmpty { null } ?: JSONObject.NULL)
                .put("bio", bio.trim().ifEmpty { null } ?: JSONObject.NULL)
                .put("pronouns", pronouns.trim().ifEmpty { null } ?: JSONObject.NULL)
                .put("updated_at", java.time.Instant.now().toString())
            val r = supa.update("dc_profiles", "id=eq.${p.id}", patch)
            if (r.length() > 0) profiles[p.id] = Profile.from(r.getJSONObject(0))
            srvRt?.send("profile", JSONObject().put("id", p.id))
            supa.rememberAccount(me?.name, me?.color, me?.avatar)
            trackServer(); say("Perfil salvo."); done()
        } catch (e: Exception) { say(authMessage(e)) }
    }

    fun uploadAvatar(uri: Uri) = scope.launch {
        val p = me ?: return@launch
        try {
            val bytes = withContext(Dispatchers.IO) { Images.squareJpeg(app, uri, 512) } ?: return@launch
            val url = supa.upload("u/${p.id}/avatar-${System.currentTimeMillis()}.jpg", bytes, "image/jpeg")
            val r = supa.update("dc_profiles", "id=eq.${p.id}", JSONObject().put("avatar_url", url))
            if (r.length() > 0) profiles[p.id] = Profile.from(r.getJSONObject(0))
            srvRt?.send("profile", JSONObject().put("id", p.id))
            supa.rememberAccount(avatar = url); say("Foto atualizada.")
        } catch (e: Exception) { say("Erro ao enviar foto: ${authMessage(e)}") }
    }

    fun sendImage(uri: Uri) = scope.launch {
        val p = me ?: return@launch
        try {
            val bytes = withContext(Dispatchers.IO) { Images.scaledJpeg(app, uri, 1600) } ?: return@launch
            val url = supa.upload("u/${p.id}/img-${System.currentTimeMillis()}.jpg", bytes, "image/jpeg")
            send(url) {}
        } catch (e: Exception) { say("Erro ao enviar imagem: ${authMessage(e)}") }
    }

    fun inviteLink(): String = Concord.SITE_INVITE + (server?.code ?: "")

    fun onForeground(fg: Boolean) {
        visible = fg
        if (fg) { rt.wake(); Notifier.cancelMessages(app) }
    }

    companion object {
        const val SITE_INVITE = "https://gabrielvazquez.github.io/concord/#"
        lateinit var I: Concord
        fun isReady() = this::I.isInitialized
    }
}

@Suppress("unused") private fun q(s: String) = enc(s)
