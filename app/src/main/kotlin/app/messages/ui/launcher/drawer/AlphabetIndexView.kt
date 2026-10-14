package app.messages.ui.launcher.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import app.messages.R

class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val alphabet = ('A'..'Z').toList() + '#'
    private var selectedIndex = -1

    private val normalTextColor = Color.parseColor("#80FFFFFF") // 50% white
    private val selectedTextColor = Color.parseColor("#1E88FF") // Accent blue

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        try {
            typeface = ResourcesCompat.getFont(context, R.font.geist_bold) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    var onLetterSelectedListener: ((letter: Char, touchY: Float) -> Unit)? = null
    var onDragEndedListener: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (height == 0 || width == 0) return

        val density = resources.displayMetrics.density
        // Compact spacing: cap max height per letter so letters aren't overly spaced on tall screens
        val maxSingleHeight = 14f * density
        val singleHeight = (height.toFloat() / alphabet.size).coerceAtMost(maxSingleHeight)
        val totalAlphabetHeight = singleHeight * alphabet.size
        val startOffsetY = ((height - totalAlphabetHeight) / 2f).coerceAtLeast(0f)

        val letterTextSize = (singleHeight * 0.72f).coerceIn(8.5f * density, 11f * density)
        val dotRadius = 1.3f * density

        val centerX = width / 2f

        // When space is tight (short screen / small device), adaptively show clean dots between key letters
        val step = when {
            singleHeight >= 12f * density -> 1
            singleHeight >= 8.5f * density -> 2
            else -> 3
        }

        for (i in alphabet.indices) {
            val centerY = startOffsetY + (i * singleHeight) + (singleHeight / 2f)

            if (i == selectedIndex) {
                paint.color = selectedTextColor
                paint.isFakeBoldText = true
                paint.textSize = letterTextSize
                val textCenterY = centerY - ((paint.descent() + paint.ascent()) / 2f)
                canvas.drawText(alphabet[i].toString(), centerX, textCenterY, paint)
            } else {
                paint.color = normalTextColor
                paint.isFakeBoldText = false

                val isKeyItem = (i % step == 0) || (i == alphabet.size - 1)
                if (isKeyItem) {
                    paint.textSize = letterTextSize
                    val textCenterY = centerY - ((paint.descent() + paint.ascent()) / 2f)
                    canvas.drawText(alphabet[i].toString(), centerX, textCenterY, paint)
                } else {
                    canvas.drawCircle(centerX, centerY, dotRadius, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val density = resources.displayMetrics.density
                val maxSingleHeight = 14f * density
                val singleHeight = (height.toFloat() / alphabet.size).coerceAtMost(maxSingleHeight)
                val totalAlphabetHeight = singleHeight * alphabet.size
                val startOffsetY = ((height - totalAlphabetHeight) / 2f).coerceAtLeast(0f)

                val touchYInAlphabet = event.y - startOffsetY
                val index = (touchYInAlphabet / singleHeight).toInt().coerceIn(0, alphabet.size - 1)

                if (index != selectedIndex || event.actionMasked == MotionEvent.ACTION_DOWN) {
                    selectedIndex = index
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val letter = alphabet[index]
                    val letterCenterY = startOffsetY + (index * singleHeight) + (singleHeight / 2f)
                    onLetterSelectedListener?.invoke(letter, top + letterCenterY)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragEndedListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun clearSelection() {
        if (selectedIndex != -1) {
            selectedIndex = -1
            invalidate()
        }
    }

    fun highlightLetter(letter: Char?) {
        val newIndex = if (letter != null) alphabet.indexOf(letter.uppercaseChar()) else -1
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            invalidate()
        }
    }
}
