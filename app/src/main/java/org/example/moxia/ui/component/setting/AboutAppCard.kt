package org.example.moxia.ui.component.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import org.example.moxia.BuildConfig
import org.example.moxia.R
import org.example.moxia.data.update.UpdateController
import org.example.moxia.data.update.UpdatePhase
import org.example.moxia.ui.theme.AppListItem

private data class KaomojiMessage(val text: String, val kaomoji: String)

@Suppress("LocalContextGetResourceValueCall")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AboutAppCard(
    onOpenLicenses: () -> Unit,
    onOpenLogs: () -> Unit,
    onShowSetup: () -> Unit,
    // Optional so previews and UI tests can render the card without a Koin graph; the settings
    // screen always passes the application-scoped controller.
    updateController: UpdateController? = null,
) {
    val context = LocalContext.current
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: stringResource(R.string.common_not_available)
    val versionLabel = stringResource(R.string.profile_about_version)
    val versionCopiedMessage = stringResource(R.string.profile_about_version_copied)

    val kaomojisArray = stringArrayResource(R.array.profile_about_kaomojis)
    val kaomojis = remember(kaomojisArray) {
        kaomojisArray.mapNotNull { item ->
            val parts = item.split("|")
            if (parts.size == 2) {
                KaomojiMessage(parts[0], parts[1])
            } else {
                null
            }
        }
    }

    val currentToast = remember { mutableStateOf<Toast?>(null) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var showMemoCatch by rememberSaveable { mutableStateOf(false) }

    if (showMemoCatch) {
        MemoCatchDialog(onDismiss = { showMemoCatch = false })
    }

    DisposableEffect(Unit) {
        onDispose {
            currentToast.value?.cancel()
        }
    }

    // Two cards: what this build is, and where to go when something is wrong. Both used to be a
    // single list where the version string sat next to the issue tracker.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsLabel(stringResource(R.string.profile_about_build_info))
            SettingsGroup {
                AppListItem(
                    title = stringResource(R.string.profile_about_version),
                    summary = versionName,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(onClick = {
                            currentToast.value?.cancel()
                            versionTaps++
                            if (versionTaps == 10) {
                                versionTaps = 0
                                showMemoCatch = true
                            } else kaomojis.randomOrNull()?.let { item ->
                                val toast = Toast.makeText(
                                    context, "${item.text} ${item.kaomoji}", Toast.LENGTH_SHORT
                                )
                                currentToast.value = toast
                                toast.show()
                            }
                        }, onLongClick = {
                            copyToClipboard(context, versionLabel, versionName, versionCopiedMessage)
                                .also { currentToast.value = it }
                        })
                )

                val gitHashCopiedMessage = stringResource(R.string.profile_about_git_hash_copied)
                AppListItem(
                    title = stringResource(R.string.profile_about_git_hash),
                    summary = BuildConfig.GIT_HASH,
                    // Same affordance as the version above: tap or long press copies. A commit hash
                    // is exactly the kind of thing that gets pasted into a bug report.
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = {
                                copyToClipboard(
                                    context,
                                    BuildConfig.GIT_HASH,
                                    BuildConfig.GIT_HASH,
                                    gitHashCopiedMessage
                                ).also { currentToast.value = it }
                            },
                            onLongClick = {
                                copyToClipboard(
                                    context,
                                    BuildConfig.GIT_HASH,
                                    BuildConfig.GIT_HASH,
                                    gitHashCopiedMessage
                                ).also { currentToast.value = it }
                            }
                        )
                )

                // Self-update lives here because this is where a user looks for it: the row both
                // starts a check and carries the persisted "a newer version exists" hint, so the
                // badge survives a restart instead of depending on this session's last check.
                if (updateController != null && updateController.supported) {
                    val updateState by updateController.state.collectAsState()
                    val knownUpdate by updateController.knownUpdate.collectAsState()
                    SettingsNavigationRow(
                        title = stringResource(R.string.update_row_title),
                        summary = knownUpdate?.let {
                            stringResource(R.string.update_row_summary_new, it.versionName)
                        } ?: stringResource(R.string.update_row_summary_idle),
                        icon = Icons.Outlined.SystemUpdate,
                        // Reopening while a download runs must show the progress, not restart it.
                        onClick = {
                            if (updateState.phase is UpdatePhase.Downloading) {
                                updateController.open()
                            } else {
                                updateController.check()
                            }
                        },
                    )
                    if (updateState.visible) {
                        UpdateDialog(
                            state = updateState,
                            onCheck = updateController::check,
                            onDownload = updateController::downloadAndInstall,
                            onDismiss = updateController::dismiss,
                        )
                    }
                }

                val repoUrl = stringResource(R.string.profile_about_repo_url)
                val issuesUrl = stringResource(R.string.profile_about_issues_url)

                AppListItem(
                    title = stringResource(R.string.profile_about_repo),
                    leading = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null
                        )
                    },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, repoUrl.toUri())
                        context.startActivity(intent)
                    }
                )

                AppListItem(
                    title = stringResource(R.string.profile_about_issues),
                    leading = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null
                        )
                    },
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, issuesUrl.toUri())
                        context.startActivity(intent)
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsLabel(stringResource(R.string.profile_about_support))
            SettingsGroup {
                SettingsNavigationRow(
                    title = stringResource(R.string.setup_show),
                    icon = Icons.Outlined.WavingHand,
                    onClick = onShowSetup
                )

                SettingsNavigationRow(
                    title = stringResource(R.string.audit_log_title),
                    icon = Icons.Outlined.History,
                    onClick = onOpenLogs
                )

                AppListItem(
                    title = stringResource(R.string.profile_about_licenses),
                    leading = { Icon(Icons.Outlined.Balance, contentDescription = null) },
                    onClick = onOpenLicenses
                )
            }
        }
    }
}

/** Copies [value] and returns the toast that reports it, so the caller keeps one shared toast. */
private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    message: String
): Toast {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
    toast.show()
    return toast
}
