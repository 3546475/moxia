package org.example.moxia.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import org.example.moxia.api.AuthInterceptor
import org.example.moxia.api.MemosApi
import org.example.moxia.api.MemosApiFactory
import org.example.moxia.api.MemoOrderBy
import org.example.moxia.data.DataStoreManager
import org.example.moxia.data.DraftManager
import org.example.moxia.data.linkpreview.LinkPreviewRepository
import org.example.moxia.data.audit.SyncAuditLogger
import org.example.moxia.data.cache.CacheListType
import org.example.moxia.data.cache.MemoCacheRepository
import org.example.moxia.data.media.AttachmentCacheManager
import org.example.moxia.data.media.AttachmentUpload
import org.example.moxia.data.media.AttachmentUploadQueue
import org.example.moxia.data.network.ConnectivityObserver
import org.example.moxia.data.offline.AttachmentCacheStore
import org.example.moxia.data.offline.NotificationCacheStore
import org.example.moxia.data.offline.NotificationsSnapshotData
import org.example.moxia.data.offline.SessionCacheStore
import org.example.moxia.data.sync.ConflictResolution
import org.example.moxia.data.sync.PendingOp
import org.example.moxia.data.sync.PendingOpType
import org.example.moxia.data.sync.PreDownloadManager
import org.example.moxia.data.sync.PreDownloadState
import org.example.moxia.data.sync.SyncManager
import org.example.moxia.data.sync.SyncRepository
import org.example.moxia.data.sync.SyncWorkScheduler
import org.example.moxia.model.Account
import org.example.moxia.R
import org.example.moxia.model.Attachment
import org.example.moxia.model.Memo
import org.example.moxia.model.MemoState
import org.example.moxia.model.OfflineMemoPrefix
import org.example.moxia.viewmodel.delegates.AppSettingsDelegate
import org.example.moxia.viewmodel.delegates.AppSettingsDelegateImpl
import org.example.moxia.viewmodel.delegates.DraftDelegate
import org.example.moxia.viewmodel.delegates.DraftDelegateImpl
import org.example.moxia.viewmodel.delegates.FilterDelegate
import org.example.moxia.viewmodel.delegates.FilterDelegateImpl
import org.example.moxia.viewmodel.delegates.MemoActionDelegate
import org.example.moxia.viewmodel.delegates.MemoActionDelegateImpl
import org.example.moxia.viewmodel.delegates.MemoListUpdater
import org.example.moxia.viewmodel.delegates.ShortcutDelegate
import org.example.moxia.viewmodel.delegates.ShortcutDelegateImpl
import org.example.moxia.viewmodel.delegates.UserDelegate
import org.example.moxia.viewmodel.delegates.UserDelegateImpl
import org.example.moxia.viewmodel.delegates.WebhookDelegate
import org.example.moxia.viewmodel.delegates.WebhookDelegateImpl
import org.example.moxia.viewmodel.manager.ArchivedMemoListManager
import org.example.moxia.viewmodel.manager.AttachmentManager
import org.example.moxia.viewmodel.manager.BaseListManager
import org.example.moxia.viewmodel.manager.MemoCacheReconciler
import org.example.moxia.viewmodel.manager.CacheCallbacks
import org.example.moxia.viewmodel.manager.CommentListManager
import org.example.moxia.viewmodel.manager.ExploreMemoListManager
import org.example.moxia.viewmodel.manager.LocalSearchFilter
import org.example.moxia.viewmodel.manager.SearchMemoListManager
import org.example.moxia.viewmodel.manager.ServerReachabilityMonitor
import org.example.moxia.viewmodel.manager.UserMemoListManager
import org.example.moxia.viewmodel.manager.USER_MEMO_COMPARATOR
import org.example.moxia.model.UserNotification
import org.example.moxia.model.filter.MemoFilter
import org.example.moxia.model.filter.matches
import org.example.moxia.model.filter.toCel

/**
 * How long a replay pass has to run before the UI is told about it. A pass that drains the queue
 * in a few milliseconds is not an event the header should show.
 */
private const val SyncStatusAppearDelayMs = 350L

/**
 * Once shown, the sync state stays up at least this long. Faster passes would otherwise render as
 * a single-frame flicker of the header's status label and icon.
 */
private const val SyncStatusMinVisibleMs = 900L

class MemosViewModel(
    private val dataStoreManager: DataStoreManager,
    private val draftManager: DraftManager,
    private val memoCacheRepository: MemoCacheRepository,
    private val okHttpClient: OkHttpClient,
    private val connectivityObserver: ConnectivityObserver,
    private val attachmentCacheManager: AttachmentCacheManager,
    private val syncRepository: SyncRepository,
    private val syncWorkScheduler: SyncWorkScheduler,
    private val syncAuditLogger: SyncAuditLogger,
    private val attachmentCacheStore: AttachmentCacheStore,
    private val sessionCacheStore: SessionCacheStore,
    private val notificationCacheStore: NotificationCacheStore,
    private val attachmentUploadQueue: AttachmentUploadQueue
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemosUiState())
    val uiState: StateFlow<MemosUiState> = _uiState.asStateFlow()

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    private val _linkPreviews = MutableStateFlow<LinkPreviewRepository?>(null)
    val linkPreviews = _linkPreviews.asStateFlow()

    private val accountSession = AccountSession(viewModelScope)

    private val api: MemosApi? get() = accountSession.current?.takeIf { it.networkReady }?.api

    private fun activeAccountId(): String? = accountSession.current?.account?.id

    val memoMapManager = org.example.moxia.viewmodel.manager.MemoMapManager(
        accountSession, _uiState, memoCacheRepository, dataStoreManager
    )

    val spaceManager = org.example.moxia.viewmodel.manager.MemoSpaceManager(
        accountSession, _uiState, dataStoreManager
    )

    // Managers
    private val userMemoManager: UserMemoListManager = UserMemoListManager(
        scope = accountSession.readScope,
        apiProvider = { api },
        filterProvider = {
            val state = _uiState.value
            val filter = state.userMemoList.filter
            val creator = api?.buildMemoCreatorFilter(state.session.currUser)
            // A space is a shared area: its feed lists every member's memos, so the creator
            // clause only applies to the unscoped feed. The space clause itself comes from the
            // filter, which is the one place that knows which space the feed is scoped to.
            filter.toCel(if (filter.space == null) creator else null)
        },
        localFilterProvider = { _uiState.value.userMemoList.filter },
        spaceProvider = { _uiState.value.userMemoList.filter.space },
        sortProvider = { _uiState.value.userMemoList.filter.sort },
        pageSizeProvider = { _uiState.value.appSettings.pageSize },
        cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
            val context = accountSession.current ?: return@CacheCallbacks
            val accountId = context.account.id
            // Merge (never replace) so a page-1 refresh cannot wipe the
            // full-history text cache built by the pre-downloader.
            memoCacheRepository.cacheMemos(accountId, CacheListType.USER, memos, replace = false)
            if (!accountSession.isCurrent(context)) return@CacheCallbacks
            // A server page only contains what the server knows: the memos whose create is still
            // in the outbox would vanish from the feed on every refresh without this.
            memoCacheRepository.getPendingLocalMemos(accountId, CacheListType.USER)
                .forEach { memoListUpdater.insertMemoIntoUserList(it) }
            refreshTextCacheCount()
            // Pre-download attachments of what is now visible, for offline viewing.
            preDownloadManager.preloadVisibleAttachments(memos)
            memoCacheReconciler.noteFetched(memos.mapNotNull { it.name }.toSet())
        }, getCachedData = { limit ->
            val accountId = activeAccountId()
                ?: return@CacheCallbacks emptyList()
            memoCacheRepository.getCachedMemos(
                accountId, CacheListType.USER, limit = limit
            )
        }),
        protectedNamesProvider = ::pendingMemoNames
    )

    private val exploreMemoManager: ExploreMemoListManager =
        ExploreMemoListManager(
            scope = accountSession.readScope,
            apiProvider = { api },
            pageSizeProvider = { _uiState.value.appSettings.pageSize },
            protectedNamesProvider = ::pendingMemoNames,
            cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
                val context = accountSession.current ?: return@CacheCallbacks
                val accountId = context.account.id
                memoCacheRepository.cacheMemos(
                    accountId, CacheListType.EXPLORE, memos, replace = false
                )
                if (!accountSession.isCurrent(context)) return@CacheCallbacks
                refreshTextCacheCount()
                preDownloadManager.preloadVisibleAttachments(memos)
                memoCacheReconciler.noteFetched(memos.mapNotNull { it.name }.toSet())
            }, getCachedData = { limit ->
                val accountId = activeAccountId()
                    ?: return@CacheCallbacks emptyList()
                memoCacheRepository.getCachedMemos(
                    accountId, CacheListType.EXPLORE, limit = limit
                )
            })
        )

    private val archivedMemoManager: ArchivedMemoListManager =
        ArchivedMemoListManager(
            scope = accountSession.readScope,
            apiProvider = { api },
            currentUserProvider = { _uiState.value.session.currUser },
            pageSizeProvider = { _uiState.value.appSettings.pageSize },
            protectedNamesProvider = ::pendingMemoNames,
            cacheCallbacks = CacheCallbacks(onFetchSuccess = { memos ->
                val context = accountSession.current ?: return@CacheCallbacks
                val accountId = context.account.id
                memoCacheRepository.cacheMemos(
                    accountId, CacheListType.ARCHIVED, memos, replace = false
                )
                if (!accountSession.isCurrent(context)) return@CacheCallbacks
                refreshTextCacheCount()
                preDownloadManager.preloadVisibleAttachments(memos)
                memoCacheReconciler.noteFetched(memos.mapNotNull { it.name }.toSet())
            }, getCachedData = { limit ->
                val accountId = activeAccountId()
                    ?: return@CacheCallbacks emptyList()
                memoCacheRepository.getCachedMemos(
                    accountId, CacheListType.ARCHIVED, limit = limit
                )
            })
        )

    private val searchMemoManager: SearchMemoListManager = SearchMemoListManager(
        accountSession.readScope,
        { api },
        pageSizeProvider = { _uiState.value.appSettings.pageSize },
        // Search results are not persisted to the cache: offline search already
        // runs against the USER/ARCHIVED/EXPLORE cached rows via
        // searchCachedMemos, so writing a dedicated SEARCH list would only
        // duplicate data and bloat the database without any reader.
        cacheCallbacks = CacheCallbacks(onFetchSuccess = {}, getCachedData = { emptyList() }),
        protectedNamesProvider = ::pendingMemoNames,
        localSearchProvider = { filter ->
            val accountId = activeAccountId()
            if (accountId == null) emptyList()
            else memoCacheRepository.searchCachedMemos(
                accountId = accountId,
                query = filter.query,
                tags = filter.tags,
                startMillis = filter.startMillis,
                endMillis = filter.endMillis,
                // The Explore tab search must not surface the user's own
                // (private) memos: restrict to the EXPLORE cache rows, which
                // were pre-downloaded with the PUBLIC/PROTECTED visibility
                // filter.
                explore = filter.explore
            )
        })

    private val commentManager: CommentListManager = CommentListManager(
        accountSession.readScope,
        { api },
        cacheCallbacks = CacheCallbacks(onFetchSuccess = { comments ->
            val context = accountSession.current ?: return@CacheCallbacks
            val accountId = context.account.id
            val parent = commentManager.currentMemoName
            if (parent != null) {
                memoCacheRepository.cacheMemos(
                    accountId, CacheListType.COMMENT, comments, replace = false, parentName = parent
                )
            }
        }, getCachedData = { _ ->
            val context = accountSession.current ?: return@CacheCallbacks emptyList()
            val accountId = context.account.id
            val parent = commentManager.currentMemoName
            if (parent == null) emptyList()
            else memoCacheRepository.getCachedMemos(
                accountId, CacheListType.COMMENT, parentName = parent
            )
        }),
        isOnlineProvider = { _uiState.value.isOnline },
        protectedNamesProvider = ::pendingMemoNames
    )

    private val attachmentManager: AttachmentManager =
        AttachmentManager(
            scope = accountSession.readScope, accountSession = accountSession, apiProvider = { api },
            initialCellWidth = _uiState.value.attachmentList.cellWidth,
            uploadQueueProvider = { attachmentUploadQueue },
            draftReferenceChecker = { clientId ->
                val accountId = activeAccountId() ?: return@AttachmentManager false
                draftManager.draftsContain(accountId, clientId)
            },
            outboxReferenceChecker = { clientId ->
                val accountId = activeAccountId() ?: return@AttachmentManager false
                syncRepository.getOps(accountId).any { it.payloadJson?.contains(clientId) == true }
            },
            cacheCallbacks = CacheCallbacks(onFetchSuccess = { attachments ->
                val context = accountSession.current ?: return@CacheCallbacks
                val accountId = context.account.id
                attachmentCacheStore.cacheMeta(accountId, attachments)
            }, getCachedData = { limit ->
                val accountId = activeAccountId()
                    ?: return@CacheCallbacks emptyList()
                attachmentCacheStore.getCachedMeta(accountId, limit)
            }, onFirstPage = { attachments ->
                // A deleted attachment is a real deletion: the first page decides what exists.
                val context = accountSession.current ?: return@CacheCallbacks
                attachmentCacheStore.replaceRecentMeta(context.account.id, attachments)
            })
        )

    /** Preserve queued creates and edits in every list until the server acknowledges them. */
    private fun pendingMemoNames(): Set<String> = _uiState.value.pendingOps
        .flatMap { listOfNotNull(it.memoName, it.parentName) }.toSet()

    private var collectionJob: Job? = null

    // Server-reachability state machine (one-shot + periodic probes); bridged
    // into _uiState in startOfflineStateCollection().
    private val reachabilityMonitor = ServerReachabilityMonitor(
        scope = viewModelScope,
        apiProvider = { api },
        accountIdProvider = { activeAccountId() },
        isBlockedProvider = {
            connectivityObserver.refreshBlockedState()
            connectivityObserver.isBlocked.value
        }
    )

    private val _attachmentAspectRatios =
        MutableStateFlow<Map<String, Float>>(emptyMap())

    // Sync engine: replays queued offline writes when connectivity returns.
    private val syncManager = SyncManager(
        scope = viewModelScope,
        repository = syncRepository,
        accountSession = accountSession,
        memoCacheRepository = memoCacheRepository,
        dataStoreManager = dataStoreManager,
        workScheduler = syncWorkScheduler,
        auditLogger = syncAuditLogger,
        currentUserProvider = { _uiState.value.session.currUser },
        attachmentUploadQueueProvider = { attachmentUploadQueue },
        onMemoSynced = { memo, tempName ->
            if (tempName != null) {
                // A queued create just landed: swap the temporary local memo for the real one.
                memoListUpdater.removeMemoFromLists(tempName)
                memoListUpdater.insertMemoIntoUserList(memo)
            } else {
                memoListUpdater.updateMemoInLists(memo)
            }
        },
        onMemoDeleted = { name -> memoListUpdater.removeMemoFromLists(name) },
        onCommentsRefresh = { commentManager.fetch(refresh = true) },
        onConflict = { item ->
            // Keep the first unresolved conflict on screen: the sync loop may
            // detect several conflicts back-to-back, and overwriting the dialog
            // would drop the user's pending decision. Remaining conflicted ops
            // stay queued and re-surface on the next sync, one at a time.
            _uiState.update { state ->
                if (state.conflict != null) state else state.copy(conflict = item)
            }
        }
    )

    // Pre-downloads full text history + attachments for offline use.
    private val preDownloadManager = PreDownloadManager(
        scope = viewModelScope,
        memoCacheRepository = memoCacheRepository,
        dataStoreManager = dataStoreManager,
        attachmentCacheManager = attachmentCacheManager,
        attachmentCacheStore = attachmentCacheStore,
        apiProvider = { api },
        accountIdProvider = { activeAccountId() },
        userProvider = { _uiState.value.session.currUser },
        hostUrlProvider = { _uiState.value.session.hostUrl },
        tokenProvider = { _uiState.value.session.token },
        isOnlineProvider = { _uiState.value.isOnline },
        isWifiProvider = { connectivityObserver.isWifi.value }
    )

    // Delegates
    val userDelegate: UserDelegate = UserDelegateImpl(
        viewModelScope, _uiState, dataStoreManager, sessionCacheStore, accountSession,
        onAccountSwitched = { account ->
            switchAccountInternal(account)
        },
        onAccountRemoved = { account ->
            viewModelScope.launch {
                syncWorkScheduler.cancel(account.id)
                memoCacheRepository.clearCache(account.id)
                syncManager.clearForAccount(account.id)
                // Drop the deleted account's staged uploads and durable queue.
                attachmentUploadQueue.clearForAccount(account.id)
                attachmentCacheManager.clearAccount(account.id)
                attachmentCacheStore.clearMeta(account.id)
                notificationCacheStore.clear(account.id)
                // Drop the deleted account's snapshots and download metadata.
                sessionCacheStore.clear(account.id)
                dataStoreManager.removeLastSyncTime(account.id)
                dataStoreManager.removeDownloadState(account.id)
                dataStoreManager.removeSnapshot("map_support", account.id)
            }
        }
    )

    val shortcutDelegate: ShortcutDelegate = ShortcutDelegateImpl(
        viewModelScope,
        _uiState,
        sessionCacheStore,
        accountSession
    )

    /**
     * The feed filter. Applying one clears the cached-merge flag before refetching: the prefill
     * holds rows from the previous filter, and the server-filtered page must replace the list
     * plainly instead of being polluted by them.
     */
    val filterDelegate: FilterDelegate = FilterDelegateImpl(
        viewModelScope,
        _uiState,
        sessionCacheStore,
        accountSession
    ) { applied ->
        // Local first: hide the rows the new filter excludes before the request, so the feed
        // never keeps showing memos the summary bar already says are filtered out.
        val predicate: (Memo) -> Boolean = { memo -> applied.matches(memo) }
        userMemoManager.updateState { state ->
            state.copy(showingCached = false, items = state.items.filter(predicate))
        }
        // Offline the request would only time out; the cache already holds the filtered rows.
        if (_uiState.value.isOnline) {
            userMemoManager.fetch(refresh = true)
        } else {
            userMemoManager.loadFromCache()
        }
    }

    val webhookDelegate: WebhookDelegate = WebhookDelegateImpl(
        viewModelScope, _uiState, accountSession
    )

    /**
     * Scopes the notes feed to one space (null = every memo of the account). Same local-first
     * ordering as the filter: rows of another space leave before the request so the feed never
     * shows memos the header no longer names.
     */
    fun selectSpace(space: String?) {
        // Switching the feed's scope leaves the conditions alone and pushes the query, the cache
        // filter and the scroll reset through the same path as a filter change; remembering the
        // selection keeps it across restarts.
        spaceManager.select(space) {}
        filterDelegate.apply(_uiState.value.userMemoList.filter.copy(space = space))
    }

    /** Applies [filter] and keeps the remembered space scope in step with it. */
    fun applyFilter(filter: MemoFilter) {
        if (filter.space != _uiState.value.space.selected) {
            spaceManager.select(filter.space) {}
        }
        filterDelegate.apply(filter)
    }


    val appSettingsDelegate: AppSettingsDelegate = AppSettingsDelegateImpl(
        viewModelScope, _uiState, dataStoreManager,
        onPageSizeChanged = {
            userMemoManager.fetch(refresh = true)
            exploreMemoManager.fetch(refresh = true)
        },
        onOfflineSettingsChanged = {
            preDownloadManager.maybeAutoDownload()
        }
    )

    val draftDelegate: DraftDelegate = DraftDelegateImpl(
        viewModelScope, _uiState, draftManager, accountSession, { memoActionDelegate }) { userMemoManager.fetch(refresh = true) }

    private val memoListUpdater = object : MemoListUpdater {
        override fun updateMemoInLists(memo: Memo) {
            updateMemoInState(memo)
        }

        override fun removeMemoFromLists(memoName: String) {
            memoMapManager.forget(memoName)
            userMemoManager.forget(memoName)
            exploreMemoManager.forget(memoName)
            archivedMemoManager.forget(memoName)
            searchMemoManager.forget(memoName)
            commentManager.forget(memoName)
        }

        override fun refreshUserMemos() {
            userMemoManager.fetch(refresh = true)
            memoMapManager.refreshIfOpen()
        }

        override fun handleMemoStateChange(memo: Memo, updated: Memo) {
            memoMapManager.upsert(updated)
            val comparator = compareByDescending<Memo> { it.displayTime }

            when (memoStateMove(memo.state, updated.state)) {
                MemoStateMove.TO_ARCHIVED -> {
                    // Move from User/Explore -> Archived
                    val isSame = { m: Memo -> m.name == memo.name }
                    userMemoManager.remove(isSame)
                    exploreMemoManager.remove(isSame)

                    val isSameUpdated = { m: Memo -> m.name == updated.name }
                    archivedMemoManager.upsert(updated, isSameUpdated, comparator)
                }

                MemoStateMove.TO_USER -> {
                    // Move from Archived -> User (and maybe Explore if public, but keep simple for now)
                    val isSame = { m: Memo -> m.name == memo.name }
                    archivedMemoManager.remove(isSame)

                    val isSameUpdated = { m: Memo -> m.name == updated.name }
                    userMemoManager.upsert(updated, isSameUpdated, USER_MEMO_COMPARATOR)
                }

                null -> Unit
            }
        }

        override fun insertMemoIntoUserList(memo: Memo) {
            memoMapManager.upsert(memo)
            userMemoManager.upsert(memo, { it.name == memo.name }, USER_MEMO_COMPARATOR)
            if (_uiState.value.detailPane.selectedMemo?.name == memo.name) {
                _uiState.update {
                    it.copy(detailPane = it.detailPane.copy(selectedMemo = memo))
                }
            }
        }
    }

    val memoActionDelegate: MemoActionDelegate = MemoActionDelegateImpl(
        viewModelScope,
        _uiState,
        memoListUpdater,
        draftDelegate,
        { attachmentManager },
        { commentManager },
        syncManager,
        memoCacheRepository,
        accountSession,
        draftManager,
        { _uiState.value.isOnline })

    private var manualRefreshJob: Job? = null
    private val memoCacheReconciler = MemoCacheReconciler(
        session = accountSession,
        repository = memoCacheRepository,
        onDeleted = { name ->
            memoListUpdater.removeMemoFromLists(name)
            if (_uiState.value.detailPane.selectedMemo?.name == name) {
                commentManager.clearParent()
                _uiState.update { it.copy(detailPane = DetailPaneState()) }
            }
            refreshTextCacheCount()
        },
        onStateMoved = { name, archived -> moveCachedMemoBetweenLists(name, archived) }
    )

    /** Mirror an archive-state reconciliation (cache already re-filed) into the visible lists. */
    private suspend fun moveCachedMemoBetweenLists(name: String, archived: Boolean) {
        val context = accountSession.current ?: return
        val memo = memoCacheRepository.getCachedMemo(context.account.id, name) ?: return
        if (!accountSession.isCurrent(context)) return
        memoMapManager.upsert(memo)
        if (archived) {
            userMemoManager.remove { it.name == name }
            exploreMemoManager.remove { it.name == name }
            archivedMemoManager.upsert(memo, { it.name == name }, compareByDescending { it.displayTime })
        } else {
            archivedMemoManager.remove { it.name == name }
            userMemoManager.upsert(memo, { it.name == name }, USER_MEMO_COMPARATOR)
        }
        if (_uiState.value.detailPane.selectedMemo?.name == name) {
            _uiState.update { it.copy(detailPane = it.detailPane.copy(selectedMemo = memo)) }
        }
    }

    private val unregisterBackupHook = org.example.moxia.data.backup.BackupCoordinator.register { restoring ->
        if (restoring) {
            // Keep the settings destination mounted while its restore coroutine is running.
            accountSession.clear()
            syncManager.cancelSync()
            preDownloadManager.cancel()
            reachabilityMonitor.cancelProbe()
            memoCacheReconciler.cancel()
            manualRefreshJob?.cancel()
        } else {
            attachmentCacheManager.invalidateLocalIndex()
            if (org.example.moxia.data.backup.BackupCoordinator.startupReady.value) {
                switchAccountInternal(dataStoreManager.getAccounts().firstOrNull { it.isActive })
                userDelegate.updateCurrentAccountInList()
                // A restore may have brought queued edits back into the outbox; replay them now
                // that the account session is live again.
                syncManager.pushPendingChanges()
            }
        }
    }

    init {
        viewModelScope.launch {
            org.example.moxia.data.backup.BackupCoordinator.awaitStartupRecovery()
            if (org.example.moxia.data.backup.BackupCoordinator.recoveryError.value == null) userDelegate.updateCurrentAccountInList()
            appSettingsDelegate.loadPageSize()
            appSettingsDelegate.loadHeaderScale()
            appSettingsDelegate.loadEditorSettings()
            appSettingsDelegate.loadLinkPreviewEnabled()
            appSettingsDelegate.loadOfflineSettings()

            startStateCollection()
            startOfflineStateCollection()
            syncManager.startObserving(accountSession.contexts.map { it?.account?.id })
        }
    }

    fun retryAccountConnection() {
        val context = accountSession.current ?: return
        viewModelScope.launch {
            val saved = dataStoreManager.getAccounts().firstOrNull { it.id == context.account.id && it.isActive }
            if (saved != null && accountSession.isCurrent(context)) switchAccountInternal(saved)
        }
    }

    private fun runRecoverySequence() = recoverConnection()

    private fun recoverConnection(excludedManager: BaseListManager<*>? = null) {
        if (!org.example.moxia.data.backup.BackupCoordinator.startupReady.value || org.example.moxia.data.backup.BackupCoordinator.restoring.value || org.example.moxia.data.backup.BackupCoordinator.recoveryError.value != null) return
        val reachable = reachabilityMonitor.state.value
        _uiState.update { it.copy(isOnline = reachable.isOnline, connectionState = reachable.connectionState, syncError = reachable.error) }
        if (reachable.connectionState != ConnectionState.ONLINE) return
        memoMapManager.verify()
        spaceManager.verify()
        syncManager.pushPendingChanges()
        preDownloadManager.maybeAutoDownload()
        if (_uiState.value.session.currUser == null) fetchCurrentUser()
        listOf(userMemoManager, exploreMemoManager, archivedMemoManager).forEach { manager ->
            if (manager !== excludedManager && (manager.listState.value.showingCached || manager.listState.value.isOffline))
                manager.fetch(refresh = true)
        }
        memoCacheReconciler.start()
    }

    // Keep this one as it's used by the delegate directly above
    private suspend fun switchAccountInternal(account: Account?) {
        if (account != null && (!org.example.moxia.data.backup.BackupCoordinator.startupReady.value || org.example.moxia.data.backup.BackupCoordinator.restoring.value || org.example.moxia.data.backup.BackupCoordinator.recoveryError.value != null)) return
        _sessionReady.value = false
        memoMapManager.reset()
        spaceManager.reset()
        _linkPreviews.value = null
        accountSession.clear()
        syncManager.cancelSync()
        preDownloadManager.cancel()
        reachabilityMonitor.cancelProbe()
        memoCacheReconciler.cancel()
        manualRefreshJob?.cancel()
        userMemoManager.reset()
        exploreMemoManager.reset()
        archivedMemoManager.reset()
        searchMemoManager.reset()
        commentManager.clearParent()
        attachmentManager.reset()
        _attachmentAspectRatios.value = emptyMap()
        _uiState.update { it.forAccount(account) }
        if (account == null) return
        val client = okHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor(account.accessToken)).build()
        val provisional = accountSession.activate(
            account, MemosApiFactory.createLatest(account.hostUrl, client), client, networkReady = false
        )
        // Local reads never wait for network version discovery or server reachability. The
        // persisted filter has to be in place first, otherwise the cached feed is read without
        // it and the first frame shows memos the filter would have hidden.
        userDelegate.restoreCachedSessionNow()
        userMemoManager.loadFromCache()
        exploreMemoManager.loadFromCache()
        memoMapManager.restoreSupport(provisional)
        spaceManager.restoreSupport(provisional)
        _sessionReady.value = true
        draftDelegate.loadDraftsForAccount(account.id)
        refreshTextCacheCount()
        accountSession.readScope.launch { attachmentCacheManager.refreshUsage(account.id) }
        accountSession.readScope.launch {
            val detectedApi = MemosApiFactory.create(account.hostUrl, client)
            if (!accountSession.completeConnection(provisional, detectedApi)) return@launch
            _linkPreviews.value = LinkPreviewRepository(
                scope = CoroutineScope(accountSession.readScope.coroutineContext),
                fetch = detectedApi::getLinkMetadata,
                canFetch = { _uiState.value.isOnline && _uiState.value.appSettings.linkPreviewEnabled }
            )
            val context = provisional
            fetchCurrentUser()
            reachabilityMonitor.checkNow {
                if (accountSession.isCurrent(context)) {
                    userMemoManager.fetch(refresh = true)
                    exploreMemoManager.fetch(refresh = true)
                    runRecoverySequence()
                }
            }
            accountSession.readScope.launch { attachmentCacheManager.refreshUsage(account.id) }
        }
    }

    private fun startStateCollection() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            combine(
                combine(
                    userMemoManager.listState,
                    exploreMemoManager.listState,
                    archivedMemoManager.listState
                ) { u, e, a -> Triple(u, e, a) },
                combine(
                    searchMemoManager.listState,
                    commentManager.listState,
                    attachmentManager.listState
                ) { s, c, at -> Triple(s, c, at) },
                attachmentManager.cellWidth,
                _attachmentAspectRatios
            ) { (userMemos, exploreMemos, archivedMemos), (searchMemos, comments, attachments), cellWidth, aspectRatios ->
                // Use update{} (CAS) instead of `value = copy(...)`: several
                // other collectors (pendingOps, isSyncing, preDownloadState,
                // usage, lastSyncTime, syncError) mutate the same StateFlow
                // concurrently, and a plain read-modify-write would silently
                // drop their updates.
                _uiState.update { current ->
                    current.copy(
                        userMemoList = current.userMemoList.copy(list = userMemos),
                        exploreMemoList = current.exploreMemoList.copy(list = exploreMemos),
                        archivedMemoList = current.archivedMemoList.copy(list = archivedMemos),
                        searchMemoList = current.searchMemoList.copy(list = searchMemos),
                        detailPane = current.detailPane.copy(comments = comments),
                        attachmentList = AttachmentListState(
                            list = attachments, cellWidth = cellWidth, aspectRatios = aspectRatios
                        )
                    )
                }

                // Fetch missing users for all visible lists (deduped in the delegate)
                val allCreators =
                    (userMemos.items + exploreMemos.items + searchMemos.items + archivedMemos.items + comments.items).mapNotNull { it.creator }
                        .distinct()
                if (allCreators.isNotEmpty()) {
                    userDelegate.fetchUsers(allCreators)
                }
            }.collect { }
        }
    }

    /**
     * Collects offline/sync state: connectivity, pending ops, sync progress,
     * pre-download progress, last sync time and attachment cache usage.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startOfflineStateCollection() {
        // Periodic reachability probe: catches the case where the OS keeps
        // reporting validated connectivity while the server process dies (or
        // comes back) without any connectivity change.
        // The view model is application-scoped: release the polling work while the app is in the
        // background and resume it when an activity is visible again.
        viewModelScope.launch {
            org.example.moxia.data.AppVisibility.foreground.collect { foreground ->
                if (foreground) resumeProcessWork() else pauseProcessWork()
            }
        }
        reachabilityMonitor.start(::runRecoverySequence)
        viewModelScope.launch {
            org.example.moxia.api.ServerRateLimit.shared.changes.collect {
                val account = accountSession.current?.account ?: return@collect
                if (org.example.moxia.api.ServerRateLimit.shared.remaining(account.hostUrl, account.accessToken) > 0)
                    reachabilityMonitor.checkNow(::runRecoverySequence)
            }
        }
        // Bridge the monitor's reachability state into the UI state.
        viewModelScope.launch {
            reachabilityMonitor.state.collect { reachability ->
                _uiState.update {
                    it.copy(
                        isOnline = reachability.isOnline,
                        connectionState = reachability.connectionState,
                        syncError = reachability.error
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(connectivityObserver.isOnline, connectivityObserver.isBlocked) { online, blocked -> online to blocked }
                .distinctUntilChanged().collect { (online, blocked) ->
                    if (!online || blocked) {
                        reachabilityMonitor.cancelProbe()
                        _uiState.update {
                            it.copy(isOnline = false, connectionState = ConnectionState.CHECKING)
                        }
                        // Stop active network work promptly; durable Room queue and
                        // optimistic cache remain intact for the next recovery pass.
                        syncManager.cancelSync()
                        memoMapManager.connectionUnavailable()
                        preDownloadManager.cancel()
                        memoCacheReconciler.cancel()
                        listOf(userMemoManager, exploreMemoManager, archivedMemoManager, searchMemoManager, commentManager, attachmentManager)
                            .forEach { manager ->
                                manager.cancelRequests()
                                manager.updateState { it.copy(isOffline = true, showingCached = it.items.isNotEmpty()) }
                            }
                        // Even when the system reports no validated Internet (common on
                        // emulators using adb reverse, or captive portals the OS hasn't
                        // validated yet), the server may still be reachable. Probe it
                        // rather than serving stale cache indefinitely.
                        reachabilityMonitor.checkNow(::runRecoverySequence)
                    } else {
                        reachabilityMonitor.checkNow(::runRecoverySequence)
                    }
                }
        }
        viewModelScope.launch {
            // StateFlow collectors always see the latest value (StateFlow is
            // conflated by design); the queue may change per operation during
            // sync bursts, but only the newest snapshot reaches the UI state.
            syncManager.currentPendingOps.collect { (context, ops) ->
                accountSession.update(_uiState, context) {
                    it.copy(pendingOps = ops, pendingOpsCount = ops.size)
                }
                pruneOrphanedLocalMemos(context, ops)
            }
        }
        viewModelScope.launch {
            // The header turns this into visible text and an icon, so it must not report a pass
            // that is over before it can be read: the queue is drained in milliseconds in the
            // normal case, and publishing that straight through made the status blink on every
            // resume, refresh and foreground probe. Wait until the pass has really been running,
            // then keep the state up long enough to read it.
            var shownAt = 0L
            syncManager.isSyncing.collectLatest { syncing ->
                if (syncing) {
                    delay(SyncStatusAppearDelayMs)
                    shownAt = System.currentTimeMillis()
                    _uiState.update { it.copy(isSyncing = true) }
                } else {
                    if (shownAt > 0L) {
                        val remaining = SyncStatusMinVisibleMs - (System.currentTimeMillis() - shownAt)
                        if (remaining > 0) delay(remaining)
                        shownAt = 0L
                    }
                    _uiState.update { it.copy(isSyncing = false) }
                }
            }
        }
        viewModelScope.launch {
            // Running progress is sampled at the source (PreDownloadManager),
            // so a pre-download burst cannot storm the UI state with
            // intermediate progress updates.
            preDownloadManager.state.collect { state ->
                _uiState.update { if (state is PreDownloadState.Running && state.accountId != activeAccountId() ||
                        state is PreDownloadState.Done && state.accountId != activeAccountId() ||
                        state is PreDownloadState.Failed && state.accountId != activeAccountId()) it
                    else it.copy(preDownloadState = state) }
                // A finished pre-download changes the cached text count; refresh
                // it so the status bar / panel show up-to-date numbers.
                if (state is PreDownloadState.Done) refreshTextCacheCount()
            }
        }
        viewModelScope.launch {
            combine(accountSession.contexts, attachmentCacheManager.usage) { context, usage ->
                context to usage
            }.collect { (context, snapshot) ->
                if (context != null && snapshot.accountId == context.account.id) {
                    accountSession.update(_uiState, context) { it.copy(attachmentCacheUsage = snapshot.usage) }
                }
            }
        }
        viewModelScope.launch {
            // Per-account key: switching accounts must show that account's own
            // last-sync time, not the previous one's.
            accountSession.contexts.flatMapLatest { context ->
                dataStoreManager.lastSyncTime(context?.account?.id).map { context to it }
            }.conflate().collect { (context, timestamp) ->
                if (context != null) accountSession.update(_uiState, context) {
                    it.copy(lastSyncTime = timestamp)
                }
            }
        }
        // Surface the most recent connection/sync error so the UI can show a
        // concrete failure reason (e.g. "timeout", "server unreachable") instead
        // of a generic offline notice.
        viewModelScope.launch {
            combine(
                userMemoManager.listState.map { it.errorMessage },
                exploreMemoManager.listState.map { it.errorMessage },
                archivedMemoManager.listState.map { it.errorMessage },
                syncManager.currentPendingOps
            ) { userErr, exploreErr, archivedErr, snapshot ->
                snapshot.context to (listOfNotNull(userErr, exploreErr, archivedErr).firstOrNull()
                    ?: snapshot.ops.asReversed().firstNotNullOfOrNull { it.lastError })
            }.distinctUntilChanged().conflate().collect { (context, err) ->
                accountSession.update(_uiState, context) { it.copy(syncError = err) }
            }
        }
    }

    // --- User & Session (Delegated) ---

    // Exposed for delegation only
    private fun fetchCurrentUser() {
        val context = accountSession.current ?: return
        userDelegate.fetchCurrentUser { user ->
            if (!accountSession.isCurrent(context)) return@fetchCurrentUser
            memoMapManager.verify()
            spaceManager.verify()
            // User fetched, now fetch related data that requires user name
            val name = user.name ?: return@fetchCurrentUser
            accountSession.readScope.launch { shortcutDelegate.fetchShortcuts(name) }
            accountSession.readScope.launch { webhookDelegate.fetchWebhooks(name) }

            // Full-text pre-download needs the user identity to build the
            // creator filter - at account-switch time it was still null, so
            // re-trigger now that we know who the user is.
            preDownloadManager.maybeAutoDownload()

            // Refresh user memos now that we have the numeric userId
            if (_uiState.value.isOnline) {
                userMemoManager.fetch(refresh = true)
            } else {
                userMemoManager.loadFromCache()
            }
        }
    }

    fun fetchUserMemos(refresh: Boolean = false) {
        if (refresh) {
            refreshManually(RefreshSource.USerMemos, userMemoManager)
            return
        }
        if (!_uiState.value.isOnline) {
            // Offline: serve cached data immediately instead of timing out.
            userMemoManager.loadFromCache()
            return
        }
        userMemoManager.fetch()
    }

    fun loadMoreUserMemos() = userMemoManager.loadMore()

    /**
     * Pages through the whole user feed so a caller can filter it locally. The feed only keeps the
     * pages it has loaded, so anything derived from it (the profile's per-kind lists and counts)
     * used to miss every memo past the first page. [maxPages] bounds the work for large accounts.
     */
    suspend fun loadAllUserMemos(maxPages: Int = 20) {
        var pages = 0
        var idleTries = 0
        while (pages < maxPages) {
            val list = _uiState.value.userMemoList.list
            if (list.nextPageToken == null) return
            if (list.isLoading) {
                delay(80)
                continue
            }
            val tokenBefore = list.nextPageToken
            userMemoManager.loadMore()
            pages++
            // Wait for the page to settle; a stalled request must not spin here forever.
            while (_uiState.value.userMemoList.list.isLoading) delay(60)
            if (_uiState.value.userMemoList.list.nextPageToken == tokenBefore) {
                if (++idleTries >= 2) return
            } else {
                idleTries = 0
            }
        }
    }

    fun fetchExploreMemos(refresh: Boolean = false) {
        if (refresh) {
            refreshManually(RefreshSource.ExploreMemos, exploreMemoManager)
            return
        }
        if (!_uiState.value.isOnline) {
            exploreMemoManager.loadFromCache()
            return
        }
        exploreMemoManager.fetch()
    }

    fun loadMoreExploreMemos() = exploreMemoManager.loadMore()

    fun fetchArchivedMemos(refresh: Boolean = false) {
        if (refresh) {
            refreshManually(RefreshSource.ArchivedMemos, archivedMemoManager)
            return
        }
        if (!_uiState.value.isOnline) {
            archivedMemoManager.loadFromCache()
            return
        }
        archivedMemoManager.fetch()
    }

    fun loadMoreArchivedMemos() = archivedMemoManager.loadMore()

    fun fetchSearchMemos(refresh: Boolean = false) {
        if (refresh) refreshManually(RefreshSource.SearchMemos, searchMemoManager)
        else searchMemoManager.fetch()
    }

    fun loadMoreSearchMemos() = searchMemoManager.loadMore()

    fun searchMemos(
        isExplore: Boolean,
        filter: String?,
        orderBy: MemoOrderBy? = null,
        localFilter: LocalSearchFilter = LocalSearchFilter()
    ) {
        searchMemoManager.updateFilter(filter, orderBy)
        searchMemoManager.updateLocalFilter(localFilter)
        if (!_uiState.value.isOnline) {
            // Offline: search the local cache instead of the server.
            searchMemoManager.searchLocal()
            return
        }
        // Updating a query is a list read; recovery is driven by explicit refresh/foreground events.
        searchMemoManager.fetch(refresh = true)
    }

    // --- Offline / sync actions ---

    /**
     * App came to the foreground: flush queued writes and refresh the cache.
     */
    /** No activity is visible: stop the reachability scheduler and the cache sweep. */
    private fun pauseProcessWork() {
        reachabilityMonitor.stop()
        memoCacheReconciler.cancel()
    }

    /** An activity is visible again: resume what [pauseProcessWork] released. */
    private fun resumeProcessWork() {
        if (accountSession.current == null) return
        reachabilityMonitor.start(::runRecoverySequence)
        memoCacheReconciler.start()
    }

    /**
     * Delete an attachment on the server (the Resources page is the only place that offers it).
     * The row disappears immediately; a transport failure keeps the deletion in the outbox so it
     * is not lost, while a server rejection restores it and reports the error.
     */
    fun deleteAttachment(attachment: Attachment) {
        val name = attachment.name ?: return
        val context = accountSession.current ?: return
        val accountId = context.account.id
        val wasOnline = _uiState.value.isOnline && context.networkReady
        viewModelScope.launch {
            val snapshot = attachmentManager.listState.value.items

            suspend fun removeLocally() {
                attachmentManager.remove { it.name == name }
                attachmentCacheStore.removeMeta(accountId, name)
                attachmentCacheManager.deleteCached(accountId, name)
            }

            suspend fun queueDeletion() {
                syncManager.enqueue(
                    PendingOp.new(
                        accountId = accountId,
                        type = PendingOpType.ATTACHMENT_DELETE,
                        memoName = name,
                        parentName = attachment.memo
                    )
                )
            }

            if (!wasOnline) {
                // The row is already gone locally; queueing the server call is what keeps the
                // next list refresh from bringing it back, and keeps the deletion across restarts.
                removeLocally()
                queueDeletion()
                return@launch
            }

            try {
                context.api.deleteAttachment(name)
                removeLocally()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is IOException || !accountSession.isCurrent(context) || !_uiState.value.isOnline) {
                    // Looks like a transport failure: keep the deletion and let the outbox retry.
                    removeLocally()
                    queueDeletion()
                } else {
                    attachmentManager.restoreItems(snapshot)
                    _uiState.update {
                        it.copy(error = UiMessage(R.string.common_operation_failed))
                    }
                }
            }
        }
    }

    /**
     * Load the attachment uploads a replay pass gave up on. The sync panel lists them so a file
     * that never reached the server is not lost silently; call this when the panel opens.
     */
    suspend fun refreshFailedUploads() {
        val context = accountSession.current ?: return
        val failed = attachmentUploadQueue.getFailed(context.account.id)
        accountSession.update(_uiState, context) { it.copy(failedUploads = failed) }
    }

    /** Discard a failed upload's staged bytes (the only way to free them). */
    fun discardFailedUpload(upload: AttachmentUpload) {
        val context = accountSession.current ?: return
        if (upload.accountId != context.account.id) return
        viewModelScope.launch {
            attachmentUploadQueue.discard(upload.clientId)
            refreshFailedUploads()
        }
    }

    fun onForeground() {
        if (!org.example.moxia.data.backup.BackupCoordinator.startupReady.value || org.example.moxia.data.backup.BackupCoordinator.restoring.value || org.example.moxia.data.backup.BackupCoordinator.recoveryError.value != null) return
        reachabilityMonitor.checkNow(::runRecoverySequence)
    }

    /** Reconcile activity-owned changes without emitting a scroll-to-top refresh trigger. */
    fun refreshAfterProfileDetails() {
        viewModelScope.launch {
            userDelegate.reconcileAccount()
            userDelegate.restoreCachedSessionNow(replace = true)
            refreshTextCacheCount()
            refreshAttachmentCacheUsage()
            if (_uiState.value.isOnline) {
                fetchCurrentUser()
                userMemoManager.fetch(refresh = true)
                exploreMemoManager.fetch(refresh = true)
                archivedMemoManager.fetch(refresh = true)
            } else {
                userMemoManager.loadFromCache()
                exploreMemoManager.loadFromCache()
                archivedMemoManager.loadFromCache()
            }
        }
    }

    /** Refresh activity-owned edits without resetting navigation or scroll state. */
    fun refreshAfterEditor() {
        val context = accountSession.current ?: return
        draftDelegate.loadDraftsForAccount(context.account.id)
        refreshAfterProfileDetails()
        viewModelScope.launch {
            // The composer runs in its own activity with its own view model instance, so the
            // memo it created lives in the shared cache but not in this instance's lists. Re-read
            // the page and the new cards from the cache: that is what makes them visible the
            // moment the composer closes instead of after a server round trip.
            val accountId = context.account.id
            val visible = userMemoManager.listState.value.items
            val refreshed = visible.mapNotNull { memo ->
                memo.name?.let { name -> memoCacheRepository.getCachedMemo(accountId, name) }
            }
            if (accountSession.isCurrent(context)) refreshed.forEach { updateMemoInState(it) }
            memoCacheRepository.getPendingLocalMemos(accountId, CacheListType.USER)
                .forEach { memoListUpdater.insertMemoIntoUserList(it) }

            val selected = _uiState.value.detailPane.selectedMemo
            selected?.name?.let { name ->
                memoCacheRepository.getCachedMemo(context.account.id, name)?.let { updated ->
                    if (accountSession.isCurrent(context)) updateMemoInState(updated)
                }
            }
            if (!accountSession.isCurrent(context)) return@launch
            if (_uiState.value.isOnline) {
                if (searchMemoManager.hasActiveQuery) searchMemoManager.fetch(refresh = true)
                if (selected != null) commentManager.fetch(refresh = true)
            } else {
                if (searchMemoManager.hasActiveQuery) searchMemoManager.searchLocal()
                commentManager.loadFromCache()
            }
            memoMapManager.refreshIfOpen()
        }
    }

    /**
     * Remove a single queued offline write (user abandons it).
     */
    fun deletePendingOp(opId: String) {
        val context = accountSession.current ?: return
        viewModelScope.launch {
            if (syncRepository.getOp(opId)?.accountId != context.account.id) return@launch
            syncManager.deleteOp(opId)
        }
    }

    /**
     * Clear the recorded failure of a queued offline write and replay it now.
     */
    fun retryPendingOp(opId: String) {
        val context = accountSession.current ?: return
        viewModelScope.launch {
            if (syncRepository.getOp(opId)?.accountId != context.account.id) return@launch
            syncManager.retryOp(opId)
        }
    }

    fun cacheNow(text: Boolean, attachments: Boolean) {
        if (text) {
            // Run both selected phases in one job; separate jobs would compete.
            preDownloadManager.downloadAllText(includeAttachments = attachments)
        } else if (attachments) {
            preDownloadManager.preloadAllAttachments()
        }
    }

    fun clearTextCache() {
        val context = accountSession.current ?: return
        val accountId = context.account.id
        viewModelScope.launch {
            preDownloadManager.clearTextCache(accountId)
            if (!accountSession.isCurrent(context)) return@launch
            // The in-memory list still shows the wiped cache; reset it so the
            // UI reflects the empty local state (re-fetch/pre-download fills it).
            userMemoManager.reset()
            exploreMemoManager.reset()
            archivedMemoManager.reset()
            memoMapManager.refreshIfOpen()
            refreshTextCacheCount()
        }
    }

    fun clearAttachmentCache() {
        val accountId = activeAccountId() ?: return
        viewModelScope.launch {
            preDownloadManager.clearAttachmentCache(accountId)
        }
    }

    /**
     * Clear both the text cache (local memo DB) and the attachment cache
     * (offline_media files) for the active account.
     */
    fun clearAllCaches() {
        val context = accountSession.current ?: return
        val accountId = context.account.id
        viewModelScope.launch {
            preDownloadManager.clearTextCache(accountId)
            preDownloadManager.clearAttachmentCache(accountId)
            if (!accountSession.isCurrent(context)) return@launch
            refreshTextCacheCount()
            attachmentCacheManager.refreshUsage(accountId)
        }
    }

    /**
     * Refresh the number of locally cached memos shown in the cache analysis UI.
     */
    fun refreshTextCacheCount() {
        val context = accountSession.current ?: return
        val accountId = context.account.id
        viewModelScope.launch {
            val count = memoCacheRepository.getCachedCount(accountId)
            accountSession.update(_uiState, context) { it.copy(textCacheCount = count) }
        }
    }

    fun refreshAttachmentCacheUsage() {
        activeAccountId()?.let { accountId ->
            viewModelScope.launch { attachmentCacheManager.refreshUsage(accountId) }
        }
    }

    fun resolveConflict(resolution: ConflictResolution, mergedContent: String? = null) {
        val item = _uiState.value.conflict ?: return
        _uiState.update { it.copy(conflict = null) }
        syncManager.resolveConflict(item, resolution, mergedContent)
        // Resolving may have unblocked other conflicted ops (the sync loop keeps
        // them queued while a dialog is up). Push again so the next conflict
        // surfaces promptly; LATER
        // deliberately leaves it parked.
        if (resolution != ConflictResolution.LATER) {
            syncManager.pushPendingChanges()
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(conflict = null) }
    }

    private fun updateRefreshTrigger(source: RefreshSource = RefreshSource.Manual) {
        _uiState.update {
            it.copy(
                isRefreshing = true,
                refreshTrigger = System.currentTimeMillis(),
                refreshSource = source
            )
        }
    }

    private fun refreshManually(source: RefreshSource, manager: BaseListManager<*>, softRefresh: Boolean = false) {
        val context = accountSession.current ?: return
        manualRefreshJob?.cancel()
        updateRefreshTrigger(source)
        manualRefreshJob = accountSession.readScope.launch {
            try {
                reachabilityMonitor.checkNow()?.join()
                if (!accountSession.isCurrent(context)) return@launch
                if (connectivityObserver.isBlocked.value) {
                    manager.loadFromCache()
                    return@launch
                }
                if (reachabilityMonitor.state.value.connectionState == ConnectionState.RATE_LIMITED) {
                    manager.loadFromCache()
                    return@launch
                }
                recoverConnection(excludedManager = manager)
                manager.fetch(refresh = true, softRefresh = softRefresh)?.join()
            } finally {
                if (accountSession.isCurrent(context) && kotlinx.coroutines.currentCoroutineContext().isActive) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    fun fetchAttachments(refresh: Boolean = false) {
        if (refresh) {
            refreshManually(RefreshSource.Attachments, attachmentManager, softRefresh = true)
            return
        }
        if (!_uiState.value.isOnline) {
            // Offline: serve cached metadata immediately instead of timing out.
            attachmentManager.loadFromCache()
            return
        }
        attachmentManager.fetch()
    }

    fun loadMoreAttachments() {
        attachmentManager.loadMore()
    }

    fun updateAttachmentCellWidth(width: Float) {
        attachmentManager.updateCellWidth(width)
    }

    suspend fun listCurrentUserNotifications(maxItems: Int = 100): NotificationsResult {
        val context = accountSession.current ?: return NotificationsResult(emptyList())
        val currentApi = context.api
        val userName = _uiState.value.session.currUser?.name
            ?: run {
                Log.e("MemosViewModel", "Cannot load notifications: current user is unavailable")
                throw IllegalStateException()
            }

        val notifications = mutableListOf<UserNotification>()
        var nextPageToken: String? = null
        var isFirstPage = true

        try {
            while (true) {
                val remaining = (maxItems - notifications.size).coerceAtMost(50)
                if (remaining <= 0) break

                val response = currentApi.listUserNotifications(
                    user = userName,
                    pageSize = remaining,
                    pageToken = nextPageToken
                )
                if (!accountSession.isCurrent(context)) throw CancellationException("Account switched")
                val pageNotifications = response.notifications.orEmpty()
                notifications += pageNotifications
                nextPageToken = response.nextPageToken?.takeIf { it.isNotBlank() }

                // Persist the first successful page so the notifications screen
                // can show last-known data offline instead of an error. Only a
                // successful response is ever written - a failed fetch never
                // overwrites a good snapshot.
                if (isFirstPage) {
                    isFirstPage = false
                    context.account.id.let { accountId ->
                        runCatching {
                            notificationCacheStore.save(
                                accountId,
                                NotificationsSnapshotData(
                                    notifications = pageNotifications,
                                    savedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                if (pageNotifications.isEmpty() || nextPageToken == null || notifications.size >= maxItems) {
                    break
                }
            }

            if (!accountSession.isCurrent(context)) throw CancellationException("Account switched")
            return NotificationsResult(notifications)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!accountSession.isCurrent(context)) throw CancellationException("Account switched")
            // Offline/failure: serve the last successful first page; the UI
            // badges it as cached instead of showing the error view.
            val accountId = context.account.id
            val snapshot = runCatching { notificationCacheStore.get(accountId) }.getOrNull()
            if (!accountSession.isCurrent(context)) throw CancellationException("Account switched")
            if (snapshot != null && snapshot.notifications.isNotEmpty()) {
                return NotificationsResult(
                    notifications = snapshot.notifications,
                    savedAt = snapshot.savedAt,
                    fromCache = true
                )
            }
            throw e
        }
    }

    /**
     * Replace an item in [manager] and re-sort it when the fields its order depends on changed
     * (pinned, displayTime). An in-place replace alone keeps the old position until the next
     * server refresh, which is what made a pinned memo stay where it was.
     */
    private fun <M : org.example.moxia.viewmodel.manager.BaseListManager<Memo>> replaceAndKeepOrder(
        manager: M,
        updated: Memo
    ) {
        val isSame = { m: Memo -> m.name == updated.name }
        val previous = manager.listState.value.items.firstOrNull(isSame)
        manager.replace(updated, isSame)
        if (previous != null &&
            (previous.pinned != updated.pinned || previous.displayTime != updated.displayTime)
        ) {
            manager.resort()
        }
    }

    private fun updateMemoInState(updatedMemo: Memo) {
        memoMapManager.upsert(updatedMemo)
        replaceAndKeepOrder(userMemoManager, updatedMemo)
        replaceAndKeepOrder(exploreMemoManager, updatedMemo)
        replaceAndKeepOrder(archivedMemoManager, updatedMemo)
        searchMemoManager.replace(updatedMemo) { m -> m.name == updatedMemo.name }
        commentManager.replace(updatedMemo) { m -> m.name == updatedMemo.name }

        if (_uiState.value.detailPane.selectedMemo?.name == updatedMemo.name) {
            _uiState.update {
                it.copy(detailPane = it.detailPane.copy(selectedMemo = updatedMemo))
            }
        }
    }

    /**
     * A locally created memo lives in the list under a temporary "offline-" name until the outbox
     * lands its create. If the queue no longer holds that op the create is either done or gone, so
     * the temporary card must not stay behind as a duplicate next to the real memo.
     */
    private fun pruneOrphanedLocalMemos(context: AccountContext, ops: List<PendingOp>) {
        val pending = ops.mapNotNullTo(HashSet()) { it.memoName }
        // A card written a moment ago may not have had its op observed yet, so only clean up
        // entries that have had time to be acknowledged.
        val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_MS
        val cards = _uiState.value.userMemoList.list.items
            .filter { it.name?.startsWith(OfflineMemoPrefix) == true }
        if (cards.isEmpty()) return
        val stale = cards.mapNotNull { memo ->
            val created = memo.createTime?.toEpochMilliseconds() ?: 0L
            memo.name?.takeIf { it !in pending && created < cutoff }
        }
        if (stale.isEmpty()) {
            // A card whose op is already gone may simply be younger than the grace period. Check
            // again once it has aged, otherwise it would sit at "待同步" until the next event.
            if (cards.any { it.name !in pending }) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(ORPHAN_GRACE_MS)
                    pruneOrphanedLocalMemos(context, syncManager.pendingOps.value)
                }
            }
            return
        }
        viewModelScope.launch {
            stale.forEach { name ->
                memoCacheRepository.removeCachedMemo(context.account.id, name)
                memoListUpdater.removeMemoFromLists(name)
                // The create was acknowledged, so the memo lives under its server name, which the
                // replay derived from the same id: swap the card without a network round trip.
                val serverName = "memos/" + name.removePrefix(OfflineMemoPrefix)
                memoCacheRepository.getCachedMemo(context.account.id, serverName)?.let { memo ->
                    memoListUpdater.insertMemoIntoUserList(memo)
                }
            }
        }
    }

    private companion object {
        /**
         * How long a temporary card may exist before its missing op counts as abandoned. It only
         * has to cover the few milliseconds between inserting the card and queueing its op.
         */
        const val ORPHAN_GRACE_MS = 3_000L
    }

    fun updateAttachmentAspectRatio(accountId: String?, generation: Long, key: String, ratio: Float) {
        val account = accountSession.current ?: return
        if (account.account.id != accountId || _uiState.value.accountGeneration != generation || !ratio.isFinite() || ratio <= 0f) return
        // Atomic CAS keeps simultaneous thumbnail loads from dropping each other's dimensions.
        _attachmentAspectRatios.update { current ->
            if (!accountSession.isCurrent(account) || current[key] == ratio) current
            else current + (key to ratio)
        }
    }
    override fun onCleared() {
        memoMapManager.reset()
        reachabilityMonitor.stop()
        memoCacheReconciler.cancel()
        unregisterBackupHook()
        super.onCleared()
    }

}

/** Which list a memo has to move between after its state changed. */
internal enum class MemoStateMove { TO_ARCHIVED, TO_USER }

/**
 * The list move implied by a state change, or null when nothing moves.
 *
 * Kept out of the view model so the mapping is unit-testable: an earlier version compared
 * `memo.state ?: "NORMAL"` (which widens to [Any]) against the string "ARCHIVED", so the
 * comparison was never true and archiving left the memo on the timeline until a full reload.
 */
internal fun memoStateMove(oldState: MemoState?, newState: MemoState?): MemoStateMove? {
    val old = oldState ?: MemoState.NORMAL
    val new = newState ?: MemoState.NORMAL
    if (old == new) return null
    return when (new) {
        MemoState.ARCHIVED -> MemoStateMove.TO_ARCHIVED
        MemoState.NORMAL -> MemoStateMove.TO_USER
        MemoState.STATE_UNSPECIFIED -> null
    }
}
