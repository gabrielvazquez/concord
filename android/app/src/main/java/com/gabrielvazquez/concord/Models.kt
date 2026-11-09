package com.gabrielvazquez.concord

import org.json.JSONObject
import java.time.Instant

private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).ifEmpty { null }

fun parseTime(s: String?): Long {
    if (s.isNullOrEmpty()) return System.currentTimeMillis()
    val t = s.replace(' ', 'T')
    return try { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (_: Exception) {
        try { Instant.parse(t).toEpochMilli() } catch (_: Exception) {
            try { java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() } catch (_: Exception) { System.currentTimeMillis() }
        }
    }
}

data class Profile(
    val id: String, val name: String, val color: String, val avatar: String?, val banner: String?, val bannerColor: String?,
    val bio: String?, val pronouns: String?, val statusText: String?, val presence: String, val nameColor: String?, val createdAt: String?,
    val username: String? = null, val statusEmoji: String? = null, val statusUntil: String? = null,
    // visual do perfil (mesmos campos do site/exe)
    val nameColor2: String? = null, val nameFont: String? = null, val nameEffect: String? = null, val decoration: String? = null,
    val shape: String? = null, val plate: String? = null, val themePrimary: String? = null, val themeAccent: String? = null,
    val bannerAnim: String? = null, val cardStyle: String? = null, val premBorder: String? = null, val premRing: Boolean = true,
) {
    /** status personalizado com emoji, sumindo quando passa da validade */
    val shownStatus: String? get() {
        val until = statusUntil?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        if (until != null && until < System.currentTimeMillis()) return null
        return listOfNotNull(statusEmoji?.takeIf { it.isNotBlank() }, statusText?.takeIf { it.isNotBlank() }).joinToString(" ").ifEmpty { null }
    }
    companion object {
        fun from(o: JSONObject) = Profile(
            o.getString("id"), o.s("display_name") ?: "…", o.s("color") ?: "#6366f1", o.s("avatar_url"), o.s("banner_url"), o.s("banner_color"),
            o.s("bio"), o.s("pronouns"), o.s("status_text"), o.s("presence_status") ?: "online", o.s("name_color"), o.s("created_at"), o.s("username"), o.s("status_emoji"), o.s("status_until"),
            o.s("name_color2"), o.s("name_font"), o.s("name_effect"), o.s("decoration"), o.s("avatar_shape"), o.s("nameplate"),
            o.s("theme_primary"), o.s("theme_accent"), o.s("banner_anim"), o.s("card_style"),
            o.optJSONObject("prem")?.let { it.s("border") }, o.optJSONObject("prem")?.let { !(it.has("ring") && !it.optBoolean("ring", true)) } ?: true,
        )
    }
}

data class Server(
    val id: String, val code: String, val name: String, val icon: String?, val iconUrl: String?, val bannerUrl: String?,
    val accent: String?, val description: String?, val welcome: String?,
    val tier: String? = null, val themeBg: String? = null, val applyAccent: Boolean = false, val ownerId: String? = null,
    val stage: String? = null, val bar: String? = null, val theme: String? = null, val head: String? = null, val headFx: String? = null,
    // moldura do ícone, GIF parado/animado e enquadramento (z = zoom, x/y = posição em %)
    val ring: String? = null, val ringColor: String? = null, val ring2: String? = null, val iconAnim: String? = null, val bannerAnim: String? = null,
    val iconFit: Triple<Float, Float, Float>? = null, val bannerFit: Triple<Float, Float, Float>? = null,
) {
    /** Emerald (CEO) e Aurora (Founder) liberam os temas premium */
    val premium get() = tier == "diamond" || tier == "aurora"
    companion object {
        private fun fit(o: JSONObject?) = o?.let { Triple(it.optDouble("z", 1.0).toFloat().coerceIn(1f, 3f), it.optDouble("x", 50.0).toFloat().coerceIn(0f, 100f), it.optDouble("y", 50.0).toFloat().coerceIn(0f, 100f)) }
        fun from(o: JSONObject): Server {
            val cu = o.optJSONObject("custom")
            return Server(o.getString("id"), o.optString("code"), o.optString("name"), o.s("icon"), o.s("icon_url"),
                o.s("banner_url"), o.s("accent"), o.s("description"), o.s("welcome"), o.s("tier"), o.s("theme_bg"), o.optBoolean("apply_accent"), o.s("owner_id"),
                cu?.s("stage"), cu?.s("bar"), cu?.s("theme"), cu?.s("head"), cu?.s("headfx"),
                cu?.s("ring"), cu?.s("ringColor"), cu?.s("ring2"), cu?.s("iconAnim"), cu?.s("bannerAnim"), fit(cu?.optJSONObject("iconFit")), fit(cu?.optJSONObject("bannerFit")))
        }
    }
}

data class Channel(
    val id: String, val serverId: String, val name: String, val kind: String, val position: Int, val topic: String?,
    val category: String?, val icon: String?, val readonly: Boolean, val slowmode: Int, val userLimit: Int,
    val dmId: String? = null,
) {
    val isVoice get() = kind == "voice"
    companion object {
        fun from(o: JSONObject) = Channel(o.getString("id"), o.optString("server_id"), o.optString("name"), o.optString("kind", "text"),
            o.optInt("position"), o.s("topic"), o.s("category"), o.s("icon"), o.optBoolean("readonly"), o.optInt("slowmode"), o.optInt("user_limit"))
    }
}

data class Msg(val id: Long, val userId: String?, val author: String, val color: String, val content: String, val at: Long) {
    companion object {
        fun from(o: JSONObject) = Msg(o.optLong("id"), o.s("user_id"), o.optString("author", ""), o.optString("author_color", "#6366f1"),
            o.optString("content"), parseTime(o.s("created_at")))
    }
}

/** amizade: a e b em ordem; quem pediu; "pending" ou "accepted" */
data class Friend(val a: String, val b: String, val requester: String, val status: String) {
    fun other(me: String) = if (a == me) b else a
    companion object { fun from(o: JSONObject) = Friend(o.getString("user_a"), o.getString("user_b"), o.getString("requester"), o.optString("status", "pending")) }
}

data class Dm(val id: String, val a: String, val b: String, val lastAt: Long) {
    fun other(me: String) = if (a == me) b else a
    companion object { fun from(o: JSONObject) = Dm(o.getString("id"), o.getString("user_a"), o.getString("user_b"), parseTime(o.s("last_at"))) }
}

data class Activity(val game: String?, val gameSince: Long, val musicTitle: String?, val musicArtist: String?)

/** Quem está online no servidor aberto (presença) */
data class Online(
    val key: String, val uid: String, val name: String, val color: String, val voice: String?, val muted: Boolean, val deaf: Boolean,
    val cam: Boolean, val screen: Boolean, val st: String, val act: Activity?, val since: Long, val ts: Long, val inv: Boolean,
) {
    companion object {
        fun from(key: String, o: JSONObject): Online {
            val a = o.optJSONObject("act")
            val act = a?.let {
                val g = it.optJSONObject("game"); val m = it.optJSONObject("music")
                if (g == null && m == null) null else Activity(g?.optString("name"), g?.optLong("since") ?: 0, m?.optString("title"), m?.optString("artist"))
            }
            return Online(key, o.optString("uid", key), o.optString("name"), o.optString("color", "#6366f1"), o.s("voice"),
                o.optBoolean("muted"), o.optBoolean("deaf"), o.optBoolean("cam"), o.optBoolean("screen"), o.optString("st", "online"),
                act, o.optLong("since"), o.optLong("ts"), o.optBoolean("inv"))
        }
    }
}

val STATUS_LABEL = linkedMapOf("online" to "Online", "game" to "Em jogo", "live" to "Transmitindo", "idle" to "Ausente", "dnd" to "Não perturbe", "invisible" to "Invisível")
