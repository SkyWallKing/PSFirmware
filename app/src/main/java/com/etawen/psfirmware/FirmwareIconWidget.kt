package com.etawen.psfirmware

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews

/**
 * Widget 1x1 com cara de ícone de app (o Android não permite desenhar texto no ícone de verdade).
 * Mostra só a versão mínima, bem grande, com o rótulo pequeno embaixo; o tema dinâmico muda só as cores ([CardTheme]).
 *
 * Alinhamento: nenhum launcher informa tamanho/posição dos ícones, então o padrão imita o bloco ícone + nome
 * (espaço invisível no layout) e o quadrado usa a proporção típica de ícone na célula. Como cada ROM é um
 * pouco diferente, o usuário pode ajustar posição e tamanho no app ([FirmwareStore.iconOffset]/[FirmwareStore.iconScale]).
 */
class FirmwareIconWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val texts = FirmwareWidget.texts(context)
        ids.forEach { update(context, manager, it, texts) }
        // Mesma consulta periódica do widget principal (sem notificação, é ela que mantém os dados).
        val pending = goAsync()
        Thread {
            try {
                FirmwareUpdater.refreshIfStale(context, FirmwareUpdater.INTERVAL_MS)
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** Primeiro widget ícone colocado: avisa que tamanho e posição podem ser ajustados no app. */
    override fun onEnabled(context: Context) {
        IconWidgetTip.show(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id, FirmwareWidget.texts(context))
    }

    companion object {
        /** Largura do ícone em relação à célula nos launchers mais comuns (~0,65–0,72), para células grandes. */
        private const val ICON_WIDTH_RATIO = 0.68f
        /**
         * Tamanho típico de ícone de app. Os launchers costumam manter o ícone num tamanho fixo em dp, e a área
         * de um widget pode ser mais estreita que a célula de um ícone (padding de widgets), então só a proporção
         * deixaria o quadrado menor que os ícones em grades mais densas.
         */
        private const val TYPICAL_ICON_DP = 46f
        /** Altura reservada ao nome embaixo do quadrado (texto 14sp + margem), para não estourar a célula. */
        private const val LABEL_SPACE_DP = 27f
        private const val BASE_BOX_DP = 56f

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, FirmwareIconWidget::class.java)).isNotEmpty()

        fun updateAll(context: Context, texts: CardTexts) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FirmwareIconWidget::class.java))
            ids.forEach { update(context, manager, it, texts) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int, texts: CardTexts) {
            val options = manager.getAppWidgetOptions(id)
            val views = if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                if (sizes.isNullOrEmpty()) views(context, texts, null)
                else FirmwareWidget.sizedRemoteViews(sizes) { views(context, texts, it) }
            } else {
                views(context, texts, null)
            }
            manager.updateAppWidget(id, views)
        }

        /** [size] em dp (Android 12+): o quadrado acompanha a célula; antes disso fica em 56dp (sem ajuste de tamanho). */
        private fun views(context: Context, texts: CardTexts, size: SizeF?): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_icon)
            rv.setTextViewText(R.id.value_minimum, texts.minimum)
            rv.setTextViewText(R.id.label_minimum, texts.minimumLabel)
            rv.setInt(R.id.icon_box, "setBackgroundResource", CardTheme.current(context).cardSmall)
            rv.setOnClickPendingIntent(R.id.icon_box, NotificationFactory.openAppIntent(context))
            val scale = FirmwareStore.iconScale(context) / 100f
            if (Build.VERSION.SDK_INT >= 31) {
                val base = if (size == null) BASE_BOX_DP
                else minOf(
                    maxOf(size.width * ICON_WIDTH_RATIO, minOf(size.width - 4f, TYPICAL_ICON_DP)),
                    size.height - LABEL_SPACE_DP,
                )
                val box = (base * scale).coerceIn(24f, 160f)
                rv.setViewLayoutWidth(R.id.icon_box, box, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(R.id.icon_box, box, TypedValue.COMPLEX_UNIT_DIP)
            }
            // Posição: o bloco é centralizado, então padding de 2×offset de um lado desloca o centro em offset.
            // Sempre definido nos dois sentidos (o launcher reaplica sobre a view existente).
            val offsetPx = (FirmwareStore.iconOffset(context) * context.resources.displayMetrics.density).toInt()
            rv.setViewPadding(R.id.icon_root, 0, maxOf(0, 2 * offsetPx), 0, maxOf(0, -2 * offsetPx))
            return rv
        }
    }
}
