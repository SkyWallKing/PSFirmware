package com.etawen.psfirmware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Aviso temporário quando o primeiro widget ícone é colocado: posição e tamanho são ajustáveis no app.
 * Notificação (banner) que some sozinha e abre o app já na seção de ajuste; sem permissão de notificação,
 * um Toast. (Só o Toast não bastava: o launcher entra no modo de redimensionar ao soltar e ele passava batido.)
 */
object IconWidgetTip {
    private const val CHANNEL_ID = "tips"
    private const val NOTIFICATION_ID = 3001
    private const val TIMEOUT_MS = 30_000L

    fun show(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) {
            Toast.makeText(context, R.string.icon_widget_tip, Toast.LENGTH_LONG).show()
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.tips_channel_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(
            context, 5,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_ICON_TUNING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playstation)
            .setColor(context.getColor(R.color.ps_blue))
            .setContentTitle(context.getString(R.string.icon_widget_tip_title))
            .setContentText(context.getString(R.string.icon_widget_tip_text))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.icon_widget_tip_text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }
}
