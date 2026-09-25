package com.etawen.psfirmware

import android.content.Context

/**
 * Cores do card (notificação, widget e tela do app). Com o tema dinâmico ligado, o card fica verde quando a
 * versão mínima é igual à mais recente e vermelho quando não; desligado ou sem dados, fica no azul padrão.
 */
enum class CardTheme(val card: Int, val cardSmall: Int, val accent: Int) {
    DEFAULT(R.drawable.bg_card, R.drawable.bg_card_small, R.color.ps_blue),
    MATCH(R.drawable.bg_card_match, R.drawable.bg_card_small_match, R.color.match),
    MISMATCH(R.drawable.bg_card_mismatch, R.drawable.bg_card_small_mismatch, R.color.mismatch);

    companion object {
        fun current(context: Context): CardTheme {
            if (!FirmwareStore.isDynamicTheme(context)) return DEFAULT
            val info = FirmwareStore.load(context) ?: return DEFAULT
            return forVersions(info.latest, info.minimum)
        }

        fun forVersions(latest: String?, minimum: String?): CardTheme = when {
            latest.isNullOrBlank() || minimum.isNullOrBlank() -> DEFAULT
            latest.trim() == minimum.trim() -> MATCH
            else -> MISMATCH
        }
    }
}
