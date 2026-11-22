package org.example.moxia.account

import kotlinx.coroutines.test.runTest
import org.example.moxia.api.GsonProvider
import org.example.moxia.api.MemosApi
import org.example.moxia.data.audit.SyncAuditDao
import org.example.moxia.data.audit.SyncAuditLogger
import org.example.moxia.data.cache.MemoCacheRepository
import org.example.moxia.data.cache.MemoDao
import org.example.moxia.data.sync.OpReplayExecutor
import org.example.moxia.data.sync.PendingOp
import org.example.moxia.data.sync.PendingOpDao
import org.example.moxia.data.sync.PendingOpType
import org.example.moxia.data.sync.SyncRepository
import org.example.moxia.model.Memo
import org.example.moxia.model.Visibility
import org.junit.Assert.*
import org.junit.Test

/**
 * A comment queued before the payload carried its placement says SPACE without a space; the
 * server rejects that shape, so the replay recovers the placement from the parent. Every other
 * queued comment must be replayed exactly as it was stored.
 */
class SpaceCommentReplayTest {

    @Test fun `a queued SPACE comment recovers the parent placement`() = runTest {
        var parentReads = 0
        var sent: Memo? = null
        val api = stub<MemosApi> { name, args ->
            when (name) {
                "getMemo" -> {
                    parentReads++
                    Memo(name = args[0] as String, content = "Parent", space = "spaces/one")
                }
                "createMemoComment" -> {
                    sent = args[1] as Memo
                    Memo(name = "memos/comment", content = "Saved")
                }
                else -> error(name)
            }
        }

        assertTrue(executor(api).replay(commentOp(Memo(content = "Old", visibility = Visibility.SPACE))))

        assertEquals(1, parentReads)
        assertEquals("spaces/one", sent?.space)
        assertEquals(Visibility.SPACE, sent?.visibility)
    }

    @Test fun `a queued comment that already has a space is replayed as stored`() = runTest {
        var sent: Memo? = null
        val api = stub<MemosApi> { name, args ->
            when (name) {
                "getMemo" -> error("must not read the parent")
                "createMemoComment" -> {
                    sent = args[1] as Memo
                    Memo(name = "memos/comment", content = "Saved")
                }
                else -> error(name)
            }
        }
        val comment = Memo(content = "New", visibility = Visibility.SPACE, space = "spaces/one")

        assertTrue(executor(api).replay(commentOp(comment)))

        assertEquals("spaces/one", sent?.space)
    }

    @Test fun `a queued non-SPACE comment is replayed without reading the parent`() = runTest {
        var sent: Memo? = null
        val api = stub<MemosApi> { name, args ->
            when (name) {
                "getMemo" -> error("must not read the parent")
                "createMemoComment" -> {
                    sent = args[1] as Memo
                    Memo(name = "memos/comment", content = "Saved")
                }
                else -> error(name)
            }
        }

        assertTrue(executor(api).replay(commentOp(Memo(content = "Old", visibility = Visibility.PRIVATE))))

        assertNull(sent?.space)
    }

    private fun commentOp(comment: Memo) = PendingOp.new(
        accountId = "acc-1",
        type = PendingOpType.COMMENT_CREATE,
        memoName = "offline-comment-1",
        parentName = "memos/parent",
        payloadJson = GsonProvider.gson.toJson(comment),
        id = "op-1"
    )

    private fun executor(api: MemosApi) = OpReplayExecutor(
        api = api,
        repository = SyncRepository(stub<PendingOpDao> { name, _ -> error(name) }),
        memoCacheRepository = MemoCacheRepository(stub<MemoDao> { name, _ -> error(name) }),
        auditLogger = SyncAuditLogger(stub<SyncAuditDao> { name, _ ->
            if (name == "count") 0 else Unit
        }),
        accountId = "acc-1"
    )
}
