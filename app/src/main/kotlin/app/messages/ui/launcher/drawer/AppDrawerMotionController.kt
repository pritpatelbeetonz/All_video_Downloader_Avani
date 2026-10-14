package app.messages.ui.launcher.drawer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.abs

/**
 * Flagship-quality app drawer motion (Pixel / HyperOS / One UI style).
 *
 * - Home fades to ~45% and scales to 0.95
 * - Wallpaper blur 0→25px via RenderEffect (API 31+), dim fallback below
 * - Drawer slides up with fade + scale 0.97→1.0
 * - Supports finger-follow via [applyProgress] and settle via open/close
 */
class AppDrawerMotionController(
    private val homeLayer: View,
    private val blurTarget: View,
    private val drawerContainer: View,
    private val drawerPanel: View,
    private val scrim: View,
    private val chromeViews: List<View>
) {

    companion object {
        const val DURATION_MS = 180L
        const val HOME_ALPHA_OPEN = 0.0f
        const val HOME_SCALE_OPEN = 0.94f
        const val DRAWER_SCALE_CLOSED = 0.97f
        const val BLUR_RADIUS_MAX_PX = 40f
        const val SCRIM_ALPHA_MAX = 0.78f
    }

    /** 0f = fully closed, 1f = fully open */
    var progress: Float = 0f
        private set

    var isOpen: Boolean = false
        private set

    val isAnimating: Boolean
        get() = activeSet?.isRunning == true || blurAnimator?.isRunning == true

    private var drawerHeight: Float = 0f
    private var activeSet: AnimatorSet? = null
    private var blurAnimator: ValueAnimator? = null
    private var currentBlurRadius: Float = 0f

    private val settleInterpolator = FastOutSlowInInterpolator()

    fun setDrawerHeight(height: Float) {
        drawerHeight = height.coerceAtLeast(1f)
    }

    /**
     * Finger-follow mapping. [p] is 0 (closed) → 1 (open).
     * Safe to call every MOVE event; no view recreation.
     */
    fun applyProgress(p: Float) {
        val clamped = p.coerceIn(0f, 1f)
        progress = clamped
        ensureDrawerVisibleForProgress(clamped)
        applyVisuals(clamped)
    }

    /** Settle-open — slightly snappier than flagship default. */
    fun openAppDrawer(onEnd: (() -> Unit)? = null) {
        cancelAnimations()
        ensureDrawerVisibleForProgress(progress.coerceAtLeast(0.001f))
        animateTo(target = 1f, onEnd = {
            isOpen = true
            onEnd?.invoke()
        })
    }

    /** Premium settle-close — reverse of open. */
    fun closeAppDrawer(onEnd: (() -> Unit)? = null) {
        cancelAnimations()
        ensureDrawerVisibleForProgress(progress.coerceAtLeast(0.001f))
        animateTo(target = 0f, onEnd = {
            isOpen = false
            hideDrawerFully()
            onEnd?.invoke()
        })
    }

    /**
     * Animate blur radius.
     * API 31+: [RenderEffect.createBlurEffect]
     * Older: no-op here (scrim dim in [applyVisuals] is the fallback)
     */
    fun animateBlur(radius: Float, duration: Long = DURATION_MS) {
        val target = radius.coerceIn(0f, BLUR_RADIUS_MAX_PX)
        blurAnimator?.cancel()
        blurAnimator = ValueAnimator.ofFloat(currentBlurRadius, target).apply {
            this.duration = duration
            interpolator = settleInterpolator
            addUpdateListener { anim ->
                val r = anim.animatedValue as Float
                currentBlurRadius = r
                applyBlurRadius(r)
            }
            start()
        }
    }

    fun cancelAnimations() {
        activeSet?.cancel()
        activeSet = null
        blurAnimator?.cancel()
        blurAnimator = null
        homeLayer.animate().cancel()
        drawerPanel.animate().cancel()
        scrim.animate().cancel()
        chromeViews.forEach { it.animate().cancel() }
    }

    fun reset() {
        cancelAnimations()
        progress = 0f
        isOpen = false
        currentBlurRadius = 0f
        hideDrawerFully()
        clearHardwareLayers()
    }

    // region internals

    private fun animateTo(target: Float, onEnd: () -> Unit) {
        val start = progress
        if (abs(target - start) < 0.001f) {
            applyProgress(target)
            onEnd()
            return
        }

        enableHardwareLayers()

        // Snappier settle; still scaled by remaining distance
        val duration = (DURATION_MS * (0.55f + abs(target - start) * 0.45f))
            .toLong()
            .coerceIn(140L, 200L)

        val progressAnim = ValueAnimator.ofFloat(start, target).apply {
            this.duration = duration
            interpolator = settleInterpolator
            addUpdateListener { anim ->
                applyProgress(anim.animatedValue as Float)
            }
        }

        activeSet = AnimatorSet().apply {
            play(progressAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    activeSet = null
                    clearHardwareLayers()
                    progress = target
                    onEnd()
                }

                override fun onAnimationCancel(animation: Animator) {
                    activeSet = null
                    clearHardwareLayers()
                }
            })
            start()
        }
    }

    private fun applyVisuals(p: Float) {
        // 1) Home content — opacity + subtle scale
        val homeAlpha = (1f - p).coerceIn(0f, 1f)
        val homeScale = 1f - (1f - HOME_SCALE_OPEN) * p
        homeLayer.alpha = homeAlpha
        homeLayer.scaleX = homeScale
        homeLayer.scaleY = homeScale
        homeLayer.pivotX = homeLayer.width / 2f
        homeLayer.pivotY = homeLayer.height / 2f
        homeLayer.visibility = if (p >= 0.99f) View.INVISIBLE else View.VISIBLE

        // Chrome follows home alpha only (no visibility toggles → no flicker)
        chromeViews.forEach { chrome ->
            chrome.alpha = homeAlpha
            chrome.isEnabled = p < 0.08f
        }

        // 2) Dim scrim (also pre-Android 12 blur fallback)
        scrim.alpha = SCRIM_ALPHA_MAX * p

        // 3) Soft blur on wallpaper
        applyBlurRadius(BLUR_RADIUS_MAX_PX * p)

        // 4) Drawer panel — slide / fade / scale
        val height = resolveHeight()
        drawerPanel.pivotX = (drawerPanel.width.takeIf { it > 0 } ?: homeLayer.width) / 2f
        drawerPanel.pivotY = height
        drawerPanel.translationY = height * (1f - p)
        drawerPanel.alpha = p
        val drawerScale = DRAWER_SCALE_CLOSED + (1f - DRAWER_SCALE_CLOSED) * p
        drawerPanel.scaleX = drawerScale
        drawerPanel.scaleY = drawerScale
    }

    private fun applyBlurRadius(radiusPx: Float) {
        currentBlurRadius = radiusPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (radiusPx < 0.5f) {
                blurTarget.setRenderEffect(null)
            } else {
                blurTarget.setRenderEffect(
                    RenderEffect.createBlurEffect(
                        radiusPx,
                        radiusPx,
                        Shader.TileMode.CLAMP
                    )
                )
            }
        }
    }

    private fun resolveHeight(): Float {
        if (drawerHeight <= 0f) {
            drawerHeight = drawerPanel.height.toFloat().coerceAtLeast(1f)
                .takeIf { drawerPanel.height > 0 }
                ?: homeLayer.height.toFloat().coerceAtLeast(1f)
        }
        return drawerHeight
    }

    private fun ensureDrawerVisibleForProgress(p: Float) {
        if (p <= 0f) return
        if (drawerContainer.visibility != View.VISIBLE) {
            drawerContainer.visibility = View.VISIBLE
        }
        resolveHeight()
    }

    private fun hideDrawerFully() {
        progress = 0f
        applyBlurRadius(0f)
        homeLayer.visibility = View.VISIBLE
        homeLayer.alpha = 1f
        homeLayer.scaleX = 1f
        homeLayer.scaleY = 1f
        drawerPanel.translationY = resolveHeight()
        drawerPanel.alpha = 0f
        drawerPanel.scaleX = DRAWER_SCALE_CLOSED
        drawerPanel.scaleY = DRAWER_SCALE_CLOSED
        scrim.alpha = 0f
        chromeViews.forEach {
            it.alpha = 1f
            it.isEnabled = true
        }
        // Hide only after visuals reset — prevents blink
        drawerContainer.visibility = View.GONE
    }

    private fun enableHardwareLayers() {
        homeLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        drawerPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        blurTarget.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun clearHardwareLayers() {
        homeLayer.setLayerType(View.LAYER_TYPE_NONE, null)
        drawerPanel.setLayerType(View.LAYER_TYPE_NONE, null)
        blurTarget.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    // endregion
}
