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
import kotlin.math.abs

/**
 * Interactive swipe-down dismiss: drawer follows the finger.
 * Only intercepts when nested list cannot scroll upward.
 */
class SwipeDownDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    interface Callback {
        fun onSwipeDownProgress(translationY: Float)
        fun onSwipeDownEnd(translationY: Float, velocityY: Float)
    }

    var callback: Callback? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var startY = 0f
    private var startX = 0f
    private var dragging = false
    private var decided = false
    private var velocityTracker: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                dragging = false
                decided = false
                recycleTracker()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dy = ev.rawY - startY
                val dx = ev.rawX - startX
                if (!decided && abs(dy) > touchSlop) {
                    decided = true
                    // Only intercept if the swipe is downwards and vertically dominant
                    if (abs(dy) > abs(dx) && dy > 0) {
                        val scrollChild = findScrollableChildUnder(ev.rawX, ev.rawY)
                        val canScrollUp = scrollChild?.canScrollVertically(-1) == true
                        if (!canScrollUp) {
                            dragging = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                }
                return dragging
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                decided = false
                recycleTracker()
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dy = event.rawY - startY
                    val dx = event.rawX - startX
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) && dy > 0) {
                        val scrollChild = findScrollableChildUnder(event.rawX, event.rawY)
                        val canScrollUp = scrollChild?.canScrollVertically(-1) == true
                        if (!canScrollUp) {
                            dragging = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else {
                            return true
                        }
                    } else return true
                }
                val translation = (event.rawY - startY).coerceAtLeast(0f)
                callback?.onSwipeDownProgress(translation)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    velocityTracker?.computeCurrentVelocity(1000, maxFling)
                    val velocityY = velocityTracker?.yVelocity ?: 0f // down = positive
                    val translation = (event.rawY - startY).coerceAtLeast(0f)
                    callback?.onSwipeDownEnd(translation, velocityY)
                }
                dragging = false
                decided = false
                parent?.requestDisallowInterceptTouchEvent(false)
                recycleTracker()
                return true
            }
        }
        return true
    }

    private fun findScrollableChildUnder(rawX: Float, rawY: Float): View? {
        val root = getChildAt(0) as? ViewGroup ?: return getChildAt(0)
        return findScrollableChildUnderRecursive(root, rawX, rawY)
    }

    private fun findScrollableChildUnderRecursive(group: ViewGroup, rawX: Float, rawY: Float): View? {
        val location = IntArray(2)
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue

            child.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + child.width
            val bottom = top + child.height

            if (rawX >= left && rawX <= right && rawY >= top && rawY <= bottom) {
                if (child is androidx.recyclerview.widget.RecyclerView ||
                    child is androidx.core.widget.NestedScrollView ||
                    child is android.widget.ScrollView
                ) {
                    return child
                }
                if (child is ViewGroup) {
                    val nested = findScrollableChildUnderRecursive(child, rawX, rawY)
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
