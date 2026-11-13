package org.example.moxia.viewmodel.delegates

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import org.example.moxia.viewmodel.AccountContext
import org.example.moxia.viewmodel.AccountSession
import org.example.moxia.data.DraftManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.moxia.R
import org.example.moxia.MemosApplication
import org.example.moxia.api.GsonProvider
import org.example.moxia.api.MemosApi
import org.example.moxia.data.cache.CacheListType
import org.example.moxia.data.cache.MemoCacheRepository
import org.example.moxia.data.sync.PendingOp
import org.example.moxia.data.sync.PendingOpType
import org.example.moxia.data.sync.ReactionOpPayload
import org.example.moxia.data.sync.SyncManager
import org.example.moxia.model.Attachment
import org.example.moxia.model.Location
import org.example.moxia.model.isLinkTo
import org.example.moxia.model.Memo
import org.example.moxia.model.isLinkTo
import org.example.moxia.model.MemoRelation
import org.example.moxia.model.MemoRelationType
import org.example.moxia.model.OfflineMemoPrefix
import org.example.moxia.model.isLinkTo
import org.example.moxia.model.MemoRelationWire
import org.example.moxia.model.isLinkTo
import org.example.moxia.model.MemoSnippet
import org.example.moxia.model.isLinkTo
import org.example.moxia.model.MemoState
import org.example.moxia.model.Reaction
import org.example.moxia.model.SetMemoRelationsRequest
import org.example.moxia.model.UpsertMemoReactionRequest
import org.example.moxia.model.User
import org.example.moxia.model.Visibility
import org.example.moxia.viewmodel.MemosUiState
import org.example.moxia.viewmodel.UiMessage
import org.example.moxia.viewmodel.manager.AttachmentManager
import org.example.moxia.viewmodel.manager.CommentListManager
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.UUID

private fun toastOfflineSaved() {
    Toast.makeText(
        MemosApplication.instance,
        MemosApplication.instance.getString(R.string.offline_saved_message),
        Toast.LENGTH_SHORT
    ).show()
}


interface MemoActionDelegate {
    fun selectMemo(memo: Memo?)
    fun clearSelectedMemo()
    fun createMemo(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>? = null,
        location: Location? = null,
        memoId: String? = null,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
        /** Memos the new memo links to (the composer's "link memo" action). */
        linkedMemoNames: List<String> = emptyList(),
        /** The space the memo is placed in (`spaces/{uid}`); null leaves it unassigned. */
        space: String? = null
    )

    fun updateMemo(
        memo: Memo,
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location? = null,
        state: MemoState? = null,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {},
        /** Outgoing links to store; null leaves the server's relation list untouched. */
        linkedMemoNames: List<String>? = null,
        /** The space the memo is placed in; defaults to the memo's current placement. */
        space: String? = memo.space
    )

    fun deleteMemo(memo: Memo, onSuccess: () -> Unit = {})
    fun updateMemoPinned(memo: Memo, pinned: Boolean, onSuccess: () -> Unit = {})

    /**
     * Places [memo] in [space], or takes it out of its space when [space] is null. The visibility
     * follows the placement: a memo in a space is a SPACE memo, and nothing else can be.
     */
    fun moveMemoToSpace(
        memo: Memo,
        space: String?,
        onError: () -> Unit = {},
        onSuccess: () -> Unit = {}
    )

    /** Drops one outgoing "link memo" relation, leaving the memo's other relations alone. */
    fun removeMemoLink(memo: Memo, targetName: String)
    fun createComment(parentMemo: Memo, content: String, onError: () -> Unit = {}, onSuccess: () -> Unit = {})
    suspend fun uploadAttachment(uri: Uri, context: Context): Attachment?

    /** Queue a pending attachment upload and return its placeholder without waiting for the bytes. */
    suspend fun queueAttachment(uri: Uri, context: Context): Attachment?

    /** Discard a queued offline upload once nothing references its placeholder anymore. */
    fun discardQueuedUploadIfOrphaned(clientId: String)
    fun upsertMemoReaction(memo: Memo, reactionType: String)
    fun deleteMemoReaction(memo: Memo, reaction: Reaction)
}

interface MemoListUpdater {
    fun updateMemoInLists(memo: Memo)
    fun removeMemoFromLists(memoName: String)
    fun refreshUserMemos()
    fun handleMemoStateChange(memo: Memo, updated: Memo)
    fun insertMemoIntoUserList(memo: Memo)
}

class MemoActionDelegateImpl(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MemosUiState>,
    private val listUpdater: MemoListUpdater,
    private val draftDelegate: DraftDelegate,
    private val attachmentManagerProvider: () -> AttachmentManager?,
    private val commentManagerProvider: () -> CommentListManager?,
    private val syncManager: SyncManager,
    private val memoCacheRepository: MemoCacheRepository,
    private val accountSession: AccountSession,
    private val draftManager: DraftManager,
    private val isOnlineProvider: () -> Boolean,
    private val notifyOfflineSaved: () -> Unit = ::toastOfflineSaved
) : MemoActionDelegate {


    private val attachmentManager: AttachmentManager? get() = attachmentManagerProvider()
    private val commentManager: CommentListManager? get() = commentManagerProvider()
    private val gson = GsonProvider.gson


    /** True when the failure is connectivity-related and safe to queue offline. */
    private fun shouldQueueOffline(context: AccountContext, e: Exception): Boolean =
        e is IOException || (accountSession.isCurrent(context) && !isOnlineProvider())

    /**
     * A space placement and SPACE visibility go together: the server rejects either half
     * alone, so the write is stopped before the round trip.
     */
    private fun rejectsSpaceVisibility(context: AccountContext, visibility: Visibility, space: String?): Boolean {
        if (visibility != Visibility.SPACE || space != null) return false
        Log.e("MemosViewModel", "SPACE visibility requires a space")
        accountSession.update(uiState, context) {
            it.copy(error = UiMessage(R.string.memo_space_visibility_requires_space))
        }
        return true
    }

    /** Keep the upstream UI error localized while retaining a diagnostic log. */
    private fun reportOperationFailure(context: AccountContext, e: Exception) {
        Log.e("MemosViewModel", "Operation failed", e)
        accountSession.update(uiState, context) { it.copy(error = UiMessage(R.string.common_operation_failed)) }
    }

    /**
     * Queue [op] for durable replay, apply the optimistic local change, report
     * success and tell the user the change was saved offline. Shared by the
     * known-offline fast path and the online-attempt fallback of every write
     * so the two copies cannot drift apart.
     */
    private suspend fun applyOffline(
        context: AccountContext,
        op: PendingOp,
        onSuccess: () -> Unit = {},
        applyOptimistic: suspend () -> Unit
    ) {
        syncManager.enqueue(op)
        applyOptimistic()
        if (accountSession.isCurrent(context)) {
            onSuccess()
            notifyOfflineSaved()
        }
    }

    private fun currentUser(): User? = uiState.value.session.currUser

    /**
     * "Link memo": records the relations of a freshly created memo.
     *
     * Best effort by design — the memo exists at this point, so a rejected relation must not turn
     * the submission into a failure. The detail page only shows links the server accepted.
     */
    private suspend fun linkMemos(api: MemosApi, memoName: String?, targets: List<String>) {
        val name = memoName ?: return
        if (targets.isEmpty()) return
        writeLinkedMemos(api, name, emptyList(), targets)
    }

    /**
     * Rewrites the outgoing links of a memo.
     *
     * The API replaces the whole relation list, so every other relation is carried over verbatim —
     * comment relations are owned by the comments, and dropping them would silently detach them.
     */
    private suspend fun writeLinkedMemos(
        api: MemosApi,
        memoName: String,
        currentRelations: List<MemoRelation>,
        targets: List<String>
    ) {
        val currentOutgoing = currentRelations
            .filter { it.isLinkTo(memoName) }
            .map { it.relatedMemo.name }
        val desired = targets.filter { it.isNotBlank() && it != memoName }.distinct()
        // Skip only when nothing changed. An empty result is a real instruction — it is what
        // unlinking the last memo means — so it must still reach the server.
        if (currentOutgoing.sorted() == desired.sorted()) return
        // Everything that is not an outgoing link survives verbatim: comment relations included.
        val others = currentRelations
            .filterNot { it.isLinkTo(memoName) }
            .map(MemoRelationWire::from)
        val links = desired.map { target ->
            MemoRelationWire.reference(
                memo = MemoSnippet(name = memoName),
                related = MemoSnippet(name = target)
            )
        }
        try {
            api.setMemoRelations(
                memoName,
                SetMemoRelationsRequest(name = memoName, relations = others + links)
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("MemosViewModel", "Could not update memo links", e)
        }
    }

    override fun removeMemoLink(memo: Memo, targetName: String) {
        val context = accountSession.current ?: return
        val name = memo.name ?: return
        val api = context.connection.api
        // Everything except the link being removed, in the order the server returned it.
        val remaining = memo.relations.orEmpty()
            .filter { it.isLinkTo(name) && it.relatedMemo.name != targetName }
            .map { it.relatedMemo.name }
        scope.launch {
            writeLinkedMemos(api, name, memo.relations.orEmpty(), remaining)
            if (accountSession.isCurrent(context)) listUpdater.refreshUserMemos()
        }
    }

    override fun selectMemo(memo: Memo?) {
        val context = accountSession.current ?: return
        accountSession.update(uiState, context) {
            it.copy(detailPane = it.detailPane.copy(selectedMemo = memo))
        }
        if (memo != null) {
            commentManager?.setMemo(memo.name ?: "")
        }
    }

    override fun clearSelectedMemo() = selectMemo(null)

    override fun createMemo(
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>?,
        location: Location?,
        memoId: String?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
        linkedMemoNames: List<String>,
        space: String?
    ) {
        val context = accountSession.current ?: return
        if (rejectsSpaceVisibility(context, visibility, space)) {
            onError()
            return
        }
        val connection = context.connection
        val wasOnline = connection.networkReady && isOnlineProvider()
        val draftIdToDelete = uiState.value.draft.currentEditingDraftId
        val accountId = context.account.id
        // One idempotency key per user create action: the local card, the queued op and every
        // replay share it, so a retry after a lost response cannot create the memo twice.
        val clientId = memoId ?: UUID.randomUUID().toString()
        var memo = Memo(
            content = content,
            visibility = visibility,
            attachments = attachments,
            location = location,
            space = space
        )
        scope.launch {
            runCreate(context, memo, clientId, draftIdToDelete, onError, onSuccess, linkedMemoNames, wasOnline)
        }
    }

    private suspend fun runCreate(
        context: AccountContext,
        memo: Memo,
        clientId: String,
        draftIdToDelete: String?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
        linkedMemoNames: List<String>,
        wasOnline: Boolean
    ) {
        var memo = memo
        val accountId = context.account.id
        kotlin.run {
            try {
                val prepared = attachmentManager?.prepareRestoredDraftAttachments(context, memo.attachments.orEmpty()) ?: memo.attachments.orEmpty()
                memo = memo.copy(attachments = prepared.ifEmpty { null })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                reportOperationFailure(context, error)
                if (accountSession.isCurrent(context)) onError()
                return
            }

            // Local first, online or not: the card exists before the network is touched and the
            // outbox owns the request from here. The composer closes immediately; the memo takes
            // its server identity when the create lands.
            val tempName = OfflineMemoPrefix + clientId
            // Stamp local timestamps so the optimistic memo sorts to the top of the list
            // (null displayTime would sort to the bottom).
            val now = Clock.System.now()
            val localMemo = memo.copy(
                name = tempName,
                state = MemoState.NORMAL,
                createTime = now,
                updateTime = now,
                displayTime = now,
                // Links ride along inside the payload: a relation needs the server name, so the
                // replay writes them once the create has returned one.
                relations = linkedMemoNames
                    .filter { it.isNotBlank() && it != tempName }
                    .distinct()
                    .map { target ->
                        MemoRelation(
                            memo = MemoSnippet(name = tempName),
                            relatedMemo = MemoSnippet(name = target),
                            type = MemoRelationType.REPLY
                        )
                    }
                    .ifEmpty { null }
            )
            // The card and its op must land together. A list refresh that replaces the cache
            // between the two writes would drop a card the outbox still owns (the protection
            // subqueries only know names that are already queued), and queueing first would let
            // a replay insert the server copy before the temporary card exists. One transaction
            // covers both directions; the UI catches up right after it commits.
            syncManager.enqueue(
                PendingOp.new(
                    accountId = accountId,
                    type = PendingOpType.CREATE,
                    memoName = tempName,
                    payloadJson = gson.toJson(localMemo),
                    id = clientId
                ),
                writeLocal = {
                    memoCacheRepository.upsertCachedMemo(
                        accountId, localMemo, CacheListType.USER, order = 0
                    )
                }
            )
            applyLocalCreateUi(context, localMemo, draftIdToDelete)
            if (accountSession.isCurrent(context)) onSuccess()
            if (!wasOnline) {
                // The user asked for an offline write: say so instead of pretending it synced.
                notifyOfflineSaved()
            } else {
                // Push now instead of waiting for the next outbox tick. A failure stays queued
                // and is retried exactly like any other offline write.
                syncManager.pushPendingChanges()
            }
        }
    }

    /**
     * UI half of a local create. The cache row is already committed by the caller, together with
     * the outbox op, so everything here is either in-memory list state or draft bookkeeping.
     */
    private suspend fun applyLocalCreateUi(context: AccountContext, localMemo: Memo, draftIdToDelete: String?) {
        if (accountSession.isCurrent(context)) listUpdater.insertMemoIntoUserList(localMemo)
        draftIdToDelete?.let { draftManager.deleteDraft(context.account.id, it) }
        if (accountSession.isCurrent(context) && uiState.value.draft.currentEditingDraftId == draftIdToDelete) {
            draftDelegate.setCurrentEditingDraft(null)
        }
        accountSession.update(uiState, context) {
            it.copy(
                draft = it.draft.copy(
                    composerResetToken = System.currentTimeMillis().toInt()
                )
            )
        }
    }

    override fun updateMemo(
        memo: Memo,
        content: String,
        visibility: Visibility,
        attachments: List<Attachment>,
        location: Location?,
        state: MemoState?,
        onError: () -> Unit,
        onSuccess: () -> Unit,
        linkedMemoNames: List<String>?,
        space: String?
    ) {
        val context = accountSession.current ?: return
        if (rejectsSpaceVisibility(context, visibility, space)) {
            onError()
            return
        }
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val name = memo.name ?: return
        val update = memo.copy(
            content = content,
            visibility = visibility,
            attachments = attachments,
            location = location,
            state = state,
            space = space
        )
        val maskParts = mutableListOf("content", "visibility", "attachments", "location")
        if (state != null) {
            maskParts.add("state")
        }
        // The space is only touched when the placement really changes: mentioning it for an
        // unchanged memo would clear the placement of every inline edit.
        if (memo.space != space) {
            maskParts.add("space")
        }
        val mask = maskParts.joinToString(",")
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.UPDATE,
            memoName = name,
            payloadJson = gson.toJson(update),
            updateMask = mask,
            baseUpdateTime = memo.updateTime?.toString()
        )

        scope.launch {
            if (!wasOnline) {
                applyOffline(context, pendingOp, onSuccess) {
                    applyLocalUpdate(context, memo, update)
                }
                return@launch
            }

            // Optimistic: the edit (or the archive move) lands in the lists now, not after the
            // round trip. The server copy replaces it below.
            if (accountSession.isCurrent(context)) applyLocalUpdate(context, memo, update)

            try {
                val updated = api.updateMemo(name, update, mask)
                if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(updated)
                cacheLocalMemo(context, updated)
                // Only when the caller manages the links (the composer): other update paths — pin,
                // inline text edit — must not touch the relation list.
                if (linkedMemoNames != null) {
                    writeLinkedMemos(api, name, memo.relations.orEmpty(), linkedMemoNames)
                    // The relation list is a separate resource: re-read the memo so the detail
                    // page stops showing a link that was just removed.
                    if (accountSession.isCurrent(context)) listUpdater.refreshUserMemos()
                }
                if (accountSession.isCurrent(context)) onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp, onSuccess) {
                        applyLocalUpdate(context, memo, update)
                    }
                } else {
                    // Roll the optimistic edit back so the lists match the server again.
                    if (accountSession.isCurrent(context)) applyLocalUpdate(context, update, memo)
                    reportOperationFailure(context, e)
                    if (accountSession.isCurrent(context)) onError()
                }
            }
        }
    }

    private suspend fun applyLocalUpdate(context: AccountContext, oldMemo: Memo, updated: Memo) {
        val oldState = oldMemo.state ?: MemoState.NORMAL
        val newState = updated.state ?: MemoState.NORMAL
        if (oldState != newState) {
            if (accountSession.isCurrent(context)) listUpdater.handleMemoStateChange(oldMemo, updated)
        } else {
            if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(updated)
        }
        cacheLocalMemo(context, updated)
    }

    private suspend fun cacheLocalMemo(context: AccountContext, memo: Memo) {
        val accountId = context.account.id
        val name = memo.name ?: return
        // A comment is a memo with a parent and belongs to that parent's comment list. Caching it
        // as a user memo made an edited comment show up in the feed as a brand-new memo (and, with
        // its comment relation on board, as one carrying a link) until the next refresh dropped it.
        val parent = memo.parent
        val listType = when {
            parent != null -> CacheListType.COMMENT
            memo.state == MemoState.ARCHIVED -> CacheListType.ARCHIVED
            else -> CacheListType.USER
        }
        // Atomic cross-list-type upsert (single transaction) - the previous
        // remove+upsert pair could race a concurrent writer and lose the row.
        memoCacheRepository.upsertCachedMemoState(
            accountId,
            memo,
            listType,
            parentName = parent
        )
    }

    override fun deleteMemo(memo: Memo, onSuccess: () -> Unit) {
        val context = accountSession.current ?: return
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val name = memo.name ?: return
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.DELETE,
            memoName = name
        )
        scope.launch {
            if (!wasOnline) {
                applyOffline(context, pendingOp, onSuccess) {
                    memoCacheRepository.removeCachedMemo(accountId, name)
                    if (accountSession.isCurrent(context)) listUpdater.removeMemoFromLists(name)
                }
                return@launch
            }

            // Optimistic: the card is gone now; the request (or the outbox) is bookkeeping.
            memoCacheRepository.removeCachedMemo(accountId, name)
            if (accountSession.isCurrent(context)) listUpdater.removeMemoFromLists(name)

            try {
                api.deleteMemo(name)
                if (accountSession.isCurrent(context)) onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp, onSuccess) {}
                } else {
                    // The server refused it: bring the memo back with a fresh read.
                    if (accountSession.isCurrent(context)) listUpdater.refreshUserMemos()
                    reportOperationFailure(context, e)
                }
            }
        }
    }

    override fun updateMemoPinned(memo: Memo, pinned: Boolean, onSuccess: () -> Unit) {
        val context = accountSession.current ?: return
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val name = memo.name ?: return
        val update = memo.copy(pinned = pinned)
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.UPDATE,
            memoName = name,
            payloadJson = gson.toJson(update),
            updateMask = "pinned",
            baseUpdateTime = memo.updateTime?.toString()
        )
        scope.launch {
            if (!wasOnline) {
                applyOffline(context, pendingOp, onSuccess) {
                    if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(update)
                    cacheLocalMemo(context, update)
                }
                return@launch
            }

            // Optimistic: the pin switch flips immediately; the server catches up in the background.
            if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(update)
            cacheLocalMemo(context, update)

            try {
                val updated = api.updateMemo(name, update, "pinned")
                if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(updated)
                cacheLocalMemo(context, updated)
                if (accountSession.isCurrent(context)) onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp, onSuccess) {}
                } else {
                    // Put the switch back where it was.
                    if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(memo)
                    cacheLocalMemo(context, memo)
                    reportOperationFailure(context, e)
                }
            }
        }
    }

    override fun moveMemoToSpace(memo: Memo, space: String?, onError: () -> Unit, onSuccess: () -> Unit) {
        if (memo.space == space) return
        // A placement is only valid on a SPACE memo and a SPACE memo is only valid inside a space,
        // so the visibility follows the placement instead of being picked separately.
        val visibility = when {
            space != null -> Visibility.SPACE
            memo.visibility == Visibility.SPACE -> Visibility.PRIVATE
            else -> memo.visibility
        }
        updateMemo(
            memo = memo,
            content = memo.content,
            visibility = visibility,
            attachments = memo.attachments.orEmpty(),
            location = memo.location,
            onError = onError,
            onSuccess = onSuccess,
            space = space
        )
    }

    override fun createComment(parentMemo: Memo, content: String, onError: () -> Unit, onSuccess: () -> Unit) {
        val context = accountSession.current ?: return
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val parentName = parentMemo.name ?: return
        val comment = Memo(content = content, visibility = parentMemo.visibility, parent = parentName)
        val tempName = "offline-comment-${UUID.randomUUID()}"
        val localComment = comment.copy(name = tempName, parent = parentName)
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.COMMENT_CREATE,
            memoName = tempName,
            parentName = parentName,
            payloadJson = gson.toJson(localComment)
        )
        scope.launch {
            if (!wasOnline) {
                applyOffline(context, pendingOp, onSuccess) {
                    applyLocalComment(context, localComment, parentName)
                }
                return@launch
            }

            try {
                val created = api.createMemoComment(parentName, comment)
                memoCacheRepository.upsertCachedMemo(accountId, created, CacheListType.COMMENT, parentName = parentName)
                if (accountSession.isCurrent(context)) onSuccess()
                if (accountSession.isCurrent(context)) commentManager?.fetch(refresh = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp, onSuccess) {
                        applyLocalComment(context, localComment, parentName)
                    }
                } else {
                    reportOperationFailure(context, e)
                    if (accountSession.isCurrent(context)) onError()
                }
            }
        }
    }

    /**
     * Show the optimistic comment and persist it so it survives restarts / a
     * failed sync until the server list refresh replaces it.
     */
    private suspend fun applyLocalComment(context: AccountContext, localComment: Memo, parentName: String) {
        if (accountSession.isCurrent(context)) commentManager?.upsert(
            localComment,
            { it.name == localComment.name },
            compareBy { it.createTime }
        )
        val accountId = context.account.id
        memoCacheRepository.upsertCachedMemo(
            accountId, localComment, CacheListType.COMMENT, parentName = parentName
        )
    }

    override suspend fun queueAttachment(uri: Uri, context: Context): Attachment? {
        val account = accountSession.current ?: return null
        val attachment = attachmentManager?.queueAttachment(uri, context)
        return attachment.takeIf { accountSession.isCurrent(account) }
    }

    override suspend fun uploadAttachment(uri: Uri, context: Context): Attachment? {
        val account = accountSession.current ?: return null
        val attachment = attachmentManager?.uploadAttachment(uri, context)
        return attachment.takeIf { accountSession.isCurrent(account) }
    }

    override fun discardQueuedUploadIfOrphaned(clientId: String) {
        val context = accountSession.current ?: return
        scope.launch { attachmentManager?.discardQueuedUploadIfOrphaned(clientId) }
    }

    override fun upsertMemoReaction(memo: Memo, reactionType: String) {
        val context = accountSession.current ?: return
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val name = memo.name ?: return
        val mine = currentUser()?.name
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.REACTION_UPSERT,
            memoName = name,
            payloadJson = gson.toJson(
                ReactionOpPayload(reactionType = reactionType, creator = mine)
            )
        )
        scope.launch {
            // Optimistic local update for instant feedback (online or offline).
            val optimistic = memo.copy(
                reactions = withLocalReactions(memo, reactionType, mine, add = true)
            )
            if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(optimistic)

            if (!wasOnline) {
                applyOffline(context, pendingOp) {
                    cacheLocalMemo(context, optimistic)
                }
                return@launch
            }

            try {
                val reaction = Reaction(contentId = name, reactionType = reactionType)
                val request = UpsertMemoReactionRequest(name = memo.name, reaction = reaction)
                api.upsertMemoReaction(name, request)

                // Fetch latest memo state to be sure about all reactions and update in-place
                val updated = api.getMemo(name)
                if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(updated)
                cacheLocalMemo(context, updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp) {
                        cacheLocalMemo(context, optimistic)
                    }
                } else {
                    // The server rejected the request (e.g. 401/404): roll the
                    // optimistic reaction back and surface the error, instead of
                    // leaving a reaction on screen that was never applied.
                    reportOperationFailure(context, e)
                    if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(memo)
                }
            }
        }
    }

    override fun deleteMemoReaction(memo: Memo, reaction: Reaction) {
        val context = accountSession.current ?: return
        val connection = context.connection
        val api = connection.api
        val wasOnline = connection.networkReady && isOnlineProvider()
        val accountId = context.account.id
        val name = memo.name ?: return
        val mine = currentUser()?.name
        val pendingOp = PendingOp.new(
            accountId = accountId,
            type = PendingOpType.REACTION_DELETE,
            memoName = name,
            payloadJson = gson.toJson(
                ReactionOpPayload(reactionType = reaction.reactionType, creator = mine)
            )
        )
        scope.launch {
            // Optimistic local update for instant feedback (online or offline).
            val optimistic = memo.copy(
                reactions = memo.reactions.orEmpty().filterNot {
                    it.reactionType == reaction.reactionType && (mine == null || it.creator == mine)
                }
            )
            if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(optimistic)

            if (!wasOnline) {
                applyOffline(context, pendingOp) {
                    cacheLocalMemo(context, optimistic)
                }
                return@launch
            }

            try {
                val reactionName = reaction.name ?: return@launch
                api.deleteMemoReaction(reactionName)

                // Fetch latest memo state and update in-place
                val updated = api.getMemo(name)
                if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(updated)
                cacheLocalMemo(context, updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (shouldQueueOffline(context, e)) {
                    applyOffline(context, pendingOp) {
                        cacheLocalMemo(context, optimistic)
                    }
                } else {
                    // The server rejected the request (e.g. 401/404): roll the
                    // optimistic removal back and surface the error, instead of
                    // leaving the reaction hidden while it still exists on the server.
                    reportOperationFailure(context, e)
                    if (accountSession.isCurrent(context)) listUpdater.updateMemoInLists(memo)
                }
            }
        }
    }

    /**
     * Rebuild the reaction list after (un)toggling [reactionType] for the
     * current user, mirroring the server's upsert semantics.
     */
    private fun withLocalReactions(
        memo: Memo, reactionType: String, mine: String?, add: Boolean
    ): List<Reaction> {
        val current = memo.reactions.orEmpty()
        val filtered = current.filterNot {
            it.reactionType == reactionType && (mine == null || it.creator == mine)
        }
        if (!add) return filtered
        return filtered + Reaction(
            contentId = memo.name ?: "", reactionType = reactionType, creator = mine
        )
    }
}
