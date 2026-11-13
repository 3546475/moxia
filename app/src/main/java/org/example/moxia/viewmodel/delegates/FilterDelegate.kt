package org.example.moxia.viewmodel.delegates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.example.moxia.data.offline.SessionCacheStore
import org.example.moxia.data.offline.SessionSnapshotData
import org.example.moxia.model.filter.MemoFilter
import org.example.moxia.model.filter.normalizeTag
import org.example.moxia.viewmodel.AccountContext
import org.example.moxia.viewmodel.AccountSession
import org.example.moxia.viewmodel.MemosUiState

/**
 * Owns the notes feed's filter. Keeping every mutation here means the state, the request filter
 * and the persisted copy can never disagree, and the UI only says *what* changed instead of
 * rebuilding the whole filter by hand.
 */
interface FilterDelegate {
    /** Replace the filter; a no-op when nothing actually changes. */
    fun apply(filter: MemoFilter)

    /** Add the tag, or remove it when it is already applied. */
    fun toggleTag(tag: String)

    fun clear()
}

class FilterDelegateImpl(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MemosUiState>,
    private val sessionCacheStore: SessionCacheStore,
    private val accountSession: AccountSession,
    private val onFilterApplied: (MemoFilter) -> Unit
) : FilterDelegate {

    override fun apply(filter: MemoFilter) {
        val context = accountSession.current ?: return
        val next = filter.normalized()
        if (uiState.value.userMemoList.filter == next) return
        accountSession.update(uiState, context) {
            it.copy(userMemoList = it.userMemoList.copy(filter = next))
        }
        persist(context, next)
        onFilterApplied(next)
    }

    override fun toggleTag(tag: String) {
        val name = normalizeTag(tag)
        if (name.isEmpty()) return
        val current = uiState.value.userMemoList.filter
        apply(if (name in current.tags) current.withoutTag(name) else current.withTag(name))
    }

    /**
     * Drops every condition; the space scope is where the user is browsing, not a condition, so
     * they stay inside the space they were in.
     */
    override fun clear() = apply(MemoFilter(space = uiState.value.userMemoList.filter.space))

    /**
     * Persist into the session snapshot next to the shortcuts, so the feed reopens with the same
     * filter after a cold start. Read-modify-write: the snapshot also holds the profile data other
     * writers refresh.
     */
    private fun persist(context: AccountContext, filter: MemoFilter) {
        if (!accountSession.isCurrent(context)) return
        val accountId = context.account.id
        scope.launch {
            runCatching {
                val current = sessionCacheStore.get(accountId) ?: SessionSnapshotData()
                sessionCacheStore.save(accountId, current.copy(filter = filter))
            }
        }
    }
}
