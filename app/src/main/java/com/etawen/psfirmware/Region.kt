package com.etawen.psfirmware

import java.util.Locale

/** Região retornada pela API. [status] é o status da última consulta (ex.: ONLINE, NO_DIRECT_XML). */
data class Region(val code: String, val apiName: String, val status: String?) {
    /** Nome em inglês, usado na interface do app. */
    val name: String get() = KNOWN[code]?.englishName ?: apiName
    val isAvailable: Boolean get() = status.equals("ONLINE", ignoreCase = true)

    /** Nome, idioma e formatação usados na notificação quando esta região está selecionada. */
    class Locality(val englishName: String, val nativeName: String, languageTag: String) {
        val locale: Locale = Locale.forLanguageTag(languageTag)
    }

    companion object {
        private val KNOWN = linkedMapOf(
            "US" to Locality("United States", "United States", "en-US"),
            "JP" to Locality("Japan", "日本", "ja-JP"),
            "EU" to Locality("Europe", "Europe", "en-GB"),
            "UK" to Locality("United Kingdom", "United Kingdom", "en-GB"),
            "KR" to Locality("South Korea", "대한민국", "ko-KR"),
            "MX" to Locality("Mexico", "México", "es-MX"),
            "AU" to Locality("Australia", "Australia", "en-AU"),
            "SA" to Locality("Saudi Arabia", "السعودية", "ar-SA"),
            "TW" to Locality("Taiwan", "台灣", "zh-Hant-TW"),
            "RU" to Locality("Russia", "Россия", "ru-RU"),
            "CN" to Locality("China", "中国", "zh-Hans-CN"),
            "HK" to Locality("Hong Kong", "香港", "zh-Hant-HK"),
            "BR" to Locality("Brazil", "Brasil", "pt-BR"),
        )

        /** Usada antes da primeira consulta à API. */
        val FALLBACK: List<Region> = KNOWN.map { (code, l) -> Region(code, l.englishName, null) }

        fun englishName(code: String): String = KNOWN[code]?.englishName ?: code

        /** Região desconhecida (nova na API) cai para inglês. */
        fun locality(code: String, apiName: String?): Locality =
            KNOWN[code] ?: Locality(apiName ?: code, apiName ?: code, "en")
    }
}
