package app.messages.ui.launcher.drawer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import app.messages.R
import app.messages.data.model.AppInfo
import app.messages.databinding.PopupAppContextBinding
import kotlin.math.roundToInt

class AppContextPopup(
    private val onAppInfo: (AppInfo) -> Unit,
    private val onToggleFavorite: (AppInfo) -> Unit,
    private val onUninstall: (AppInfo) -> Unit
) {
    private var popup: PopupWindow? = null
    private var currentPackage: String? = null
    private var showGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    private var enableOutsideTouchRunnable: Runnable? = null

    fun show(anchor: View, app: AppInfo) {
        // Same app already showing — keep current popup.
        if (popup?.isShowing == true && currentPackage == app.packageName) return

        val generation = ++showGeneration
        dismissInternal(clearPackage = false)
        currentPackage = app.packageName

        // Show on next frame so the previous popup window is fully gone first.
        anchor.post {
            if (generation != showGeneration) return@post
            if (!anchor.isAttachedToWindow) return@post
            showNow(anchor, app, generation)
        }
    }

    private fun showNow(anchor: View, app: AppInfo, generation: Int) {
        if (generation != showGeneration) return

        val context = anchor.context
        val binding = PopupAppContextBinding.inflate(LayoutInflater.from(context))
        val density = context.resources.displayMetrics.density
        val screenPadding = (10 * density).roundToInt()
        val arrowGap = (2 * density).roundToInt()
        val arrowWidth = (14 * density).roundToInt()
        val arrowHeight = (7 * density).roundToInt()
        val edgeInset = (10 * density).roundToInt()

        if (app.isFavorite) {
            binding.tvFavorite.setText(R.string.remove_from_favorites)
            binding.ivFavorite.setImageResource(R.drawable.ic_star)
        } else {
            binding.tvFavorite.setText(R.string.add_to_favorites)
            binding.ivFavorite.setImageResource(R.drawable.ic_star_outline)
        }

        val isHostApp = app.packageName == context.packageName

        val showUninstall = app.canUninstall && !isHostApp
        // For the host app, we show the divider and the "Already installed" row instead of Uninstall
        binding.dividerUninstall.visibility = if (showUninstall || isHostApp) View.VISIBLE else View.GONE
        binding.rowUninstall.visibility = if (showUninstall) View.VISIBLE else View.GONE
        binding.rowAlreadyInstalled.visibility = if (isHostApp) View.VISIBLE else View.GONE
        
        val showAppInfo = !isHostApp
        binding.rowAppInfo.visibility = if (showAppInfo) View.VISIBLE else View.GONE
        binding.dividerAppInfo.visibility = if (showAppInfo) View.VISIBLE else View.GONE

        binding.rowAppInfo.setOnClickListener {
            dismiss()
            onAppInfo(app)
        }
        binding.rowFavorite.setOnClickListener {
            dismiss()
            onToggleFavorite(app)
        }
        binding.rowUninstall.setOnClickListener {
            if (!showUninstall) return@setOnClickListener
            dismiss()
            onUninstall(app)
        }

        binding.arrowUp.visibility = View.GONE
        binding.arrowDown.visibility = View.GONE
        val maxMenuWidth = (150 * density).roundToInt()
        binding.menuCard.measure(
            View.MeasureSpec.makeMeasureSpec(maxMenuWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        binding.menuCard.layoutParams = binding.menuCard.layoutParams.apply {
            width = binding.menuCard.measuredWidth.coerceAtMost(maxMenuWidth)
        }
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardWidth = binding.menuCard.measuredWidth.coerceAtMost(maxMenuWidth)
        val cardHeight = binding.menuCard.measuredHeight
        val popupHeight = cardHeight + arrowHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val iconCenterX = location[0] + anchor.width / 2f
        val iconTop = location[1]
        val iconBottom = location[1] + anchor.height

        val spaceBelow = screenHeight - iconBottom - screenPadding
        val showBelow = spaceBelow >= popupHeight + arrowGap

        val preferLeftAnchor = iconCenterX < screenWidth / 2f
        var popupX = if (preferLeftAnchor) {
            (iconCenterX - edgeInset - arrowWidth / 2f).roundToInt()
        } else {
            (iconCenterX - cardWidth + edgeInset + arrowWidth / 2f).roundToInt()
        }
        val maxX = (screenWidth - cardWidth - screenPadding).coerceAtLeast(screenPadding)
        popupX = popupX.coerceIn(screenPadding, maxX)

        val minArrow = (6 * density).roundToInt()
        val maxArrow = (cardWidth - arrowWidth - minArrow).coerceAtLeast(minArrow)
        val arrowXInPopup = (iconCenterX - popupX - arrowWidth / 2f)
            .roundToInt()
            .coerceIn(minArrow, maxArrow)

        val activeArrow: ImageView
        val popupY: Int
        if (showBelow) {
            binding.arrowUp.visibility = View.VISIBLE
            binding.arrowDown.visibility = View.GONE
            activeArrow = binding.arrowUp
            popupY = iconBottom + arrowGap
        } else {
            binding.arrowDown.visibility = View.VISIBLE
            binding.arrowUp.visibility = View.GONE
            activeArrow = binding.arrowDown
            popupY = iconTop - popupHeight - arrowGap
        }
        positionArrow(activeArrow, arrowXInPopup)

        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val finalHeight = binding.root.measuredHeight

        // focusable=false lets the next long-press on another icon reach the list.
        // outsideTouchable starts false so the finger-up from this long-press
        // does not immediately dismiss the new popup.
        val window = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            /* focusable = */ false
        ).apply {
            elevation = 8f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = false
            isClippingEnabled = false
            setOnDismissListener {
                if (popup === this) {
                    popup = null
                    currentPackage = null
                }
            }
        }

        val safeY = if (showBelow) {
            popupY
        } else {
            (iconTop - finalHeight - arrowGap)
        }.coerceIn(screenPadding, (screenHeight - finalHeight - screenPadding).coerceAtLeast(screenPadding))

        if (!anchor.isAttachedToWindow || generation != showGeneration) return
        window.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, safeY)
        popup = window
        currentPackage = app.packageName

        val enableDelay = ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(120L)
        val enableOutside = Runnable {
            if (generation != showGeneration) return@Runnable
            val current = popup ?: return@Runnable
            if (!current.isShowing) return@Runnable
            current.isOutsideTouchable = true
            current.update()
        }
        enableOutsideTouchRunnable = enableOutside
        handler.postDelayed(enableOutside, enableDelay)
    }

    private fun positionArrow(arrow: ImageView, xInPopup: Int) {
        val params = (arrow.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(arrow.layoutParams.width, arrow.layoutParams.height)
        params.marginStart = xInPopup
        arrow.layoutParams = params
    }

    fun dismiss() {
        showGeneration++
        dismissInternal(clearPackage = true)
    }

    private fun dismissInternal(clearPackage: Boolean) {
        enableOutsideTouchRunnable?.let { handler.removeCallbacks(it) }
        enableOutsideTouchRunnable = null
        val current = popup
        popup = null
        if (clearPackage) currentPackage = null
        current?.setOnDismissListener(null)
        if (current?.isShowing == true) {
            current.dismiss()
        }
    }
}
