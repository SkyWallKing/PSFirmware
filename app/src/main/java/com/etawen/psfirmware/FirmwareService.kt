package com.etawen.psfirmware

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Serviço em primeiro plano que mantém a notificação permanente e a atualiza:
 * - a cada 5 minutos por um timer próprio enquanto o aparelho está acordado;
 * - na hora em que a tela liga/desbloqueia, se a última consulta estiver velha;
 * - pelo AlarmManager ([RefreshReceiver]) como reserva quando o aparelho dorme.
 */
class FirmwareService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val tick = Runnable { refreshAsync(FirmwareUpdater.INTERVAL_MS - STALE_TOLERANCE_MS) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshAsync(SCREEN_ON_MIN_AGE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!FirmwareStore.isEnabled(this) || FirmwareStore.selectedRegion(this) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NotificationFactory.NOTIFICATION_ID, NotificationFactory.build(this))
        refreshAsync(0)
        return START_STICKY
    }

    /**
     * Consulta em segundo plano se a última tentativa tiver mais de [minAgeMs] e reagenda o
     * timer para 5 minutos depois da última tentativa (seja qual for a origem dela).
     */
    private fun refreshAsync(minAgeMs: Long) {
        if (executor.isShutdown) return
        executor.execute {
            if (FirmwareUpdater.refreshIfStale(this, minAgeMs)) FirmwareUpdater.scheduleNext(this)
            UpdateChecker.checkIfDue(this, UpdateChecker.PERIODIC_INTERVAL_MS)
            val delay = (FirmwareUpdater.INTERVAL_MS - FirmwareUpdater.sinceLastAttempt(this))
                .coerceIn(MIN_TICK_MS, FirmwareUpdater.INTERVAL_MS)
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, delay)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(screenReceiver)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Evita pular um ciclo quando o alarme e o timer disparam quase juntos. */
        private const val STALE_TOLERANCE_MS = 30_000L
        private const val SCREEN_ON_MIN_AGE_MS = 60_000L
        private const val MIN_TICK_MS = 10_000L
    }
}
