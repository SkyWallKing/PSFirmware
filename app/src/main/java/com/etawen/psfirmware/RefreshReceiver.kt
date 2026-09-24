package com.etawen.psfirmware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!FirmwareStore.isEnabled(context)) return
        when (intent.action) {
            ACTION_REFRESH -> {
                val pending = goAsync()
                Thread {
                    try {
                        // O timer do serviço pode ter acabado de consultar.
                        FirmwareUpdater.refreshIfStale(context, 60_000L)
                    } finally {
                        FirmwareUpdater.scheduleNext(context)
                        pending.finish()
                    }
                }.start()
            }
            ACTION_REPOST -> FirmwareUpdater.postNotification(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.etawen.psfirmware.REFRESH"
        const val ACTION_REPOST = "com.etawen.psfirmware.REPOST"
    }
}
