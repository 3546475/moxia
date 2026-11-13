package org.example.moxia.ui.component.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.model.Space
import org.example.moxia.model.SpaceInvitation
import org.example.moxia.model.SpaceMember
import org.example.moxia.model.SpaceRole
import org.example.moxia.model.SpaceState
import org.example.moxia.ui.component.ActionSheet
import org.example.moxia.ui.nav.SpaceGlyph
import org.example.moxia.ui.theme.AppButton
import org.example.moxia.ui.theme.AppDialog
import org.example.moxia.ui.theme.AppDropdownMenu
import org.example.moxia.ui.theme.AppListItem
import org.example.moxia.ui.theme.AppTextButton
import org.example.moxia.ui.theme.AppTextField
import org.example.moxia.ui.theme.AppTextFieldShape

/**
 * Space management: the list, the create/edit dialogs, and the per-space sheet that edits the
 * description and icon, moves members between ADMIN and USER and hands out invitations.
 * Everything here is only reachable when the server answers the space endpoints.
 */
@Composable
internal fun SpacesSettingsCard(
    spaces: SpaceState,
    onRefresh: () -> Unit,
    onLoadIncoming: () -> Unit,
    onCreate: (title: String, description: String, icon: String?, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onUpdate: (space: Space, title: String, description: String, icon: String?, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDelete: (space: Space, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onLoadMembers: (Space) -> Unit,
    onUpdateRole: (SpaceMember, SpaceRole) -> Unit,
    onRemoveMember: (SpaceMember) -> Unit,
    onLoadInvitations: (Space) -> Unit,
    onInvite: (Space, String, SpaceRole, (String) -> Unit) -> Unit,
    onRevokeInvitation: (SpaceInvitation) -> Unit,
    onAcceptInvitation: (SpaceInvitation) -> Unit,
    onDeclineInvitation: (SpaceInvitation) -> Unit,
    openCreateOnStart: Boolean = false
) {
    var showCreate by remember { mutableStateOf(openCreateOnStart) }
    var editing by remember { mutableStateOf<Space?>(null) }

    LaunchedEffect(Unit) {
        onRefresh()
        onLoadIncoming()
    }

    if (!spaces.isSupported) {
        // Old instances hide the whole feature; this only shows if the capability turns out to be
        // missing after the screen opened.
        SettingsGroup {
            Text(
                text = stringResource(R.string.space_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    SettingsGroup {
        if (spaces.incoming.isNotEmpty()) {
            GroupLabel(stringResource(R.string.space_incoming))
            spaces.incoming.forEach { invitation ->
                InvitationRow(
                    invitation = invitation,
                    onAccept = { onAcceptInvitation(invitation) },
                    onDecline = { onDeclineInvitation(invitation) }
                )
            }
        }
        GroupLabel(stringResource(R.string.space_title))
        if (spaces.spaces.isEmpty()) {
            Text(
                text = stringResource(R.string.space_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        spaces.spaces.forEach { space ->
            AppListItem(
                title = space.title,
                summary = if (space.memberCount > 0) {
                    stringResource(R.string.space_member_count, space.memberCount)
                } else {
                    null
                },
                leading = { SpaceGlyph(space) },
                onClick = { editing = space }
            )
        }
        AppListItem(
            title = stringResource(R.string.space_create),
            leading = { Icon(Icons.Outlined.Add, contentDescription = null) },
            onClick = { showCreate = true }
        )
    }

    var draftTitle by remember(showCreate) { mutableStateOf("") }
    var draftDescription by remember(showCreate) { mutableStateOf("") }
    var draftIcon by remember(showCreate) { mutableStateOf("") }
    var error by remember(showCreate) { mutableStateOf<String?>(null) }
    if (showCreate) {
        SpaceEditorDialog(
            title = stringResource(R.string.space_create),
            initialTitle = draftTitle,
            initialDescription = draftDescription,
            initialIcon = draftIcon,
            error = error,
            onTitleChange = { draftTitle = it },
            onDescriptionChange = { draftDescription = it },
            onIconChange = { draftIcon = it },
            onDismiss = { showCreate = false },
            onConfirm = {
                onCreate(draftTitle, draftDescription, draftIcon.ifBlank { null }, {
                    showCreate = false
                }, { message -> error = message })
            }
        )
    }

    val editingSpace = editing
    if (editingSpace != null) {
        SpaceDetailSheet(
            space = editingSpace,
            members = spaces.members[editingSpace.name].orEmpty(),
            invitations = spaces.invitations[editingSpace.name].orEmpty(),
            onLoad = {
                onLoadMembers(editingSpace)
                onLoadInvitations(editingSpace)
            },
            onUpdate = onUpdate,
            onDelete = onDelete,
            onUpdateRole = onUpdateRole,
            onRemoveMember = onRemoveMember,
            onInvite = onInvite,
            onRevokeInvitation = onRevokeInvitation,
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun SpaceEditorDialog(
    title: String,
    initialTitle: String,
    initialDescription: String,
    initialIcon: String,
    error: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AppDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        buttons = {
            // Saving is the one action that carries the accent; cancelling stays the quiet fill.
            AppButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                secondary = true
            ) {
                Text(stringResource(R.string.common_cancel))
            }
            AppButton(
                onClick = onConfirm,
                enabled = initialTitle.isNotBlank(),
                modifier = Modifier.weight(2f)
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceIconButton(value = initialIcon, onValueChange = onIconChange)
                AppTextField(
                    value = initialTitle,
                    onValueChange = onTitleChange,
                    label = stringResource(R.string.space_name),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            AppTextField(
                value = initialDescription,
                onValueChange = onDescriptionChange,
                label = stringResource(R.string.space_description),
                minLines = 2
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** The emoji a space is marked with, in the order people reach for them. */
private val SpaceIconChoices = listOf(
    "🏠", "💻", "📚", "🎨", "🎵", "🎮", "🍀", "🌱",
    "🔥", "⭐", "☕", "🐱", "🎧", "📷", "🏀", "✈️"
)

/**
 * The space's icon where the name field ends: a square showing what the list will draw, opening the
 * emoji grid right where it stands, so the choice never costs the name its width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceIconButton(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val emoji = value.trim()
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            shape = AppTextFieldShape(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                .compositeOver(MaterialTheme.colorScheme.surface),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (emoji.isEmpty()) {
                    Icon(Icons.Outlined.Groups, contentDescription = null)
                } else {
                    Text(emoji, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FlowRow(
                modifier = Modifier.widthIn(max = 264.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SpaceIconChoices.forEach { choice ->
                    val selected = emoji == choice
                    Surface(
                        onClick = {
                            onValueChange(if (selected) "" else choice)
                            expanded = false
                        },
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(choice, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceDetailSheet(
    space: Space,
    members: List<SpaceMember>,
    invitations: List<SpaceInvitation>,
    onLoad: () -> Unit,
    onUpdate: (Space, String, String, String?, () -> Unit, (String) -> Unit) -> Unit,
    onDelete: (Space, () -> Unit, (String) -> Unit) -> Unit,
    onUpdateRole: (SpaceMember, SpaceRole) -> Unit,
    onRemoveMember: (SpaceMember) -> Unit,
    onInvite: (Space, String, SpaceRole, (String) -> Unit) -> Unit,
    onRevokeInvitation: (SpaceInvitation) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(space.name) { onLoad() }
    var title by remember(space.name) { mutableStateOf(space.title) }
    var description by remember(space.name) { mutableStateOf(space.description.orEmpty()) }
    var icon by remember(space.name) { mutableStateOf(space.icon?.emoji.orEmpty()) }
    var invitee by remember(space.name) { mutableStateOf("") }
    var error by remember(space.name) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(space.name) { mutableStateOf(false) }
    val isAdmin = space.currentUserRole == SpaceRole.ADMIN
    val dirty = title.trim() != space.title ||
        description.trim() != space.description.orEmpty() ||
        icon.trim() != space.icon?.emoji.orEmpty()

    ActionSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpaceGlyph(space)
            Column(Modifier.weight(1f)) {
                Text(
                    text = space.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.space_member_count, space.memberCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceIconButton(
                    value = icon,
                    onValueChange = { icon = it },
                    enabled = isAdmin
                )
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.space_name),
                    singleLine = true,
                    enabled = isAdmin,
                    modifier = Modifier.weight(1f)
                )
            }
            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.space_description),
                minLines = 2,
                enabled = isAdmin
            )
            if (dirty && isAdmin) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppTextButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            onUpdate(space, title, description, icon.ifBlank { null }, {}, { error = it })
                        }
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        GroupLabel(stringResource(R.string.space_members))
        members.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = member.username,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = roleLabel(member.role),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isAdmin) {
                    AppTextButton(
                        text = if (member.role == SpaceRole.ADMIN) {
                            stringResource(R.string.space_role_user)
                        } else {
                            stringResource(R.string.space_role_admin)
                        },
                        onClick = {
                            onUpdateRole(
                                member,
                                if (member.role == SpaceRole.ADMIN) SpaceRole.USER else SpaceRole.ADMIN
                            )
                        }
                    )
                    AppTextButton(
                        text = stringResource(R.string.common_remove),
                        destructive = true,
                        onClick = { onRemoveMember(member) }
                    )
                }
            }
        }

        if (isAdmin) {
            GroupLabel(stringResource(R.string.space_invitations))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextField(
                    value = invitee,
                    onValueChange = { invitee = it },
                    label = stringResource(R.string.space_invite_username),
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppTextButton(
                        text = stringResource(R.string.space_invite),
                        enabled = invitee.isNotBlank(),
                        onClick = {
                            onInvite(space, invitee, SpaceRole.USER) { message ->
                                error = message
                            }
                            invitee = ""
                        }
                    )
                }
            }
            invitations.forEach { invitation ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = invitation.username,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = roleLabel(invitation.role),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextButton(
                        text = stringResource(R.string.space_revoke),
                        onClick = { onRevokeInvitation(invitation) }
                    )
                }
            }
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (confirmDelete) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTextButton(
                            text = stringResource(R.string.common_cancel),
                            onClick = { confirmDelete = false }
                        )
                        AppTextButton(
                            text = stringResource(R.string.space_delete),
                            destructive = true,
                            modifier = Modifier.weight(1f),
                            onClick = { onDelete(space, onDismiss, { error = it }) }
                        )
                    }
                } else {
                    AppTextButton(
                        text = stringResource(R.string.space_delete),
                        destructive = true,
                        onClick = { confirmDelete = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationRow(
    invitation: SpaceInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = invitation.name.substringAfterLast('/').ifEmpty { invitation.username },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AppTextButton(text = stringResource(R.string.space_decline), onClick = onDecline)
        AppTextButton(text = stringResource(R.string.space_accept), onClick = onAccept)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun roleLabel(role: SpaceRole): String = when (role) {
    SpaceRole.ADMIN -> stringResource(R.string.space_role_admin)
    SpaceRole.USER -> stringResource(R.string.space_role_user)
    SpaceRole.ROLE_UNSPECIFIED -> stringResource(R.string.space_role_user)
}
