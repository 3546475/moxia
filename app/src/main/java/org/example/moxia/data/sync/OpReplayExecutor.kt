package org.example.moxia.data.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import org.example.moxia.api.GsonProvider
import org.example.moxia.api.MemosApi
import org.example.moxia.data.audit.SyncAuditLogger
import org.example.moxia.data.cache.CacheListType
import org.example.moxia.data.cache.MemoCacheRepository
import org.example.moxia.data.media.AttachmentUploadQueue
import org.example.moxia.model.Attachment
import org.example.moxia.model.Memo
import org.example.moxia.model.MemoState
import org.example.moxia.model.MemoRelationType
import org.example.moxia.model.MemoRelationWire
import org.example.moxia.model.MemoSnippet
import org.example.moxia.model.SetMemoRelationsRequest
import org.example.moxia.model.Reaction
import org.example.moxia.model.UpsertMemoReactionRequest
import org.example.moxia.model.User
import org.example.moxia.model.Visibility
import retrofit2.HttpException

private const val TAG = "OpReplayExecutor"

/**
 * Shared per-op replay logic used by both the in-process [SyncManager] and the
 * background [OutboxSyncWorker], so the two replay paths can never diverge.
 *
 * UI-only effects (conflict surfacing, comment list refresh, memo callbacks)
 * are injected callbacks; the worker leaves them as no-ops. Payloads are
 * parsed with [GsonProvider.gson] because they are serialized with it and may
 * contain custom types (Instant, Visibility).
 */
class OpReplayExecutor(
    private val api: MemosApi,
    private val repository: SyncRepository,
    private val memoCacheRepository: MemoCacheRepository,
    private val auditLogger: SyncAuditLogger,
    private val accountId: String,
    private val currentUser: User? = null,
    private val attachmentUploadQueueProvider: () -> AttachmentUploadQueue? = { null },
    private val onMemoSynced: suspend (memo: Memo, tempName: String?) -> Unit = { _, _ -> },
    private val onMemoDeleted: suspend (memoName: String) -> Unit = {},
    private val onCommentsRefresh: suspend () -> Unit = {},
    private val onConflict: (ConflictItem) -> Unit = {}
) {

    private val gson = GsonProvider.gson

    /**
     * Replay a single pending op. Returns true when the op was applied (or is
     * a no-op) and can be deleted from the queue; false when it must stay
     * queued (conflict pending, attachments still uploading).
     */
    suspend fun replay(op: PendingOp): Boolean {
        require(op.accountId == accountId) { "Operation belongs to another account" }
        return when (PendingOpType.valueOf(op.type)) {
        PendingOpType.CREATE -> syncCreate(op)
        PendingOpType.UPDATE -> syncUpdate(op)
        PendingOpType.DELETE -> syncDelete(op)
        PendingOpType.COMMENT_CREATE -> syncCommentCreate(op)
        PendingOpType.REACTION_UPSERT -> syncReactionUpsert(op)
        PendingOpType.REACTION_DELETE -> syncReactionDelete(op)
        PendingOpType.ATTACHMENT_DELETE -> syncAttachmentDelete(op)
    }

    }

    private suspend fun syncCreate(op: PendingOp): Boolean {
        val local = gson.fromJson(op.payloadJson, Memo::class.java) ?: return true
        val tempName = op.memoName
        val resolved = resolvePlaceholderAttachments(local, op) ?: return false
        // Pass the op id as the server-side idempotency key: a replayed create
        // (retry after a lost response, or WorkManager replay after process
        // death) is then deduplicated by the server instead of creating a
        // duplicate memo. The API layer drops it on server versions that
        // reject memoId (v0.26/v0.27), keeping the non-idempotent path there.
        val created = try {
            api.createMemo(resolved, memoId = op.id)
        } catch (e: HttpException) {
            // A previous attempt created the memo but its response was lost: the retry is answered
            // with 409 because memos/<memoId> already exists. Resolve that memo and finish the op
            // instead of retrying the conflict until the queue gives up.
            if (e.code() != 409) throw e
            api.getMemo("memos/${op.id}")
        }
        val serverName = created.name ?: return false
        if (tempName != null && tempName != serverName) {
            repository.renameMemo(accountId, tempName, serverName)
            // Follow-up UPDATE ops were based on the just-created memo's LOCAL
            // timestamp (an offline edit of an offline create); anchor ALL of
            // them to the real server updateTime so they don't trip a false
            // conflict on the next op. Previously only ops with a null
            // baseUpdateTime were anchored, which left offline edits of
            // offline creates with a stale local base time -> spurious dialog.
            repository.getOps(accountId)
                .filter { it.memoName == serverName && it.type == PendingOpType.UPDATE.name }
                .forEach { repository.setBaseUpdateTime(it.id, created.updateTime?.toString()) }
        }
        cacheMemo(created)
        if (tempName != null && tempName != serverName) {
            memoCacheRepository.removeCachedMemo(accountId, tempName)
        }
        writeAttachedLinks(local, tempName, serverName)
        onMemoSynced(created, tempName)
        return true
    }

    /**
     * Links of a locally created memo travel inside its payload, because a relation needs the
     * server name that only exists after the create. Best effort: the memo is already saved, so a
     * rejected relation must not turn the create into a failure.
     */
    private suspend fun writeAttachedLinks(local: Memo, tempName: String?, serverName: String) {
        val targets = local.relations.orEmpty()
            .filter { it.type == MemoRelationType.REPLY && it.memo.name == tempName }
            .map { it.relatedMemo.name }
            .filter { it.isNotBlank() && it != serverName }
            .distinct()
        if (targets.isEmpty()) return
        try {
            api.setMemoRelations(
                serverName,
                SetMemoRelationsRequest(
                    name = serverName,
                    relations = targets.map { target ->
                        MemoRelationWire.reference(
                            memo = MemoSnippet(name = serverName),
                            related = MemoSnippet(name = target)
                        )
                    }
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the links of $serverName", e)
        }
    }

    private suspend fun syncUpdate(op: PendingOp): Boolean {
        val name = op.memoName ?: return true
        val local = gson.fromJson(op.payloadJson, Memo::class.java) ?: return true
        val server = api.getMemo(name)
        val serverUpdate = server.updateTime?.toString()
        if (op.baseUpdateTime == null) {
            // Nothing to compare against, so this pushes over whatever the server has. That is
            // not a conflict (there is no base version to conflict with), but it is recorded so
            // an unconditional overwrite leaves a trace instead of looking like a normal sync.
            auditLogger.record(
                accountId, "SYNC", "OVERWRITE", op.type, name, "missing_base_update_time"
            )
        }
        if (op.baseUpdateTime != null && serverUpdate != op.baseUpdateTime) {
            Log.d(TAG, "Conflict on $name: local base=${op.baseUpdateTime} vs server=$serverUpdate")
            // Record the attempt timestamp so a deferred (LATER) conflict is
            // not re-surfaced on every foreground - the backoff window gates
            // when the next conflict check may run.
            repository.markFailed(
                op.id, op.attemptCount + 1, "conflict pending",
                System.currentTimeMillis(), permanentlyFailed = false
            )
            auditLogger.record(accountId, "SYNC", "CONFLICT", op.type, name, "base_update_time")
            onConflict(ConflictItem(op.id, accountId, name, local, server))
            return false // keep op queued until the user resolves
        }
        val resolved = resolvePlaceholderAttachments(local, op) ?: return false
        val updated = api.updateMemo(name, resolved, op.updateMask ?: "content")
        // Re-anchor follow-up edits (queued while offline, all based on the same
        // baseUpdateTime) to the new server state so chained edits don't trip a
        // false conflict. A real conflict (someone else edited meanwhile) still
        // fires because the first op would have already hit a different server time.
        anchorFollowUps(name, op.baseUpdateTime, updated.updateTime)
        cacheMemo(updated)
        onMemoSynced(updated, null)
        return true
    }

    /**
     * Re-anchor queued UPDATE ops that were based on the same [baseUpdateTime]
     * to the new [serverUpdateTime] after a successful push. Without this, two
     * offline edits of the same memo would both carry the old base time and the
     * second one would incorrectly surface as a conflict.
     */
    suspend fun anchorFollowUps(memoName: String, baseUpdateTime: String?, serverUpdateTime: Any?) {
        if (baseUpdateTime == null || serverUpdateTime == null) return
        val newBase = serverUpdateTime.toString()
        repository.getOps(accountId)
            .filter { it.memoName == memoName && it.baseUpdateTime == baseUpdateTime }
            .forEach { repository.setBaseUpdateTime(it.id, newBase) }
    }

    /**
     * Replace queued-upload placeholders (no server `name`, carrying the
     * durable queue clientId) in [memo]'s attachment list with the real
     * server attachment, and strip the local-only fields so the payload sent
     * to the server never references clientIds or local paths.
     *
     * Returns null when the op must be deferred: a placeholder's upload row is
     * still in the durable queue, so the queue is kicked and the op re-marked
     * for a later retry (same semantics as before placeholders existed).
     * Placeholders whose row is gone are verified server-side: the clientId
     * was forwarded as the API attachmentId, making the resource name
     * deterministic ("attachments/<clientId>"). A confirmed-missing upload
     * (retired row, 404) drops the placeholder and is audited, so one lost
     * file never blocks the memo itself.
     */
    private suspend fun resolvePlaceholderAttachments(memo: Memo, op: PendingOp): Memo? {
        val attachments = memo.attachments?.takeIf { list ->
            list.any { it.name.isNullOrBlank() && it.clientId != null }
        } ?: return memo.stripLocalAttachmentFields()
        val queue = attachmentUploadQueueProvider()
            ?: error("Attachment upload queue is unavailable; queued attachments cannot be resolved")
        val resolved = mutableListOf<Attachment>()
        var dropped = 0
        for (attachment in memo.attachments.orEmpty()) {
            val clientId = attachment.clientId
            if (!attachment.name.isNullOrBlank() || clientId == null) {
                resolved += attachment
                continue
            }
            val queued = queue.get(clientId)
            if (queued != null && !queued.permanentlyFailed) {
                // The upload is still queued: posting now would persist a dangling
                // ref. Kick the queue and defer; the retry/backoff path replays the
                // op once the upload has landed.
                queue.schedule(accountId)
                repository.markFailed(
                    op.id, op.attemptCount + 1, "attachments pending upload",
                    System.currentTimeMillis(), permanentlyFailed = false
                )
                return null
            }
            // Row gone (uploaded, or its staged bytes were lost) or retired as permanently
            // failed: it will never bind itself, so verify server-side before dropping it.
            val uploaded = try {
                api.getAttachment("attachments/$clientId")
            } catch (e: HttpException) {
                // A permanent rejection (e.g. 404) means the file never made
                // it; transient lookup errors propagate and retry the op.
                if (e.code() in 400..499 && e.code() !in setOf(408, 429)) null else throw e
            }
            if (uploaded?.name != null) {
                resolved += uploaded
            } else {
                Log.w(TAG, "resolvePlaceholderAttachments: dropping lost upload $clientId")
                dropped++
            }
        }
        if (dropped > 0) {
            auditLogger.record(
                accountId, "SYNC", "REJECTED", op.type, op.memoName, "attachment_upload_lost"
            )
        }
        return memo.copy(attachments = resolved).stripLocalAttachmentFields()
    }

    /** Strip local-only placeholder fields so they are never sent to the server. */
    private fun Memo.stripLocalAttachmentFields(): Memo {
        val attachments = attachments ?: return this
        if (attachments.none { it.clientId != null || it.localPath != null }) return this
        return copy(attachments = attachments.map { it.copy(clientId = null, localPath = null) })
    }

    private suspend fun syncDelete(op: PendingOp): Boolean {
        val name = op.memoName ?: return true
        api.deleteMemo(name)
        memoCacheRepository.removeCachedMemo(accountId, name)
        onMemoDeleted(name)
        return true
    }

    /**
     * Deletes an attachment from the server. The local list and caches were already cleared when
     * the op was queued, so a 404 (already gone) is the desired end state, not a failure.
     */
    private suspend fun syncAttachmentDelete(op: PendingOp): Boolean {
        val name = op.memoName ?: return true
        try {
            api.deleteAttachment(name)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            Log.d(TAG, "Attachment $name was already deleted")
        }
        return true
    }

    private suspend fun syncCommentCreate(op: PendingOp): Boolean {
        var comment = gson.fromJson(op.payloadJson, Memo::class.java) ?: return true
        val parentName = op.parentName ?: return true
        // A comment queued before it carried its placement says SPACE without a space, which the
        // server rejects. Recover the placement from the parent instead of losing the op.
        if (comment.space == null && comment.visibility == Visibility.SPACE) {
            val parent = api.getMemo(parentName)
            if (parent.space == null) {
                Log.w(TAG, "Comment op ${op.id} still has no space; parent $parentName is not placed")
            } else {
                comment = comment.copy(space = parent.space)
            }
        }
        // The op id doubles as the idempotency key (commentId): a replayed
        // comment create is then deduplicated by the server instead of
        // duplicating the comment. The API layer drops it on server versions
        // that reject client-supplied ids (v0.26/v0.27).
        api.createMemoComment(parentName, comment, commentId = op.id)
        onCommentsRefresh()
        return true
    }

    private suspend fun syncReactionUpsert(op: PendingOp): Boolean {
        val name = op.memoName ?: return true
        val payload = gson.fromJson(op.payloadJson, ReactionOpPayload::class.java) ?: return true
        api.upsertMemoReaction(
            name, UpsertMemoReactionRequest(name = name, reaction = Reaction(
                contentId = name, reactionType = payload.reactionType
            ))
        )
        refreshMemo(name)
        return true
    }

    private suspend fun syncReactionDelete(op: PendingOp): Boolean {
        val name = op.memoName ?: return true
        val payload = gson.fromJson(op.payloadJson, ReactionOpPayload::class.java) ?: return true
        val server = api.getMemo(name)
        // Match on type AND creator: reaction types are not unique per memo,
        // so matching the type alone could delete another user's reaction.
        val mine = payload.creator ?: currentUser?.name
        val reaction = server.reactions?.firstOrNull {
            it.reactionType == payload.reactionType && (mine == null || it.creator == mine)
        }
        if (reaction?.name != null) {
            api.deleteMemoReaction(reaction.name)
        }
        refreshMemo(name)
        return true
    }

    private suspend fun refreshMemo(name: String) {
        try {
            val fresh = api.getMemo(name)
            cacheMemo(fresh)
            onMemoSynced(fresh, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "refreshMemo failed for $name", e)
        }
    }

    suspend fun cacheMemo(memo: Memo) {
        val name = memo.name ?: return
        // Atomic cross-list-type upsert: removes any stale row for this name
        // (previous state) and inserts the fresh one in a single transaction.
        memoCacheRepository.upsertCachedMemoState(
            accountId,
            memo,
            if (memo.state == MemoState.ARCHIVED) CacheListType.ARCHIVED else CacheListType.USER
        )
    }
}
