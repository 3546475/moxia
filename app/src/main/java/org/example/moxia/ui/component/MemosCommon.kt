package org.example.moxia.ui.component

import androidx.activity.compose.BackHandler
import org.example.moxia.ui.theme.LocalThemeStyle
import org.example.moxia.ui.theme.isMiuixMotion

import org.example.moxia.ui.theme.appSlideSpec

import org.example.moxia.ui.theme.appFadeSpec

import org.example.moxia.ui.theme.bottomBarBlurSupported
import org.example.moxia.ui.theme.topBarBlurSupported

import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

import top.yukonga.miuix.kmp.blur.layerBackdrop

import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur

import top.yukonga.miuix.kmp.blur.blur

import androidx.compose.ui.graphics.RectangleShape

import DeleteConfirmationDialog

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.moxia.R
import org.example.moxia.model.Memo
import org.example.moxia.model.Space
import org.example.moxia.model.MemoState
import org.example.moxia.model.User
import org.example.moxia.model.EditGesture
import org.example.moxia.ui.MemoKey
import org.example.moxia.ui.component.composer.ComposerMode
import org.example.moxia.ui.component.composer.EditorRequest
import org.example.moxia.ui.component.composer.rememberMemoEditorLauncher
import org.example.moxia.data.StartupMirror
import org.example.moxia.ui.component.item.MemoItem
import org.example.moxia.ui.component.item.MemoSkeletonCard
import org.example.moxia.ui.component.item.markdown.MarkdownCache
import org.example.moxia.ui.theme.AppCircularProgressIndicator
import org.example.moxia.ui.theme.AppPullToRefresh
import org.example.moxia.ui.theme.navigationBarBottom
import org.example.moxia.ui.theme.pageContentWindowInsets
import org.example.moxia.viewmodel.MemosViewModel

val LocalMemoEditor = compositionLocalOf<(Memo) -> Unit> { { } }
val LocalMemoCommenter = compositionLocalOf<(Memo) -> Unit> { { } }

/**
 * Which gesture on a memo card opens the editor directly. The card menu always offers 编辑, so
 * this only decides whether a tap, a double tap or a long press can skip the menu.
 */
val LocalMemoEditGesture = compositionLocalOf { EditGesture.NONE }

/**
 * Memo names with a queued local write the server has not acknowledged yet. Read by the card so
 * an edited memo shows the same "pending sync" hint as a locally created one; without it only the
 * global queue counter hinted at unsent edits.
 */
val LocalPendingMemoNames = compositionLocalOf { emptySet<String>() }

/**
 * True while the sync manager is replaying the queue, so a card that is still pending can switch
 * its status dots from the frozen "queued" grid to the live loader. Cards that are not pending
 * ignore it, and outside the feed (where nothing provides it) it stays false.
 */
val LocalSyncInProgress = compositionLocalOf { false }

/**
 * The list content, recorded as a backdrop layer, so anything floating above it (the frosted
 * header, the compose button) can sample and blur what is scrolling underneath. Null when the
 * blur is unavailable, which is every MD session and every device below API 33.
 */
val LocalListBackdrop = compositionLocalOf<LayerBackdrop?> { null }

private const val HeaderBlurRadius = 24f

/** How many placeholder cards a loading feed shows before the first real page arrives. */
private const val FeedSkeletonCount = 5

/** How many cards' markdown is parsed before the placeholders are lifted. */
private const val MarkdownPrewarmCount = 8

/**
 * Full strength at the very top, zero at the bottom of the header band. A [ProgressiveBlur.curve]
 * below 1 spends the falloff early and leaves a long soft tail, so the frost thins out gradually
 * instead of holding full strength through most of the header and then dropping off a cliff.
 */
private val HeaderProgressiveBlur = ProgressiveBlur.Top.copy(curve = 0.7f)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MemosScaffold(
    viewModel: MemosViewModel,
    memos: List<Memo>,
    listState: LazyListState,
    listPane: @Composable BoxScope.(onMemoClick: (Memo) -> Unit) -> Unit,
    topBar: @Composable (isDetailVisible: Boolean, isDualPane: Boolean) -> Unit = { _, _ -> },
    overlay: @Composable BoxScope.(onMemoClick: (Memo) -> Unit, showSearchBar: Boolean, isSearchExpanded: Boolean, onSearchExpandedChange: (Boolean) -> Unit, isDualPane: Boolean, isDetailVisible: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onToggleNavBar: (Boolean) -> Unit = {},
    isNavBarVisible: Boolean = true,
    /** "Always visible" only pins the bar against scroll hiding, not against detail/search. */
    navBarAlwaysVisible: Boolean = false,
    /** Which card gesture opens the editor directly; see [LocalMemoEditGesture]. */
    memoEditGesture: EditGesture = EditGesture.NONE,
    /** Space the floating bottom bar covers, so panes can keep their own floating controls clear. */
    navBarInset: Dp = navigationBarBottom(),
    navigationKey: Any? = null,
    /** One-shot request from outside (launcher shortcut, compose sheet) to expand the search field. */
    openSearch: Boolean = false,
    onSearchOpened: () -> Unit = {},
    listHeader: @Composable (
        showSearchBar: Boolean,
        isSearchExpanded: Boolean,
        onSearchExpandedChange: (Boolean) -> Unit
    ) -> Unit = { _, _, _ -> },
    /**
     * True when the list pane itself starts at the window's top edge (the home feed), so [listHeader]
     * has to clear the status bar. Pages with their own top bar leave this off: their pane already
     * starts below the bar, and reserving the inset again would paint an empty band over the list.
     */
    listPaneUnderStatusBar: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).copy(
        defaultPanePreferredWidth = 600.dp
    )

    // Each feed starts with a fresh detail back stack when the user switches tabs.
    val navigator = key(navigationKey) {
        rememberListDetailPaneScaffoldNavigator<MemoKey>(scaffoldDirective = scaffoldDirective)
    }

    // Opening a memo from the detail of another one ("linked memo") navigates within the same pane
    // role, which the scaffold treats as a replacement. Remember where the user came from so Back
    // returns to that memo instead of dropping to the list.
    val detailBackStack = remember(navigationKey) { mutableStateListOf<MemoKey>() }

    // Workaround for Compose Material 3 Adaptive Navigator not automatically updating the inner state
    // when scaffoldValue changes (e.g. on window resize).
    LaunchedEffect(navigator, scaffoldDirective) {
        (navigator.scaffoldState as? androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState)?.snapTo(navigator.scaffoldValue)
    }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // One Back step inside the detail pane: the memo we came from, or the list when there is none.
    val leaveDetail: () -> Unit = {
        val previous = detailBackStack.removeLastOrNull()
        scope.launch {
            if (previous != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, previous)
            } else {
                navigator.navigateBack()
            }
        }
    }

    // Capture initial focus to prevent child inputs (like composer or search) from auto-focusing
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(navigator, navigator.currentDestination) {
        focusManager.clearFocus()
    }

    // Scroll direction tracking for search bar visibility
    val scrollContext = rememberScrollContext(
        listState = listState
    )

    val isDetailVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val isListVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val isDualPane = isListVisible && isDetailVisible

    // The floating header frosts the list behind it, and the compose button samples the same layer
    // so it shares the bottom bar's material. Each bar has its own switch: the layer is recorded
    // when either of them needs it, but each surface is frosted only by its own preference.
    val headerBlurEnabled = topBarBlurSupported()
    val fabBlurEnabled = bottomBarBlurSupported()
    val backdropNeeded = headerBlurEnabled || fabBlurEnabled
    val headerBackdrop = rememberLayerBackdrop()

    // Sync selected memo with navigator
    // We add memos and search items as dependencies to ensure selectedMemo is updated if the items in the list change (e.g. after an edit)
    LaunchedEffect(navigator, navigator.currentDestination, memos, uiState.searchMemoList.list.items) {
        val currentMemoKey = navigator.currentDestination?.contentKey
        if (currentMemoKey != null) {
            val selectedId =
                uiState.detailPane.selectedMemo?.let { it.name ?: it.content.hashCode().toString() }
            if (currentMemoKey.id != selectedId || uiState.detailPane.selectedMemo == null) {
                val pool =
                    if (currentMemoKey.fromSearch) uiState.searchMemoList.list.items else memos
                val memo = pool.find {
                    (it.name ?: it.content.hashCode().toString()) == currentMemoKey.id
                }
                if (memo != null) {
                    viewModel.memoActionDelegate.selectMemo(memo)
                }
            }
        } else if (uiState.detailPane.selectedMemo != null) {
            viewModel.memoActionDelegate.clearSelectedMemo()
        }
    }

    var isSearchExpanded by remember(navigationKey) { mutableStateOf(false) }

    // The search field is owned here, so an external request (launcher shortcut, the compose
    // sheet) has to be applied here; the caller clears its flag once the field is really open.
    LaunchedEffect(openSearch, navigationKey) {
        if (openSearch && !isSearchExpanded) {
            isSearchExpanded = true
            onSearchOpened()
        }
    }

    val showNavBarByScroll by remember(listState, scrollContext) {
        derivedStateOf { !scrollContext.isScrollingDown || listState.firstVisibleItemIndex == 0 }
    }

    LaunchedEffect(listState, showNavBarByScroll, isDetailVisible, isDualPane, isSearchExpanded, navBarAlwaysVisible) {
        if ((isDetailVisible || isSearchExpanded) && !isDualPane) {
            // Detail and search always hide the bar, even when scroll-hiding is switched off.
            onToggleNavBar(false)
        } else {
            // Scrolling only hides when the user allowed it; "always visible" pins the bar.
            onToggleNavBar(navBarAlwaysVisible || showNavBarByScroll)
        }
    }

    val showSearchBar = showNavBarByScroll

    // System back unwinds this screen before it reaches the activity. Without these, back from an
    // open search panel or an open memo dropped straight out to the launcher, because the pane
    // scaffold and the search panel keep their own state instead of a navigation back stack.
    BackHandler(enabled = isSearchExpanded) { isSearchExpanded = false }
    BackHandler(enabled = isDetailVisible) {
        focusManager.clearFocus()
        leaveDetail()
    }

    val openEditor = rememberMemoEditorLauncher(viewModel)
    val editMemo: (Memo) -> Unit = { memo ->
        uiState.accounts.firstOrNull { it.isActive }?.let { account ->
            openEditor(EditorRequest(account.id, ComposerMode.UPDATE, R.string.memo_dialog_edit_title, memo = memo))
        }
    }
    val commentMemo: (Memo) -> Unit = { memo ->
        uiState.accounts.firstOrNull { it.isActive }?.let { account ->
            openEditor(EditorRequest(account.id, ComposerMode.COMMENT, R.string.memo_detail_add_comment, parentMemo = memo))
        }
    }
    CompositionLocalProvider(
        LocalMemoEditor provides editMemo,
        LocalMemoCommenter provides commentMemo,
        LocalMemoEditGesture provides memoEditGesture,
        LocalPendingMemoNames provides remember(uiState.pendingOps) {
            uiState.pendingOps.mapNotNullTo(HashSet()) { it.memoName }
        },
        LocalSyncInProgress provides uiState.isSyncing,
        LocalListBackdrop provides if (fabBlurEnabled) headerBackdrop else null
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // The feed runs under every system bar. Applying an inset here would clip the
                // list at the status bar and at the gesture handle, so the pages add the insets
                // they need themselves: the floating header pads its own rows with the status
                // bar, and each list/overlay pads its content at the bottom.
                contentWindowInsets = pageContentWindowInsets(),
                topBar = { topBar(isDetailVisible, isDualPane) }) { paddingValues ->
                NavigableListDetailPaneScaffold(
                    modifier = Modifier
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues)
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable(),
                    navigator = navigator,
                    listPane = {
                        AnimatedPane {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // MIUIX draws the list into a backdrop layer so the floating header
                                // can sample and blur it; MD keeps the plain list.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (backdropNeeded) {
                                                Modifier.layerBackdrop(headerBackdrop)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    listPane { memo ->
                                        focusManager.clearFocus()
                                        detailBackStack.clear()
                                        scope.launch {
                                            val id = memo.name ?: memo.content.hashCode().toString()
                                            navigator.navigateTo(
                                                ListDetailPaneScaffoldRole.Detail, MemoKey(id)
                                            )
                                        }
                                    }
                                }

                                // Keep the header above the list without changing the list's
                                // constraints as the header hides or search expands.
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = if (headerBlurEnabled) {
                                            // A gradient frost: strongest at the top edge and gone at
                                            // the bottom, so the list fades into the header instead of
                                            // ending against a visible blur edge.
                                            Modifier.progressiveTextureBlur(
                                                backdrop = headerBackdrop,
                                                shape = RectangleShape,
                                                blurRadius = HeaderBlurRadius,
                                                gradient = HeaderProgressiveBlur
                                            )
                                        } else {
                                            // Blur off must not leave the list showing through the
                                            // header; give it the page's solid color instead.
                                            Modifier.background(MaterialTheme.colorScheme.background)
                                        }
                                    ) {
                                        // The frosted band reaches the top edge, so its rows
                                        // are pushed below the status bar instead: the blur has
                                        // to cover the inset, the labels must not.
                                        Box(
                                            if (listPaneUnderStatusBar) {
                                                Modifier.statusBarsPadding()
                                            } else {
                                                Modifier
                                            }
                                        ) {
                                            listHeader(showSearchBar, isSearchExpanded) { isSearchExpanded = it }
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        overlay(
                                            { memo ->
                                                focusManager.clearFocus()
                                                detailBackStack.clear()
                                                scope.launch {
                                                    val id = memo.name ?: memo.content.hashCode().toString()
                                                    navigator.navigateTo(
                                                        ListDetailPaneScaffoldRole.Detail,
                                                        MemoKey(id, fromSearch = true)
                                                    )
                                                }
                                            },
                                            showSearchBar,
                                            isSearchExpanded,
                                            { isSearchExpanded = it },
                                            isDualPane,
                                            isDetailVisible
                                        )
                                    }
                                }
                            }
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val miuixMotion = isMiuixMotion(LocalThemeStyle.current)
                            AnimatedContent(
                                targetState = navigator.currentDestination?.contentKey, transitionSpec = {
                                    if (isDualPane) {
                                        if (initialState == null) {
                                            (fadeIn(
                                                animationSpec = appFadeSpec(
                                                    miuixMotion,
                                                    durationMillis = 220,
                                                    delayMillis = 90
                                                )
                                            ) + scaleIn(
                                                initialScale = 0.92f,
                                                animationSpec = appFadeSpec(
                                                    miuixMotion,
                                                    durationMillis = 220,
                                                    delayMillis = 90
                                                )
                                            )).togetherWith(fadeOut(animationSpec = appFadeSpec(miuixMotion, durationMillis = 90)))
                                        } else {
                                            fadeIn(animationSpec = appFadeSpec(miuixMotion, durationMillis = 300)).togetherWith(
                                                fadeOut(animationSpec = appFadeSpec(miuixMotion, durationMillis = 300))
                                            )
                                        }
                                    } else {
                                        // Phone layout: a vertical push. Opening brings the detail
                                        // up from the bottom while the list leaves through the top;
                                        // closing plays the same motion backwards (list returns from
                                        // the top, detail leaves downwards). Both directions used to
                                        // start from the bottom, so entering and leaving did not
                                        // mirror each other.
                                        val slide = appSlideSpec(miuixMotion, 300)
                                        val detailOpening = targetState != null
                                        if (detailOpening) {
                                            (slideInVertically(
                                                initialOffsetY = { it },
                                                animationSpec = slide
                                            ) + fadeIn()).togetherWith(
                                                slideOutVertically(
                                                    targetOffsetY = { -it },
                                                    animationSpec = slide
                                                ) + fadeOut()
                                            )
                                        } else {
                                            (slideInVertically(
                                                initialOffsetY = { -it },
                                                animationSpec = slide
                                            ) + fadeIn()).togetherWith(
                                                slideOutVertically(
                                                    targetOffsetY = { it },
                                                    animationSpec = slide
                                                ) + fadeOut()
                                            )
                                        }
                                    }
                                }, label = "DetailPaneTransition"
                            ) { memoKey ->
                                val memo =
                                    remember(memoKey, memos, uiState.searchMemoList.list.items) {
                                        memoKey?.let { key ->
                                            val pool =
                                                if (key.fromSearch) uiState.searchMemoList.list.items else memos
                                            pool.find {
                                                (it.name ?: it.content.hashCode()
                                                    .toString()) == key.id
                                            }
                                        }
                                    }

                                if (memo != null) {
                                    MemoDetailView(
                                        memo = memo,
                                        comments = uiState.detailPane.comments,
                                        token = uiState.session.token,
                                        hostUrl = uiState.session.hostUrl,
                                        showBackButton = navigator.canNavigateBack(),
                                        onBack = {
                                            focusManager.clearFocus()
                                            leaveDetail()
                                        },
                                        viewModel = viewModel,
                                        reactionOptions = uiState.session.instanceSettings?.memoRelatedSetting?.reactions
                                            ?: emptyList(),
                                        bottomBarInset = navBarInset,
                                        // Only navigate when the target is already loaded: the
                                        // detail pane resolves its memo from the current list.
                                        onOpenLinkedMemo = { name ->
                                            val current = navigator.currentDestination?.contentKey
                                            if (current != null && current.id != name &&
                                                memos.any { it.name == name }
                                            ) {
                                                detailBackStack.add(current)
                                                scope.launch {
                                                    navigator.navigateTo(
                                                        ListDetailPaneScaffoldRole.Detail,
                                                        MemoKey(name)
                                                    )
                                                }
                                            }
                                        },
                                        onRemoveLinkedMemo = { name ->
                                            viewModel.memoActionDelegate.removeMemoLink(memo, name)
                                        }
                                    )
                                } else if (isDualPane) {
                                    MemoDetailPlaceholder()
                                }
                            }
                        }
                    })
                }

        }
    }
}

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GenericMemosListPane(
        viewModel: MemosViewModel,
        memos: List<Memo>,
        isLoading: Boolean,
        isRefreshing: Boolean,
        nextPageToken: String?,
        onLoadMore: () -> Unit,
        onRefresh: () -> Unit,
        onMemoClick: (Memo) -> Unit,
        modifier: Modifier = Modifier,
        listState: LazyListState = rememberLazyListState(),
        userProvider: (Memo) -> User? = { null },
        header: (LazyListScope.() -> Unit)? = null,
        contentPadding: PaddingValues = PaddingValues(
            start = 16.dp, top = 88.dp, end = 16.dp, bottom = 80.dp
        ),
        /** Pushes the pull-to-refresh spinner below a floating header instead of behind it. */
        indicatorTopPadding: Dp = 0.dp,
        errorTitle: String = stringResource(R.string.common_error_failed_to_load),
        onHashtagClick: ((String) -> Unit)? = null,
        /** Spaces a card can be moved into; empty hides that menu entry. */
        spaces: List<Space> = emptyList(),
        emptyState: (@Composable () -> Unit)? = null,
        /**
         * True until the first page (or its failure) has been applied. An empty feed then shows
         * skeleton cards instead of the empty state: the page opens into the shape of the feed
         * and fills in, rather than flashing "no memos yet" before the request comes back.
         */
        showSkeleton: Boolean = false,
        /** Called once the placeholders are gone and the pane shows settled content. */
        onPlaceholdersLifted: (() -> Unit)? = null,
        isActive: Boolean = true
    ) {
        val uiState by viewModel.uiState.collectAsState()
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current
        val density = LocalDensity.current
        // The feed only moves while it has content to reveal: no stretch at either end.
        val overscroll = rememberPageOverscroll()

        // Items read these through derived states: a sync tick or a refresh no longer recomposes
        // every card, only a session change or a different selected memo does.
        val sessionState = remember { derivedStateOf { uiState.session } }
        val selectedNameState = remember { derivedStateOf { uiState.detailPane.selectedMemo?.name } }
        val headerScale = uiState.appSettings.headerScale
        val reactionOptions = uiState.session.instanceSettings?.memoRelatedSetting?.reactions
            ?: emptyList()

        // Skeleton instead of a loader or the empty state until the feed has a first answer,
        // and then until the first cards' markdown has actually been parsed: swapping on data
        // arrival alone shows cards with empty bodies whose text pops in a frame later.
        // Survives a destination switch (the pane is re-created on return) so coming back to
        // the feed does not flash placeholders over cards whose markdown is already cached.
        var markdownPrewarmed by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(memos) {
            if (memos.isEmpty()) {
                markdownPrewarmed = false
                return@LaunchedEffect
            }
            // A restored flag can outlive the parse cache (process death): verify the first card
            // is still parsed, otherwise hold the placeholders and parse again.
            if (markdownPrewarmed && MarkdownCache.cached(memos.first().content) != null) {
                return@LaunchedEffect
            }
            markdownPrewarmed = false
            withContext(Dispatchers.Default) {
                memos.take(MarkdownPrewarmCount).forEach { memo ->
                    val content = memo.content
                    if (content.isNotBlank()) MarkdownCache.parse(content)
                }
            }
            markdownPrewarmed = true
        }
        val showPlaceholders =
            (memos.isEmpty() && (showSkeleton || (isLoading && !isRefreshing))) ||
                (memos.isNotEmpty() && !markdownPrewarmed)

        // Tell the host chrome when the pane is no longer showing placeholders: the bottom bar
        // waits for this instead of appearing over the skeleton.
        LaunchedEffect(showPlaceholders) {
            if (!showPlaceholders) onPlaceholdersLifted?.invoke()
        }

        LaunchedEffect(listState, isLoading, nextPageToken, isActive) {
            if (!isActive) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
                if (lastIndex != null && !isLoading && nextPageToken != null && lastIndex >= listState.layoutInfo.totalItemsCount - 5) {
                    onLoadMore()
                }
            }
        }

        AppPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { if (isActive) onRefresh() },
            indicatorTopPadding = indicatorTopPadding,
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
        ) {
            LazyColumn(
                state = listState,
                overscrollEffect = overscroll,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        // Where the list starts in the window: the top bar above it is not part of
                        // the feed's content padding, and the launch shell needs both.
                        val top = with(density) { coordinates.positionInWindow().y.toDp() }
                        StartupMirror.updateFeedPaneTop(context, top.value)
                    },
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                header?.invoke(this)

                if (showPlaceholders) {
                    // Placeholder cards, not a spinner: the feed opens in its final shape and the
                    // real cards replace these in place, once their markdown is ready.
                    items(count = FeedSkeletonCount, key = { "feed-skeleton-$it" }) {
                        MemoSkeletonCard(Modifier.animateItem())
                    }
                } else if (uiState.error != null && memos.isEmpty()) {
                    item {
                        ErrorView(
                            title = errorTitle,
                            message = stringResource(uiState.error!!.resourceId, *uiState.error!!.formatArgs.toTypedArray()),
                            onRetry = onRefresh,
                            modifier = Modifier.fillParentMaxHeight(0.7f)
                        )
                    }
                } else if (memos.isEmpty() && nextPageToken == null && emptyState != null) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxHeight(0.6f),
                            contentAlignment = Alignment.Center
                        ) {
                            emptyState()
                        }
                    }
                } else {
                    itemsIndexed(memos, key = { index, it ->
                        // Stable key by memo name so list updates (cache merge,
                        // network refresh) reuse existing items instead of
                        // rebuilding every card and jumping the scroll position.
                        if (!it.name.isNullOrBlank()) it.name
                        else "${it.content.hashCode()}_${it.createTime}_$index"
                    }) { index, memo ->
                        val session = sessionState.value
                        Box(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            contentAlignment = Alignment.Center
                        ) {
                            MemoPreviewItem(
                                viewModel = viewModel,
                                memo = memo,
                                currentUser = session.currUser,
                                token = session.token,
                                hostUrl = session.hostUrl,
                                selected = memo.name != null &&
                                    memo.name == selectedNameState.value,
                                headerScale = headerScale,
                                reactionOptions = reactionOptions,
                                user = userProvider(memo),
                                onClick = { onMemoClick(memo) },
                                onHashtagClick = onHashtagClick,
                                spaces = spaces
                            )
                        }
                    }

                    if (isLoading && memos.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppCircularProgressIndicator()
                            }
                        }
                    } else if (!isLoading && nextPageToken == null && memos.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.memo_list_end),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

/** The home feed's preview card and actions, also used for memos selected on the map. */
@Composable
fun MemoPreviewItem(
    viewModel: MemosViewModel,
    memo: Memo,
    /** Hoisted so a card does not subscribe to the whole ui state just to read the session. */
    currentUser: User?,
    token: String,
    hostUrl: String,
    selected: Boolean,
    headerScale: Float,
    reactionOptions: List<String>,
    user: User? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHashtagClick: ((String) -> Unit)? = null,
    spaces: List<Space> = emptyList()
) {
    val focusManager = LocalFocusManager.current
    val onEditMemo = LocalMemoEditor.current
    var showDelete by remember(memo.name) { mutableStateOf(false) }
    val isOwner = memo.creator == currentUser?.name
    MemoItem(
        memo = memo,
        user = user,
        currentUser = currentUser,
        token = token,
        hostUrl = hostUrl,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        onClick = {
            focusManager.clearFocus()
            onClick()
        },
        onEdit = if (isOwner) {
            { onEditMemo(memo) }
        } else null,
        onArchive = if (isOwner) {
            {
                viewModel.memoActionDelegate.updateMemo(
                    memo,
                    memo.content,
                    memo.visibility,
                    memo.attachments ?: emptyList(),
                    memo.location,
                    MemoState.ARCHIVED
                )
            }
        } else null,
        onUnarchive = if (isOwner) {
            {
                viewModel.memoActionDelegate.updateMemo(
                    memo,
                    memo.content,
                    memo.visibility,
                    memo.attachments ?: emptyList(),
                    memo.location,
                    MemoState.NORMAL
                )
            }
        } else null,
        onPin = if (isOwner) { pinned ->
            viewModel.memoActionDelegate.updateMemoPinned(memo, pinned)
        } else null,
        onDelete = if (isOwner) {
            { showDelete = true }
        } else null,
        onMoveToSpace = if (isOwner) { space ->
            // A space-scoped feed shows the server's result set, so a memo that just left or
            // entered the scope only disappears or appears on the next page fetch.
            viewModel.memoActionDelegate.moveMemoToSpace(
                memo,
                space,
                onSuccess = { viewModel.fetchUserMemos(refresh = true) }
            )
        } else null,
        spaces = spaces,
        onUpsertReaction = { emoji ->
            viewModel.memoActionDelegate.upsertMemoReaction(memo, emoji)
        },
        onDeleteReaction = { reaction ->
            viewModel.memoActionDelegate.deleteMemoReaction(memo, reaction)
        },
        onContentUpdate = if (isOwner) { newContent ->
            viewModel.memoActionDelegate.updateMemo(
                memo,
                newContent,
                memo.visibility,
                memo.attachments ?: emptyList(),
                memo.location
            )
        } else null,
        maxHeight = 400.dp,
        modifier = modifier.widthIn(max = 800.dp),
        onHashtagClick = onHashtagClick,
        headerScale = headerScale,
        reactionOptions = reactionOptions)
    if (showDelete) {
        DeleteConfirmationDialog(memo = memo, onDismiss = { showDelete = false }, onConfirm = {
            viewModel.memoActionDelegate.deleteMemo(memo)
            showDelete = false
        })
    }
}

fun resolveResourceUrl(hostUrl: String, relativeUrl: String?): String? {
    if (relativeUrl.isNullOrBlank()) return null
    // Absolute URLs and inline URIs (avatars are stored as data URIs) must not be joined to the
    // host, or the result stops being loadable.
    if (
        relativeUrl.startsWith("http") ||
        relativeUrl.startsWith("data:") ||
        relativeUrl.startsWith("content:") ||
        relativeUrl.startsWith("file:")
    ) {
        return relativeUrl
    }

    val cleanHost = hostUrl.trimEnd('/')
    val cleanRelative = relativeUrl.trimStart('/')

    val result = "$cleanHost/$cleanRelative"
    android.util.Log.d(
        "MemosDebug", "resolveResourceUrl: host=$hostUrl, relative=$relativeUrl -> $result"
    )
    return result
}
