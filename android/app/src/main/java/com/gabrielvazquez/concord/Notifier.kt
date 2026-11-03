package com.gabrielvazquez.concord

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notifier {
    const val CH_MSG = "mensagens"
    const val CH_MENTION = "mencoes"
    const val CH_CALL = "chamadas_recebidas"
    const val CH_ONGOING = "em_chamada"
    const val ID_MSG = 1
    const val ID_CALL = 2
    const val ID_ONGOING = 77

    fun setup(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Mensagens", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Mensagens nos canais e conversas" })
        nm.createNotificationChannel(NotificationChannel(CH_MENTION, "Menções e mensagens diretas", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel(CH_CALL, "Chamadas recebidas", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Em chamada", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }

    private fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun open(c: Context, action: String, req: Int): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("concord_action", action)
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun message(c: Context, title: String, body: String, tag: String, important: Boolean) {
        if (!allowed(c)) return
        val n = Notification.Builder(c, if (important) CH_MENTION else CH_MSG)
            .setSmallIcon(R.drawable.ic_stat).setColor(Color.WHITE)
            .setContentTitle(title).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open(c, "open:$tag", tag.hashCode()))
            .build()
        c.getSystemService(NotificationManager::class.java).notify(tag, ID_MSG, n)
    }

    fun incomingCall(c: Context, from: String, dmId: String) {
        if (!allowed(c)) return
        val n = Notification.Builder(c, CH_CALL)
            .setSmallIcon(R.drawable.ic_stat).setColor(Color.WHITE)
            .setContentTitle("$from está ligando").setContentText("Chamada de voz no CONCORD")
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setTimeoutAfter(40_000)
            .setContentIntent(open(c, "call-open:$dmId", 501))
            .setFullScreenIntent(open(c, "call-open:$dmId", 502), true)
            .addAction(Notification.Action.Builder(null, "Recusar", open(c, "call-decline:$dmId", 503)).build())
            .addAction(Notification.Action.Builder(null, "Atender", open(c, "call-accept:$dmId", 504)).build())
            .build()
        c.getSystemService(NotificationManager::class.java).notify("call", ID_CALL, n)
    }

    fun cancelCall(c: Context) = c.getSystemService(NotificationManager::class.java).cancel("call", ID_CALL)
    fun cancel(c: Context, tag: String) = c.getSystemService(NotificationManager::class.java).cancel(tag, ID_MSG)
    fun cancelMessages(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.id == ID_MSG }.forEach { nm.cancel(it.tag, it.id) }
    }
}
