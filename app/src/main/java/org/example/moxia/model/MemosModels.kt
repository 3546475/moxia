package org.example.moxia.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

data class ListMemosResponse(
    val memos: List<Memo>?, val nextPageToken: String?
)

data class ListAttachmentsResponse(
    val attachments: List<Attachment>?, val nextPageToken: String?, val totalSize: Int?
)

@Serializable
enum class Visibility {
    @SerialName("PUBLIC")
    PUBLIC,

    @SerialName("PROTECTED")
    PROTECTED,

    @SerialName("PRIVATE")
    PRIVATE,

    @SerialName("SPACE")
    SPACE,

    @SerializedName("VISIBILITY_UNSPECIFIED")
    VISIBILITY_UNSPECIFIED
}

@Serializable
enum class MemoState {
    @SerialName("NORMAL")
    NORMAL,

    @SerialName("ARCHIVED")
    ARCHIVED,

    @SerialName("STATE_UNSPECIFIED")
    STATE_UNSPECIFIED
}

@Immutable
data class Memo(
    val name: String? = null,
    val state: MemoState? = null,
    val creator: String? = null,
    @SerializedName("createTime", alternate = ["create_time"]) val createTime: Instant? = null,
    @SerializedName("updateTime", alternate = ["update_time"]) val updateTime: Instant? = null,
    @SerializedName("displayTime", alternate = ["display_time"]) val displayTime: Instant? = null,
    val content: String,
    val visibility: Visibility = Visibility.PRIVATE,
    /** The space this memo belongs to (`spaces/{uid}`), or null when it is unassigned. */
    val space: String? = null,
    val tags: List<String>? = null,
    val pinned: Boolean? = null,
    val attachments: List<Attachment>? = null,
    val relations: List<MemoRelation>? = null,
    val reactions: List<Reaction>? = null,
    val property: MemoProperty? = null,
    val parent: String? = null,
    val snippet: String? = null,
    val location: Location? = null
)

@Immutable
data class Attachment(
    val name: String? = null,
    @SerializedName("createTime", alternate = ["create_time"]) val createTime: Instant? = null,
    val filename: String,
    val content: String? = null,
    val externalLink: String? = null,
    val type: String,
    val mimeType: String? = null,
    val size: String? = null,
    val memo: String? = null,
    // Local-only fields for an attachment whose upload is still sitting in the
    // durable upload queue (offline compose). Never sent to the server: the
    // outbox replay strips them after resolving the placeholder against the
    // queue. clientId matches the AttachmentUpload row (forwarded as the API
    // attachmentId); localPath points at the staged bytes for local preview.
    val clientId: String? = null,
    val localPath: String? = null
) {
    /** Stable identity for thumbnails, row keys, and transitions, including local-only attachments. */
    val identityKey: String
        get() = name ?: clientId ?: "$filename:$type:$createTime:$externalLink:$localPath"

    val displayType: String
        get() = mimeType ?: type
}


enum class MemoRelationType {
    @SerialName("TYPE_UNSPECIFIED")
    TYPE_UNSPECIFIED,

    @SerialName("REFERENCE")
    COMMENT,

    @SerialName("COMMENT")
    REPLY
}

data class MemoRelation(
    val memo: MemoSnippet, val relatedMemo: MemoSnippet, val type: MemoRelationType
)

/**
 * True for a "Link memo" relation: this memo points at [memoName] with the outgoing type.
 *
 * Comment relations carry the very same direction — the comment is the `memo`, the memo it belongs
 * to is the `relatedMemo` — and differ only in type, so direction alone would mistake every comment
 * for a link.
 */
fun MemoRelation.isLinkTo(memoName: String?): Boolean =
    memoName != null && type == MemoRelationType.REPLY && memo.name == memoName

data class MemoSnippet(
    val name: String, val snippet: String? = null
)

@Immutable
data class Reaction(
    val name: String? = null,
    val creator: String? = null,
    val contentId: String,
    val reactionType: String,
    val createTime: Instant? = null
)

data class MemoProperty(
    val hasLink: Boolean? = null,
    val hasTaskList: Boolean? = null,
    val hasCode: Boolean? = null,
    val hasIncompleteTasks: Boolean? = null
)

data class Location(
    val placeholder: String? = null, val latitude: Double? = null, val longitude: Double? = null
)

// --- Activity Models ---

data class ListActivitiesResponse(
    val activities: List<Activity>?, val nextPageToken: String?
)

@Immutable
data class Activity(
    val name: String? = null,
    val creator: String? = null,
    val type: String? = null,
    val level: String? = null,
    @SerializedName("createTime", alternate = ["create_time"]) val createTime: Instant? = null,
    val payload: ActivityPayload? = null
)

data class ActivityPayload(
    val memoComment: ActivityMemoCommentPayload? = null
)

data class ActivityMemoCommentPayload(
    val memo: String? = null, val relatedMemo: String? = null
)

// --- Additional Memo Service Models ---

data class ListMemoAttachmentsResponse(
    val attachments: List<Attachment>?, val nextPageToken: String?
)

data class SetMemoAttachmentsRequest(
    val name: String, val attachments: List<Attachment>
)

data class ListMemoCommentsResponse(
    val memos: List<Memo>?, val nextPageToken: String?, val totalSize: Int?
)

data class ListMemoReactionsResponse(
    val reactions: List<Reaction>?, val nextPageToken: String?, val totalSize: Int?
)

@Immutable
data class UpsertMemoReactionRequest(
    val name: String, val reaction: Reaction
)

data class ListMemoRelationsResponse(
    val relations: List<MemoRelation>?, val nextPageToken: String?
)

/**
 * Wire shape of a relation. The server spells the outgoing type `REFERENCE`, while the model calls
 * it [MemoRelationType.REPLY]; sending the enum name directly makes the request fail.
 */
data class MemoRelationWire(
    val memo: MemoSnippet, val relatedMemo: MemoSnippet, val type: String
) {
    companion object {
        const val REFERENCE = "REFERENCE"
        const val COMMENT = "COMMENT"

        fun reference(memo: MemoSnippet, related: MemoSnippet) =
            MemoRelationWire(memo = memo, relatedMemo = related, type = REFERENCE)

        fun from(relation: MemoRelation) = MemoRelationWire(
            memo = relation.memo,
            relatedMemo = relation.relatedMemo,
            type = when (relation.type) {
                MemoRelationType.COMMENT -> COMMENT
                MemoRelationType.REPLY -> REFERENCE
                MemoRelationType.TYPE_UNSPECIFIED -> "TYPE_UNSPECIFIED"
            }
        )
    }
}

data class SetMemoRelationsRequest(
    val name: String, val relations: List<MemoRelationWire>
)

/**
 * Marker of a memo that only exists locally so far: the outbox still has to create it on the
 * server, which hands back the real name and replaces this one everywhere.
 */
const val OfflineMemoPrefix = "offline-"

