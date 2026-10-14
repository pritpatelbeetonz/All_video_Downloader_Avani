package app.messages.data.telephony

import android.telephony.SmsMessage
import javax.inject.Inject
import javax.inject.Singleton

/** Character / segment breakdown for the compose counter (build doc §F4). */
data class SegmentInfo(
    val segments: Int,
    val remainingInSegment: Int,
    val isUnicode: Boolean,
) {
    /** Whether to surface the counter — hidden when empty, shown as a segment nears full. */
    fun shouldShow(textLength: Int): Boolean =
        textLength > 0 && (segments > 1 || remainingInSegment <= 10)
}

/**
 * Wraps the static [SmsMessage.calculateLength] so the composer can show
 * "1 of 2" / remaining-character feedback as the user types. GSM-7 packs 160
 * chars into one segment; a single Unicode char (emoji, many Indic scripts)
 * drops that to 70 — the counter makes that visible before the user is
 * surprised by a multi-part send.
 */
@Singleton
class SmsSegments @Inject constructor() {

    fun compute(text: String): SegmentInfo {
        if (text.isEmpty()) return SegmentInfo(0, 0, isUnicode = false)
        // int[]: [0]=segments, [1]=used, [2]=remaining-in-segment, [3]=encoding
        val parts = SmsMessage.calculateLength(text, false)
        val encoding = parts.getOrElse(3) { 0 }
        return SegmentInfo(
            segments = parts.getOrElse(0) { 1 },
            remainingInSegment = parts.getOrElse(2) { 0 },
            isUnicode = encoding == 3, // ENCODING_16BIT (UCS-2)
        )
    }
}
