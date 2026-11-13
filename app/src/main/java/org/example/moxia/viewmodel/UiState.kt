package org.example.moxia.viewmodel

import org.example.moxia.data.media.AttachmentCacheManager
import org.example.moxia.data.media.AttachmentUpload
import org.example.moxia.data.sync.ConflictItem
import org.example.moxia.data.sync.PendingOp
import org.example.moxia.data.sync.PreDownloadState
import org.example.moxia.model.Account
import org.example.moxia.model.Activity
import org.example.moxia.model.Attachment
import org.example.moxia.model.InstanceProfile
import org.example.moxia.model.InstanceSetting
import org.example.moxia.model.Memo
import org.example.moxia.model.Shortcut
import org.example.moxia.model.User
import org.example.moxia.model.UserGeneralSetting
import org.example.moxia.model.UserNotification
import org.example.moxia.model.UserStats
import org.example.moxia.model.UserWebhook
import org.example.moxia.model.filter.MemoFilter

// --- Paginated List State (Generic) ---

data class PaginatedListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val nextPageToken: String? = null,
    val isOffline: Boolean = false,  // True when displaying cached data due to network failure
    val errorMessage: String? = null, // Error message from last failed fetch (shown with cached data)
    val showingCached: Boolean = false, // True while the list may contain local-cache items that have not been fully confirmed by the network yet (local-first prefill)
    // True once the first network page has been applied (or failed). An empty feed shows skeleton
    // cards until then, instead of flashing the empty state before the request comes back.
    val initialLoadFinished: Boolean = false
)

// --- Session State (Auth & User) ---

data class SessionState(
    val token: String = "",
    val hostUrl: String = "",
    val currUser: User? = null,
    val userStats: UserStats? = null,
    val userSettings: UserGeneralSetting? = null,
    val webhooks: List<UserWebhook> = emptyList(),
    val instanceProfile: InstanceProfile? = null,
    val instanceSettings: InstanceSetting? = null,
    val activities: List<Activity> = emptyList()
)

// --- Memo List State ---

data class MemoListState(
    val list: PaginatedListState<Memo> = PaginatedListState(),
    val shortcuts: List<Shortcut> = emptyList(),
    /** Applied feed filter; compiled to CEL for the request and evaluated locally for the cache. */
    val filter: MemoFilter = MemoFilter()
)

// --- Attachment List State ---

data class AttachmentListState(
    val list: PaginatedListState<Attachment> = PaginatedListState(),
    val cellWidth: Float = 240f,
    val aspectRatios: Map<String, Float> = emptyMap()
)

// --- Draft State ---

data class DraftState(
    val drafts: List<org.example.moxia.model.Draft> = emptyList(),
    val isDraftLoaded: Boolean = false,
    val composerResetToken: Int = 0,
    val currentEditingDraftId: String? = null // Track which draft is being edited
)

// --- Detail Pane State ---

data class DetailPaneState(
    val selectedMemo: Memo? = null,
    val comments: PaginatedListState<Memo> = PaginatedListState(),
    // isLoadingComments is now part of comments.isLoading
)

// --- App Settings (local) ---

data class AppSettings(
    val linkPreviewEnabled: Boolean = true,
    val pageSize: Int = 10,
    val headerScale: Float = 1.0f,
    // --- Markdown rendering ("editor") settings; see docs/markdown-rendering.md ---
    val codeLineNumbers: Boolean = false,
    val codeWrapLines: Boolean = false,
    val paragraphIndent: Boolean = false,
    val cjkSpacing: Boolean = false,
    // --- Offline / pre-download settings ---
    val preDownloadText: Boolean = true,
    val preDownloadAttachments: Boolean = true,
    val preDownloadWifiOnly: Boolean = true,
    val preDownloadExplore: Boolean = false,
    val attachmentCacheMaxMb: Int = 250,
    val textCacheMaxMb: Int = 100,
    val themeCacheMaxMb: Int = 200
)

enum class ConnectionState {
    CHECKING,
    OFFLINE,
    SERVER_UNREACHABLE,
    AUTH_REQUIRED,
    RATE_LIMITED,
    ONLINE
}

// --- Main UI State ---

data class MemosUiState(
    val memoMap: MemoMapState = MemoMapState(),
    val space: org.example.moxia.model.SpaceState = org.example.moxia.model.SpaceState(),
    val session: SessionState = SessionState(),
    val userMemoList: MemoListState = MemoListState(),
    val exploreMemoList: MemoListState = MemoListState(),
    val archivedMemoList: MemoListState = MemoListState(),
    val searchMemoList: MemoListState = MemoListState(),
    val attachmentList: AttachmentListState = AttachmentListState(),
    val draft: DraftState = DraftState(),
    val detailPane: DetailPaneState = DetailPaneState(),
    val appSettings: AppSettings = AppSettings(),

    val accounts: List<Account> = emptyList(),
    val accountGeneration: Long = 0L,
    val users: Map<String, User> = emptyMap(),

    val isPosting: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshTrigger: Long = 0L,
    val refreshSource: RefreshSource = RefreshSource.Manual,
    val error: UiMessage? = null,

    // --- Offline / sync state ---
    val isOnline: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.CHECKING,
    val pendingOps: List<PendingOp> = emptyList(),
    val pendingOpsCount: Int = 0,
    /** Attachment uploads a replay pass gave up on; the sync panel lists them for discarding. */
    val failedUploads: List<AttachmentUpload> = emptyList(),
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val preDownloadState: PreDownloadState = PreDownloadState.Idle,
    val conflict: ConflictItem? = null,
    val attachmentCacheUsage: AttachmentCacheManager.Usage = AttachmentCacheManager.Usage(),
    val textCacheCount: Int = 0,
    val syncError: String? = null
)

data class MemoMapState(
    val capability: org.example.moxia.model.MapCapability = org.example.moxia.model.MapCapability.UNKNOWN,
    val scope: org.example.moxia.model.MapScope = org.example.moxia.model.MapScope.MEMOS,
    val memos: List<Memo> = emptyList(),
    val isLoading: Boolean = false,
    val complete: Boolean = false,
    val isOffline: Boolean = false,
    val loadFailed: Boolean = false,
    val savedView: Shortcut? = null,
    val filterUnavailable: Boolean = false
)

enum class RefreshSource {
    Manual, // Pull-to-refresh or explicit user action
    USerMemos, ExploreMemos, ArchivedMemos, SearchMemos, Attachments
}

/**
 * Result of listing the current user's notifications: either live server
 * pages, or the offline snapshot of the last successful first page
 * ([fromCache] true, [savedAt] = snapshot write time in epoch millis).
 */
data class NotificationsResult(
    val notifications: List<UserNotification>,
    val savedAt: Long = 0L,
    val fromCache: Boolean = false
)

// --- Error Response ---

data class MemosErrorResponse(
    val code: Int? = null, val message: String? = null
)
