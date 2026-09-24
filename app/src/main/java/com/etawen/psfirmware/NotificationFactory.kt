package com.etawen.psfirmware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Locale

object NotificationFactory {
    const val NOTIFICATION_ID = 1001
    // Importância de um canal não muda depois de criado, por isso um novo ID; o antigo (LOW) é removido.
    private const val CHANNEL_ID = "firmware_status_top"
    private const val LEGACY_CHANNEL_ID = "firmware_status"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            // DEFAULT (e não LOW) para ficar na seção principal, acima das notificações silenciosas.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        nm.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        ensureChannel(context)
        // Textos no idioma da região escolhida, independente do idioma do aparelho/app.
        val locality = FirmwareStore.selectedLocality(context)
        val texts = CardTexts.create(context, locality?.locale ?: Locale.ENGLISH, locality?.nativeName)

        val collapsed = RemoteViews(context.packageName, R.layout.notification_collapsed)
        val expanded = RemoteViews(context.packageName, R.layout.notification_expanded)
        texts.applyTo(collapsed)
        texts.applyTo(expanded)

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // No Android 14+ o usuário pode dispensar a notificação; ela é recolocada em seguida.
        val repost = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, RefreshReceiver::class.java).setAction(RefreshReceiver.ACTION_REPOST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val subText = listOfNotNull(locality?.nativeName, texts.checkedTime).joinToString(" · ")

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playstation)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            // Texto padrão usado em tela de bloqueio pública, relógios, acessibilidade etc.
            .setContentTitle(texts.summaryTitle)
            .setContentText(texts.summaryText)
            .setSubText(subText.ifEmpty { null })
            .setColor(context.getColor(R.color.ps_blue))
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setDeleteIntent(repost)
            .build()
    }
}
