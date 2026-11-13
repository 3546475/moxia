package org.example.moxia.ui.nav

import org.example.moxia.model.TopBarScrollMode
import org.example.moxia.model.EditGesture

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.model.ThemeStyle
import org.example.moxia.ui.component.ActionSheet
import org.example.moxia.ui.component.ActionSheetItem
import org.example.moxia.ui.theme.AppDialog
import org.example.moxia.ui.theme.AppTextButton
import org.example.moxia.ui.theme.LocalThemeStyle
import org.example.moxia.ui.theme.navigationBarBottom
import org.example.moxia.ui.theme.statusBarTop
import org.example.moxia.ui.component.filter.FeedFilterBar
import org.example.moxia.ui.component.filter.FeedFilterBarHeight
import org.example.moxia.ui.component.filter.FeedFilterButton
import org.example.moxia.ui.component.filter.FeedEmptyState
import org.example.moxia.ui.component.filter.FeedFilterToken
import org.example.moxia.ui.component.filter.FilterPanel
import org.example.moxia.ui.component.filter.dateLabel
import org.example.moxia.ui.component.filter.filterDatePresetOf
import org.example.moxia.ui.component.filter.memoFilterTokens
import org.example.moxia.ui.component.filter.sortLabel
import org.example.moxia.model.Shortcut
import org.example.moxia.model.MemoContentType
import org.example.moxia.model.filter.MemoFilter
import org.example.moxia.model.filter.MemoSort
import org.example.moxia.model.filter.normalizeTag
import org.example.moxia.ui.component.setting.ShortcutEditDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.example.moxia.model.Memo
import org.example.moxia.model.Visibility
import org.example.moxia.ui.component.GenericMemosListPane
import org.example.moxia.ui.component.MemoSearchBar
import org.example.moxia.ui.component.MemosScaffold
import org.example.moxia.ui.component.SyncStatusChip
import org.example.moxia.ui.component.SyncStatus
import org.example.moxia.ui.component.SyncStatusIconButton
import org.example.moxia.ui.component.SyncStatusPanel
import org.example.moxia.ui.component.syncStatusChipLabel
import org.example.moxia.ui.component.syncStatusOf
import org.example.moxia.ui.component.composer.EditorRequest
import org.example.moxia.ui.component.composer.rememberMemoEditorLauncher
import org.example.moxia.ui.profile.DraftsActivity
import org.example.moxia.ui.profile.SettingsActivity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import org.example.moxia.ui.component.item.DraftsCard
import org.example.moxia.ui.component.rememberScrollContext
import org.example.moxia.viewmodel.MemosViewModel
import org.example.moxia.viewmodel.RefreshSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// The frosted disc clips the blur, so the radius can be generous.

@Composable
fun MemosScreen(
    viewModel: MemosViewModel,
    pagerState: PagerState,
    onToggleNavBar: ((Boolean) -> Unit)? = null,
    isNavBarVisible: Boolean = true,
    navBarAlwaysVisible: Boolean = false,
    topBarScrollMode: TopBarScrollMode = TopBarScrollMode.ON_SCROLL,
    memoEditGesture: EditGesture = EditGesture.NONE,
    openComposer: Boolean = false,
    onComposerOpened: () -> Unit = {},
    /** Off when the floating bottom bar carries its own compose button. */
    showComposeFab: Boolean = true,
    /** Shortcut request: expand the search field once, then report back. */
    openSearch: Boolean = false,
    onSearchOpened: () -> Unit = {},
    /** Called when the notes feed first stops showing placeholders (cards, empty or error). */
    onFeedSettled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val miuixFeed = LocalThemeStyle.current == ThemeStyle.MIUIX
    // Feed cards read the user map through this derived state, so a ui-state tick that does not
    // change the map leaves them alone.
    val usersState = remember { derivedStateOf { uiState.users } }

    // The first frame that actually shows memo cards is when the app is usable; report it so the
    // system and Play vitals measure that instead of the first frame.
    val activity = LocalActivity.current
    var reportedFullyDrawn by remember { mutableStateOf(false) }
    val hasFeedContent = uiState.userMemoList.list.items.isNotEmpty()
    LaunchedEffect(hasFeedContent) {
        if (!hasFeedContent || reportedFullyDrawn) return@LaunchedEffect
        // The first frame draws the cards; report at the start of the next one, so the draw that
        // shows the feed has actually happened.
        withFrameNanos { }
        withFrameNanos { }
        reportedFullyDrawn = true
        activity?.reportFullyDrawn()
    }

    val memosListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val activeFeed = MemoFeed.entries[pagerState.currentPage]
    val pagerScope = rememberCoroutineScope()
    // 探索 is a page of this feed, so Back leaves it for 笔记 before it leaves the app. Registered
    // here (inner composition) so it takes precedence over the destination-level handler.
    BackHandler(enabled = activeFeed == MemoFeed.EXPLORE) {
        pagerScope.launch {
            pagerState.animateScrollToPage(MemoFeed.entries.indexOf(MemoFeed.MEMOS))
        }
    }
    val listState = if (activeFeed == MemoFeed.MEMOS) memosListState else exploreListState
    // A filter replaces the result set; keeping the old offset would land the user on an
    // arbitrary memo of the new list. Scroll first, then let the request fill it in.
    // Only a real filter change scrolls: coming back to the feed must keep the position the
    // user left it at, so the first composition is not treated as a change.
    var appliedFilter by remember { mutableStateOf<MemoFilter?>(null) }
    LaunchedEffect(uiState.userMemoList.filter) {
        val filter = uiState.userMemoList.filter
        if (appliedFilter != null && appliedFilter != filter) {
            memosListState.scrollToItem(0)
        }
        appliedFilter = filter
    }
    val activeMemos = if (activeFeed == MemoFeed.MEMOS) {
        uiState.userMemoList.list.items
    } else {
        uiState.exploreMemoList.list.items
    }
    var isSearchExpanded by remember(activeFeed) { mutableStateOf(false) }
    val density = LocalDensity.current
    var feedHeaderHeight by remember { mutableStateOf(64.dp) }
    val context = LocalContext.current

    // --- Feed filter: one chrome entry opening the structured panel, plus a pinned summary of
    // what is applied. Everything goes through FilterDelegate, so the request filter, the summary
    // and the persisted copy can never disagree.
    var showFilterPanel by remember { mutableStateOf(false) }
    // The header entry that replaced the tab row: it opens the feed/space switcher.
    var showFeedSwitcher by remember { mutableStateOf(false) }
    val feedFilter = uiState.userMemoList.filter
    // The filter owns the scope, so the header names the space it queries instead of a memory
    // of one: restoring a session cannot leave the title and the feed disagreeing.
    val scopeSpace = feedFilter.space?.let { name ->
        uiState.space.spaces.firstOrNull { it.name == name }
    }
    // Non-null when the "save as view" dialog is open, carrying the compiled filter expression.
    var creatingViewCel by remember { mutableStateOf<String?>(null) }
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    val feedFilterActive = feedFilter.isActive
    // Explore has no filter model yet, so the entry only exists on the notes feed.
    val feedFilterAvailable = activeFeed == MemoFeed.MEMOS
    val clearFeedFilter: () -> Unit = { viewModel.filterDelegate.clear() }
    val selectedView = remember(feedFilter.viewName, uiState.userMemoList.shortcuts) {
        uiState.userMemoList.shortcuts.firstOrNull { it.name != null && it.name == feedFilter.viewName }
    }
    val fallbackViewLabel = stringResource(R.string.memo_filter_title)
    val dateTokenLabel = if (feedFilter.date == null) {
        null
    } else {
        filterDatePresetOf(feedFilter.date)?.dateLabel()
            ?: stringResource(R.string.memo_filter_date_custom)
    }
    val sortTokenLabel = if (feedFilter.sort == MemoSort.PINNED_DESC) null else feedFilter.sort.sortLabel()
    val typeLabels = mapOf(
        MemoContentType.LINK to stringResource(R.string.profile_stats_links),
        MemoContentType.CODE to stringResource(R.string.profile_stats_code),
        MemoContentType.TODO to stringResource(R.string.profile_stats_todo)
    )
    // Tag suggestions come from both sources: the profile stats know tags the feed has not loaded
    // yet, and the loaded feed knows tags the stats snapshot may be missing (offline, new notes).
    // Inside a space only what the space itself has is offered: the stats cover the whole account.
    val feedSpace = feedFilter.space
    val tagCounts = remember(
        feedSpace, uiState.session.userStats?.tagCount, uiState.userMemoList.list.items
    ) {
        val fromStats: Map<String, Int> = if (feedSpace == null) {
            uiState.session.userStats?.tagCount.orEmpty()
        } else {
            emptyMap()
        }
        val normalizedStats = fromStats.mapKeys { (tag, _) -> normalizeTag(tag) }
        val fromFeed = uiState.userMemoList.list.items
            .flatMap { it.tags.orEmpty() }
            .groupingBy { tag -> normalizeTag(tag) }
            .eachCount()
        (normalizedStats.keys + fromFeed.keys).associateWith { tag ->
            maxOf(normalizedStats[tag] ?: 0, fromFeed[tag] ?: 0)
        }
    }
    val filterTokens = remember(
        feedFilter, selectedView, fallbackViewLabel, dateTokenLabel, sortTokenLabel, typeLabels
    ) {
        memoFilterTokens(
            filter = feedFilter,
            view = selectedView,
            fallbackLabel = fallbackViewLabel,
            dateLabel = dateTokenLabel,
            sortLabel = sortTokenLabel,
            typeLabels = typeLabels
        )
    }
    val removeFilterToken: (FeedFilterToken) -> Unit = { token ->
        when (token.kind) {
            FeedFilterToken.Kind.VIEW -> viewModel.filterDelegate.apply(feedFilter.withoutView())
            FeedFilterToken.Kind.TAG -> token.tag?.let { viewModel.filterDelegate.toggleTag(it) }
            FeedFilterToken.Kind.DATE -> viewModel.filterDelegate.apply(feedFilter.copy(date = null))
            FeedFilterToken.Kind.SORT -> viewModel.filterDelegate.apply(
                feedFilter.copy(sort = MemoSort.PINNED_DESC)
            )
            FeedFilterToken.Kind.TYPE -> token.type?.let { type ->
                viewModel.filterDelegate.apply(feedFilter.copy(types = feedFilter.types - type))
            }
        }
    }
    val feedFilterButton: @Composable () -> Unit = {
        if (feedFilterAvailable) {
            FeedFilterButton(
                active = feedFilterActive,
                onClick = { showFilterPanel = true }
            )
        }
    }

    // The panel belongs to the notes feed; leaving it open across a tab switch would edit the
    // wrong feed's state.
    LaunchedEffect(activeFeed) { showFilterPanel = false }

    val openEditor = rememberMemoEditorLauncher(viewModel)
    val draftsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshAfterEditor()
    }
    val composeNew: () -> Unit = {
        uiState.accounts.firstOrNull { it.isActive }?.let { account ->
            // The feed's scope is the placement the user is looking at, so a memo written from a
            // space lands in it. SPACE visibility is the only one a placement is valid with.
            val space = feedFilter.space
            openEditor(EditorRequest(account.id, titleRes = R.string.memo_composer_fab_new_memo,
                visibility = if (space != null) Visibility.SPACE
                else uiState.session.userSettings?.memoVisibility ?: Visibility.PRIVATE,
                space = space))
        }
    }
    var showDraftPrompt by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(true) }
    // The compose button opens the composer on tap; holding it reveals the other entries.
    var showQuickActions by remember { mutableStateOf(false) }
    var fabLongPressConsumed by remember { mutableStateOf(false) }
    var searchFromSheet by remember { mutableStateOf(false) }
    // Miuix keeps the sync status next to the search action instead of inside a
    // search field, so its panel is owned here.
    var showSyncPanel by remember { mutableStateOf(false) }
    val cacheSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshAfterProfileDetails()
    }
    val composeAction: () -> Unit = {
        if (uiState.draft.drafts.isNotEmpty()) showDraftPrompt = true else composeNew()
    }
    fun onFabTap() {
        // A long press also reports an up event, so the trailing click must be swallowed.
        if (fabLongPressConsumed) fabLongPressConsumed = false else composeAction()
    }


    // FAB expansion based on scroll direction
    val scrollContext = rememberScrollContext(listState = listState)

    // Explicitly handle initial state or non-scroll updates if needed
    LaunchedEffect(listState, scrollContext.isScrollingDown) {
        isFabExpanded = !scrollContext.isScrollingDown
    }

    // The bars are edge-to-edge overlays: the system navigation area (including the gesture
    // handle) sits between them and the screen edge, so the content has to clear that inset too.
    val navigationBarInset = navigationBarBottom()

    // The feed runs under the status bar, so the chrome above the first card is the status bar
    // band plus whatever the header itself draws.
    val statusBarInset = statusBarTop()

    val bottomPadding by animateDpAsState(
        targetValue = (if (isNavBarVisible) 80.dp else 16.dp) + navigationBarInset,
        label = "BottomPadding"
    )

    // The bottom bar floats over the content, so the detail pane must reserve its height to keep
    // its own floating button clear. Null callback means this layout has no bottom bar at all.
    val navBarInset by animateDpAsState(
        targetValue = if (onToggleNavBar != null && isNavBarVisible) {
            80.dp + navigationBarInset
        } else {
            // With the bar gone the page still runs under the gesture handle, so the detail
            // pane's own floating action keeps that inset instead of dropping onto it.
            navigationBarInset
        },
        label = "NavBarInset"
    )

    // Handle external composer open request (e.g. from widget)
    LaunchedEffect(openComposer) {
        if (openComposer) {
            // Same logic as FAB click
            if (uiState.draft.drafts.isNotEmpty()) {
                showDraftPrompt = true
            } else {
                composeNew()
            }
            onComposerOpened()
        }
    }

    // Double tap refresh logic: scroll to top
    var lastProcessedTrigger by remember { mutableLongStateOf(uiState.refreshTrigger) }
    LaunchedEffect(uiState.refreshTrigger) {
        val activeRefreshSource = if (activeFeed == MemoFeed.MEMOS) {
            RefreshSource.USerMemos
        } else {
            RefreshSource.ExploreMemos
        }
        if (uiState.refreshTrigger > lastProcessedTrigger && uiState.refreshSource == activeRefreshSource) {
            listState.animateScrollToItem(0)
        }
        lastProcessedTrigger = uiState.refreshTrigger
    }

    MemosScaffold(
        // The feed is the page whose list pane starts at the window's top edge, so its header
        // reserves the status bar itself.
        listPaneUnderStatusBar = true,
        viewModel = viewModel,
        memos = activeMemos,
        listState = listState,
        onToggleNavBar = { onToggleNavBar?.invoke(it) },
        isNavBarVisible = isNavBarVisible,
        navBarAlwaysVisible = navBarAlwaysVisible,
        memoEditGesture = memoEditGesture,
        navBarInset = navBarInset,
        navigationKey = activeFeed,
        openSearch = openSearch || searchFromSheet,
        onSearchOpened = {
            searchFromSheet = false
            // The scaffold expanded the field on its own, so mirror it here: paging must stop
            // while the search panel is open even when the user never tapped the field.
            isSearchExpanded = true
            onSearchOpened()
        },
        listHeader = { showSearchBar, searchExpanded, onSearchExpandedChange ->
            // The summary stays pinned under the chrome: a filtered feed must say so even after
            // the tab row has scrolled away. It is hidden while search is open, because the
            // results are not filtered by the feed's conditions.
            val filterBar: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = feedFilterAvailable && feedFilterActive && !searchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    FeedFilterBar(
                        tokens = filterTokens,
                        // Only one condition can be active until the structured filter lands,
                        // so removing any token means clearing the feed filter.
                        onRemove = { clearFeedFilter() },
                        onClearAll = clearFeedFilter
                    )
                }
            }
            if (miuixFeed) {
                // One row of chrome: text tabs on the left, filter and search on the right. The
                // search field itself only exists while expanded, so the feed starts near the top
                // of the screen instead of under two stacked capsules.
                val rowVisible =
                    (topBarScrollMode == TopBarScrollMode.ALWAYS || showSearchBar) && !searchExpanded
                val tabsVisible = topBarScrollMode == TopBarScrollMode.ALWAYS || showSearchBar
                // Closing the search panel already fades it out above this row; growing the row
                // back in at the same time read as a second, shrinking motion. It appears in
                // place instead when the change was caused by the search panel closing.
                var wasSearchExpanded by remember { mutableStateOf(searchExpanded) }
                val returningFromSearch = wasSearchExpanded && !searchExpanded
                LaunchedEffect(searchExpanded) { wasSearchExpanded = searchExpanded }
                Column(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = rowVisible,
                        enter = if (returningFromSearch) {
                            EnterTransition.None
                        } else {
                            fadeIn() + expandVertically()
                        },
                        // Expanding the search replaces the row outright, so it must not
                        // animate the pane downwards while the panel animates in.
                        exit = if (searchExpanded) ExitTransition.None else fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = tabsVisible) {
                                FeedSwitcher(
                                    feed = activeFeed,
                                    space = scopeSpace,
                                    modifier = Modifier.testTag("memo_feed_tabs"),
                                    onClick = { showFeedSwitcher = true }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            // An idle sync state carries no information, so it stays silent.
                            // Miuix's feed header is a single fixed-height row, so the status
                            // text rides inline here instead of floating over the list: it can
                            // never grow the row and shift the feed.
                            if (syncStatusOf(uiState) != SyncStatus.IDLE) {
                                syncStatusChipLabel(uiState)?.let { label ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                SyncStatusIconButton(
                                    uiState = uiState,
                                    onClick = { showSyncPanel = true }
                                )
                            }
                            feedFilterButton()
                            IconButton(onClick = { onSearchExpandedChange(true) }) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.memo_search_placeholder)
                                )
                            }
                        }
                    }
                    filterBar()
                }
            } else {
                // ON_SCROLL hides the tabs with the header; ALWAYS keeps them pinned.
                val tabsVisible = if (topBarScrollMode == TopBarScrollMode.ON_SCROLL) {
                    showSearchBar && !searchExpanded
                } else {
                    !searchExpanded
                }
                Column(Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = tabsVisible,
                        enter = slideInVertically { -it } + expandVertically() + fadeIn(),
                        exit = slideOutVertically { -it } + shrinkVertically() + fadeOut()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp)) {
                            FeedSwitcher(
                                feed = activeFeed,
                                space = scopeSpace,
                                modifier = Modifier
                                    .testTag("memo_feed_tabs")
                                    .onSizeChanged {
                                        feedHeaderHeight = with(density) { it.height.toDp() }
                                    }
                                    .padding(bottom = 16.dp),
                                onClick = { showFeedSwitcher = true }
                            )
                        }
                    }
                    filterBar()
                }
            }
        },
        listPane = { onMemoClick ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("memo_feed_pager"),
                userScrollEnabled = !isSearchExpanded && !showDraftPrompt,
                key = { MemoFeed.entries[it] }
            ) { page ->
                val feed = MemoFeed.entries[page]
                // The summary is a real chrome row: it pushes the list down by its own height,
                // animated so applying a filter does not make the cards jump.
                val filterBarInset by animateDpAsState(
                    targetValue = if (feedFilterAvailable && feedFilterActive) FeedFilterBarHeight else 0.dp,
                    label = "feedFilterBarInset"
                )
                val headerTop = if (miuixFeed) {
                    statusBarInset + 64.dp + filterBarInset
                } else {
                    statusBarInset + 88.dp + feedHeaderHeight + filterBarInset
                }
                val contentPadding = PaddingValues(
                    start = 16.dp,
                    top = headerTop,
                    end = 16.dp,
                    bottom = bottomPadding
                )
                // Remember where the first card lands so the launch shell can place its
                // placeholders at the same spot on the next cold start.
                LaunchedEffect(headerTop) {
                    // The base offset only: a filter bar pushes the list down while it is shown,
                    // and the launch shell always starts without one.
                    org.example.moxia.data.StartupMirror.updateFeedTopInset(
                        context,
                        (headerTop - filterBarInset).value
                    )
                }
                // The pull-to-refresh chip floats over the list, so it has to clear the chrome
                // instead of being clipped by it: Miuix has one header row, MD has the tabs plus
                // the floating search field.
                val indicatorPadding = if (miuixFeed) {
                    statusBarInset + 64.dp + filterBarInset
                } else {
                    statusBarInset + feedHeaderHeight + 56.dp + filterBarInset
                }
                when (feed) {
                    MemoFeed.MEMOS -> MemosListPane(
                        viewModel = viewModel,
                        listState = memosListState,
                        onMemoClick = onMemoClick,
                        contentPadding = contentPadding,
                        indicatorTopPadding = indicatorPadding,
                        onDraftsCardClick = { draftsLauncher.launch(Intent(context, DraftsActivity::class.java)) },
                        onHashtagClick = { tag -> viewModel.filterDelegate.toggleTag(tag) },
                        emptyState = {
                            FeedEmptyState(
                                filtered = feedFilter.hasConditions,
                                onClearFilter = clearFeedFilter,
                                onChangeFilter = { showFilterPanel = true }
                            )
                        },
                        onFeedSettled = onFeedSettled,
                        isActive = activeFeed == feed
                    )
                    MemoFeed.EXPLORE -> GenericMemosListPane(
                        viewModel = viewModel,
                        modifier = Modifier.testTag("memo_feed_explore"),
                        memos = uiState.exploreMemoList.list.items,
                        isLoading = uiState.exploreMemoList.list.isLoading,
                        isRefreshing = uiState.isRefreshing && uiState.refreshSource == RefreshSource.ExploreMemos,
                        nextPageToken = uiState.exploreMemoList.list.nextPageToken,
                        onLoadMore = { viewModel.loadMoreExploreMemos() },
                        onRefresh = { viewModel.fetchExploreMemos(refresh = true) },
                        onMemoClick = onMemoClick,
                        listState = exploreListState,
                        contentPadding = contentPadding,
                        indicatorTopPadding = indicatorPadding,
                        userProvider = { memo -> uiState.users[memo.creator] },
                        errorTitle = stringResource(R.string.common_error_failed_to_load_explore),
                        showSkeleton = !uiState.exploreMemoList.list.initialLoadFinished,
                        isActive = activeFeed == feed
                    )
                }
            }
        },
        overlay = { onMemoClick, showSearchBar, searchExpanded, onSearchExpandedChange, isDualPane, isDetailVisible ->
            val miuixSearch = miuixFeed
            val searchBar: @Composable () -> Unit = {
                key(activeFeed) {
                    MemoSearchBar(
                        viewModel = viewModel,
                        isExplore = activeFeed == MemoFeed.EXPLORE,
                        placeholder = stringResource(
                            if (activeFeed == MemoFeed.EXPLORE) R.string.memo_search_explore_placeholder
                            else R.string.memo_search_placeholder
                        ),
                        onMemoClick = onMemoClick,
                        // Miuix has no collapsed field: the header row's search action
                        // drives the expanded panel, so the state is hoisted there.
                        expandedOverride = if (miuixSearch) searchExpanded else null,
                        onExpandedChange = {
                            isSearchExpanded = it
                            onSearchExpandedChange(it)
                        }
                    )
                }
            }
            // Sync/cache status floats over the feed instead of living in the
            // list's header item: a row that appears and disappears inside the
            // LazyColumn re-measures the header and shifts every memo card.
            // It is drawn before the search bar so the expanding panel covers
            // it, and it stays out of the way while search is open.
            // Offline is left out on purpose - the search bar's red cloud icon
            // already reports it.
            if (!miuixSearch && !searchExpanded) {
                SyncStatusChip(
                    uiState = uiState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                )
            }

            if (miuixSearch) {
                searchBar()
            } else {
                AnimatedVisibility(
                    visible = if (topBarScrollMode == TopBarScrollMode.ALWAYS) {
                        !searchExpanded || isDualPane || !isDetailVisible
                    } else {
                        (showSearchBar || searchExpanded) && (!searchExpanded || isDualPane || !isDetailVisible)
                    },
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)) {
                    // The MD filter entry rides with the floating search field: both are the
                    // list's chrome, and they appear and hide together. While the search panel
                    // is expanded the field needs the full width, so the button steps aside.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) { searchBar() }
                        if (!searchExpanded) {
                            feedFilterButton()
                        }
                    }
                }
            }

            // FAB for creating new memo
            if (showComposeFab && uiState.session.currUser != null && !searchExpanded) {
                // Animate FAB position only if nav bar can be toggled (onToggleNavBar provided)
                val fabBottomPadding by animateDpAsState(
                    targetValue = (if (onToggleNavBar != null && isNavBarVisible) 96.dp else 16.dp) +
                        navigationBarInset,
                    label = "fabBottomPadding"
                )

                if (miuixFeed) {
                    // The compose button is always drawn here, never with Miuix's own FAB (it paints
                    // an inner squircle that shows up as a white block) and never frosted: the blur
                    // effect masks itself with the same squircle, which left a visible seam inside
                    // the circle. A small opaque circle on a shadow is what a FAB is anyway.
                    Box(
                        modifier = Modifier
                            .testTag("memo_feed_compose")
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = fabBottomPadding)
                            .fabLongPress { showQuickActions = true; fabLongPressConsumed = true }
                            .size(56.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary)
                            .clickable(onClick = ::onFabTap),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.memo_composer_fab_new_memo),
                            // White on the day theme's dark green. The night primary is a light
                            // green, where a white plus would all but disappear, so it falls back
                            // to the palette's own content colour.
                            tint = if (MiuixTheme.colorScheme.primary.luminance() < 0.5f) {
                                Color.White
                            } else {
                                MiuixTheme.colorScheme.onPrimary
                            }
                        )
                    }
                } else {
                    ExtendedFloatingActionButton(
                    onClick = ::onFabTap,
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = if (isFabExpanded) null
                                else stringResource(R.string.memo_composer_fab_new_memo)
                        )
                    },
                    text = {
                        Text(text = stringResource(R.string.memo_composer_fab_new_memo))
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .testTag("memo_feed_compose")
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding)
                        .fabLongPress { showQuickActions = true; fabLongPressConsumed = true }
                    )
                }
            }
        })

    // The structured filter panel: drafts are edited there and committed in one request.
    FilterPanel(
        show = showFilterPanel,
        filter = feedFilter,
        shortcuts = uiState.userMemoList.shortcuts,
        tagCounts = tagCounts,
        onDismiss = { showFilterPanel = false },
        onApply = { filter ->
            showFilterPanel = false
            viewModel.applyFilter(filter)
        },
        onManageShortcuts = {
            showFilterPanel = false
            context.startActivity(
                SettingsActivity.createIntent(context, SettingsSection.SHORTCUTS)
            )
        },
        onSaveAsView = { cel ->
            showFilterPanel = false
            creatingViewCel = cel
        },
        onEditShortcut = { shortcut ->
            showFilterPanel = false
            editingShortcut = shortcut
        }
    )

    // The switcher: browse feeds on top, then the spaces of this server, then the management
    // entry points. A space is a scope of the notes feed as well as a place of its own.
    if (showFeedSwitcher) {
        FeedSwitcherSheet(
            activeFeed = activeFeed,
            spaces = uiState.space,
            selectedSpace = feedFilter.space,
            onSelectFeed = { feed ->
                showFeedSwitcher = false
                pagerScope.launch {
                    pagerState.animateScrollToPage(MemoFeed.entries.indexOf(feed))
                }
            },
            onSelectSpace = { space -> viewModel.selectSpace(space) },
            onCreateSpace = {
                showFeedSwitcher = false
                context.startActivity(SettingsActivity.spacesIntent(context, create = true))
            },
            onManageSpaces = {
                showFeedSwitcher = false
                context.startActivity(SettingsActivity.spacesIntent(context, create = false))
            },
            onDismiss = { showFeedSwitcher = false }
        )
    }

    // "Save as view" reuses the shortcut editor from settings: it already validates the expression    // and reports server errors, so a failed save has somewhere to show up.
    creatingViewCel?.let { cel ->
        ShortcutEditDialog(
            title = stringResource(R.string.memo_filter_save_view_title),
            initialFilter = cel,
            onDismiss = { creatingViewCel = null },
            onConfirm = { title, filterText, onSuccess, onError ->
                viewModel.shortcutDelegate.createShortcut(title, filterText, onSuccess, onError)
            }
        )
    }
    editingShortcut?.let { shortcut ->
        ShortcutEditDialog(
            title = stringResource(R.string.profile_shortcuts_edit),
            initialTitle = shortcut.title.orEmpty(),
            initialFilter = shortcut.filter.orEmpty(),
            onDismiss = { editingShortcut = null },
            onConfirm = { title, filterText, onSuccess, onError ->
                viewModel.shortcutDelegate.updateShortcut(shortcut, title, filterText, onSuccess, onError)
            }
        )
    }

    // Failed attachment uploads are only read when the panel is opened: they change rarely and the
    // list is a diagnostic view.
    LaunchedEffect(showSyncPanel) {
        if (showSyncPanel) viewModel.refreshFailedUploads()
    }

    if (showSyncPanel) {
        SyncStatusPanel(
            uiState = uiState,
            onDismiss = { showSyncPanel = false },
            onDeleteOp = { opId -> viewModel.deletePendingOp(opId) },
            onManageCache = {
                showSyncPanel = false
                cacheSettingsLauncher.launch(
                    SettingsActivity.createIntent(context, SettingsSection.OFFLINE)
                )
            },
            onRetryOp = { opId -> viewModel.retryPendingOp(opId) },
            onDiscardFailedUpload = { upload -> viewModel.discardFailedUpload(upload) }
        )
    }

    // Held compose button: the other ways into the feed without leaving it.
    if (showQuickActions) {
        ActionSheet(onDismissRequest = {
            showQuickActions = false
            fabLongPressConsumed = false
        }) {
            Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            ActionSheetItem(
                text = { Text(stringResource(R.string.memo_composer_fab_new_memo)) },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                onClick = {
                    showQuickActions = false
                    fabLongPressConsumed = false
                    composeAction()
                }
            )
            ActionSheetItem(
                text = { Text(stringResource(R.string.drafts_title)) },
                leadingIcon = { Icon(Icons.Outlined.Drafts, contentDescription = null) },
                onClick = {
                    showQuickActions = false
                    fabLongPressConsumed = false
                    draftsLauncher.launch(Intent(context, DraftsActivity::class.java))
                }
            )
            ActionSheetItem(
                text = { Text(stringResource(R.string.shortcut_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                onClick = {
                    showQuickActions = false
                    fabLongPressConsumed = false
                    searchFromSheet = true
                }
            )
        }
    }

    // Draft prompt dialog
    AppDialog(
        show = showDraftPrompt,
        title = stringResource(R.string.drafts_prompt_title),
        summary = stringResource(R.string.drafts_prompt_message),
        onDismissRequest = { showDraftPrompt = false },
        buttons = {
            AppTextButton(
                text = stringResource(R.string.drafts_prompt_continue),
                modifier = Modifier.weight(1f),
                onClick = {
                    showDraftPrompt = false
                    // Load latest draft
                    val latestDraft = viewModel.draftDelegate.getLatestDraft()
                    val account = uiState.accounts.firstOrNull { it.isActive }
                    if (latestDraft != null && account != null) {
                        openEditor(EditorRequest(account.id, titleRes = R.string.memo_composer_fab_new_memo,
                            draft = latestDraft))
                    } else composeNew()
                }
            )
            AppTextButton(
                text = stringResource(R.string.drafts_prompt_start_fresh),
                modifier = Modifier.weight(1f),
                onClick = {
                    showDraftPrompt = false
                    // Start fresh with a new draft session ID
                    composeNew()
                }
            )
        }
    )

}

/**
 * Observes a long press without consuming it, so the compose button keeps its own click, ripple
 * and shared transition. The click that a long press still produces is swallowed by the caller.
 *
 * The release is awaited with the raw pointer stream instead of [waitForUpOrCancellation]: that
 * helper reports `null` for a *consumed* up event, and the button's own `clickable` handles the
 * event first, so every plain tap looked like a long press.
 */
@Composable
private fun Modifier.fabLongPress(onLongPress: () -> Unit): Modifier {
    val current by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var up = false
                while (!up) {
                    up = awaitPointerEvent().changes.none { it.pressed }
                }
            }
            if (released == null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                current()
            }
        }
    }
}

@Composable
private fun MemosListPane(
    viewModel: MemosViewModel,
    listState: LazyListState,
    onMemoClick: (Memo) -> Unit,
    contentPadding: PaddingValues,
    indicatorTopPadding: Dp = 0.dp,
    onDraftsCardClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    emptyState: @Composable () -> Unit,
    onFeedSettled: () -> Unit,
    isActive: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val hasDrafts = uiState.draft.drafts.isNotEmpty()
    val draftCount = uiState.draft.drafts.size

    GenericMemosListPane(
        viewModel = viewModel,
        modifier = Modifier.testTag("memo_feed_memos"),
        memos = uiState.userMemoList.list.items,
        isLoading = uiState.userMemoList.list.isLoading,
        isRefreshing = uiState.isRefreshing && uiState.refreshSource == RefreshSource.USerMemos,
        nextPageToken = uiState.userMemoList.list.nextPageToken,
        onLoadMore = { viewModel.loadMoreUserMemos() },
        onRefresh = { viewModel.fetchUserMemos(refresh = true) },
        onMemoClick = onMemoClick,
        listState = listState,
        contentPadding = contentPadding,
        indicatorTopPadding = indicatorTopPadding,
        errorTitle = stringResource(R.string.common_error_failed_to_load_memos),
        onHashtagClick = onHashtagClick,
        spaces = uiState.space.spaces,
        emptyState = emptyState,
        showSkeleton = !uiState.userMemoList.list.initialLoadFinished,
        onPlaceholdersLifted = onFeedSettled,
        isActive = isActive,
        header = {
            // The shortcut chips used to be a list header item here: they scrolled away with
            // the feed and re-measured the header whenever the filter changed. Filtering is now
            // the chrome entry plus the pinned summary bar, so only the drafts card is left.
            // Sync/cache status floats over the pane (see MemosScreen's overlay) for the same
            // reason: anything inside this header is a LazyColumn item and shifts the cards.
            if (hasDrafts) {
                item(key = "header_section") {
                    DraftsCard(
                        draftCount = draftCount,
                        viewModel = viewModel,
                        onCardClick = onDraftsCardClick
                    )
                }
            }
        })

}
