package org.example.moxia.api

import org.example.moxia.model.LinkMetadata
import org.example.moxia.model.Activity
import org.example.moxia.model.Attachment
import org.example.moxia.model.CreatePersonalAccessTokenRequest
import org.example.moxia.model.CreatePersonalAccessTokenResponse
import org.example.moxia.model.CurrentSessionResponse
import org.example.moxia.model.IdentityProvider
import org.example.moxia.model.InstanceProfile
import org.example.moxia.model.InstanceSetting
import org.example.moxia.model.ListActivitiesResponse
import org.example.moxia.model.ListAllUserStatsResponse
import org.example.moxia.model.ListAttachmentsResponse
import org.example.moxia.model.ListIdentityProvidersResponse
import org.example.moxia.model.ListMemoAttachmentsResponse
import org.example.moxia.model.ListMemoCommentsResponse
import org.example.moxia.model.ListMemoReactionsResponse
import org.example.moxia.model.ListMemoRelationsResponse
import org.example.moxia.model.ListMemosResponse
import org.example.moxia.model.ListPersonalAccessTokensResponse
import org.example.moxia.model.ListSpaceInvitationsResponse
import org.example.moxia.model.ListSpaceMembersResponse
import org.example.moxia.model.ListSpacesResponse
import org.example.moxia.model.ListUserNotificationsResponse
import org.example.moxia.model.ListUserSettingsResponse
import org.example.moxia.model.ListUserWebhooksResponse
import org.example.moxia.model.ListUsersResponse
import org.example.moxia.model.Memo
import org.example.moxia.model.Reaction
import org.example.moxia.model.RefreshTokenRequest
import org.example.moxia.model.RefreshTokenResponse
import org.example.moxia.model.SetMemoAttachmentsRequest
import org.example.moxia.model.SetMemoRelationsRequest
import org.example.moxia.model.Shortcut
import org.example.moxia.model.ShortcutResponse
import org.example.moxia.model.SignInRequest
import org.example.moxia.model.SignInResponse
import org.example.moxia.model.Space
import org.example.moxia.model.SpaceInvitation
import org.example.moxia.model.SpaceMember
import org.example.moxia.model.UpsertMemoReactionRequest
import org.example.moxia.model.User
import org.example.moxia.model.UserNotification
import org.example.moxia.model.UserSetting
import org.example.moxia.model.UserStats
import org.example.moxia.model.UserWebhook

data class ApiConstants(
    val userSettingGeneralKey: String,
    val userSettingLocaleMask: String,
    val userSettingMemoVisibilityMask: String,
    val memoCreatorFilterStyle: MemoCreatorFilterStyle,
    val memoOrderByPinnedDesc: String,
    val memoOrderByNewest: String,
    val memoOrderByOldest: String,

    val userMaskUsername: String,
    val userMaskEmail: String,
    val userMaskDisplayName: String,
    val userMaskAvatarUrl: String,
    val userMaskDescription: String,
    val userMaskPassword: String,

    val webhookMaskDisplayName: String,
    val webhookMaskUrl: String,

    val shortcutMaskTitle: String,
    val shortcutMaskFilter: String,
    val apiPath: String = "api/v1"
)

enum class MemoCreatorFilterStyle {
    LEGACY_ID,
    RESOURCE_NAME
}

enum class MemoOrderBy {
    PINNED_DESC,
    NEWEST,
    OLDEST
}

fun MemosApi.resolveMemoOrderBy(orderBy: MemoOrderBy?): String? {
    return when (orderBy) {
        MemoOrderBy.PINNED_DESC -> constants.memoOrderByPinnedDesc
        MemoOrderBy.NEWEST -> constants.memoOrderByNewest
        MemoOrderBy.OLDEST -> constants.memoOrderByOldest
        null -> null
    }
}

interface MemosApi {

    suspend fun getLinkMetadata(url: String): LinkMetadata

    val constants: ApiConstants

    fun getUserResourceName(user: User?): String?

    fun buildMemoCreatorFilter(user: User?): String?

    // --- ActivityService ---
    suspend fun listActivities(
        pageSize: Int? = null, pageToken: String? = null
    ): ListActivitiesResponse

    suspend fun getActivity(activity: String): Activity

    // --- AttachmentService ---
    suspend fun listAttachments(
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null,
        orderBy: String? = null
    ): ListAttachmentsResponse

    suspend fun createAttachment(
        attachment: Attachment, attachmentId: String? = null
    ): Attachment

    suspend fun getAttachment(attachment: String): Attachment

    suspend fun deleteAttachment(attachment: String)

    suspend fun updateAttachment(
        attachment: String,
        attachmentData: Attachment,
        updateMask: String
    ): Attachment

    // --- AuthService ---
    suspend fun getCurrentSession(): CurrentSessionResponse

    suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse

    suspend fun signIn(request: SignInRequest): SignInResponse

    suspend fun signOut()

    // --- IdentityProviderService ---
    suspend fun listIdentityProviders(): ListIdentityProvidersResponse

    suspend fun createIdentityProvider(
        identityProvider: IdentityProvider,
        identityProviderId: String? = null
    ): IdentityProvider

    suspend fun getIdentityProvider(
        identityProvider: String
    ): IdentityProvider

    suspend fun deleteIdentityProvider(
        identityProvider: String
    )

    suspend fun updateIdentityProvider(
        identityProvider: String,
        identityProviderData: IdentityProvider,
        updateMask: String
    ): IdentityProvider

    // --- InstanceService ---
    suspend fun getInstanceProfile(): InstanceProfile

    suspend fun getInstanceSetting(instance: String): InstanceSetting

    suspend fun updateInstanceSetting(
        instance: String,
        setting: InstanceSetting,
        updateMask: String
    ): InstanceSetting

    // --- MemoService ---
    suspend fun listMemos(
        pageSize: Int? = null,
        pageToken: String? = null,
        state: String? = null,
        orderBy: String? = null,
        filter: String? = null,
        showDeleted: Boolean? = null
    ): ListMemosResponse

    suspend fun createMemo(
        memo: Memo, memoId: String? = null
    ): Memo

    suspend fun getMemo(memo: String): Memo

    suspend fun deleteMemo(
        memo: String, force: Boolean? = null
    )

    suspend fun updateMemo(
        memo: String,
        memoData: Memo,
        updateMask: String
    ): Memo

    suspend fun listMemoAttachments(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoAttachmentsResponse

    suspend fun setMemoAttachments(
        memo: String, request: SetMemoAttachmentsRequest
    )

    suspend fun listMemoComments(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null,
        orderBy: String? = null
    ): ListMemoCommentsResponse

    suspend fun createMemoComment(
        memo: String,
        comment: Memo,
        commentId: String? = null
    ): Memo

    suspend fun listMemoReactions(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoReactionsResponse

    suspend fun upsertMemoReaction(
        memo: String, request: UpsertMemoReactionRequest
    ): Reaction

    suspend fun deleteMemoReaction(
        reaction: String
    )

    suspend fun listMemoRelations(
        memo: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListMemoRelationsResponse

    suspend fun setMemoRelations(
        memo: String, request: SetMemoRelationsRequest
    )

    // --- SpaceService ---
    suspend fun listSpaces(
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListSpacesResponse

    suspend fun getSpace(
        space: String
    ): Space

    suspend fun createSpace(
        space: Space,
        spaceId: String? = null
    ): Space

    suspend fun updateSpace(
        space: String,
        spaceData: Space,
        updateMask: String
    ): Space

    suspend fun deleteSpace(
        space: String
    )

    suspend fun listSpaceMembers(
        space: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListSpaceMembersResponse

    suspend fun updateSpaceMember(
        spaceMember: String,
        spaceMemberData: SpaceMember,
        updateMask: String
    ): SpaceMember

    suspend fun deleteSpaceMember(
        spaceMember: String
    )

    suspend fun listSpaceInvitations(
        space: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListSpaceInvitationsResponse

    suspend fun createSpaceInvitation(
        space: String,
        spaceInvitation: SpaceInvitation
    ): SpaceInvitation

    suspend fun deleteSpaceInvitation(
        spaceInvitation: String
    )

    suspend fun acceptSpaceInvitation(
        spaceInvitation: String
    ): SpaceMember

    suspend fun declineSpaceInvitation(
        spaceInvitation: String
    )

    suspend fun listUserSpaceInvitations(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListSpaceInvitationsResponse

    // --- UserService ---
    suspend fun listUsers(
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null,
        showDeleted: Boolean? = null
    ): ListUsersResponse

    suspend fun createUser(
        user: User,
        userId: String? = null,
        validateOnly: Boolean? = null,
        requestId: String? = null
    ): User

    suspend fun getUser(
        user: String, readMask: String? = null
    ): User

    suspend fun getUsers(
        users: List<String>
    ): Map<String, User>

    suspend fun deleteUser(
        user: String, force: Boolean? = null
    )

    suspend fun updateUser(
        user: String,
        userData: User,
        updateMask: String,
        allowMissing: Boolean? = null
    ): User

    suspend fun listUserNotifications(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null,
        filter: String? = null
    ): ListUserNotificationsResponse

    suspend fun deleteUserNotification(
        user: String,
        notification: String
    )

    suspend fun updateUserNotification(
        user: String,
        notification: String,
        notificationData: UserNotification,
        updateMask: String
    ): UserNotification

    suspend fun listPersonalAccessTokens(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListPersonalAccessTokensResponse

    suspend fun createPersonalAccessToken(
        user: String, request: CreatePersonalAccessTokenRequest
    ): CreatePersonalAccessTokenResponse

    suspend fun deletePersonalAccessToken(
        user: String,
        personalAccessToken: String
    )

    suspend fun listUserSettings(
        user: String,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ListUserSettingsResponse

    suspend fun getUserSetting(
        user: String, setting: String
    ): UserSetting

    suspend fun updateUserSetting(
        user: String,
        setting: String,
        settingData: UserSetting,
        updateMask: String
    ): UserSetting

    suspend fun getShortcuts(user: String): ShortcutResponse

    suspend fun createShortcut(
        user: String,
        shortcut: Shortcut,
        validateOnly: Boolean? = null
    ): Shortcut

    suspend fun deleteShortcut(
        user: String,
        shortcut: String
    )

    suspend fun updateShortcut(
        user: String,
        shortcut: String,
        shortcutData: Shortcut,
        updateMask: String? = null
    ): Shortcut

    suspend fun listUserWebhooks(
        user: String
    ): ListUserWebhooksResponse

    suspend fun createUserWebhook(
        user: String, webhook: UserWebhook
    ): UserWebhook

    suspend fun deleteUserWebhook(
        user: String, webhook: String
    )

    suspend fun updateUserWebhook(
        user: String,
        webhook: String,
        webhookData: UserWebhook,
        updateMask: String
    ): UserWebhook

    suspend fun getUserStats(user: String): UserStats

    suspend fun listAllUserStats(): ListAllUserStatsResponse
}
