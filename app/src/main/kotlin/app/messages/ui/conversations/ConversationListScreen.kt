package app.messages.ui.conversations

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.google.android.gms.ads.nativead.NativeAd
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import app.messages.core.prefs.SwipeAction
import app.messages.data.role.SmsRoleStatus
import app.messages.data.telephony.model.Conversation
import app.messages.ui.theme.Spacing
import app.messages.ui.theme.appCardBg
import app.messages.ui.theme.appScreenBg
import app.messages.ui.util.rememberHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Home — the conversation list, restyled to Figma 17:226: a large "Messages"
 * title with a sort/menu pill, a rounded search bar, conversation rows with a
 * chevron + blue unread badge, a circular compose FAB, and swipe-to-reveal a
 * red Delete (the configured swipe action). All telephony/ads wiring is intact.
 *
 * Scroll-performance rules this screen follows — breaking any of them makes the
 * list stutter no matter how little data is loaded:
 *  - **Nothing above the list changes height while scrolling.** The search field
 *    and category tabs are items *inside* the LazyColumn, so the search bar
 *    scrolls away for free. They used to sit in an outer Column behind an
 *    `AnimatedVisibility(expandVertically)`, which re-measured the whole list on
 *    every frame of the show/hide animation — a guaranteed hitch at the exact
 *    moment a scroll gesture began.
 *  - **Nothing in the composable body reads scroll position.** The header's
 *    search icon reads it through a deferred lambda, so only the header
 *    recomposes when it toggles.
 *  - **Every value handed to a row is stable across recompositions.** A freshly
 *    allocated lambda or a rebuilt ad-slot Set invalidates every visible row.
 */
// Swipe-pill action colors are fixed (self-colored backgrounds, white content) —
// they read correctly on both light and dark canvases.
private val Brand = Color(0xFF0057CC)
private val Danger = Color(0xFFEF3B3B)

@Composable
fun ConversationListScreen(
    onOpenThread: (String) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onPremium: () -> Unit,
    onArchived: () -> Unit,
    onBlocked: () -> Unit,
    onStarred: () -> Unit,
    onScheduled: () -> Unit,
    onBackup: () -> Unit,
    vm: ConversationListViewModel = hiltViewModel(),
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val role by vm.roleStatus.collectAsStateWithLifecycle()
    val swipeAction by vm.swipeAction.collectAsStateWithLifecycle()
    val adsEnabled by vm.nativeListAdsEnabled.collectAsStateWithLifecycle()
    var actionTarget by remember { mutableStateOf<Conversation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current

    val listState = rememberLazyListState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> vm.onScreenVisible()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> vm.onScreenHidden()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            vm.onScreenVisible()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onScreenHidden()
        }
    }

    // Deferred read: handing the State down instead of its value keeps scroll
    // position out of this function's recomposition scope.
    val headerSearchVisible = remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    // Read at call time so the remembered callbacks below stay stable in identity
    // without ever capturing a stale value.
    val currentSwipeAction by rememberUpdatedState(swipeAction)
    val currentOnOpenThread by rememberUpdatedState(onOpenThread)

    fun archiveWithUndo(threadId: String) {
        vm.archive(threadId)
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "Conversation archived",
                actionLabel = "Undo",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) vm.unarchive(threadId)
        }
    }

    val onSwipe: (Conversation) -> Unit = remember {
        { c ->
            when (currentSwipeAction) {
                SwipeAction.Archive -> {
                    vm.archive(c.threadId)
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Conversation archived",
                            actionLabel = "Undo",
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.unarchive(c.threadId)
                    }
                }
                SwipeAction.MarkRead -> vm.markRead(c.threadId)
                SwipeAction.Delete -> {
                    vm.delete(c.threadId)
                    scope.launch { snackbar.showSnackbar("Conversation deleted") }
                }
            }
        }
    }

    val onTileTap: (String) -> Unit = remember {
        { threadId ->
            vm.markRead(threadId)
            currentOnOpenThread(threadId)
        }
    }

    val onSelectCategory: (MessageCategory) -> Unit = remember {
        { category -> vm.selectCategory(category) }
    }

    val onTileLongPress: (Conversation) -> Unit = remember {
        { c ->
            haptics.longPress()
            actionTarget = c
        }
    }

    val onSetDefault: () -> Unit = remember {
        {
            // Hosted inside the launcher's pager this context can be a theme
            // wrapper rather than the Activity itself, and a plain cast would
            // silently swallow the tap.
            context.findActivity()?.let { activity -> vm.requestDefaultSms(activity) }
            Unit
        }
    }

    // A freshly built Set on every recomposition would change the `entries` key
    // below and invalidate every visible row.
    val adSlots = remember(adsEnabled, ui.others.size) {
        if (adsEnabled) vm.adSlots(ui.others.size) else emptySet()
    }
    val assignedAds by vm.assignedAds.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = appScreenBg(),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCompose,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Start chat")
            }
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            ListHeader(
                showSearchIcon = { headerSearchVisible.value },
                onSearchClick = onSearch,
                onMenu = {
                    context.startActivity(android.content.Intent(context, app.messages.ui.activity.MenuActivity::class.java))
                },
            )
            ConversationList(
                state = ui,
                swipeAction = swipeAction,
                assignedAds = assignedAds,
                adSlots = adSlots,
                listState = listState,
                showRoleBanner = role == SmsRoleStatus.Denied,
                onSearch = onSearch,
                onSelectCategory = onSelectCategory,
                onSetDefault = onSetDefault,
                onOpenThread = onTileTap,
                onLongPress = onTileLongPress,
                onSwipe = onSwipe,
                onSlotSettled = { vm.onSlotSettled(it) },
                onAdViewBound = { vm.onAdViewBound(it) },
                onAdViewReleased = { vm.onAdViewReleased(it) },
            )
        }
    }

    actionTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = sheetState,
        ) {
            ConversationActionSheet(
                conversation = target,
                onPinToggle = { vm.setPinned(target.threadId, !target.isPinned); actionTarget = null },
                onMuteToggle = { vm.setMuted(target.threadId, !target.isMuted); actionTarget = null },
                onArchive = { archiveWithUndo(target.threadId); actionTarget = null },
                onStarToggle = { vm.setStarred(target.threadId, !target.isStarred); actionTarget = null },
                onBlock = { vm.block(target.primaryAddress); actionTarget = null },
                onDelete = { vm.delete(target.threadId); actionTarget = null },
            )
        }
    }
}

@Composable
private fun ListHeader(
    showSearchIcon: () -> Boolean,
    onSearchClick: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Messages",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W800, fontSize = 28.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (showSearchIcon()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, "Menu", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hint = scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(appCardBg())
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = hint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Search Here", color = hint, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = hint, modifier = Modifier.size(13.dp))
        }
    }
}

private const val TYPE_CONVO = "convo"
private const val TYPE_AD = "ad"

@Composable
private fun ConversationList(
    state: ConversationListUiState,
    swipeAction: SwipeAction,
    assignedAds: Map<Int, NativeAd>,
    adSlots: Set<Int>,
    listState: LazyListState,
    showRoleBanner: Boolean,
    onSearch: () -> Unit,
    onSelectCategory: (MessageCategory) -> Unit,
    onSetDefault: () -> Unit,
    onOpenThread: (String) -> Unit,
    onLongPress: (Conversation) -> Unit,
    onSwipe: (Conversation) -> Unit,
    onSlotSettled: (Int) -> Unit,
    onAdViewBound: (NativeAd) -> Unit,
    onAdViewReleased: (NativeAd) -> Unit,
) {
    // Listen for scroll settle (!listState.isScrollInProgress) to promote preloaded ad and preload next buffer
    androidx.compose.runtime.LaunchedEffect(listState, adSlots, state.others) {
        androidx.compose.runtime.snapshotFlow {
            if (listState.isScrollInProgress) {
                null
            } else {
                val visible = listState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) {
                    0..10
                } else {
                    val visibleKeys = visible.mapNotNull { it.key as? String }
                    val visibleThreadIds = visibleKeys
                        .filter { it.startsWith("o_") }
                        .map { it.removePrefix("o_") }
                        .toSet()

                    val visibleIndices = state.others.mapIndexedNotNull { idx, c ->
                        if (c.threadId in visibleThreadIds) idx else null
                    }
                    if (visibleIndices.isNotEmpty()) {
                        (visibleIndices.minOrNull() ?: 0)..(visibleIndices.maxOrNull() ?: 0)
                    } else {
                        val first = visible.first().index
                        val last = visible.last().index
                        first..last
                    }
                }
            }
        }.collect { visibleRange ->
            if (visibleRange != null) {
                val sortedSlots = adSlots.sorted()
                val targetOrdinal = sortedSlots.indexOfFirst { slotIdx ->
                    slotIdx in (visibleRange.first - 1)..(visibleRange.last + 1)
                }
                if (targetOrdinal != -1) {
                    onSlotSettled(targetOrdinal)
                }
            }
        }
    }

    val entries = remember(state.others, adSlots, assignedAds) {
        buildList {
            var adOrdinal = 0
            state.others.forEachIndexed { index, convo ->
                if (index in adSlots) {
                    val distinctAd = assignedAds[adOrdinal]
                    if (distinctAd != null) {
                        add(ListEntry.Ad(atIndex = index, adOrdinal = adOrdinal, ad = distinctAd))
                        android.util.Log.d("ChatAds", "🏷️ [ChatAds] AD DISPLAYED IN SLOT ──> Ordinal #$adOrdinal at list index $index (Headline: \"${distinctAd.headline}\")")
                    }
                    adOrdinal++
                }
                add(ListEntry.Convo(convo))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // Chrome lives inside the list, so hiding the search bar costs a scroll
        // rather than a re-measure of everything below it.
        item(key = "search", contentType = "search") { SearchField(onClick = onSearch) }
        item(key = "tabs", contentType = "tabs") {
            CategoryTabs(selected = state.selectedCategory, onSelected = onSelectCategory)
        }
        if (showRoleBanner) {
            item(key = "role_banner", contentType = "banner") { SetDefaultBanner(onSetDefault) }
        }

        when {
            state.isLoading -> item(key = "loading") { LoadingState(Modifier.fillParentMaxSize()) }
            state.isEmpty -> item(key = "empty") { EmptyState(Modifier.fillParentMaxSize()) }
            else -> {
                if (state.pinned.isNotEmpty()) {
                    item(key = "header_pinned", contentType = "section") { SectionHeader("Pinned") }
                    items(
                        items = state.pinned,
                        key = { "p_${it.threadId}" },
                        contentType = { TYPE_CONVO },
                    ) { c ->
                        SwipeableTile(c, swipeAction, onOpenThread, onLongPress, onSwipe)
                    }
                    if (state.others.isNotEmpty()) {
                        item(key = "header_others", contentType = "section") { SectionHeader("All messages") }
                    }
                }
                items(
                    items = entries,
                    key = { e ->
                        when (e) {
                            is ListEntry.Convo -> "o_${e.conversation.threadId}"
                            is ListEntry.Ad -> "ad_${e.atIndex}_${e.adOrdinal}"
                        }
                    },
                    // Lets the LazyColumn reuse a recycled row's slot table instead of
                    // composing a conversation tile from scratch on every scroll.
                    contentType = { e ->
                        when (e) {
                            is ListEntry.Convo -> TYPE_CONVO
                            is ListEntry.Ad -> TYPE_AD
                        }
                    },
                ) { e ->
                    when (e) {
                        is ListEntry.Convo -> SwipeableTile(e.conversation, swipeAction, onOpenThread, onLongPress, onSwipe)
                        is ListEntry.Ad -> key(e.ad) {
                            NativeAdTile(
                                ad = e.ad,
                                onAdViewBound = onAdViewBound,
                                onAdViewReleased = onAdViewReleased,
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ListEntry {
    data class Convo(val conversation: Conversation) : ListEntry
    data class Ad(val atIndex: Int, val adOrdinal: Int, val ad: NativeAd) : ListEntry
}

/** How far a row slides open to expose the action button. */
private val SwipeRevealWidth = 96.dp

/**
 * Swiping a row no longer performs the action. It slides the row open to expose
 * the action button, and nothing happens until that button is tapped — a swipe
 * on a message list is far too easy to make by accident for it to archive or
 * delete outright, and the undo snackbar was the only thing standing between the
 * user and a silent mistake.
 *
 * Tapping the row itself while it is open just closes it, so the first tap after
 * an accidental swipe is always harmless.
 */
@Composable
private fun SwipeableTile(
    conversation: Conversation,
    swipeAction: SwipeAction,
    onOpenThread: (String) -> Unit,
    onLongPress: (Conversation) -> Unit,
    onSwipe: (Conversation) -> Unit,
) {
    val revealPx = with(LocalDensity.current) { SwipeRevealWidth.toPx() }
    // Keyed on the thread so a recycled row never inherits another row's offset.
    val offsetX = remember(conversation.threadId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isOpen = offsetX.value < -0.5f

    Box(modifier = Modifier.fillMaxWidth()) {
        // Only composed once the row has actually moved — an untouched list pays
        // nothing for the action pill.
        if (isOpen) {
            SwipeActionButton(
                action = swipeAction,
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onSwipe(conversation)
                },
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(conversation.threadId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                                offsetX.animateTo(target)
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                        }
                    }
                },
        ) {
            ConversationTile(
                conversation = conversation,
                onTap = {
                    if (isOpen) {
                        scope.launch { offsetX.animateTo(0f) }
                    } else {
                        onOpenThread(conversation.threadId)
                    }
                },
                onLongPress = { onLongPress(conversation) },
            )
        }
    }
}

@Composable
private fun SwipeActionButton(
    action: SwipeAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (icon, label, color) = when (action) {
        SwipeAction.Delete -> Triple(Icons.Outlined.Delete, "Delete", Danger)
        SwipeAction.Archive -> Triple(Icons.Outlined.Archive, "Archive", Brand)
        SwipeAction.MarkRead -> Triple(Icons.Outlined.DoneAll, "Read", Brand)
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(SwipeRevealWidth)
            .padding(top = 4.dp, bottom = 4.dp, end = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(color)
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W600)
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = label.uppercase(),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = Spacing.lg, bottom = Spacing.sm),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.W700,
            letterSpacing = 0.8.sp,
            fontSize = 11.sp,
        ),
        color = scheme.onSurface.copy(alpha = 0.6f),
    )
}

@Composable
private fun SetDefaultBanner(onSetDefault: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = Spacing.sm)
            .clip(RoundedCornerShape(Spacing.sheetRadius))
            .background(scheme.primaryContainer)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Message,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Set as default to send messages",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.W600,
                    color = scheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSetDefault) {
                Text("Set default", color = scheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        app.messages.ui.components.EmptyState(
            icon = Icons.Outlined.Forum,
            text = "No messages yet. Say hello 👋",
            hint = "Tap Start chat to begin",
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

/** Enum.values() allocates a fresh array per call; the tabs never change. */
private val Categories = MessageCategory.entries.toList()

@Composable
private fun CategoryTabs(
    selected: MessageCategory,
    onSelected: (MessageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(Categories, key = { it.name }) { category ->
            CategoryChip(
                label = when (category) {
                    MessageCategory.All -> "All"
                    MessageCategory.Personal -> "Personal"
                    MessageCategory.OTP -> "OTP"
                    MessageCategory.Transactions -> "Transactions"
                    MessageCategory.Unread -> "Unread"
                    MessageCategory.Archived -> "Archived"
                },
                selected = category == selected,
                onClick = { onSelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bgColor = if (selected) scheme.primary else scheme.surfaceContainerLow
    val contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
    val borderModifier = if (selected) Modifier else Modifier.border(1.dp, scheme.outline.copy(alpha = 0.12f), RoundedCornerShape(Spacing.pillRadius))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.pillRadius))
            .background(bgColor)
            .then(borderModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.W600 else FontWeight.W500
            ),
            color = contentColor
        )
    }
}

/**
 * Walks the [android.content.ContextWrapper] chain to the hosting Activity.
 * `LocalContext.current` is the Activity when this screen is MainActivity's
 * content, but a wrapper when it is a page of the launcher's pager.
 */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
