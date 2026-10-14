package app.messages.ui.thread

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.messages.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val MAX_SECONDS = 60

/**
 * Voice-clip recorder (§5.3 Audio target). Starts recording on open, shows an
 * elapsed timer, and auto-stops at [MAX_SECONDS] to keep the MMS small. "Stop &
 * attach" hands the clip to [onRecorded]; cancelling/dismissing discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecorderSheet(
    onDismiss: () -> Unit,
    onRecorded: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var startedOk by remember { mutableStateOf(true) }
    var claimed by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        startedOk = recorder.start()
        onDispose { if (!claimed) recorder.cancel() }
    }

    fun finish() {
        val uri = recorder.stop()
        if (uri != null) {
            claimed = true
            onRecorded(uri)
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(startedOk) {
        if (!startedOk) return@LaunchedEffect
        while (elapsed < MAX_SECONDS) {
            delay(1000)
            elapsed += 1
        }
        finish() // auto-stop at the cap
    }

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { recorder.cancel(); onDismiss() },
        sheetState = sheetState,
    ) {
        val scheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(scheme.errorContainer),
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                if (startedOk) formatMmSs(elapsed) else "Microphone unavailable",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (startedOk) "Recording…" else "Couldn't start recording",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(Spacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextButton(onClick = { recorder.cancel(); onDismiss() }) { Text("Cancel") }
                Button(onClick = { finish() }, enabled = startedOk) { Text("Stop & attach") }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
