package org.example.moxia.viewmodel.manager

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.example.moxia.data.DataStoreManager
import org.example.moxia.model.Space
import org.example.moxia.model.SpaceIcon
import org.example.moxia.model.SpaceCapability
import org.example.moxia.model.SpaceInvitation
import org.example.moxia.model.SpaceMember
import org.example.moxia.model.SpaceRole
import org.example.moxia.model.SpaceSelection
import org.example.moxia.model.SpaceSupport
import org.example.moxia.model.normalizeMapHost
import org.example.moxia.viewmodel.AccountContext
import org.example.moxia.viewmodel.AccountSession
import org.example.moxia.viewmodel.ConnectionState
import org.example.moxia.viewmodel.MemosUiState
import retrofit2.HttpException

/**
 * Spaces are an instance level collaboration feature. Older servers answer the endpoint with
 * 404/405/410, so every entry point is gated on [state.capability] instead of on the version.
 */
class MemoSpaceManager(
    private val session: AccountSession,
    private val state: MutableStateFlow<MemosUiState>,
    private val settings: DataStoreManager
) {
    private var probeJob: Job? = null
    private var loadJob: Job? = null

    fun reset() {
        probeJob?.cancel()
        loadJob?.cancel()
    }

    /** Restores the cached answer before the network has said anything. */
    suspend fun restoreSupport(context: AccountContext) {
        val support = try {
            settings.snapshotJson("space_support", context.account.id).first()?.let {
                Gson().fromJson(it, SpaceSupport::class.java)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val knownVersion = state.value.session.instanceProfile?.version
        if (support == null) return
        if (support.hostUrl != normalizeMapHost(context.account.hostUrl)) return
        if (knownVersion != null && support.version != null && support.version != knownVersion) return
        session.update(state, context) {
            it.copy(space = it.space.copy(capability = support.capability))
        }
    }

    fun verify() {
        val context = session.current ?: return
        if (!context.networkReady || state.value.session.currUser == null ||
            state.value.connectionState != ConnectionState.ONLINE || probeJob?.isActive == true
        ) return
        probeJob = session.readScope.launch {
            try {
                val response = context.api.listSpaces(pageSize = 100)
                val spaces = response.spaces.orEmpty()
                rememberSupport(context, SpaceCapability.SUPPORTED)
                session.update(state, context) { it.copy(space = it.space.copy(spaces = spaces)) }
                restoreSelection(context, spaces)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (rejectsSpaceApi(error)) {
                    rememberSupport(context, SpaceCapability.UNSUPPORTED)
                }
                // Authentication, offline, throttling and server errors leave the last answer intact.
            }
        }
    }

    private suspend fun rememberSupport(context: AccountContext, capability: SpaceCapability) {
        if (!session.isCurrent(context)) return
        val support = SpaceSupport(
            hostUrl = normalizeMapHost(context.account.hostUrl),
            version = state.value.session.instanceProfile?.version,
            capability = capability
        )
        settings.saveSnapshotJson("space_support", context.account.id, Gson().toJson(support))
        if (!session.isCurrent(context)) return
        session.update(state, context) { it.copy(space = it.space.copy(capability = capability)) }
    }

    /** Reads the last selected space and drops it when the server no longer reports it. */
    private suspend fun restoreSelection(context: AccountContext, spaces: List<Space>) {
        val stored = try {
            settings.snapshotJson("space_selection", context.account.id).first()?.let {
                Gson().fromJson(it, SpaceSelection::class.java)?.space
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (stored == null) return
        val selected = if (spaces.any { it.name == stored }) stored else null
        session.update(state, context) { it.copy(space = it.space.copy(selected = selected)) }
        if (selected == null) persistSelection(context, null)
    }

    private suspend fun persistSelection(context: AccountContext, space: String?) {
        if (!session.isCurrent(context)) return
        settings.saveSnapshotJson(
            "space_selection", context.account.id, Gson().toJson(SpaceSelection(space))
        )
    }

    fun refresh(onChanged: () -> Unit = {}) {
        val context = session.current ?: return
        if (!context.networkReady || state.value.session.currUser == null) return
        if (loadJob?.isActive == true) return
        loadJob = session.readScope.launch {
            val current = session.current
            if (current == null || !session.isCurrent(current)) return@launch
            try {
                val spaces = loadAll(current)
                session.update(state, current) {
                    it.copy(
                        space = it.space.copy(
                            spaces = spaces,
                            capability = SpaceCapability.SUPPORTED,
                            selected = it.space.selected?.takeIf { name -> spaces.any { s -> s.name == name } }
                        )
                    )
                }
                rememberSupport(current, SpaceCapability.SUPPORTED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (rejectsSpaceApi(error)) rememberSupport(current, SpaceCapability.UNSUPPORTED)
            }
        }
        onChanged()
    }

    private suspend fun loadAll(context: AccountContext): List<Space> {
        val all = mutableListOf<Space>()
        var token: String? = null
        do {
            val page = context.api.listSpaces(pageSize = 100, pageToken = token)
            all += page.spaces.orEmpty()
            token = page.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (token != null && all.size < 500)
        return all
    }

    /** Switches the feed scope; null means every memo of the account. */
    fun select(space: String?, onChanged: () -> Unit) {
        val context = session.current
        if (context != null) {
            session.update(state, context) { it.copy(space = it.space.copy(selected = space)) }
            session.readScope.launch { persistSelection(context, space) }
        }
        onChanged()
    }

    fun create(
        title: String,
        description: String,
        icon: String?,
        onSuccess: (Space) -> Unit,
        onError: (String) -> Unit
    ) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                val created = context.api.createSpace(
                    space = Space(
                        name = "",
                        title = title.trim(),
                        description = description.trim().ifEmpty { null },
                        icon = icon?.trim()?.takeIf { it.isNotEmpty() }?.let { emoji -> SpaceIcon(emoji = emoji) }
                    )
                )
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(space = it.space.copy(spaces = (it.space.spaces + created).distinctBy { s -> s.name }))
                }
                onSuccess(created)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "create space failed")
            }
        }
    }

    fun update(
        space: Space,
        title: String,
        description: String,
        icon: String?,
        onSuccess: (Space) -> Unit,
        onError: (String) -> Unit
    ) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                val updated = context.api.updateSpace(
                    space = space.name,
                    spaceData = space.copy(
                        title = title.trim(),
                        description = description.trim().ifEmpty { null },
                        icon = icon?.trim()?.takeIf { it.isNotEmpty() }?.let { emoji -> SpaceIcon(emoji = emoji) }
                    ),
                    updateMask = "title,description,icon"
                )
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(
                        space = it.space.copy(
                            spaces = it.space.spaces.map { s -> if (s.name == updated.name) updated else s }
                        )
                    )
                }
                onSuccess(updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "update space failed")
            }
        }
    }

    fun delete(space: Space, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                context.api.deleteSpace(space.name)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(
                        space = it.space.copy(
                            spaces = it.space.spaces.filterNot { s -> s.name == space.name },
                            selected = it.space.selected?.takeIf { name -> name != space.name },
                            members = it.space.members - space.name,
                            invitations = it.space.invitations - space.name
                        )
                    )
                }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "delete space failed")
            }
        }
    }

    fun loadMembers(space: Space) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                val response = context.api.listSpaceMembers(space.name, pageSize = 100)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(
                        space = it.space.copy(
                            members = it.space.members + (space.name to response.spaceMembers.orEmpty())
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun updateMemberRole(member: SpaceMember, role: SpaceRole, onError: (String) -> Unit = {}) {
        val context = session.current ?: return
        val space = member.name.substringBeforeLast("/members/", "")
        session.readScope.launch {
            try {
                val updated = context.api.updateSpaceMember(
                    spaceMember = member.name,
                    spaceMemberData = member.copy(role = role),
                    updateMask = "role"
                )
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    val members = it.space.members[space].orEmpty().map { m ->
                        if (m.name == updated.name) updated else m
                    }
                    it.copy(space = it.space.copy(members = it.space.members + (space to members)))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "update member failed")
            }
        }
    }

    fun removeMember(member: SpaceMember, onError: (String) -> Unit = {}) {
        val context = session.current ?: return
        val space = member.name.substringBeforeLast("/members/", "")
        session.readScope.launch {
            try {
                context.api.deleteSpaceMember(member.name)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    val members = it.space.members[space].orEmpty().filterNot { m -> m.name == member.name }
                    it.copy(space = it.space.copy(members = it.space.members + (space to members)))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "remove member failed")
            }
        }
    }

    fun loadInvitations(space: Space) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                val response = context.api.listSpaceInvitations(space.name, pageSize = 100)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(
                        space = it.space.copy(
                            invitations = it.space.invitations + (space.name to response.spaceInvitations.orEmpty())
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun invite(space: Space, username: String, role: SpaceRole, onError: (String) -> Unit = {}) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                context.api.createSpaceInvitation(
                    space = space.name,
                    spaceInvitation = SpaceInvitation(
                        name = "",
                        invitee = "users/${username.trim().removePrefix("users/")}",
                        role = role
                    )
                )
                loadInvitations(space)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "invite failed")
            }
        }
    }

    fun revokeInvitation(invitation: SpaceInvitation) {
        val context = session.current ?: return
        val space = invitation.name.substringBeforeLast("/invitations/", "")
        session.readScope.launch {
            try {
                context.api.deleteSpaceInvitation(invitation.name)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    val invitations = it.space.invitations[space].orEmpty()
                        .filterNot { i -> i.name == invitation.name }
                    it.copy(space = it.space.copy(invitations = it.space.invitations + (space to invitations)))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun loadIncomingInvitations() {
        val context = session.current ?: return
        val user = state.value.session.currUser?.name ?: return
        session.readScope.launch {
            try {
                val response = context.api.listUserSpaceInvitations(user, pageSize = 100)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(space = it.space.copy(incoming = response.spaceInvitations.orEmpty()))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    fun acceptInvitation(invitation: SpaceInvitation, onError: (String) -> Unit = {}) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                context.api.acceptSpaceInvitation(invitation.name)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(space = it.space.copy(incoming = it.space.incoming.filterNot { i -> i.name == invitation.name }))
                }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error.message ?: "accept invitation failed")
            }
        }
    }

    fun declineInvitation(invitation: SpaceInvitation) {
        val context = session.current ?: return
        session.readScope.launch {
            try {
                context.api.declineSpaceInvitation(invitation.name)
                if (!session.isCurrent(context)) return@launch
                session.update(state, context) {
                    it.copy(space = it.space.copy(incoming = it.space.incoming.filterNot { i -> i.name == invitation.name }))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    /** The server answers 404/405/410 on instances that do not know the endpoint. */
    private fun rejectsSpaceApi(error: Exception): Boolean {
        if (error !is HttpException) return false
        return error.code() == 404 || error.code() == 405 || error.code() == 410 || error.code() == 501
    }
}
