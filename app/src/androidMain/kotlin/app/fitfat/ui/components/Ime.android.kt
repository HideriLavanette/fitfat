package app.fitfat.ui.components

import app.fitfat.platform.AndroidApp

import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationControlListenerCompat
import androidx.core.view.WindowInsetsAnimationControllerCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Клавиатура уезжает быстрее системной анимации: сами ведём её высоту за [ms] мс.
 * Android 11+; если система не отдала управление — обычное скрытие. [done] — когда клавиатура ушла.
 */
actual fun hideImeFast(done: () -> Unit) {
    val view = AndroidApp.activity?.window?.decorView ?: run { done(); return }
    val ms = 150L
    val ctrl = ViewCompat.getWindowInsetsController(view)
    val shown = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    if (ctrl == null || !shown || Build.VERSION.SDK_INT < 30) { ctrl?.hide(WindowInsetsCompat.Type.ime()); done(); return }
    val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    var finished = false
    fun finish() { if (!finished) { finished = true; done() } }
    ctrl.controlWindowInsetsAnimation(
        WindowInsetsCompat.Type.ime(), ms, ease, null,
        object : WindowInsetsAnimationControlListenerCompat {
            override fun onReady(c: WindowInsetsAnimationControllerCompat, types: Int) {
                val from = c.shownStateInsets.bottom
                val to = c.hiddenStateInsets.bottom
                val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(ms)
                anim.interpolator = ease
                anim.addUpdateListener {
                    if (!c.isReady) return@addUpdateListener
                    val f = it.animatedValue as Float
                    c.setInsetsAndAlpha(Insets.of(0, 0, 0, (from + (to - from) * f).toInt()), 1f, f)
                }
                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { if (c.isReady) c.finish(false) }
                })
                anim.start()
            }
            override fun onFinished(c: WindowInsetsAnimationControllerCompat) = finish()
            override fun onCancelled(c: WindowInsetsAnimationControllerCompat?) {
                // Не дали управлять — прячем как обычно
                ctrl.hide(WindowInsetsCompat.Type.ime()); finish()
            }
        },
    )
}
