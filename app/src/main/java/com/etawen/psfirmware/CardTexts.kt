package com.etawen.psfirmware

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Textos do card de firmware, já formatados num idioma. Usado pela notificação (idioma da
 * região escolhida) e pela tela do app (sempre inglês). Todos os textos são definidos por
 * código porque a notificação é desenhada pelo sistema, com o idioma do aparelho.
 */
class CardTexts private constructor(
    val title: String,
    val latestLabel: String,
    val minimumLabel: String,
    val latest: String,
    val minimum: String,
    val status: String,
    val statusColor: Int,
    val updated: String,
    /** Horário curto da leitura, para o cabeçalho da notificação. */
    val checkedTime: String?,
    val summaryTitle: String,
    val summaryText: String,
) {
    fun applyTo(rv: RemoteViews) {
        rv.setTextViewText(R.id.label_card_title, title)
        rv.setTextViewText(R.id.label_latest, latestLabel)
        rv.setTextViewText(R.id.label_minimum, minimumLabel)
        rv.setTextViewText(R.id.value_latest, latest)
        rv.setTextViewText(R.id.value_minimum, minimum)
        rv.setTextViewText(R.id.value_status, status)
        rv.setTextColor(R.id.status_dot, statusColor)
        rv.setTextViewText(R.id.value_updated, updated)
    }

    /** Preenche o card incluído na tela do app ([root] contém as mesmas IDs do layout). */
    fun applyTo(root: android.view.View) {
        root.findViewById<TextView>(R.id.label_card_title).text = title
        root.findViewById<TextView>(R.id.label_latest).text = latestLabel
        root.findViewById<TextView>(R.id.label_minimum).text = minimumLabel
        root.findViewById<TextView>(R.id.value_latest).text = latest
        root.findViewById<TextView>(R.id.value_minimum).text = minimum
        root.findViewById<TextView>(R.id.value_status).text = status
        root.findViewById<TextView>(R.id.status_dot).setTextColor(statusColor)
        root.findViewById<TextView>(R.id.value_updated).text = updated
    }

    companion object {
        /**
         * @param locale idioma dos textos.
         * @param regionName nome da região já no idioma desejado, ou null se nenhuma foi escolhida.
         */
        fun create(context: Context, locale: Locale, regionName: String?): CardTexts {
            val res = localized(context, locale).resources
            val info = FirmwareStore.load(context)
            val zone = ZoneId.systemDefault()
            val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
            val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

            val checked = info?.checkedAtInstant()?.atZone(zone)
            val fetched = FirmwareStore.fetchedAt(context)?.let { Instant.ofEpochMilli(it).atZone(zone) }

            val status = when {
                info?.status == null -> "…"
                info.isOnline -> res.getString(R.string.notif_status_online)
                info.status.equals("NO_DIRECT_XML", true) -> res.getString(R.string.notif_status_unavailable)
                else -> info.status.replace('_', ' ')
            }
            var fetchedLine = res.getString(R.string.notif_last_fetch, fetched?.format(timeFormat) ?: "—")
            if (FirmwareStore.hasError(context)) fetchedLine += "  ·  " + res.getString(R.string.notif_offline)
            val checkedLine = res.getString(R.string.notif_last_update, checked?.format(dateFormat) ?: "—")

            val latest = info?.latest ?: "—"
            val minimum = info?.minimum ?: "—"
            val title = if (regionName == null) "Firmware" else res.getString(R.string.notif_card_title, regionName)

            return CardTexts(
                title = title.uppercase(locale),
                latestLabel = res.getString(R.string.notif_label_latest),
                minimumLabel = res.getString(R.string.notif_label_minimum),
                latest = latest,
                minimum = minimum,
                status = status,
                statusColor = context.getColor(
                    when {
                        info == null -> R.color.status_unknown
                        info.isOnline -> R.color.status_online
                        else -> R.color.status_offline
                    }
                ),
                updated = "$checkedLine\n$fetchedLine",
                checkedTime = checked?.format(timeFormat),
                summaryTitle = res.getString(R.string.notif_summary_title, latest, minimum),
                summaryText = res.getString(R.string.notif_summary_text, status),
            )
        }

        private fun localized(context: Context, locale: Locale): Context {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
