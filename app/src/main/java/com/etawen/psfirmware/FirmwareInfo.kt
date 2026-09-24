package com.etawen.psfirmware

import android.content.Context
import java.time.Instant

data class FirmwareInfo(
    val region: String,
    val minimum: String?,
    val latest: String?,
    val status: String?,
    val checkedAt: String?,
) {
    val isOnline: Boolean get() = status.equals("ONLINE", ignoreCase = true)

    fun checkedAtInstant(): Instant? = try {
        checkedAt?.let { Instant.parse(it) }
    } catch (e: Exception) {
        null
    }

    /** true se esta leitura é mais recente que [other] (leituras sem data nunca são mais recentes). */
    fun isNewerThan(other: FirmwareInfo): Boolean {
        val mine = checkedAtInstant() ?: return false
        val theirs = other.checkedAtInstant() ?: return true
        return mine.isAfter(theirs)
    }
}

/** Guarda a última leitura, a região escolhida e o estado da notificação em SharedPreferences. */
object FirmwareStore {
    const val PREFS = "firmware"
    private const val KEY_INFO_REGION = "infoRegion"
    private const val KEY_MIN = "minimum"
    private const val KEY_LATEST = "latest"
    private const val KEY_STATUS = "status"
    private const val KEY_CHECKED = "checkedAt"
    private const val KEY_FETCHED_AT = "fetchedAt"
    private const val KEY_ATTEMPTED_AT = "attemptedAt"
    private const val KEY_REGION = "region"
    private const val KEY_REGIONS = "regions"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ERROR = "lastError"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Última leitura da região selecionada, ou null se ainda não houver. */
    fun load(context: Context): FirmwareInfo? {
        val p = prefs(context)
        val region = p.getString(KEY_INFO_REGION, null)
        if (region == null || region != selectedRegion(context)) return null
        return FirmwareInfo(
            region,
            p.getString(KEY_MIN, null),
            p.getString(KEY_LATEST, null),
            p.getString(KEY_STATUS, null),
            p.getString(KEY_CHECKED, null),
        )
    }

    /** Salva a leitura, a menos que já exista uma mais recente da mesma região (cache antigo da API). */
    fun save(context: Context, info: FirmwareInfo, regions: List<Region>) {
        val current = load(context)
        val editor = prefs(context).edit()
        if (current == null || current.region != info.region || !current.isNewerThan(info)) {
            editor.putString(KEY_INFO_REGION, info.region)
                .putString(KEY_MIN, info.minimum)
                .putString(KEY_LATEST, info.latest)
                .putString(KEY_STATUS, info.status)
                .putString(KEY_CHECKED, info.checkedAt)
        }
        editor.putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .putString(KEY_REGIONS, regions.joinToString("\n") { "${it.code}\t${it.apiName}\t${it.status.orEmpty()}" })
            .putBoolean(KEY_ERROR, false)
            .apply()
    }

    fun regions(context: Context): List<Region> {
        val raw = prefs(context).getString(KEY_REGIONS, null) ?: return Region.FALLBACK
        return raw.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null else Region(parts[0], parts[1], parts[2].ifEmpty { null })
        }.ifEmpty { Region.FALLBACK }
    }

    /**
     * Região escolhida pelo usuário, ou null na primeira execução.
     * Instalações antigas (antes da escolha obrigatória) usam a região da última leitura salva.
     */
    fun selectedRegion(context: Context): String? {
        val p = prefs(context)
        return p.getString(KEY_REGION, null) ?: p.getString(KEY_INFO_REGION, null)
    }

    private fun apiName(context: Context, code: String): String? =
        regions(context).firstOrNull { it.code == code }?.apiName

    /** Nome em inglês da região selecionada, para a interface do app. */
    fun selectedRegionName(context: Context): String? {
        val code = selectedRegion(context) ?: return null
        return regions(context).firstOrNull { it.code == code }?.name ?: Region.englishName(code)
    }

    /** Idioma/nome local da região selecionada, usados na notificação. */
    fun selectedLocality(context: Context): Region.Locality? {
        val code = selectedRegion(context) ?: return null
        return Region.locality(code, apiName(context, code))
    }

    fun setSelectedRegion(context: Context, code: String) {
        prefs(context).edit()
            .putString(KEY_REGION, code)
            .putBoolean(KEY_ERROR, false)
            .apply()
    }

    /** Momento (millis) da última consulta bem-sucedida feita pelo app, ou null. */
    fun fetchedAt(context: Context): Long? =
        prefs(context).getLong(KEY_FETCHED_AT, 0L).takeIf { it > 0 }

    /** Momento (millis) da última tentativa de consulta, com ou sem sucesso, ou null. */
    fun attemptedAt(context: Context): Long? =
        prefs(context).getLong(KEY_ATTEMPTED_AT, 0L).takeIf { it > 0 }

    fun setAttemptedAt(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_ATTEMPTED_AT, millis).apply()
    }

    fun setError(context: Context, error: Boolean) {
        prefs(context).edit().putBoolean(KEY_ERROR, error).apply()
    }

    fun hasError(context: Context) = prefs(context).getBoolean(KEY_ERROR, false)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
