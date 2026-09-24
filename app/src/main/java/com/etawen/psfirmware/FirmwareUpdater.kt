package com.etawen.psfirmware

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

object FirmwareUpdater {
    const val INTERVAL_MS = 5 * 60 * 1000L
    private const val TAG = "FirmwareUpdater"

    /**
     * Busca os dados, salva e atualiza a notificação existente (mesmo ID). Bloqueante.
     * Sincronizado porque timer, alarme, tela ligando e o app podem disparar juntos.
     */
    @Synchronized
    fun refresh(context: Context): Boolean {
        val code = FirmwareStore.selectedRegion(context) ?: return false
        FirmwareStore.setAttemptedAt(context, System.currentTimeMillis())
        val ok = try {
            val result = FirmwareApi.fetch(code)
            // Ignora a resposta se o usuário trocou de região durante a consulta.
            if (FirmwareStore.selectedRegion(context) == code) {
                FirmwareStore.save(context, result.info, result.regions)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao consultar a API", e)
            FirmwareStore.setError(context, true)
            false
        }
        if (FirmwareStore.isEnabled(context)) postNotification(context)
        return ok
    }

    /** Tempo desde a última tentativa de consulta (infinito se nunca houve). */
    fun sinceLastAttempt(context: Context): Long =
        System.currentTimeMillis() - (FirmwareStore.attemptedAt(context) ?: 0L)

    /** Como [refresh], mas não faz nada se a última tentativa tiver menos de [minAgeMs]. */
    @Synchronized
    fun refreshIfStale(context: Context, minAgeMs: Long): Boolean {
        if (sinceLastAttempt(context) < minAgeMs) return false
        refresh(context)
        return true
    }

    fun postNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(NotificationFactory.NOTIFICATION_ID, NotificationFactory.build(context))
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Inexato, mas funciona também em modo Doze e não exige permissão de alarme exato.
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            refreshIntent(context),
        )
    }

    fun cancelSchedule(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RefreshReceiver::class.java).setAction(RefreshReceiver.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun enable(context: Context) {
        FirmwareStore.setEnabled(context, true)
        context.startForegroundService(Intent(context, FirmwareService::class.java))
    }

    fun disable(context: Context) {
        FirmwareStore.setEnabled(context, false)
        cancelSchedule(context)
        context.stopService(Intent(context, FirmwareService::class.java))
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationFactory.NOTIFICATION_ID)
    }
}
