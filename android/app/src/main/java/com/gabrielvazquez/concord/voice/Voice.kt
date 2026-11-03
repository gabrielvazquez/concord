package com.gabrielvazquez.concord.voice

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gabrielvazquez.concord.CallService
import com.gabrielvazquez.concord.Channel
import com.gabrielvazquez.concord.Concord
import com.gabrielvazquez.concord.net.RtChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.sqrt

/** Um participante da sala (outra pessoa) */
class Peer(val key: String, val polite: Boolean, val pc: PeerConnection) {
    var makingOffer = false
    var ignoreOffer = false
    val lock = Mutex()
    val audio = ArrayList<AudioTrack>()
    val pending = ArrayList<IceCandidate>()
}

/** Vídeo recebido (câmera ou tela de alguém) */
data class RemoteVideo(val key: String, val streamId: String, val track: VideoTrack)

/**
 * Voz e vídeo nativos com WebRTC, falando o mesmo "idioma" do site e do app de PC:
 * mesma sala (canal dc-voice-<id>), mesma negociação (perfect negotiation) e o mesmo Opus em alta qualidade.
 */
class Voice(private val c: Concord) {
    private val main = Handler(Looper.getMainLooper())
    val egl: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var micSource: AudioSource? = null
    private var micTrack: AudioTrack? = null
    private val micStream = "mic-" + UUID.randomUUID().toString()
    private var camStream = "cam-" + UUID.randomUUID().toString()
    private var scrStream = "scr-" + UUID.randomUUID().toString()
    private var camCapturer: CameraVideoCapturer? = null
    private var camSource: VideoSource? = null
    var camTrack by mutableStateOf<VideoTrack?>(null); private set
    private var scrCapturer: VideoCapturer? = null
    private var scrSource: VideoSource? = null
    private var scrTrack: VideoTrack? = null
    private var texHelper: SurfaceTextureHelper? = null
    private var texHelper2: SurfaceTextureHelper? = null
    private val senders = HashMap<String, MutableList<RtpSender>>()   // peer -> senders de vídeo

    private var rt: RtChannel? = null
    private val peers = HashMap<String, Peer>()
    val meta = mutableStateMapOf<String, JSONObject>()        // chave -> estado publicado de cada um
    val videos = mutableStateListOf<RemoteVideo>()
    val speaking = mutableStateMapOf<String, Boolean>()
    var channel by mutableStateOf<Channel?>(null); private set
    var muted by mutableStateOf(false); private set
    var deaf by mutableStateOf(false); private set
    var camOn by mutableStateOf(false); private set
    var screenOn by mutableStateOf(false); private set
    var state by mutableStateOf("Conectando…"); private set
    private var jt = 0L
    /** modo TV: só assiste (sem microfone), som opcional */
    var viewer = false; private set
    var tvSound by mutableStateOf(false); private set
    private var ownerUid: String? = null
    private var ownerName = ""
    private var statsJob: Job? = null
    private var micLevel = 0.0

    fun peerCount() = peers.size

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c.app).createInitializationOptions())
        adm = JavaAudioDeviceModule.builder(c.app)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setUseStereoOutput(true)
            .setSamplesReadyCallback { s ->
                val d = s.data; var sum = 0.0; var n = 0
                var i = 0
                while (i + 1 < d.size) { val v = ((d[i + 1].toInt() shl 8) or (d[i].toInt() and 0xff)).toShort().toDouble(); sum += v * v; n++; i += 8 }
                micLevel = if (n > 0) sqrt(sum / n) / 32768.0 else 0.0
            }
            .createAudioDeviceModule()
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f
        return f
    }

    private fun iceServers() = listOf(
        PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302", "stun:stun.cloudflare.com:3478")).createIceServer()
    )

    /* ---------------- Entrar e sair ---------------- */
    fun joinAsTv(ch: Channel, owner: String?, ownerName: String) {
        if (channel != null) leave()
        ensureFactory()
        viewer = true; this.ownerUid = owner; this.ownerName = ownerName
        channel = ch; jt = 0; state = "Conectando…"
        val r = c.rt.channel("dc-voice-${ch.id}", c.sid)
        rt = r
        r.on("sig") { p -> if (p.optString("to") == c.sid) onSignal(p.optString("from"), p) }
        r.onPresence = { onPresence() }
        r.onJoined = { r.track(myMeta()); state = "Voz conectada" }
        startStats()
    }

    fun toggleTvSound() { tvSound = !tvSound; peers.values.forEach { p -> p.audio.forEach { it.setEnabled(tvSound) } }; rt?.track(myMeta()) }

    fun join(ch: Channel) {
        viewer = false
        if (c.app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val ask = c.askMic
            if (ask != null) { ask { join(ch) }; return }
        }
        if (channel != null) leave()
        val f = ensureFactory()
        val mc = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        micSource = f.createAudioSource(mc)
        micTrack = f.createAudioTrack("a-" + UUID.randomUUID(), micSource)
        micTrack?.setEnabled(!muted && !deaf)
        channel = ch; jt = System.currentTimeMillis(); state = "Conectando…"
        CallService.start(c.app, title(), muted)
        val r = c.rt.channel("dc-voice-${ch.id}", c.sid)
        rt = r
        r.on("sig") { p -> if (p.optString("to") == c.sid) onSignal(p.optString("from"), p) }
        r.onPresence = { onPresence() }
        r.onJoined = { r.track(myMeta()); c.trackServer(); beep(true); state = "Voz conectada" }
        startStats()
    }

    fun leave() {
        val ch = channel ?: return
        channel = null
        statsJob?.cancel()
        peers.values.forEach { try { it.pc.close() } catch (_: Exception) {} }
        peers.clear(); videos.clear(); speaking.clear(); meta.clear(); senders.clear()
        stopCam(); stopScreen()
        try { rt?.untrack() } catch (_: Exception) {}
        rt?.leave(); rt = null
        micTrack?.dispose(); micTrack = null; micSource?.dispose(); micSource = null
        if (!viewer) { CallService.stop(c.app); c.trackServer(); beep(false) }
        viewer = false
    }

    fun title() = channel?.let { if (it.dmId != null) "Chamada com ${it.name}" else "${it.name} · ${c.server?.name ?: "CONCORD"}" } ?: "CONCORD"

    private fun myMeta(): JSONObject = if (viewer) JSONObject().put("tv", true).put("viewer", true).put("uid", ownerUid ?: JSONObject.NULL)
        .put("name", "TV de $ownerName").put("jt", 0).put("micId", JSONObject.NULL).put("camId", JSONObject.NULL).put("screenId", JSONObject.NULL)
        .put("muted", true).put("deaf", !tvSound) else JSONObject()
        .put("jt", jt).put("uid", c.meId).put("name", c.name(c.meId)).put("color", c.me?.color ?: "#6366f1")
        .put("micId", micStream).put("camId", if (camOn) camStream else JSONObject.NULL).put("screenId", if (screenOn) scrStream else JSONObject.NULL)
        .put("screenMode", JSONObject.NULL).put("muted", muted).put("deaf", deaf).put("mobile", true)

    private fun republish() { rt?.track(myMeta()); c.trackServer(); CallService.update(c.app, title(), muted) }

    fun toggleMute() { if (deaf) { deaf = false; muted = false } else muted = !muted; applyAudio(); beep(!muted) }
    fun toggleDeaf() { deaf = !deaf; applyAudio(); beep(!deaf) }
    fun setMutedFromOutside(m: Boolean) { muted = m; applyAudio() }

    private fun applyAudio() {
        micTrack?.setEnabled(!muted && !deaf)
        peers.values.forEach { p -> p.audio.forEach { it.setEnabled(!deaf) } }
        republish()
    }

    private fun beep(up: Boolean) {
        if (viewer) return
        try { val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60); tg.startTone(if (up) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 120); main.postDelayed({ tg.release() }, 400) } catch (_: Exception) {}
    }

    /* ---------------- Presença da sala ---------------- */
    private fun onPresence() {
        val r = rt ?: return
        val now = HashMap<String, JSONObject>()
        r.presence.forEach { (k, metas) -> metas.maxByOrNull { it.optLong("jt") }?.let { now[k] = it } }
        // mesma conta em outro aparelho: fica só a entrada mais recente
        if (viewer) now.entries.removeAll { it.key != c.sid && it.value.optBoolean("tv") }
        for ((k, m) in now.entries.toList()) {
            if (viewer || k == c.sid || m.optBoolean("tv") || m.optString("uid") != c.meId) continue
            val ojt = m.optLong("jt")
            if (ojt > jt || (ojt == jt && k > c.sid)) { c.say("Você entrou nesta chamada em outro aparelho."); leave(); return }
            now.remove(k); closePeer(k)
        }
        meta.keys.toList().forEach { if (!now.containsKey(it)) meta.remove(it) }
        now.forEach { (k, v) -> if (k != c.sid) meta[k] = v }
        for (k in now.keys) if (k != c.sid && !peers.containsKey(k)) { createPeer(k); if (!viewer && now[k]?.optBoolean("tv") != true) beep(true) }
        for (k in peers.keys.toList()) if (!now.containsKey(k)) { val wasTv = meta[k]?.optBoolean("tv") == true; closePeer(k); if (!viewer && !wasTv) beep(false) }
    }

    private fun closePeer(k: String) {
        peers.remove(k)?.let { try { it.pc.close() } catch (_: Exception) {} }
        videos.removeAll { it.key == k }; speaking.remove(k); senders.remove(k)
    }

    /* ---------------- Conexão com cada pessoa ---------------- */
    private fun createPeer(key: String): Peer {
        peers[key]?.let { return it }
        val f = ensureFactory()
        val cfg = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }
        lateinit var peer: Peer
        val obs = object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                if (s == PeerConnection.IceConnectionState.FAILED) main.post { try { peer.pc.restartIce() } catch (_: Exception) {} }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(cand: IceCandidate) {
                main.post { send(key, JSONObject().put("cand", JSONObject().put("candidate", cand.sdp).put("sdpMid", cand.sdpMid).put("sdpMLineIndex", cand.sdpMLineIndex))) }
            }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(d: DataChannel?) {}
            override fun onRenegotiationNeeded() { main.post { if (peers[key] === peer) c.scope.launch { negotiate(peer) } } }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                val t = receiver.track() ?: return
                val sid = streams.firstOrNull()?.id ?: t.id()
                main.post {
                    if (t is AudioTrack) { peer.audio.add(t); t.setEnabled(if (viewer) tvSound else !deaf) }
                    if (t is VideoTrack) { videos.removeAll { it.key == key && it.streamId == sid }; videos.add(RemoteVideo(key, sid, t)) }
                }
            }
            override fun onRemoveTrack(receiver: RtpReceiver) {
                val t = receiver.track() ?: return
                main.post { if (t is VideoTrack) videos.removeAll { it.track === t } }
            }
            override fun onConnectionChange(s: PeerConnection.PeerConnectionState) {
                main.post { if (s == PeerConnection.PeerConnectionState.CONNECTED) tune(peer) }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }
        val pc = f.createPeerConnection(cfg, obs) ?: throw IllegalStateException("pc")
        peer = Peer(key, c.sid > key, pc)
        peers[key] = peer
        micTrack?.let { pc.addTrack(it, listOf(micStream)) }
        camTrack?.let { senders.getOrPut(key) { mutableListOf() }.add(pc.addTrack(it, listOf(camStream))) }
        scrTrack?.let { senders.getOrPut(key) { mutableListOf() }.add(pc.addTrack(it, listOf(scrStream))) }
        return peer
    }

    private suspend fun negotiate(p: Peer) = p.lock.withLock {
        try {
            p.makingOffer = true
            val offer = createOffer(p.pc) ?: return@withLock
            if (p.pc.signalingState() != PeerConnection.SignalingState.STABLE) return@withLock
            if (!setLocal(p.pc, SessionDescription(offer.type, munge(offer.description)))) setLocal(p.pc, offer)
            val ld = p.pc.localDescription ?: return@withLock
            if (ld.type != SessionDescription.Type.OFFER) return@withLock
            send(p.key, JSONObject().put("desc", JSONObject().put("type", "offer").put("sdp", ld.description)))
        } finally { p.makingOffer = false }
    }

    private fun onSignal(from: String, msg: JSONObject) {
        if (channel == null || from.isEmpty()) return
        val p = peers[from] ?: createPeer(from)
        c.scope.launch {
            p.lock.withLock {
                val pc = p.pc
                val desc = msg.optJSONObject("desc")
                val cand = msg.optJSONObject("cand")
                if (desc != null) {
                    val type = desc.optString("type")
                    val sd = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), desc.optString("sdp"))
                    val collision = type == "offer" && (p.makingOffer || pc.signalingState() != PeerConnection.SignalingState.STABLE)
                    p.ignoreOffer = !p.polite && collision
                    if (p.ignoreOffer) return@withLock
                    if (collision) setLocal(pc, SessionDescription(SessionDescription.Type.ROLLBACK, ""))
                    if (!setRemote(pc, sd)) return@withLock
                    p.pending.forEach { pc.addIceCandidate(it) }; p.pending.clear()
                    if (type == "offer") {
                        val ans = createAnswer(pc) ?: return@withLock
                        if (!setLocal(pc, SessionDescription(ans.type, munge(ans.description)))) setLocal(pc, ans)
                        pc.localDescription?.let { send(from, JSONObject().put("desc", JSONObject().put("type", "answer").put("sdp", it.description))) }
                    }
                    tune(p)
                } else if (cand != null) {
                    val ic = IceCandidate(cand.optString("sdpMid"), cand.optInt("sdpMLineIndex"), cand.optString("candidate"))
                    if (pc.remoteDescription == null) p.pending.add(ic) else try { pc.addIceCandidate(ic) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun send(to: String, data: JSONObject) { rt?.send("sig", data.put("to", to).put("from", c.sid)) }

    /** Opus em estéreo, com correção de perdas e sem cortes no silêncio (igual ao site) */
    private fun munge(sdp: String): String {
        val lines = sdp.split("\r\n").toMutableList()
        val opus = lines.firstNotNullOfOrNull { Regex("^a=rtpmap:(\\d+) opus/").find(it)?.groupValues?.get(1) } ?: return sdp
        val i = lines.indexOfFirst { it.startsWith("a=fmtp:$opus ") }
        val extra = "stereo=1;sprop-stereo=1;maxaveragebitrate=510000;useinbandfec=1;usedtx=0;maxplaybackrate=48000;minptime=10"
        if (i >= 0) {
            val params = lines[i].substringAfter(" ").split(";").map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("stereo") && !it.startsWith("sprop-stereo") && !it.startsWith("maxaveragebitrate") && !it.startsWith("useinbandfec") && !it.startsWith("usedtx") && !it.startsWith("maxplaybackrate") && !it.startsWith("minptime") }
            lines[i] = "a=fmtp:$opus " + (params + extra).joinToString(";")
        }
        return lines.joinToString("\r\n")
    }

    private fun tune(p: Peer) {
        val kbps = 128
        p.pc.senders.forEach { s ->
            val t = s.track() ?: return@forEach
            try {
                val prm = s.parameters
                val e = prm.encodings.firstOrNull() ?: return@forEach
                if (t.kind() == "audio") e.maxBitrateBps = kbps * 1000
                else e.maxBitrateBps = if (t === scrTrack) 4_000_000 else 1_500_000
                s.setParameters(prm)
            } catch (_: Exception) {}
        }
    }

    /* ---------------- Câmera e tela ---------------- */
    fun toggleCam() { if (camOn) stopCam() else startCam() ; republish() }

    private fun startCam() {
        val f = ensureFactory()
        val en = Camera2Enumerator(c.app)
        val name = en.deviceNames.firstOrNull { en.isFrontFacing(it) } ?: en.deviceNames.firstOrNull() ?: run { c.say("Nenhuma câmera encontrada."); return }
        val cap = en.createCapturer(name, null) ?: return
        val src = f.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("cam", egl.eglBaseContext)
        cap.initialize(helper, c.app, src.capturerObserver)
        cap.startCapture(1280, 720, 30)
        val tr = f.createVideoTrack("v-" + UUID.randomUUID(), src)
        camStream = "cam-" + UUID.randomUUID(); camCapturer = cap; camSource = src; texHelper = helper; camTrack = tr; camOn = true
        peers.forEach { (k, p) -> senders.getOrPut(k) { mutableListOf() }.add(p.pc.addTrack(tr, listOf(camStream))) }
    }

    fun flipCam() { camCapturer?.switchCamera(null) }

    private fun stopCam() {
        val tr = camTrack ?: return
        peers.forEach { (k, p) -> senders[k]?.filter { it.track() === tr }?.forEach { s -> try { p.pc.removeTrack(s) } catch (_: Exception) {}; senders[k]?.remove(s) } }
        try { camCapturer?.stopCapture() } catch (_: Exception) {}
        camCapturer?.dispose(); camCapturer = null
        tr.dispose(); camTrack = null; camSource?.dispose(); camSource = null; texHelper?.dispose(); texHelper = null
        camOn = false
    }

    /** chamada depois que a pessoa autorizou a gravação de tela */
    fun startScreen(data: Intent) {
        val f = ensureFactory()
        CallService.enableProjection(c.app)
        main.postDelayed({
            try {
                val cap = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() { main.post { if (screenOn) { stopScreen(); republish() } } }
                })
                val src = f.createVideoSource(true)
                val helper = SurfaceTextureHelper.create("scr", egl.eglBaseContext)
                cap.initialize(helper, c.app, src.capturerObserver)
                val dm = c.app.resources.displayMetrics
                val scale = 1280.0 / maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
                val w = (dm.widthPixels * minOf(1.0, scale)).toInt() / 2 * 2
                val h = (dm.heightPixels * minOf(1.0, scale)).toInt() / 2 * 2
                cap.startCapture(w, h, 30)
                val tr = f.createVideoTrack("s-" + UUID.randomUUID(), src)
                scrStream = "scr-" + UUID.randomUUID(); scrCapturer = cap; scrSource = src; texHelper2 = helper; scrTrack = tr; screenOn = true
                peers.forEach { (k, p) -> senders.getOrPut(k) { mutableListOf() }.add(p.pc.addTrack(tr, listOf(scrStream))) }
                republish()
            } catch (e: Exception) { c.say("Não foi possível transmitir a tela."); CallService.disableProjection(c.app) }
        }, 300)
    }

    fun stopScreen() {
        val tr = scrTrack ?: return
        peers.forEach { (k, p) -> senders[k]?.filter { it.track() === tr }?.forEach { s -> try { p.pc.removeTrack(s) } catch (_: Exception) {}; senders[k]?.remove(s) } }
        try { scrCapturer?.stopCapture() } catch (_: Exception) {}
        scrCapturer?.dispose(); scrCapturer = null
        tr.dispose(); scrTrack = null; scrSource?.dispose(); scrSource = null; texHelper2?.dispose(); texHelper2 = null
        screenOn = false
        CallService.disableProjection(c.app)
    }

    /** qual vídeo é tela (transmissão) segundo o que a pessoa publicou */
    fun isScreen(v: RemoteVideo) = meta[v.key]?.optString("screenId") == v.streamId

    /* ---------------- Quem está falando ---------------- */
    private fun startStats() {
        statsJob?.cancel()
        statsJob = c.scope.launch {
            while (isActive && channel != null) {
                speaking[c.sid] = micLevel > 0.02 && !muted && !deaf
                peers.values.toList().forEach { p ->
                    p.pc.getStats { rep ->
                        var lvl = 0.0
                        rep.statsMap.values.forEach { s -> if (s.type == "inbound-rtp" && s.members["kind"] == "audio") (s.members["audioLevel"] as? Number)?.let { lvl = maxOf(lvl, it.toDouble()) } }
                        main.post { speaking[p.key] = lvl > 0.02 }
                    }
                }
                delay(300)
            }
        }
    }

    fun onServerPresence() {}

    /* ---------------- Ajudantes assíncronos ---------------- */
    private suspend fun createOffer(pc: PeerConnection): SessionDescription? = suspendCancellableCoroutine { k ->
        pc.createOffer(object : SdpObs() { override fun onCreateSuccess(d: SessionDescription) { k.resume(d) }; override fun onCreateFailure(e: String?) { k.resume(null) } }, MediaConstraints())
    }
    private suspend fun createAnswer(pc: PeerConnection): SessionDescription? = suspendCancellableCoroutine { k ->
        pc.createAnswer(object : SdpObs() { override fun onCreateSuccess(d: SessionDescription) { k.resume(d) }; override fun onCreateFailure(e: String?) { k.resume(null) } }, MediaConstraints())
    }
    private suspend fun setLocal(pc: PeerConnection, d: SessionDescription): Boolean = suspendCancellableCoroutine { k ->
        pc.setLocalDescription(object : SdpObs() { override fun onSetSuccess() { k.resume(true) }; override fun onSetFailure(e: String?) { k.resume(false) } }, d)
    }
    private suspend fun setRemote(pc: PeerConnection, d: SessionDescription): Boolean = suspendCancellableCoroutine { k ->
        pc.setRemoteDescription(object : SdpObs() { override fun onSetSuccess() { k.resume(true) }; override fun onSetFailure(e: String?) { k.resume(false) } }, d)
    }

    private open class SdpObs : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }
}
