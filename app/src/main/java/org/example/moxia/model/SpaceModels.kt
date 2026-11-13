package org.example.moxia.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A space is an instance level collaboration resource. A memo either belongs to
 * no space or to exactly one space, and the space is addressed by its full
 * resource name (`spaces/{uid}`).
 */
@Immutable
data class Space(
    val name: String,
    val title: String,
    val description: String? = null,
    val icon: SpaceIcon? = null,
    @SerializedName("currentUserRole", alternate = ["current_user_role"])
    val currentUserRole: SpaceRole = SpaceRole.USER,
    @SerializedName("memberCount", alternate = ["member_count"])
    val memberCount: Int = 0
) {
    /** The bare uid of the space, i.e. the `spaces/{uid}` name without the prefix. */
    val uid: String get() = name.substringAfterLast('/')

    companion object {
        /** Builds the resource name of a space from its uid. */
        fun nameOf(uid: String): String = "spaces/$uid"
    }
}

/** The icon of a space. Only one of the two is set. */
@Immutable
data class SpaceIcon(
    val emoji: String? = null,
    val lucide: String? = null
)

@Serializable
enum class SpaceRole {
    @SerialName("ADMIN")
    ADMIN,

    @SerialName("USER")
    USER,

    @SerializedName("ROLE_UNSPECIFIED")
    ROLE_UNSPECIFIED
}

@Immutable
data class SpaceMember(
    val name: String,
    val user: String,
    val role: SpaceRole = SpaceRole.USER
) {
    /** The bare username of the member. */
    val username: String get() = user.substringAfterLast('/')
}

@Immutable
data class SpaceInvitation(
    val name: String,
    val invitee: String,
    val role: SpaceRole = SpaceRole.USER,
    val space: String? = null
) {
    /** The bare username of the invitee. */
    val username: String get() = invitee.substringAfterLast('/')
}

data class ListSpacesResponse(
    val spaces: List<Space>?, val nextPageToken: String?
)

data class ListSpaceMembersResponse(
    val spaceMembers: List<SpaceMember>?, val nextPageToken: String?
)

data class ListSpaceInvitationsResponse(
    val spaceInvitations: List<SpaceInvitation>?, val nextPageToken: String?
)

/** Whether the server knows the space endpoints at all. */
enum class SpaceCapability {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED
}

/** Cached capability answer, scoped to a host and version like the map support snapshot. */
data class SpaceSupport(
    val hostUrl: String? = null,
    val version: String? = null,
    val capability: SpaceCapability = SpaceCapability.UNKNOWN
)

/** The space the notes feed is scoped to; null means every memo of the account. */
data class SpaceSelection(val space: String? = null)

/**
 * Spaces the account can read, the feed scope, and the per-space member/invitation lists that the
 * management screen shows. The lists are filled on demand.
 */
@Immutable
data class SpaceState(
    val capability: SpaceCapability = SpaceCapability.UNKNOWN,
    val spaces: List<Space> = emptyList(),
    val selected: String? = null,
    val members: Map<String, List<SpaceMember>> = emptyMap(),
    val invitations: Map<String, List<SpaceInvitation>> = emptyMap(),
    val incoming: List<SpaceInvitation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isSupported: Boolean get() = capability == SpaceCapability.SUPPORTED
    val selectedSpace: Space? get() = selected?.let { name -> spaces.firstOrNull { it.name == name } }
}
