package app.messages.ui.scheduling

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.TimeZone

/**
 * Two-step scheduled-send picker (§F13): pick the date, then the time. The date
 * picker reports a UTC midnight; we recombine its calendar Y/M/D with the chosen
 * local hour/minute to get a correct local epoch.
 *
 * Past instants can't be chosen: past **dates** are greyed out, and the time
 * step disables "Schedule" (with an inline hint) whenever the selected date+time
 * is already in the past — so a message can never silently fail to schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePickerDialog(
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }

    val chosenDate = pickedDateUtc
    if (chosenDate == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialMs,
            selectableDates = remember { TodayOnwardDates() },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickedDateUtc = dateState.selectedDateMillis },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialMs } }
        val timeState = rememberTimePickerState(
            initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCal.get(Calendar.MINUTE),
            is24Hour = false,
        )
        // Recomputes as the user spins the clock (hour/minute are snapshot state).
        val candidate = combine(chosenDate, timeState.hour, timeState.minute)
        val isPast = candidate <= System.currentTimeMillis()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a time") },
            text = {
                Column {
                    TimePicker(state = timeState)
                    if (isPast) {
                        Text(
                            "Pick a time in the future",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !isPast, onClick = { onConfirm(candidate) }) {
                    Text("Schedule")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

/** Allows today and any future date; greys out the past. */
@OptIn(ExperimentalMaterial3Api::class)
private class TodayOnwardDates : SelectableDates {
    private val todayUtcMidnight: Long = run {
        val local = Calendar.getInstance()
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMidnight

    override fun isSelectableYear(year: Int): Boolean =
        year >= Calendar.getInstance().get(Calendar.YEAR)
}

/** Combines a UTC-midnight date with a local hour/minute into a local epoch. */
private fun combine(dateUtcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0,
        )
    }.timeInMillis
}
