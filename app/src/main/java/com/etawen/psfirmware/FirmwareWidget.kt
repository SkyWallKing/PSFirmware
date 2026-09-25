package com.etawen.psfirmware

import android.annotation.TargetApi
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Widget da tela inicial com o mesmo card da notificação (textos no idioma da região).
 * Redimensionável: o layout é escolhido pelo tamanho real do widget, em dp.
 * - Android 12+: um layout para cada tamanho informado pelo launcher (retrato/paisagem, dobráveis);
 * - antes disso: um layout para retrato e outro para paisagem.
 */
class FirmwareWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val texts = texts(context)
        ids.forEach { update(context, manager, it, texts) }
        // Com a notificação desligada, é esta chamada periódica (30 min) que mantém os dados em dia.
        val pending = goAsync()
        Thread {
            try {
                FirmwareUpdater.refreshIfStale(context, FirmwareUpdater.INTERVAL_MS)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return super.onReceive(context, intent)
        // Toques repetidos enquanto a consulta anterior não terminou são ignorados.
        if (!refreshing.compareAndSet(false, true)) return
        updateAll(context)
        val pending = goAsync()
        Thread {
            try {
                // Forçado pelo usuário: consulta mesmo que a última leitura seja recente.
                FirmwareUpdater.refresh(context)
            } finally {
                refreshing.set(false)
                // Redesenha com o botão aceso de novo (o refresh redesenhou com ele apagado).
                updateAll(context)
                pending.finish()
            }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context, manager, id, texts(context))
    }

    private enum class Kind(val layout: Int) {
        /** Só a mínima, horizontal (pequeno e baixo). */
        MINI(R.layout.widget_mini),
        /** Só a mínima, vertical (pequeno, mais quadrado ou alto). */
        SMALL(R.layout.widget_small),
        ROW(R.layout.widget_row),
        STACK(R.layout.widget_stack),
        CARD(R.layout.widget_card),
    }

    companion object {
        private const val ACTION_REFRESH = "com.etawen.psfirmware.WIDGET_REFRESH"
        private val refreshing = AtomicBoolean(false)

        /** Redesenha todos os widgets com os dados salvos. Chamado a cada consulta e troca de região. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FirmwareWidget::class.java))
            val texts = texts(context)
            ids.forEach { update(context, manager, it, texts) }
            // O widget estilo ícone mostra os mesmos dados.
            FirmwareIconWidget.updateAll(context, texts)
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            Intent(context, FirmwareWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun texts(context: Context): CardTexts {
            val locality = FirmwareStore.selectedLocality(context)
            return CardTexts.create(context, locality?.locale ?: Locale.ENGLISH, locality?.nativeName)
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int, texts: CardTexts) {
            manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id), texts))
        }

        private fun build(context: Context, options: Bundle, texts: CardTexts): RemoteViews {
            if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                if (!sizes.isNullOrEmpty()) {
                    return sizedRemoteViews(sizes) { views(context, texts, it.width, it.height) }
                }
            }
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            // Launcher ainda não informou o tamanho: usa o tamanho inicial (4x2).
            if (minWidth == 0 || minHeight == 0) return views(context, texts, 250f, 110f)
            // Em retrato o widget fica com a largura mínima e a altura máxima; em paisagem, o contrário.
            return RemoteViews(
                views(context, texts, maxWidth.toFloat(), minHeight.toFloat()),
                views(context, texts, minWidth.toFloat(), maxHeight.toFloat()),
            )
        }

        /**
         * Os dois números lado a lado precisam de ~190dp de largura (com bolinha e botão) e empilhados de ~170dp de
         * altura; abaixo disso ficariam ilegíveis, então só a mínima é mostrada, ocupando todo o espaço.
         */
        /**
         * Um layout por tamanho informado pelo launcher (Android 12+). O launcher escolhe a maior chave que "cabe"
         * no espaço real do widget e, se nenhuma couber, usa a de menor área — em geral a de paisagem, errada no
         * celular em pé. Como o espaço real pode ser bem menor que o informado (padding do launcher, arredondamento),
         * o layout do tamanho mais estreito (retrato) ganha a chave mínima, que sempre cabe; os outros só ganham
         * quando o widget é de fato mais largo (85% do tamanho, para tolerar o padding).
         */
        @TargetApi(31) // só chamado dentro de SDK_INT >= 31
        fun sizedRemoteViews(sizes: List<SizeF>, build: (SizeF) -> RemoteViews): RemoteViews {
            val narrowest = sizes.minBy { it.width }
            return RemoteViews(sizes.associate { size ->
                val key = if (size === narrowest) SizeF(1f, 1f) else SizeF(size.width * 0.85f, size.height * 0.85f)
                key to build(size)
            })
        }

        private fun kindFor(width: Float, height: Float): Kind = when {
            width >= 250 && height >= 110 -> Kind.CARD
            width >= 90 && height >= 170 -> Kind.STACK
            width >= 190 -> Kind.ROW
            height < 80 -> Kind.MINI
            else -> Kind.SMALL
        }

        private fun views(context: Context, texts: CardTexts, width: Float, height: Float): RemoteViews {
            val kind = kindFor(width, height)
            val rv = RemoteViews(context.packageName, kind.layout)
            texts.applyTo(rv)
            val theme = CardTheme.current(context)
            val small = kind == Kind.SMALL || kind == Kind.MINI
            rv.setInt(android.R.id.background, "setBackgroundResource", if (small) theme.cardSmall else theme.card)
            rv.setOnClickPendingIntent(android.R.id.background, NotificationFactory.openAppIntent(context))
            rv.setOnClickPendingIntent(R.id.button_refresh, refreshIntent(context))
            // Botão apagado enquanto a consulta pedida pelo usuário está em andamento. Sempre definido,
            // porque o launcher reaplica sobre a view existente e o valor anterior ficaria.
            rv.setInt(R.id.button_refresh, "setImageAlpha", if (refreshing.get()) 70 else 255)
            // Visibilidades sempre definidas nos dois sentidos: o launcher reaplica sobre a view existente
            // quando o layout é o mesmo, então um GONE de um tamanho anterior ficaria.
            fun visibleIf(id: Int, visible: Boolean, hidden: Int = View.GONE) =
                rv.setViewVisibility(id, if (visible) View.VISIBLE else hidden)
            when (kind) {
                // Sem espaço para o texto do status, fica só a bolinha colorida.
                Kind.ROW -> {
                    visibleIf(R.id.value_status, width >= 260)
                    // Muito baixo: os rótulos roubariam a altura dos números.
                    visibleIf(R.id.label_latest, height >= 56)
                    visibleIf(R.id.label_minimum, height >= 56)
                }
                Kind.STACK -> {
                    visibleIf(R.id.value_status, width >= 150)
                    // Estreito demais, o título vira só "FI…"; INVISIBLE mantém o espaço e o botão no canto.
                    visibleIf(R.id.label_card_title, width >= 130, View.INVISIBLE)
                }
                Kind.CARD -> visibleIf(R.id.value_updated, height >= 170)
                Kind.MINI -> {
                    visibleIf(R.id.label_minimum, height >= 60)
                    visibleIf(R.id.button_refresh, width >= 100)
                    // Num 1x1 estreito, até a bolinha tira espaço do número.
                    visibleIf(R.id.status_dot, width >= 70)
                }
                Kind.SMALL -> Unit
            }
            return rv
        }
    }
}
