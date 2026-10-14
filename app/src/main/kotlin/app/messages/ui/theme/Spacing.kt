package app.messages.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing & geometry tokens per build doc §5.2.
 *
 * Scale: 4 / 8 / 12 / 16 / 24 / 32. Use these — do not hardcode dp values
 * in Compose modifiers.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 16dp screen edge padding. */
    val screenEdge = lg

    /** 72dp conversation tile height. */
    val tileHeight = 72.dp

    /** 40dp avatar circle. */
    val avatarSize = 40.dp

    /** Bubble radius — 18dp on outer corners, 4dp on the "tail" corner of
     *  consecutive same-sender groups. */
    val bubbleRadius = 18.dp
    val bubbleTailRadius = 4.dp

    /** Sheet / card radius. */
    val sheetRadius = 16.dp

    /** Pill / search bar radius. */
    val pillRadius = 28.dp

    /** Minimum touch target. */
    val minTouch = 48.dp

    /** Max bubble width as a fraction of available space. */
    const val BUBBLE_MAX_WIDTH_FRACTION = 0.78f
}
