package org.example.moxia.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.moxia.R
import org.example.moxia.data.update.UpdateHub
import org.example.moxia.data.update.UpdatePhase
import org.example.moxia.ui.component.setting.SettingsGroup
import org.example.moxia.ui.component.setting.UpdateDialog
import org.example.moxia.ui.component.rememberPageOverscroll
import org.example.moxia.ui.theme.AppListItem
import org.example.moxia.ui.theme.navigationBarBottom

enum class SettingsSection(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    GENERAL(R.string.settings_group_general, Icons.Outlined.Settings),
    APPEARANCE(R.string.settings_appearance, Icons.Outlined.Palette),
    EDITOR(R.string.settings_editor, Icons.Outlined.Code),
    NAVIGATION(R.string.settings_navigation, Icons.Outlined.ViewAgenda),
    SPACES(R.string.space_title, Icons.Outlined.Groups),
    SHORTCUTS(R.string.profile_shortcuts, Icons.Outlined.Bookmarks),
    WEBHOOKS(R.string.profile_webhooks, Icons.Outlined.Link),
    OFFLINE(R.string.offline_settings_title, Icons.Outlined.CloudDownload),
    RECOVERY(R.string.recovery_title, Icons.Outlined.ImportExport),
    AUDIT(R.string.audit_log_title, Icons.Outlined.History),
    ABOUT(R.string.profile_about, Icons.Outlined.Info),
    INSTANCE(R.string.profile_instance_info, Icons.Outlined.Dns)
}

@Composable
internal fun SettingsOverview(
    onOpenSection: (SettingsSection) -> Unit,
    showSpaces: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Every section is listed here: the profile page used to link four of them directly, which
    // gave the same screen two homes and left the remaining five reachable only from there.
    // Spaces only exist on servers that answer the space endpoints.
    val groups = listOf(
        listOf(
            SettingsSection.GENERAL,
            SettingsSection.APPEARANCE,
            SettingsSection.EDITOR,
            SettingsSection.NAVIGATION
        ),
        listOfNotNull(
            SettingsSection.SPACES.takeIf { showSpaces },
            SettingsSection.SHORTCUTS,
            SettingsSection.WEBHOOKS,
            SettingsSection.INSTANCE
        ),
        listOf(SettingsSection.OFFLINE, SettingsSection.RECOVERY, SettingsSection.AUDIT),
        listOf(SettingsSection.ABOUT)
    )
    val listState = rememberLazyListState()
    val overscroll = rememberPageOverscroll()
    LazyColumn(
        state = listState,
        overscrollEffect = overscroll,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 12.dp + navigationBarBottom()
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groups.forEach { group ->
            item(key = group.first().name) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    SettingsGroup {
                        group.forEach { section ->
                            // The update row sits right above About: it is the one row on this
                            // page that acts instead of navigating.
                            if (section == SettingsSection.ABOUT) {
                                UpdateOverviewRow()
                            }
                            AppListItem(
                                title = stringResource(section.titleRes),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                                leading = {
                                    // Same treatment as the profile rows: a plain icon, not a
                                    // filled circle, which read as a colour block in Miuix.
                                    Icon(section.icon, contentDescription = null)
                                },
                                onClick = { onOpenSection(section) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The self-update row on the settings home list.
 *
 * It wears the clothes of its neighbours: the same list item, the same plain leading icon, and a
 * summary only when there is something to say. The controller owns the flow, so the row can also
 * reopen a download that is still running instead of starting a new check.
 */
@Composable
private fun UpdateOverviewRow() {
    // Null when the app graph is not up (previews, UI tests): the row simply is not there.
    val controller = remember { runCatching { UpdateHub.controller }.getOrNull() }
    if (controller == null || !controller.supported) return
    val state by controller.state.collectAsStateWithLifecycle()
    val knownUpdate by controller.knownUpdate.collectAsStateWithLifecycle()
    AppListItem(
        title = stringResource(R.string.update_row_title),
        summary = knownUpdate?.let { stringResource(R.string.update_row_summary_new, it.versionName) },
        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
        leading = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        onClick = {
            if (state.phase is UpdatePhase.Downloading) {
                controller.open()
            } else {
                controller.check()
            }
        },
    )
    if (state.visible) {
        UpdateDialog(
            state = state,
            onCheck = controller::check,
            onDownload = controller::downloadAndInstall,
            onDismiss = controller::dismiss,
        )
    }
}
