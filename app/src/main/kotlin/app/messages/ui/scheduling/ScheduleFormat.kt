package app.messages.ui.scheduling

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Human label for a scheduled time, e.g. "Mon, Jun 16 · 9:30 AM".
 * [java.text.SimpleDateFormat] keeps us off java.time (minSdk 24, no desugaring).
 */
fun formatScheduledAt(ms: Long): String =
    SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(ms))
