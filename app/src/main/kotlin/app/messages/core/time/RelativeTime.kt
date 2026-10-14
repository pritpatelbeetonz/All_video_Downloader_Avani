package app.messages.core.time

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Compact, conversation-list-style timestamp.
 *
 * Today     → HH:mm    ("14:32")
 * Yesterday → "Yesterday"
 * This week → weekday abbrev ("Mon")
 * This year → "MMM d"  ("Jun 9")
 * Older     → "d/M/yy"
 *
 * The build doc's relative-time spec uses 24h format on the today branch.
 * If you change this, update the corresponding unit test in
 * `RelativeTimeTest` — that one pins the contract.
 */
fun formatRelativeTimestamp(
    then: Date,
    now: Date = Date(),
    locale: Locale = Locale.getDefault(),
): String {
    val today = startOfDay(now)
    val thatDay = startOfDay(then)
    val daysAgo = TimeUnit.MILLISECONDS.toDays(today.time - thatDay.time).toInt()

    if (daysAgo == 0) return format("HH:mm", locale, then)
    if (daysAgo == 1) return "Yesterday"
    if (daysAgo in 2..6) return format("EEE", locale, then)

    if (yearOf(now) == yearOf(then)) {
        return format("MMM d", locale, then)
    }
    return format("d/M/yy", locale, then)
}

/**
 * Every conversation tile formats a timestamp on each recomposition, and a fresh
 * [SimpleDateFormat] plus a [Calendar] per call was enough allocation churn to
 * show up as GC pauses while scrolling. Both are mutable and not thread-safe, so
 * they're cached per thread rather than shared.
 */
// ThreadLocal.withInitial is API 26+; this project ships to API 24.
private val formatters = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
    override fun initialValue() = HashMap<String, SimpleDateFormat>(8)
}
private val calendar = object : ThreadLocal<Calendar>() {
    override fun initialValue(): Calendar = Calendar.getInstance()
}

private fun format(pattern: String, locale: Locale, date: Date): String {
    val cache = formatters.get()!!
    val key = "$pattern|$locale"
    val formatter = cache.getOrPut(key) { SimpleDateFormat(pattern, locale) }
    return formatter.format(date)
}

private fun yearOf(d: Date): Int = calendar.get()!!.run {
    time = d
    get(Calendar.YEAR)
}

private fun startOfDay(d: Date): Date {
    val c = calendar.get()!!.apply {
        time = d
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.time
}

/**
 * Day-separator label for the thread view.
 *
 * Today / Yesterday → those words
 * This week         → weekday ("Monday")
 * This year         → "12 June"
 * Older             → "12 June 2024"
 */
fun formatDaySeparator(
    then: Date,
    now: Date = Date(),
    locale: Locale = Locale.getDefault(),
): String {
    val today = startOfDay(now)
    val thatDay = startOfDay(then)
    val daysAgo = TimeUnit.MILLISECONDS.toDays(today.time - thatDay.time).toInt()

    if (daysAgo == 0) return "Today"
    if (daysAgo == 1) return "Yesterday"
    if (daysAgo in 2..6) return format("EEEE", locale, then)

    return if (yearOf(now) == yearOf(then)) {
        format("d MMMM", locale, then)
    } else {
        format("d MMMM yyyy", locale, then)
    }
}

/** Clock time shown under a bubble — "14:32". */
fun formatClockTime(then: Date, locale: Locale = Locale.getDefault()): String =
    format("HH:mm", locale, then)

/** True when two instants fall on different calendar days. */
fun isDifferentDay(a: Date, b: Date): Boolean =
    startOfDay(a).time != startOfDay(b).time

@Suppress("unused")
private val unused: DateFormat? = null // keeps the DateFormat import path obvious
