package com.etawen.psfirmware

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale

/**
 * "Mini vídeo" em loop do tema dinâmico:
 * 1. mínima igual à mais recente → verde;
 * 2. sai um firmware novo (a mais recente sobe) → vermelho;
 * 3. a mínima alcança a mais recente → verde de novo; e recomeça.
 * Só roda entre [start] e [stop] (tela visível).
 */
class ThemePreview(root: View) {
    private val latest: TextView = root.findViewById(R.id.preview_latest)
    private val minimum: TextView = root.findViewById(R.id.preview_minimum)
    private val caption: TextView = root.findViewById(R.id.preview_caption)
    private val matchLayer: View = root.findViewById(R.id.preview_match_layer)
    private val content: View = root.findViewById(R.id.preview_content)
    private val icon: ImageView = root.findViewById(R.id.preview_icon)
    private val appName: TextView = root.findViewById(R.id.preview_app)
    private val matchColor = root.context.getColor(R.color.match_light)
    private val mismatchColor = root.context.getColor(R.color.mismatch_light)

    private val handler = Handler(Looper.getMainLooper())
    private val animators = mutableListOf<Animator>()
    private var running = false
    private var isMatch = true
    /**
     * Muda a cada start/stop. Callbacks de um loop anterior (ex.: o fim de um fade que o stop não pegou) comparam
     * com ela e desistem — sem isso, parar e voltar para a tela deixava dois loops rodando e as cores dessincronizavam.
     */
    private var generation = 0

    fun start() {
        if (running) return
        running = true
        generation++
        step(0)
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        animators.toList().forEach { it.cancel() }
        animators.clear()
        matchLayer.animate().cancel()
        content.animate().cancel()
        content.alpha = 1f
    }

    private fun step(n: Int) {
        if (!running) return
        val gen = generation
        when (n) {
            0 -> {
                latest.text = format(OLD)
                minimum.text = format(OLD)
                setMatch(true, animate = false)
                caption.setText(R.string.preview_match)
                content.animate().alpha(1f).setDuration(FADE_MS).start()
                later(HOLD_MS) { step(1) }
            }
            1 -> count(latest) {
                setMatch(false, animate = true)
                caption.setText(R.string.preview_new_firmware)
                later(HOLD_MS) { step(2) }
            }
            2 -> count(minimum) {
                setMatch(true, animate = true)
                caption.setText(R.string.preview_caught_up)
                later(HOLD_MS) { step(3) }
            }
            // Some e volta do começo, para o loop não "pular" de 14.20 para 14.00.
            3 -> content.animate().alpha(0f).setDuration(FADE_MS).withEndAction { if (gen == generation) step(0) }.start()
        }
    }

    /** Conta de [OLD] até [NEW] no texto, como uma versão subindo. */
    private fun count(view: TextView, then: () -> Unit) {
        val anim = ValueAnimator.ofFloat(OLD, NEW).setDuration(COUNT_MS)
        anim.addUpdateListener { view.text = format(it.animatedValue as Float) }
        anim.onEnd {
            view.text = format(NEW)
            then()
        }
        track(anim).start()
    }

    private fun setMatch(match: Boolean, animate: Boolean) {
        val from = if (isMatch) matchColor else mismatchColor
        val to = if (match) matchColor else mismatchColor
        isMatch = match
        if (!animate) {
            matchLayer.alpha = if (match) 1f else 0f
            tint(to)
            return
        }
        matchLayer.animate().alpha(if (match) 1f else 0f).setDuration(COLOR_MS).start()
        val anim = ValueAnimator.ofObject(ArgbEvaluator(), from, to).setDuration(COLOR_MS)
        anim.addUpdateListener { tint(it.animatedValue as Int) }
        track(anim).start()
    }

    private fun tint(color: Int) {
        icon.imageTintList = ColorStateList.valueOf(color)
        appName.setTextColor(color)
    }

    private fun later(delay: Long, action: () -> Unit) {
        val gen = generation
        handler.postDelayed({ if (running && gen == generation) action() }, delay)
    }

    private fun track(anim: ValueAnimator): ValueAnimator {
        animators += anim
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { animators -= anim }
        })
        return anim
    }

    /** Só chama no fim natural, não quando o [stop] cancela a animação. */
    private fun ValueAnimator.onEnd(action: () -> Unit) {
        val gen = generation
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) { if (!cancelled && running && gen == generation) action() }
        })
    }

    private fun format(value: Float) = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val OLD = 14.00f
        const val NEW = 14.20f
        const val HOLD_MS = 2200L
        const val COUNT_MS = 700L
        const val COLOR_MS = 500L
        const val FADE_MS = 250L
    }
}
