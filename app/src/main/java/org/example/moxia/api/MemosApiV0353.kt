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
import org.example.moxia.model.UseRole
import org.example.moxia.model.UseState
import org.example.moxia.model.User
import org.example.moxia.model.UserSnapshot
import org.example.moxia.model.UserNotification
import org.example.moxia.model.UserSetting
import org.example.moxia.model.UserStats
import org.example.moxia.model.UserWebhook
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Resource paths are relative to the API root selected by [MemosApiFactory]. */
interface MemosApiV0353 {

    @GET("memos/-/linkMetadata")
    suspend fun getLinkMetadata(@Query("url") url: String): LinkMetadata

    // --- ActivityService ---
    @GET("activities")
    suspend fun listActivities(
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListActivitiesResponse

    @GET("{activity}")
    suspend fun getActivity(@Path("activity", encoded = true) activity: String): Activity

    // --- AttachmentService ---
    @GET("attachments")
    suspend fun listAttachments(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("orderBy") orderBy: String? = null
    ): ListAttachmentsResponse

    @POST("attachments")
    suspend fun createAttachment(
        @Body attachment: Attachment, @Query("attachmentId") attachmentId: String? = null
    ): Attachment

    @GET("{attachment}")
    suspend fun getAttachment(@Path("attachment", encoded = true) attachment: String): Attachment

    @DELETE("{attachment}")
    suspend fun deleteAttachment(@Path("attachment", encoded = true) attachment: String)

    @PATCH("{attachment}")
    suspend fun updateAttachment(
        @Path("attachment", encoded = true) attachment: String,
        @Body attachmentData: Attachment,
        @Query("updateMask") updateMask: String
    ): Attachment


    // --- AuthService ---
    @GET("auth/me")
    suspend fun getCurrentSession(): CurrentSessionResponseV0353

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @POST("auth/signin")
    suspend fun signIn(@Body request: SignInRequest): SignInResponseV0353

    @POST("auth/signout")
    suspend fun signOut()

    // --- IdentityProviderService ---
    @GET("identity-providers")
    suspend fun listIdentityProviders(): ListIdentityProvidersResponse

    @POST("identity-providers")
    suspend fun createIdentityProvider(
        @Body identityProvider: IdentityProvider,
        @Query("identityProviderId") identityProviderId: String? = null
    ): IdentityProvider

    @GET("{identityProvider}")
    suspend fun getIdentityProvider(
        @Path(
            "identityProvider", encoded = true
        ) identityProvider: String
    ): IdentityProvider

    @DELETE("{identityProvider}")
    suspend fun deleteIdentityProvider(
        @Path(
            "identityProvider", encoded = true
        ) identityProvider: String
    )

    @PATCH("{identityProvider}")
    suspend fun updateIdentityProvider(
        @Path("identityProvider", encoded = true) identityProvider: String,
        @Body identityProviderData: IdentityProvider,
        @Query("updateMask") updateMask: String
    ): IdentityProvider

    // --- InstanceService ---
    @GET("instance/profile")
    suspend fun getInstanceProfile(): InstanceProfile

    @GET("instance/{instance}")
    suspend fun getInstanceSetting(
        @Path(
            "instance",
            encoded = true
        ) instance: String
    ): InstanceSetting

    @PATCH("instance/{instance}/*")
    suspend fun updateInstanceSetting(
        @Path("instance") instance: String,
        @Body setting: InstanceSetting,
        @Query("updateMask") updateMask: String
    ): InstanceSetting

    // --- MemoService ---
    @GET("memos")
    suspend fun listMemos(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("state") state: String? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null
    ): ListMemosResponse

    @POST("memos")
    suspend fun createMemo(
        @Body memo: Memo, @Query("memoId") memoId: String? = null
    ): Memo

    @GET("{memo}")
    suspend fun getMemo(@Path("memo", encoded = true) memo: String): Memo

    @DELETE("{memo}")
    suspend fun deleteMemo(
        @Path("memo", encoded = true) memo: String, @Query("force") force: Boolean? = null
    )

    @PATCH("{memo}")
    suspend fun updateMemo(
        @Path("memo", encoded = true) memo: String,
        @Body memoData: Memo,
        @Query("updateMask") updateMask: String
    ): Memo

    @GET("{memo}/attachments")
    suspend fun listMemoAttachments(
        @Path("memo", encoded = true) memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoAttachmentsResponse

    @PATCH("{memo}/attachments")
    suspend fun setMemoAttachments(
        @Path("memo", encoded = true) memo: String, @Body request: SetMemoAttachmentsRequest
    )

    @GET("{memo}/comments")
    suspend fun listMemoComments(
        @Path("memo", encoded = true) memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String? = null
    ): ListMemoCommentsResponse

    @POST("{memo}/comments")
    suspend fun createMemoComment(
        @Path("memo", encoded = true) memo: String,
        @Body comment: Memo,
        @Query("commentId") commentId: String? = null
    ): Memo

    @GET("{memo}/reactions")
    suspend fun listMemoReactions(
        @Path("memo", encoded = true) memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoReactionsResponse

    @POST("{memo}/reactions")
    suspend fun upsertMemoReaction(
        @Path("memo", encoded = true) memo: String, @Body request: UpsertMemoReactionRequest
    ): Reaction

    @DELETE("{reaction}")
    suspend fun deleteMemoReaction(
        @Path("reaction", encoded = true) reaction: String
    )

    @GET("{memo}/relations")
    suspend fun listMemoRelations(
        @Path("memo", encoded = true) memo: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListMemoRelationsResponse

    @PATCH("{memo}/relations")
    suspend fun setMemoRelations(
        @Path("memo", encoded = true) memo: String, @Body request: SetMemoRelationsRequest
    )

    // --- UserService ---
    @GET("users")
    suspend fun listUsers(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null,
        @Query("showDeleted") showDeleted: Boolean? = null
    ): ListUsersResponseV0353

    @POST("users")
    suspend fun createUser(
        @Body user: UserSnapshot,
        @Query("userId") userId: String? = null,
        @Query("validateOnly") validateOnly: Boolean? = null,
        @Query("requestId") requestId: String? = null
    ): UserV0353

    @GET("{user}")
    suspend fun getUser(
        @Path("user", encoded = true) user: String, @Query("readMask") readMask: String? = null
    ): UserV0353

    @DELETE("{user}")
    suspend fun deleteUser(
        @Path("user", encoded = true) user: String, @Query("force") force: Boolean? = null
    )

    @PATCH("{user}")
    suspend fun updateUser(
        @Path("user", encoded = true) user: String,
        @Body userData: UserSnapshot,
        @Query("updateMask") updateMask: String,
        @Query("allowMissing") allowMissing: Boolean? = null
    ): UserV0353

    @GET("{user}/notifications")
    suspend fun listUserNotifications(
        @Path("user", encoded = true) user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ListUserNotificationsResponse

    @DELETE("{user}/notifications/{notification}")
    suspend fun deleteUserNotification(
        @Path("user", encoded = true) user: String,
        @Path("notification", encoded = true) notification: String
    )

    @PATCH("{user}/notifications/{notification}")
    suspend fun updateUserNotification(
        @Path("user", encoded = true) user: String,
        @Path("notification", encoded = true) notification: String,
        @Body notificationData: UserNotification,
        @Query("updateMask") updateMask: String
    ): UserNotification

    @GET("{user}/personalAccessTokens")
    suspend fun listPersonalAccessTokens(
        @Path("user", encoded = true) user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListPersonalAccessTokensResponse

    @POST("{user}/personalAccessTokens")
    suspend fun createPersonalAccessToken(
        @Path("user", encoded = true) user: String, @Body request: CreatePersonalAccessTokenRequest
    ): CreatePersonalAccessTokenResponse

    @DELETE("{user}/personalAccessTokens/{personalAccessToken}")
    suspend fun deletePersonalAccessToken(
        @Path("user", encoded = true) user: String,
        @Path("personalAccessToken", encoded = true) personalAccessToken: String
    )

    @GET("{user}/settings")
    suspend fun listUserSettings(
        @Path("user", encoded = true) user: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null
    ): ListUserSettingsResponse

    @GET("{user}/settings/{setting}")
    suspend fun getUserSetting(
        @Path("user", encoded = true) user: String, @Path("setting") setting: String
    ): UserSetting

    @PATCH("{user}/settings/{setting}")
    suspend fun updateUserSetting(
        @Path("user", encoded = true) user: String,
        @Path("setting") setting: String,
        @Body settingData: UserSetting,
        @Query("updateMask") updateMask: String
    ): UserSetting

    @GET("{user}/shortcuts")
    suspend fun getShortcuts(@Path("user", encoded = true) user: String): ShortcutResponse

    @POST("{user}/shortcuts")
    suspend fun createShortcut(
        @Path("user", encoded = true) user: String,
        @Body shortcut: Shortcut,
        @Query("validateOnly") validateOnly: Boolean? = null
    ): Shortcut

    @DELETE("{user}/shortcuts/{shortcut}")
    suspend fun deleteShortcut(
        @Path("user", encoded = true) user: String,
        @Path("shortcut", encoded = true) shortcut: String
    )

    @PATCH("{user}/shortcuts/{shortcut}")
    suspend fun updateShortcut(
        @Path("user", encoded = true) user: String,
        @Path("shortcut", encoded = true) shortcut: String,
        @Body shortcutData: Shortcut,
        @Query("updateMask") updateMask: String? = null
    ): Shortcut

    @GET("{user}/webhooks")
    suspend fun listUserWebhooks(
        @Path(
            "user", encoded = true
        ) user: String
    ): ListUserWebhooksResponse

    @POST("{user}/webhooks")
    suspend fun createUserWebhook(
        @Path("user", encoded = true) user: String, @Body webhook: UserWebhook
    ): UserWebhook

    @DELETE("{user}/webhooks/{webhook}")
    suspend fun deleteUserWebhook(
        @Path("user", encoded = true) user: String, @Path("webhook", encoded = true) webhook: String
    )

    @PATCH("{user}/webhooks/{webhook}")
    suspend fun updateUserWebhook(
        @Path("user", encoded = true) user: String,
        @Path("webhook", encoded = true) webhook: String,
        @Body webhookData: UserWebhook,
        @Query("updateMask") updateMask: String
    ): UserWebhook

    @GET("{user}:getStats")
    suspend fun getUserStats(@Path("user", encoded = true) user: String): UserStats

    @GET("users:stats")
    suspend fun listAllUserStats(): ListAllUserStatsResponse

    // --- SpaceService ---
    // The space resource name is `spaces/{uid}`; the uid alone addresses the
    // space in the URL, so the prefix is stripped before the path is built.
    @GET("spaces")
    suspend fun listSpaces(
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListSpacesResponse

    @GET("spaces/{uid}")
    suspend fun getSpace(@Path("uid") uid: String): Space

    @POST("spaces")
    suspend fun createSpace(
        @Body space: Space, @Query("spaceId") spaceId: String? = null
    ): Space

    @PATCH("spaces/{uid}")
    suspend fun updateSpace(
        @Path("uid") uid: String,
        @Body spaceData: Space,
        @Query("updateMask") updateMask: String
    ): Space

    @DELETE("spaces/{uid}")
    suspend fun deleteSpace(@Path("uid") uid: String)

    @GET("spaces/{uid}/members")
    suspend fun listSpaceMembers(
        @Path("uid") uid: String,
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListSpaceMembersResponse

    @PATCH("spaces/{uid}/members/{member}")
    suspend fun updateSpaceMember(
        @Path("uid") uid: String,
        @Path("member") member: String,
        @Body spaceMemberData: SpaceMember,
        @Query("updateMask") updateMask: String
    ): SpaceMember

    @DELETE("spaces/{uid}/members/{member}")
    suspend fun deleteSpaceMember(
        @Path("uid") uid: String, @Path("member") member: String
    )

    @GET("spaces/{uid}/invitations")
    suspend fun listSpaceInvitations(
        @Path("uid") uid: String,
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListSpaceInvitationsResponse

    @POST("spaces/{uid}/invitations")
    suspend fun createSpaceInvitation(
        @Path("uid") uid: String, @Body spaceInvitation: SpaceInvitation
    ): SpaceInvitation

    @DELETE("spaces/{uid}/invitations/{invitation}")
    suspend fun deleteSpaceInvitation(
        @Path("uid") uid: String, @Path("invitation") invitation: String
    )

    @POST("spaces/{uid}/invitations/{invitation}:accept")
    suspend fun acceptSpaceInvitation(
        @Path("uid") uid: String, @Path("invitation") invitation: String
    ): SpaceMember

    @POST("spaces/{uid}/invitations/{invitation}:decline")
    suspend fun declineSpaceInvitation(
        @Path("uid") uid: String, @Path("invitation") invitation: String
    )

    @GET("users/{uid}/spaceInvitations")
    suspend fun listUserSpaceInvitations(
        @Path("uid") uid: String,
        @Query("pageSize") pageSize: Int? = null, @Query("pageToken") pageToken: String? = null
    ): ListSpaceInvitationsResponse
}

data class UserV0353(
    override val name: String? = null,
    override val role: UseRole? = null,
    override val username: String? = null,
    override val email: String? = null,
    override val displayName: String? = null,
    override val avatarUrl: String? = null,
    override val description: String? = null,
    override val password: String? = null,
    override val state: UseState? = null,
    override val createTime: String? = null,
    override val updateTime: String? = null,
    override val token: String? = null
) : User

data class CurrentSessionResponseV0353(val user: UserV0353?) {
    fun toModel(): CurrentSessionResponse = CurrentSessionResponse(user = user)
}

data class SignInResponseV0353(
    val user: UserV0353,
    val accessToken: String,
    val accessTokenExpiresAt: String
) {
    fun toModel(): SignInResponse = SignInResponse(
        user = user,
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt
    )
}

data class ListUsersResponseV0353(
    val users: List<UserV0353>?,
    val nextPageToken: String? = null,
    val totalSize: Int? = null
) {
    fun toModel(): ListUsersResponse = ListUsersResponse(
        users = users,
        nextPageToken = nextPageToken,
        totalSize = totalSize
    )
}
