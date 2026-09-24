package com.etawen.psfirmware

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object FirmwareApi {
    private const val URL_FIRMWARE = "https://psn.etawen.lol/api/v1/firmware"

    class Result(val info: FirmwareInfo, val regions: List<Region>)

    private const val ATTEMPTS = 3
    private const val TIME_BUDGET_MS = 8_000L

    /**
     * A API é servida por várias instâncias, cada uma com cache próprio de ~30 min, então
     * respostas seguidas podem trazer leituras de horários diferentes. Faz algumas consultas
     * e fica com a mais recente. Lança exceção só se todas falharem.
     */
    fun fetch(code: String): Result {
        val start = System.currentTimeMillis()
        var best: Result? = null
        var lastError: Exception? = null
        for (i in 0 until ATTEMPTS) {
            if (i > 0 && System.currentTimeMillis() - start > TIME_BUDGET_MS) break
            try {
                val result = fetchOnce(code)
                if (best == null || result.info.isNewerThan(best.info)) best = result
            } catch (e: Exception) {
                lastError = e
            }
        }
        return best ?: throw (lastError ?: IllegalStateException("Sem resposta"))
    }

    private fun fetchOnce(code: String): Result {
        val conn = URL(URL_FIRMWARE).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body, code)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, code: String): Result {
        val array = JSONObject(body).getJSONArray("regions")
        val regions = mutableListOf<Region>()
        var info: FirmwareInfo? = null
        for (i in 0 until array.length()) {
            val r = array.getJSONObject(i)
            val regionCode = r.optString("code")
            regions += Region(regionCode, r.optString("region", regionCode), r.stringOrNull("status"))
            if (regionCode == code) {
                info = FirmwareInfo(
                    region = regionCode,
                    minimum = r.stringOrNull("minimum"),
                    latest = r.stringOrNull("latest"),
                    status = r.stringOrNull("status"),
                    checkedAt = r.stringOrNull("checkedAt"),
                )
            }
        }
        return Result(info ?: error("Região $code não encontrada"), regions)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key)
}
