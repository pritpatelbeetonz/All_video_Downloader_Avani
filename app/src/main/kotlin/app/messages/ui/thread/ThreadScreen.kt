package app.messages.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.messages.data.telephony.model.SmsMessage
import app.messages.ui.conversations.ContactAvatar
import app.messages.ui.scheduling.SchedulePickerDialog
import app.messages.ui.theme.Spacing
import app.messages.ui.util.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Conversation/thread view — build doc §F3. The competitor-killing surface:
 * **never carries an ad** (enforced by `AdRules.prohibitedSurfaces`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    threadId: String,
    onBack: () -> Unit,
    vm: ThreadViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val selectedSim by vm.selectedSim.collectAsStateWithLifecycle()
    val pendingAttachment by vm.pendingAttachment.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val listState = rememberLazyListState()
    var actionTarget by remember { mutableStateOf<SmsMessage?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var showAttach by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showRecorder by remember { mutableStateOf(false) }
    var showGifSearch by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.setAttachment(imageAttachmentOf(context, uri)) }

    val pickGif = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) vm.setAttachment(imageAttachmentOf(context, uri)) }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok -> cameraUri?.let { if (ok) vm.setAttachment(imageAttachmentOf(context, it)) } }

    val requestMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showRecorder = true
        else scope.launch { snackbar.showSnackbar("Microphone permission is needed to record audio") }
    }

    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri ->
        if (uri != null) {
            val contactText = getContactAsText(context, uri)
            if (contactText != null) {
                vm.appendToDraft(contactText)
            } else {
                scope.launch { snackbar.showSnackbar("Couldn't read that contact") }
            }
        }
    }

    // Surface scheduled-send outcomes (success / cap reached / past time).
    LaunchedEffect(Unit) {
        vm.scheduleEvents.collect { snackbar.showSnackbar(it) }
    }

    // Items run oldest → newest, so a growing list means one of two very different
    // things: a genuinely new message appended at the end, or an older page
    // prepended at the front. Keying this on item *count* treated both the same,
    // so every pagination yanked the reader back to the bottom — which is what
    // made scrolling up through a long thread feel stuck. Key on the newest
    // message's id instead: paging older messages leaves it untouched.
    val newestMessageId = (ui.items.lastOrNull() as? ThreadItem.Message)?.message?.id
    var hasAnchoredToBottom by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(newestMessageId) {
        val lastIndex = ui.items.lastIndex
        if (newestMessageId == null || lastIndex < 0) return@LaunchedEffect
        if (!hasAnchoredToBottom) {
            // First paint: jump, don't animate — animating from the top of a long
            // thread scrolls through every bubble on the way down.
            listState.scrollToItem(lastIndex)
            hasAnchoredToBottom = true
            return@LaunchedEffect
        }
        // Only follow a new message if the user is already parked at the end;
        // otherwise they're reading history and must not be interrupted.
        val visible = listState.layoutInfo.visibleItemsInfo
        val isAtBottom = visible.isEmpty() ||
            visible.last().index >= listState.layoutInfo.totalItemsCount - 2
        if (isAtBottom) listState.animateScrollToItem(lastIndex)
    }

    // Pagination: load older messages when the user scrolls near the top. Emitting
    // on the boolean rather than the raw index means one request per entry into the
    // zone instead of one per index change within it.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex <= 2 }
            .distinctUntilChanged()
            .filter { it }
            .collect { vm.loadMore() }
    }

    Scaffold(
        topBar = {
            ThreadAppBar(
                title = ui.header?.displayName ?: "",
                address = ui.header?.primaryAddress ?: "",
                photoUri = ui.header?.photoUri,
                isGroup = ui.header?.isGroup == true,
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MessageComposer(
                text = draft,
                onTextChange = vm::onDraftChange,
                onSend = vm::send,
                onAttach = { showAttach = true },
                attachment = pendingAttachment,
                onClearAttachment = vm::clearAttachment,
                sims = vm.sims,
                selectedSim = selectedSim,
                onCycleSim = vm::cycleSim,
                // Bottom inset = max(keyboard, gesture nav bar): keeps the
                // composer above the keyboard when typing and above the gesture
                // pill otherwise, without double-padding when both are present.
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars),
                ),
            )
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = Spacing.sm,
                    top = Spacing.sm,
                ),
            ) {
                items(
                    items = ui.items,
                    key = { item ->
                        when (item) {
                            is ThreadItem.DaySeparator -> "sep_${item.label}"
                            is ThreadItem.Message -> "msg_${item.message.id}"
                        }
                    },
                ) { item ->
                    when (item) {
                        is ThreadItem.DaySeparator -> DaySeparatorRow(item.label)
                        is ThreadItem.Message -> MessageBubble(
                            message = item.message,
                            connectedAbove = item.connectedAbove,
                            connectedBelow = item.connectedBelow,
                            showMeta = item.showMeta,
                            onRetry = { vm.retry(item.message) },
                            onLongPress = { haptics.longPress(); actionTarget = item.message },
                            senderName = ui.header?.displayName,
                            senderPhotoUri = ui.header?.photoUri,
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        MessageActionSheet(
            message = target,
            onDismiss = { actionTarget = null },
            onStarToggle = {
                vm.toggleStar(target)
                actionTarget = null
            },
            onDelete = {
                vm.deleteMessage(target)
                actionTarget = null
            },
        )
    }

    if (showAttach) {
        AttachmentSheet(
            onDismiss = { showAttach = false },
            onGallery = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCamera = {
                val uri = createImageCaptureUri(context)
                cameraUri = uri
                takePhoto.launch(uri)
            },
            onGif = { showGifSearch = true },
            onContact = { pickContact.launch(null) },
            onSchedule = {
                if (draft.isBlank()) {
                    scope.launch { snackbar.showSnackbar("Type a message to schedule first") }
                } else {
                    showSchedule = true
                }
            },
            onAudio = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) showRecorder = true else requestMic.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }

    if (showRecorder) {
        AudioRecorderSheet(
            onDismiss = { showRecorder = false },
            onRecorded = { uri ->
                showRecorder = false
                vm.setAttachment(PendingAttachment(uri, "audio/3gpp", AttachmentKind.Audio))
            },
        )
    }

    if (showGifSearch) {
        GifSearchSheet(
            onDismiss = { showGifSearch = false },
            onPicked = { attachment ->
                showGifSearch = false
                vm.setAttachment(attachment)
            },
            onPickFromDevice = {
                showGifSearch = false
                pickGif.launch("image/gif")
            },
        )
    }

    if (showSchedule) {
        SchedulePickerDialog(
            initialMs = System.currentTimeMillis() + 60 * 60 * 1000L,
            onDismiss = { showSchedule = false },
            onConfirm = { atMs ->
                showSchedule = false
                vm.scheduleDraft(atMs)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadAppBar(
    title: String,
    address: String,
    photoUri: String?,
    isGroup: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(
                    address = address,
                    name = title.takeIf { it != address },
                    photoUri = photoUri,
                    isGroup = isGroup,
                    size = 36.dp,
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Only offer Call for a single, dialable number — not groups, and not
            // alphanumeric senders (OTP/service IDs like "VM-GOOGLE" you can't call).
            val dialable = address.any(Char::isDigit) && address.none(Char::isLetter)
            if (!isGroup && dialable) {
                val context = LocalContext.current
                IconButton(onClick = {
                    // ACTION_DIAL opens the dialer pre-filled (no CALL_PHONE permission needed).
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address")))
                    }
                }) {
                    Icon(Icons.Outlined.Call, contentDescription = "Call")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun DaySeparatorRow(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: SmsMessage,
    onDismiss: () -> Unit,
    onStarToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            ActionRow(Icons.Outlined.ContentCopy, "Copy") {
                clipboard.setText(AnnotatedString(message.body))
                onDismiss()
            }
            ActionRow(Icons.AutoMirrored.Outlined.Forward, "Forward") { onDismiss() }
            if (message.isStarred) {
                ActionRow(Icons.Outlined.Star, "Unstar", onClick = onStarToggle)
            } else {
                ActionRow(Icons.Outlined.StarBorder, "Star", onClick = onStarToggle)
            }
            ActionRow(Icons.Outlined.Info, "Details") { onDismiss() }
            ActionRow(Icons.Outlined.Delete, "Delete", destructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .retryClickable(onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
