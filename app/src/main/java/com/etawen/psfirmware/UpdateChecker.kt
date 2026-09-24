package com.etawen.psfirmware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Procura novas versões do app nas releases do GitHub. */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val LATEST_URL = "https://api.github.com/repos/SkyWallKing/PSFirmware/releases/latest"
    private const val APK_NAME = "PSFirmware.apk"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 2001

    private const val PREFS = "updates"
    private const val KEY_CHECKED_AT = "checkedAt"
    private const val KEY_VERSION = "version"
    private const val KEY_APK_URL = "apkUrl"
    private const val KEY_NOTIFIED = "notifiedVersion"

    const val PERIODIC_INTERVAL_MS = 12 * 60 * 60 * 1000L
    const val ON_OPEN_INTERVAL_MS = 10 * 60 * 1000L

    class Release(val version: String, val apkUrl: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Versão mais nova que a instalada encontrada na última checagem, ou null. */
    fun availableUpdate(context: Context): Release? {
        val p = prefs(context)
        val version = p.getString(KEY_VERSION, null) ?: return null
        val url = p.getString(KEY_APK_URL, null) ?: return null
        return if (isNewer(version, installedVersion(context))) Release(version, url) else null
    }

    /**
     * Consulta o GitHub se a última checagem tiver mais de [minAgeMs] e avisa por notificação
     * (uma vez por versão). Bloqueante; retorna a atualização disponível, se houver.
     */
    @Synchronized
    fun checkIfDue(context: Context, minAgeMs: Long): Release? {
        val p = prefs(context)
        if (System.currentTimeMillis() - p.getLong(KEY_CHECKED_AT, 0L) < minAgeMs) return availableUpdate(context)
        try {
            val latest = fetchLatest()
            p.edit()
                .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                .putString(KEY_VERSION, latest?.version)
                .putString(KEY_APK_URL, latest?.apkUrl)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao procurar atualizações", e)
        }
        val update = availableUpdate(context) ?: return null
        if (p.getString(KEY_NOTIFIED, null) != update.version) {
            notifyUpdate(context, update)
            p.edit().putString(KEY_NOTIFIED, update.version).apply()
        }
        return update
    }

    private fun fetchLatest(): Release? {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "PSFirmware-Android")
            if (conn.responseCode == 404) return null // nenhuma release publicada
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == APK_NAME) {
                    return Release(version, asset.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Compara versões no formato 1.2.3 (partes ausentes valem 0). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val diff = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun notifyUpdate(context: Context, update: Release) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_INSTALL_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playstation)
            .setColor(context.getColor(R.color.ps_blue))
            .setContentTitle(context.getString(R.string.update_notification_title, update.version))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
