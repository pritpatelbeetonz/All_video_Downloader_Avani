package app.messages.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Owns home-screen gesture arbitration:
 * - Any upward swipe (including diagonal / “cross” drift) → app drawer
 * - Clear mostly-horizontal swipe → ViewPager page change (fake-drag)
 *
 * ViewPager user input is disabled while we decide so it cannot steal diagonal opens.
 */
class SwipeUpInterceptLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Callback {
        fun onSwipeUpStart()
        /** progress 0f (closed) → 1f (fully open) */
        fun onSwipeUpProgress(progress: Float)
        fun onSwipeUpEnd(progress: Float, velocityY: Float)
    }

    var callback: Callback? = null
    var enabledIntercept: Boolean = false
    var drawerHeightPx: Float = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var tracking = false
    private var draggingVertical = false
    private var draggingHorizontal = false
    private var velocityTracker: VelocityTracker? = null

    /**
     * Icons call [requestDisallowInterceptTouchEvent] on DOWN for long-press.
     * Ignoring that keeps drawer / page swipes working from icon empty space.
     * We still wait for touchSlop before claiming, so long-press / taps work.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (!enabledIntercept) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!enabledIntercept) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastX = ev.x
                tracking = true
                draggingVertical = false
                draggingHorizontal = false
                recycleTracker()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                velocityTracker?.addMovement(ev)
                if (draggingVertical) return true
                if (draggingHorizontal) return false
                return decideDirection(ev.x, ev.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
                recycleTracker()
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledIntercept) return false
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                lastX = event.x
                tracking = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!draggingVertical && !draggingHorizontal) {
                    if (!decideDirection(event.x, event.y)) return true
                }
                if (draggingVertical) {
                    val pulled = (startY - event.y).coerceAtLeast(0f)
                    val height = drawerHeightPx.takeIf { it > 0f } ?: height.toFloat()
                    val progress = (pulled / height).coerceIn(0f, 1f)
                    callback?.onSwipeUpProgress(progress)
                    return true
                }
                if (draggingHorizontal) {
                    // Let ViewPager handle it since we don't intercept horizontally anymore.
                    // But if we are somehow here, do nothing.
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when {
                    draggingVertical -> {
                        velocityTracker?.computeCurrentVelocity(1000, maxFling)
                        val velocityY = -(velocityTracker?.yVelocity ?: 0f)
                        val pulled = (startY - event.y).coerceAtLeast(0f)
                        val height = drawerHeightPx.takeIf { it > 0f } ?: height.toFloat()
                        val progress = (pulled / height).coerceIn(0f, 1f)
                        callback?.onSwipeUpEnd(progress, velocityY)
                    }
                    draggingHorizontal -> {}
                }
                cleanup()
                return true
            }
        }
        return draggingVertical || draggingHorizontal
    }

    /**
     * @return true once this layout has claimed the gesture.
     */
    private fun decideDirection(x: Float, y: Float): Boolean {
        val absDx = abs(x - startX)
        val absDy = abs(y - startY)
        val upDy = startY - y

        if (absDx < touchSlop && absDy < touchSlop) return false

        // Any real upward motion → drawer (even with strong sideways “cross” drift).
        if (upDy > touchSlop) {
            claimVertical(y)
            return true
        }

        // Proper left/right only when the gesture is mostly flat horizontal.
        if (absDx > touchSlop && absDx >= absDy * HORIZONTAL_MIN_RATIO) {
            claimHorizontal()
            // We return false here so that ViewPager2 can handle the horizontal swipe naturally.
            return false
        }

        return false
    }

    private fun claimVertical(y: Float) {
        if (draggingVertical) return
        draggingVertical = true
        draggingHorizontal = false
        parent?.requestDisallowInterceptTouchEvent(true)
        findViewPager()?.let { pager ->
            (pager.getChildAt(0) as? RecyclerView)?.stopScroll()
            pager.setCurrentItem(pager.currentItem, false)
        }
        callback?.onSwipeUpStart()
        val pulled = (startY - y).coerceAtLeast(0f)
        val height = drawerHeightPx.takeIf { it > 0f } ?: height.toFloat()
        callback?.onSwipeUpProgress((pulled / height).coerceIn(0f, 1f))
    }

    private fun claimHorizontal() {
        if (draggingHorizontal || draggingVertical) return
        draggingHorizontal = true
        tracking = false
        // Do not intercept or fake drag, just let child (ViewPager2) handle it natively.
    }

    private fun findViewPager(): ViewPager2? = findViewPager(this)

    private fun findViewPager(group: ViewGroup): ViewPager2? {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is ViewPager2 -> return child
                is ViewGroup -> findViewPager(child)?.let { return it }
            }
        }
        return null
    }

    private fun cleanup() {
        tracking = false
        draggingVertical = false
        draggingHorizontal = false
        parent?.requestDisallowInterceptTouchEvent(false)
        recycleTracker()
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        /**
         * Horizontal page swipe: dx must be clearly larger than dy
         * (and there must be no upward claim — upward is checked first).
         */
        private const val HORIZONTAL_MIN_RATIO = 1.25f
    }
}
