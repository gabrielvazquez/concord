package com.gabrielvazquez.concord.net

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Filtro de mudanças do banco que o canal quer receber */
data class PgSub(val table: String, val event: String = "*", val filter: String? = null)

/**
 * Canal do Supabase Realtime (protocolo Phoenix, o mesmo do site): broadcast, presença e mudanças do banco.
 * Todos os callbacks rodam na thread principal.
 */
class RtChannel internal constructor(
    private val rt: Realtime,
    val name: String,
    val presenceKey: String?,
    private val pg: List<PgSub>,
) {
    val topic = "realtime:$name"
    internal var joinRef: String? = null
    @Volatile var joined = false
        internal set
    private var lastTrack: JSONObject? = null
    private var closed = false

    /** chave da presença -> lista de estados (como presenceState() do supabase-js) */
    val presence = LinkedHashMap<String, MutableList<JSONObject>>()

    private val broadcastHandlers = HashMap<String, (JSONObject) -> Unit>()
    var onPresence: (() -> Unit)? = null
    var onChange: ((table: String, type: String, record: JSONObject, old: JSONObject) -> Unit)? = null
    var onJoined: (() -> Unit)? = null

    fun on(event: String, h: (JSONObject) -> Unit): RtChannel { broadcastHandlers[event] = h; return this }

    fun send(event: String, payload: JSONObject) {
        if (!joined) return
        rt.push(topic, "broadcast", JSONObject().put("type", "broadcast").put("event", event).put("payload", payload), joinRef)
    }

    fun track(state: JSONObject) {
        lastTrack = state
        if (joined) rt.push(topic, "presence", JSONObject().put("type", "presence").put("event", "track").put("payload", state), joinRef)
    }

    fun untrack() {
        lastTrack = null
        if (joined) rt.push(topic, "presence", JSONObject().put("type", "presence").put("event", "untrack"), joinRef)
    }

    fun leave() {
        closed = true
        if (joined) rt.push(topic, "phx_leave", JSONObject(), joinRef)
        joined = false
        rt.remove(this)
    }

    internal fun joinPayload(token: String): JSONObject {
        val cfg = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", presenceKey ?: "").put("enabled", presenceKey != null))
            .put("private", false)
        val arr = JSONArray()
        pg.forEach { s -> arr.put(JSONObject().put("event", s.event).put("schema", "public").put("table", s.table).apply { s.filter?.let { put("filter", it) } }) }
        cfg.put("postgres_changes", arr)
        return JSONObject().put("config", cfg).put("access_token", token)
    }

    internal fun handle(event: String, payload: JSONObject) {
        if (event.startsWith("presence") || event.startsWith("phx")) android.util.Log.d("Concord", "rt $name $event ${payload.toString().take(300)}")
        when (event) {
            "phx_reply" -> {
                if (!joined && payload.optString("status") == "ok") {
                    joined = true
                    lastTrack?.let { track(it) }
                    onJoined?.invoke()
                }
            }
            "presence_state" -> {
                presence.clear()
                payload.keys().forEach { k -> presence[k] = metas(payload.optJSONObject(k)) }
                onPresence?.invoke()
            }
            "presence_diff" -> {
                payload.optJSONObject("leaves")?.let { l ->
                    l.keys().forEach { k ->
                        val refs = metas(l.optJSONObject(k)).map { it.optString("phx_ref") }.toSet()
                        presence[k]?.removeAll { it.optString("phx_ref") in refs }
                        if (presence[k].isNullOrEmpty()) presence.remove(k)
                    }
                }
                payload.optJSONObject("joins")?.let { j ->
                    j.keys().forEach { k ->
                        val add = metas(j.optJSONObject(k))
                        val cur = presence.getOrPut(k) { mutableListOf() }
                        val refs = add.map { it.optString("phx_ref") }.toSet()
                        cur.removeAll { it.optString("phx_ref") in refs }
                        cur.addAll(add)
                    }
                }
                onPresence?.invoke()
            }
            "broadcast" -> {
                val ev = payload.optString("event")
                broadcastHandlers[ev]?.invoke(payload.optJSONObject("payload") ?: JSONObject())
            }
            "postgres_changes" -> {
                val d = payload.optJSONObject("data") ?: return
                onChange?.invoke(d.optString("table"), d.optString("type"), d.optJSONObject("record") ?: JSONObject(), d.optJSONObject("old_record") ?: JSONObject())
            }
            "phx_error", "phx_close" -> {
                joined = false
                if (!closed) rt.scheduleRejoin(this)
            }
        }
    }

    private fun metas(o: JSONObject?): MutableList<JSONObject> {
        val arr = o?.optJSONArray("metas") ?: return mutableListOf()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.toMutableList()
    }

    internal fun resetForReconnect() { joined = false }
}

class Realtime(private val supa: Supa, private val scope: CoroutineScope) {
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private val ref = AtomicInteger(1)
    private val channels = LinkedHashMap<String, RtChannel>()
    private var hb: Job? = null
    private var connecting = false
    private var backoff = 1000L
    @Volatile var connected = false
        private set
    var onStatus: ((Boolean) -> Unit)? = null
    /** a TV assiste sem login (canais públicos de voz e pareamento) */
    var allowAnon = false

    fun channel(name: String, presenceKey: String? = null, pg: List<PgSub> = emptyList()): RtChannel {
        channels["realtime:$name"]?.let { it.leave() }
        val ch = RtChannel(this, name, presenceKey, pg)
        channels[ch.topic] = ch
        if (connected) join(ch) else connect()
        return ch
    }

    internal fun remove(ch: RtChannel) { if (channels[ch.topic] === ch) channels.remove(ch.topic) }

    fun connect() {
        if (connected || connecting || (supa.session == null && !allowAnon)) return
        connecting = true
        val url = Cfg.URL.replace("https://", "wss://") + "/realtime/v1/websocket?apikey=${Cfg.KEY}&vsn=1.0.0"
        ws = supa.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post {
                    connecting = false; connected = true; backoff = 1000
                    onStatus?.invoke(true)
                    startHeartbeat()
                    channels.values.toList().forEach { it.resetForReconnect(); join(it) }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = try { JSONObject(text) } catch (_: Exception) { return }
                main.post {
                    val topic = m.optString("topic")
                    if (topic == "phoenix") return@post
                    channels[topic]?.handle(m.optString("event"), m.optJSONObject("payload") ?: JSONObject())
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { main.post { dropped() } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { main.post { dropped() } }
        })
    }

    private fun dropped() {
        connecting = false
        if (connected) onStatus?.invoke(false)
        connected = false
        hb?.cancel()
        channels.values.forEach { it.resetForReconnect() }
        if (supa.session == null && !allowAnon) return
        val wait = backoff; backoff = (backoff * 2).coerceAtMost(15_000)
        main.postDelayed({ connect() }, wait)
    }

    fun disconnect() {
        channels.values.toList().forEach { it.leave() }
        channels.clear()
        hb?.cancel()
        ws?.close(1000, "bye"); ws = null
        connected = false; connecting = false
    }

    /** chama quando o app volta para a tela: se a conexão caiu, reconecta na hora */
    fun wake() { if (!connected && !connecting) { backoff = 1000; connect() } }

    private fun join(ch: RtChannel) {
        scope.launch {
            val t = if (supa.session == null) Cfg.KEY else try { supa.token() } catch (_: Exception) { Cfg.KEY }
            main.post {
                val r = ref.getAndIncrement().toString()
                ch.joinRef = r
                send(JSONObject().put("topic", ch.topic).put("event", "phx_join").put("payload", ch.joinPayload(t)).put("ref", r).put("join_ref", r))
            }
        }
    }

    internal fun scheduleRejoin(ch: RtChannel) {
        main.postDelayed({ if (connected && channels[ch.topic] === ch && !ch.joined) join(ch) }, 2000)
    }

    internal fun push(topic: String, event: String, payload: JSONObject, joinRef: String?) {
        send(JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", ref.getAndIncrement().toString()).apply { if (joinRef != null) put("join_ref", joinRef) })
    }

    private fun send(o: JSONObject) { ws?.send(o.toString()) }

    private fun startHeartbeat() {
        hb?.cancel()
        hb = scope.launch {
            var n = 0
            while (isActive) {
                delay(25_000)
                main.post { send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()).put("ref", ref.getAndIncrement().toString())) }
                // renova o token nos canais antes de expirar
                if (++n % 20 == 0) {
                    val t = try { supa.token() } catch (_: Exception) { null }
                    if (t != null) main.post { channels.values.filter { it.joined }.forEach { push(it.topic, "access_token", JSONObject().put("access_token", t), it.joinRef) } }
                }
            }
        }
    }
}
