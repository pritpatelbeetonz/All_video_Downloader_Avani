package app.messages.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class ScalableVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoDimensions(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(videoWidth, widthMeasureSpec)
        val height = getDefaultSize(videoHeight, heightMeasureSpec)
        if (videoWidth > 0 && videoHeight > 0) {
            val widthRatio = width.toFloat() / videoWidth
            val heightRatio = height.toFloat() / videoHeight
            val scale = maxOf(widthRatio, heightRatio)
            setMeasuredDimension((videoWidth * scale).toInt(), (videoHeight * scale).toInt())
        } else {
            setMeasuredDimension(width, height)
        }
    }
}
