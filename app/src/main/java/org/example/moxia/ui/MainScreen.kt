package org.example.moxia.ui

import androidx.activity.compose.BackHandler
import org.example.moxia.model.NavBarScrollMode

import org.example.moxia.model.AppearancePreferences

import org.example.moxia.ui.theme.isMiuixMotion

import org.example.moxia.ui.theme.appSlideSpec

import org.example.moxia.ui.theme.appFadeSpec

import top.yukonga.miuix.kmp.theme.MiuixTheme

import org.example.moxia.ui.theme.navBarMaterialSupported

import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

import top.yukonga.miuix.kmp.blur.layerBackdrop

import top.yukonga.miuix.kmp.blur.drawBackdrop

import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent


import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.filled.Map
import org.example.moxia.model.MapCapability
import org.example.moxia.ui.nav.MapScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.vector.ImageVector
import org.example.moxia.model.ThemeStyle
import org.example.moxia.ui.theme.AppSurface
import org.example.moxia.ui.theme.CapsuleSurfaceAlpha
import org.example.moxia.ui.theme.LocalThemeStyle
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail as MiuixNavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem as MiuixNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import org.example.moxia.ui.component.item.media.AccountMediaIdentity
import org.example.moxia.ui.component.item.media.LocalAccountMediaIdentity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.example.moxia.R
import org.example.moxia.model.Attachment
import org.example.moxia.model.ShareIntentData
import org.example.moxia.model.Visibility
import org.example.moxia.ui.component.ConflictDialog
import org.example.moxia.ui.component.LoginDialog
import org.example.moxia.ui.component.LocalNetworkPermission
import org.example.moxia.ui.theme.LocalBottomBarBlurEnabled
import org.example.moxia.ui.theme.LocalTopBarBlurEnabled
import org.example.moxia.ui.component.composer.EditorRequest
import org.example.moxia.ui.component.composer.rememberMemoEditorLauncher
import org.example.moxia.ui.component.item.media.MemoImage
import org.example.moxia.ui.component.item.markdown.LocalLinkPreviews
import org.example.moxia.ui.component.item.markdown.LocalMarkdownFormat
import org.example.moxia.ui.component.item.markdown.MarkdownFormat
import org.example.moxia.ui.component.item.markdown.LinkPreviewEnvironment
import org.example.moxia.ui.component.resolveResourceUrl
import org.example.moxia.ui.nav.AttachmentsScreen
import org.example.moxia.ui.nav.FloatingBottomBar
import org.example.moxia.ui.nav.MemoFeed
import org.example.moxia.ui.nav.MemosScreen
import org.example.moxia.ui.nav.ProfileScreen
import org.example.moxia.viewmodel.MemosViewModel
import org.example.moxia.viewmodel.MemosUiState
import androidx.compose.runtime.MutableState

enum class MainDestination(
    val labelRes: Int
) {
    MEMOS(R.string.nav_memos), MAP(R.string.nav_map), ATTACHMENTS(R.string.nav_attachments), PROFILE(
        R.string.nav_profile
    )
}

private const val NavBlurRadius = 24f

/**
 * The docked bar is a full-width rectangle. Its shape carries no corners, but it still goes through
 * a rounded-corner shape so the bar can sample itself the same way whichever material is on.
 */
private val DockedBarShape = RoundedCornerShape(0.dp)

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    appearance: AppearancePreferences,
    modifier: Modifier = Modifier,
    shareIntentData: ShareIntentData? = null,
    onShareIntentConsumed: () -> Unit = {},
    shouldOpenComposer: Boolean = false,
    onComposerOpened: () -> Unit = {},
    /** One-shot tab request from a shortcut or widget, consumed by the screen. */
    destinationRequest: MainDestination? = null,
    onDestinationRequestConsumed: () -> Unit = {},
    /** One-shot search request; [MemosScreen] consumes it after expanding the field. */
    openSearchRequest: Boolean = false,
    onOpenSearchRequestConsumed: () -> Unit = {}
) {
    val destination = rememberSaveable { mutableStateOf(MainDestination.MEMOS) }
    val viewModel: MemosViewModel = org.koin.compose.koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val previewRepository by viewModel.linkPreviews.collectAsState()
    val linkPreviews = remember(previewRepository, uiState.appSettings.linkPreviewEnabled, uiState.isOnline) {
        previewRepository?.takeIf { uiState.appSettings.linkPreviewEnabled }
            ?.let { LinkPreviewEnvironment(it, uiState.isOnline) }
    }
    val mediaIdentity = uiState.accounts.firstOrNull { it.isActive }?.let {
        AccountMediaIdentity(it.id, uiState.accountGeneration)
    }
    val markdownFormat = remember(
        uiState.appSettings.codeLineNumbers,
        uiState.appSettings.codeWrapLines,
        uiState.appSettings.paragraphIndent,
        uiState.appSettings.cjkSpacing
    ) {
        MarkdownFormat(
            codeLineNumbers = uiState.appSettings.codeLineNumbers,
            codeWrapLines = uiState.appSettings.codeWrapLines,
            paragraphIndent = uiState.appSettings.paragraphIndent,
            cjkSpacing = uiState.appSettings.cjkSpacing
        )
    }
    CompositionLocalProvider(
        LocalAccountMediaIdentity provides mediaIdentity,
        LocalLinkPreviews provides linkPreviews,
        LocalMarkdownFormat provides markdownFormat
    ) {
        key(uiState.accountGeneration) {
            MainScreenContent(viewModel, uiState, destination, onLogout, appearance, modifier,
                shareIntentData, onShareIntentConsumed, shouldOpenComposer, onComposerOpened,
                destinationRequest, onDestinationRequestConsumed,
                openSearchRequest, onOpenSearchRequestConsumed)
        }
    }
}

@Composable
private fun MainScreenContent(
    viewModel: MemosViewModel,
    uiState: MemosUiState,
    destination: MutableState<MainDestination>,
    onLogout: () -> Unit,
    appearance: AppearancePreferences,
    modifier: Modifier,
    shareIntentData: ShareIntentData?,
    onShareIntentConsumed: () -> Unit,
    shouldOpenComposer: Boolean,
    onComposerOpened: () -> Unit,
    destinationRequest: MainDestination?,
    onDestinationRequestConsumed: () -> Unit,
    openSearchRequest: Boolean,
    onOpenSearchRequestConsumed: () -> Unit
) {
    var currentDestination by destination
    // Back returns to the notes feed instead of leaving the app from any other destination. It is
    // declared before the content so the inner handlers (search panel, map selection, memo detail)
    // are registered later and therefore get the first chance to consume the gesture.
    BackHandler(enabled = currentDestination != MainDestination.MEMOS) {
        currentDestination = MainDestination.MEMOS
    }
    val mapSupported = uiState.memoMap.capability == MapCapability.SUPPORTED
    // The user can hide, reorder and drop bottom-bar destinations; MAP only exists when supported.
    val availableDestinations = remember(mapSupported) {
        MainDestination.entries.filter { it != MainDestination.MAP || mapSupported }
    }
    val visibleDestinations = remember(appearance.navDestinations, availableDestinations) {
        if (appearance.navDestinations.isEmpty()) {
            availableDestinations
        } else {
            appearance.navDestinations
                .mapNotNull { name -> availableDestinations.firstOrNull { it.name == name } }
                .ifEmpty { availableDestinations }
        }
    }
    LaunchedEffect(mapSupported, uiState.accountGeneration) {
        if (!mapSupported && currentDestination == MainDestination.MAP) currentDestination = MainDestination.MEMOS
    }
    val feedPagerState = rememberPagerState(pageCount = { MemoFeed.entries.size })

    // Shortcuts and widgets can name a tab. Consume the request once so a later recomposition
    // (or a configuration change) does not keep forcing the user back to that tab.
    LaunchedEffect(destinationRequest) {
        if (destinationRequest != null) {
            if (destinationRequest == MainDestination.MEMOS) {
                feedPagerState.requestScrollToPage(MemoFeed.MEMOS.ordinal)
            }
            currentDestination = destinationRequest
            onDestinationRequestConsumed()
        }
    }

    // The search field lives inside the Memos destination, so the shortcut has to land there
    // first; MemosScreen reports back once the field is actually expanded.
    LaunchedEffect(openSearchRequest) {
        if (openSearchRequest) {
            currentDestination = MainDestination.MEMOS
            feedPagerState.requestScrollToPage(MemoFeed.MEMOS.ordinal)
        }
    }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val networkPermission = LocalNetworkPermission.current
    var previouslyGranted by remember { mutableStateOf(networkPermission.granted) }
    LaunchedEffect(networkPermission.granted) {
        if (networkPermission.granted && !previouslyGranted) {
            viewModel.retryAccountConnection()
        }
        previouslyGranted = networkPermission.granted
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    val navBarAlwaysVisible = appearance.navBarScrollMode == NavBarScrollMode.ALWAYS_VISIBLE
    var isNavBarVisible by remember { mutableStateOf(true) }
    // The bar waits for the notes feed's first settled content instead of appearing over the
    // skeleton: the feed reports when its placeholders are gone, then the bar draws two frames
    // later (its blur needs a drawn backdrop). Every other destination, and a feed that never
    // settles, reveals it as before.
    var feedSettled by remember { mutableStateOf(false) }
    var bottomBarReady by remember { mutableStateOf(false) }
    LaunchedEffect(currentDestination, feedSettled) {
        if (currentDestination != MainDestination.MEMOS || feedSettled) {
            withFrameNanos { }
            withFrameNanos { }
            bottomBarReady = true
        }
    }
    LaunchedEffect(currentDestination) {
        if (currentDestination == MainDestination.MEMOS && !bottomBarReady) {
            // Fail-safe: an offline start that never finishes a first page must not hide the bar.
            delay(3_000)
            bottomBarReady = true
        }
    }

    LaunchedEffect(navBarAlwaysVisible) { if (navBarAlwaysVisible) isNavBarVisible = true }
    var isAddingAccount by remember { mutableStateOf(false) }

    val openEditor = rememberMemoEditorLauncher(viewModel)

    // Track if we've already processed the current share intent
    var processedShareData by remember { mutableStateOf<ShareIntentData?>(null) }

    // Trigger composer when share data is received - CREATE NEW draft

    // Switch to Memos tab if widget triggered composer
    LaunchedEffect(shouldOpenComposer) {
        if (shouldOpenComposer) {
            currentDestination = MainDestination.MEMOS
            // The pager may be off-screen when a widget request arrives.
            feedPagerState.requestScrollToPage(MemoFeed.MEMOS.ordinal)
        }
    }

    LaunchedEffect(shareIntentData) {
        // Only process if:
        // 1. We have share data
        // 2. We haven't already processed this exact share data
        if (shareIntentData != null && !shareIntentData.isEmpty && processedShareData != shareIntentData) {
            val account = uiState.accounts.firstOrNull { it.isActive } ?: return@LaunchedEffect
            openEditor(EditorRequest(account.id, titleRes = R.string.memo_composer_fab_new_memo,
                content = shareIntentData.text.orEmpty(), uris = shareIntentData.uris.map { it.toString() },
                visibility = uiState.session.userSettings?.memoVisibility ?: Visibility.PRIVATE))
            processedShareData = shareIntentData
            onShareIntentConsumed()
        }
    }

    DisposableEffect(currentDestination) {
        focusManager.clearFocus()
        onDispose { }
    }

    val configuration = LocalConfiguration.current
    val isMobile = configuration.screenWidthDp < 600

    val miuixMotion = isMiuixMotion(LocalThemeStyle.current)

    // The bottom bar blurs a snapshot of the content layer, and neither that snapshot nor the bar
    // is invalidated by the destination cross-fade on its own. This transition ticks on every frame
    // of that fade; both the content layer and the bar read it, so the backdrop is re-recorded and
    // re-sampled instead of freezing on the page that was just left.
    val barBlurTransition = updateTransition(currentDestination, label = "BottomBarBlurRefresh")
    val barBlurRefresh = barBlurTransition.animateFloat(
        // Fixed duration (not the spring used by the content fade): the value is only an
        // invalidation tick, and the page cross-fade has settled well inside this window even
        // for adjacent tabs, whose ordinal delta would otherwise finish too early.
        transitionSpec = { tween(durationMillis = 500) },
        label = "BottomBarBlurRefreshValue"
    ) { destination -> destination.ordinal.toFloat() }

    val adaptiveInfo = androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2()


    // The explicit setter is for detail/search visibility (always honoured). The scroll-aware
    // setter only governs scroll-driven hiding, so "always visible" does not pin the bar while
    // reading a memo detail or searching.
    val setNavBarVisible: ((Boolean) -> Unit)? = if (isMobile) {
        { visible -> isNavBarVisible = visible }
    } else null
    val toggleNavBar: ((Boolean) -> Unit)? = if (isMobile) {
        { visible -> if (!navBarAlwaysVisible) isNavBarVisible = visible }
    } else null


    if (isAddingAccount) {
        LoginDialog(onLoginSuccess = { newBaseUrl, newToken ->
            scope.launch {
                viewModel.userDelegate.addAccount(newBaseUrl, newToken)
                isAddingAccount = false
            }
        }, onDismiss = { isAddingAccount = false })
    }


    @Composable
    fun NavigationIcon(
        destination: MainDestination, isSelected: Boolean, modifier: Modifier = Modifier.size(24.dp)
    ) {
        when (destination) {
            MainDestination.MAP -> Icon(
                if (isSelected) Icons.Filled.Map else Icons.Outlined.Map,
                contentDescription = null,
                modifier = modifier
            )
            MainDestination.MEMOS -> Icon(
                if (isSelected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks,
                contentDescription = null,
                modifier = modifier
            )

            MainDestination.ATTACHMENTS -> Icon(
                if (isSelected) Icons.Default.Attachment else Icons.Outlined.Attachment,
                contentDescription = null,
                modifier = modifier
            )

            MainDestination.PROFILE -> {
                val user = uiState.session.currUser
                val account = uiState.accounts.find { it.isActive }
                val rawAvatarUrl = user?.avatarUrl ?: account?.avatarUrl
                val hostUrl = uiState.session.hostUrl

                val avatarUri = remember(rawAvatarUrl, hostUrl) {
                    if (rawAvatarUrl.isNullOrBlank()) Uri.EMPTY
                    else (resolveResourceUrl(hostUrl, rawAvatarUrl) ?: "").toUri()
                }

                MemoImage(
                    attachment = null,
                    token = uiState.session.token,
                    hostUrl = hostUrl,
                    uri = avatarUri,
                    contentDescription = null,
                    isRound = true,
                    placeholderIcon = if (isSelected) Icons.Default.Person else Icons.Outlined.Person,
                    modifier = modifier
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, CircleShape
                            ) else Modifier
                        )
                        .padding(if (isSelected) 1.dp else 0.dp)
                )
            }
        }
    }

    fun handleDestinationClick(destination: MainDestination) {
        focusManager.clearFocus()
        val currentTime = System.currentTimeMillis()
        if (currentDestination == destination && currentTime - lastTapTime < 500) {
            when (destination) {
                MainDestination.MEMOS -> when (MemoFeed.entries[feedPagerState.currentPage]) {
                    MemoFeed.MEMOS -> viewModel.fetchUserMemos(refresh = true)
                    MemoFeed.EXPLORE -> viewModel.fetchExploreMemos(refresh = true)
                }
                MainDestination.ATTACHMENTS -> viewModel.fetchAttachments(refresh = true)
                MainDestination.MAP -> viewModel.memoMapManager.load()
                else -> {}
            }
        }
        currentDestination = destination
        lastTapTime = currentTime
    }

    // One backdrop per destination. The bar samples only the destination it is showing, so the
    // outgoing page's fade layer never bleeds into the bar's blur while the page cross-fades.
    val memoBackdrop = rememberLayerBackdrop()
    val mapBackdrop = rememberLayerBackdrop()
    val attachmentsBackdrop = rememberLayerBackdrop()
    val profileBackdrop = rememberLayerBackdrop()
    fun backdropFor(destination: MainDestination): LayerBackdrop = when (destination) {
        MainDestination.MEMOS -> memoBackdrop
        MainDestination.MAP -> mapBackdrop
        MainDestination.ATTACHMENTS -> attachmentsBackdrop
        MainDestination.PROFILE -> profileBackdrop
    }
    // The destination page records its backdrop in the same frame it is first composed. Delay the
    // bar's switch by one frame: switching immediately sampled a still-empty layer for an instant,
    // which made the blur vanish and the sharp content flash through the translucent bar.
    var activeNavBackdrop by remember { mutableStateOf(backdropFor(currentDestination)) }
    LaunchedEffect(currentDestination) {
        withFrameNanos { }
        activeNavBackdrop = backdropFor(currentDestination)
    }

    AppSurface(
        modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background
    ) {
        // Each bar has its own frosted switch; the values are provided here so every screen below
        // reads the same preference without threading it through every composable. The frost also
        // covers the surfaces that copy the bar's material (the compose button, the composer's own
        // bottom bar), so it stays on while the bar is liquid glass: the bar is still translucent
        // there, it is only glass instead of a frosted surface.
        CompositionLocalProvider(
            LocalTopBarBlurEnabled provides appearance.topBarBlur,
            LocalBottomBarBlurEnabled provides appearance.bottomBarBlur
        ) {
        // The bar's own material comes straight from the preferences: the helper above answers for
        // the surfaces that copy the bar's material, which keep frosting under either switch.
        // Mutually exclusive in the settings, so at most one of these is true.
        val barMaterialSupported = navBarMaterialSupported()
        val navBlurEnabled = appearance.bottomBarBlur && barMaterialSupported
        val navGlassEnabled = appearance.liquidNavBar && barMaterialSupported
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                // Navigation Rail for tablets/desktops
                if (!isMobile) {
                    if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                        MiuixNavigationRail {
                            Spacer(Modifier.height(12.dp))
                            visibleDestinations.forEach { destination ->
                                if (destination == MainDestination.PROFILE) {
                                    Spacer(Modifier.weight(1f))
                                }
                                MiuixNavigationRailItem(
                                    selected = currentDestination == destination,
                                    onClick = { handleDestinationClick(destination) },
                                    icon = destination.navIcon(currentDestination == destination),
                                    label = stringResource(destination.labelRes)
                                )
                            }
                        }
                    } else {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Spacer(Modifier.height(12.dp))
                            // Items
                            visibleDestinations.forEach { destination ->
                                if (destination == MainDestination.PROFILE) {
                                    Spacer(Modifier.weight(1f))
                                }
                                NavigationRailItem(
                                    selected = currentDestination == destination,
                                    onClick = { handleDestinationClick(destination) },
                                    icon = {
                                        NavigationIcon(
                                            destination, currentDestination == destination
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) })
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Content
                    AnimatedContent(
                        targetState = currentDestination,
                        transitionSpec = {
                            val spec = appFadeSpec(miuixMotion)
                            // Same-level bottom destinations have no spatial order, so they get a
                            // fade-through with a subtle zoom instead of a horizontal slide (which
                            // would falsely imply a stack push). The outgoing page still leaves
                            // quickly so it cannot linger in the translucent bar's backdrop.
                            (fadeIn(animationSpec = spec) + scaleIn(
                                animationSpec = spec,
                                initialScale = if (miuixMotion) 0.96f else 0.92f,
                                transformOrigin = TransformOrigin.Center
                            )) togetherWith fadeOut(animationSpec = tween(90))
                        },
                        label = "MainScreenDestinationTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetDestination ->
                        val destinationKey = if (targetDestination == MainDestination.MAP) {
                            "map:${uiState.accounts.firstOrNull { it.isActive }?.id}:${uiState.session.hostUrl}"
                        } else targetDestination
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    // The page records its backdrop for whichever material is on:
                                    // the frost and the glass both sample it.
                                    if (navBlurEnabled || navGlassEnabled) {
                                        Modifier.layerBackdrop(backdropFor(targetDestination))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            saveableStateHolder.SaveableStateProvider(destinationKey) {
                            when (targetDestination) {
                                MainDestination.MAP -> if (mapSupported) {
                                    key(uiState.accountGeneration) {
                                        MapScreen(viewModel, isNavBarVisible = isNavBarVisible, onToggleNavBar = setNavBarVisible, navBarAlwaysVisible = navBarAlwaysVisible)
                                    }
                                }
                                MainDestination.MEMOS -> MemosScreen(
                                    viewModel = viewModel,
                                    pagerState = feedPagerState,
                                    topBarScrollMode = appearance.topBarScrollMode,
                                    onToggleNavBar = setNavBarVisible,
                                    isNavBarVisible = isNavBarVisible,
                                    navBarAlwaysVisible = navBarAlwaysVisible,
                                    memoEditGesture = appearance.editGesture,
                                    // The floating bar carries the compose action already.
                                    showComposeFab = !(
                                        LocalThemeStyle.current == ThemeStyle.MIUIX &&
                                            appearance.floatingNavBar
                                        ),
                                    openComposer = shouldOpenComposer,
                                    onComposerOpened = onComposerOpened,
                                    openSearch = openSearchRequest,
                                    onSearchOpened = onOpenSearchRequestConsumed,
                                    onFeedSettled = { feedSettled = true }
                                )

                                MainDestination.ATTACHMENTS -> AttachmentsScreen(
                                    viewModel = viewModel,
                                    onToggleNavBar = toggleNavBar,
                                    isNavBarVisible = isNavBarVisible
                                )

                                MainDestination.PROFILE -> ProfileScreen(
                                    viewModel = viewModel,
                                    onLogout = onLogout,
                                    onAddAccount = { isAddingAccount = true },
                                    onToggleNavBar = toggleNavBar,
                                    isNavBarVisible = isNavBarVisible,
                                    onOpenMemos = {
                                        handleDestinationClick(MainDestination.MEMOS)
                                        feedPagerState.requestScrollToPage(MemoFeed.MEMOS.ordinal)
                                    }
                                )
                            }
                        }
                        }
                    }
                }
            }

            // Bottom Navigation Bar for mobile
            if (isMobile) {
                val floatingBar =
                    LocalThemeStyle.current == ThemeStyle.MIUIX && appearance.floatingNavBar
                if (floatingBar) {
                    FloatingBottomBar(
                        visible = isNavBarVisible && bottomBarReady,
                        destinations = visibleDestinations,
                        current = currentDestination,
                        onDestination = { handleDestinationClick(it) },
                        onCompose = {
                            uiState.accounts.firstOrNull { it.isActive }?.let { account ->
                                // Same rule as the feed's own compose button: a memo written while a
                                // space is scoped goes into that space.
                                val space = uiState.userMemoList.filter.space
                                openEditor(
                                    EditorRequest(
                                        account.id,
                                        titleRes = R.string.memo_composer_fab_new_memo,
                                        visibility = if (space != null) Visibility.SPACE
                                        else uiState.session.userSettings?.memoVisibility
                                            ?: Visibility.PRIVATE,
                                        space = space
                                    )
                                )
                            }
                        },
                        blurEnabled = navBlurEnabled,
                        liquidGlass = navGlassEnabled,
                        backdrop = activeNavBackdrop,
                        blurRefresh = barBlurRefresh,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                // The docked bar keeps its own navigation-bar inset: unlike the floating capsule,
                // it should fill the gesture-handle row with the same solid/blurred surface.
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AnimatedVisibility(
                        visible = isNavBarVisible && bottomBarReady,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(140)),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        if (LocalThemeStyle.current == ThemeStyle.MIUIX) {
                            val barMaterial = navBlurEnabled
                            MiuixNavigationBar(
                                // A translucent bar lets the list frost through; without blur it
                                // stays the plain opaque surface. The docked bar has no droplet to
                                // carry the glass, so both switches land on the same surface here.
                                color = if (barMaterial) {
                                    MiuixTheme.colorScheme.surface.copy(alpha = CapsuleSurfaceAlpha)
                                } else {
                                    MiuixTheme.colorScheme.surface
                                },
                                modifier = if (barMaterial) {
                                    Modifier
                                        .drawWithContent {
                                            if (barBlurRefresh.value < 0f) {
                                                return@drawWithContent
                                            }
                                            drawContent()
                                        }
                                        .drawBackdrop(
                                            backdrop = activeNavBackdrop,
                                            shape = { DockedBarShape },
                                            effects = { blur(NavBlurRadius, NavBlurRadius) }
                                        )
                                } else {
                                    Modifier
                                }
                            ) {
                                visibleDestinations.forEach { destination ->
                                    MiuixNavigationBarItem(
                                        selected = currentDestination == destination,
                                        onClick = { handleDestinationClick(destination) },
                                        icon = destination.navIcon(currentDestination == destination),
                                        label = stringResource(destination.labelRes)
                                    )
                                }
                            }
                        } else {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                visibleDestinations.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentDestination == destination,
                                        onClick = { handleDestinationClick(destination) },
                                        icon = {
                                            NavigationIcon(
                                                destination, currentDestination == destination
                                            )
                                        },
                                        label = { Text(stringResource(destination.labelRes)) })
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        }
    }

    // Conflict resolution dialog (server version changed while offline)
    uiState.conflict?.let { conflict ->
        ConflictDialog(
            conflict = conflict,
            onResolve = { resolution, mergedContent ->
                viewModel.resolveConflict(resolution, mergedContent)
            },
            onDismiss = { viewModel.dismissConflict() }
        )
    }

}

/** Miuix navigation items take a vector icon, so the avatar stays on the profile page itself. */
internal fun MainDestination.navIcon(selected: Boolean): ImageVector = when (this) {
    MainDestination.MAP -> if (selected) Icons.Filled.Map else Icons.Outlined.Map
    MainDestination.MEMOS ->
        if (selected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks
    MainDestination.ATTACHMENTS -> if (selected) Icons.Filled.Attachment else Icons.Outlined.Attachment
    MainDestination.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

