package app.messages.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.data.contacts.ContactSuggestion
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.theme.Spacing
import app.messages.ui.theme.appScreenBg
import app.messages.ui.thread.MessageComposer

/**
 * New-message screen — build doc §F4. Recipient autocomplete + multi-recipient
 * chips, character/segment counter, dual-SIM, then send. Ad-free surface. Aligned
 * to the app: [appScreenBg] + a custom Inter header (the Scaffold is kept for the
 * bottom composer + IME insets).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    vm: ComposeViewModel = hiltViewModel(),
) {
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val selectedSim by vm.selectedSim.collectAsStateWithLifecycle()
    val segment by vm.segmentInfo.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = appScreenBg(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBackIos,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "New message",
                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars),
                ),
            ) {
                if (segment.shouldShow(draft.length)) {
                    SegmentCounter(
                        segments = segment.segments,
                        remaining = segment.remainingInSegment,
                        unicode = segment.isUnicode,
                    )
                }
                MessageComposer(
                    text = draft,
                    onTextChange = vm::onDraftChange,
                    onSend = { vm.send { threadId -> if (threadId != null) onOpenThread(threadId) else onBack() } },
                    onAttach = { /* attachment sheet shares M3 wiring */ },
                    sims = vm.sims,
                    selectedSim = selectedSim,
                    onCycleSim = vm::cycleSim,
                )
            }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            RecipientField(
                recipients = recipients,
                query = query,
                onQueryChange = vm::onQueryChange,
                onCommitTyped = vm::commitTypedRecipient,
                onRemove = vm::removeRecipient,
            )
            HorizontalDivider()
            if (suggestions.isNotEmpty()) {
                SuggestionList(
                    suggestions = suggestions,
                    onPick = vm::addRecipient,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientField(
    recipients: List<Recipient>,
    query: String,
    onQueryChange: (String) -> Unit,
    onCommitTyped: () -> Unit,
    onRemove: (Recipient) -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        if (recipients.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
            ) {
                recipients.forEach { r -> RecipientChip(r, onRemove) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "To:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(Spacing.sm))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Name or number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitTyped() }),
            )
        }
    }
}

@Composable
private fun RecipientChip(recipient: Recipient, onRemove: (Recipient) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(scheme.secondaryContainer, RoundedCornerShape(Spacing.pillRadius))
            .padding(start = Spacing.md, end = Spacing.sm, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = recipient.label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove ${recipient.label}",
            modifier = Modifier
                .width(16.dp)
                .clickable { onRemove(recipient) },
            tint = scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<ContactSuggestion>,
    onPick: (ContactSuggestion) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(suggestions, key = { it.number }) { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                ContactAvatar(
                    address = s.number,
                    name = s.name,
                    photoUri = s.photoUri,
                    isGroup = false,
                    size = 40.dp,
                )
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        s.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentCounter(segments: Int, remaining: Int, unicode: Boolean) {
    val text = buildString {
        append("$remaining left")
        if (segments > 1) append(" · $segments parts")
        if (unicode) append(" · Unicode")
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
