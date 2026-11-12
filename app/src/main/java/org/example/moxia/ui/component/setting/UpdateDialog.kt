package org.example.moxia.ui.component.setting

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.data.update.UpdateFailure
import org.example.moxia.data.update.UpdateMessage
import org.example.moxia.data.update.UpdatePhase
import org.example.moxia.data.update.UpdateUiState
import org.example.moxia.ui.theme.AppDialog
import org.example.moxia.ui.theme.AppLinearProgressIndicator
import org.example.moxia.ui.theme.AppTextButton

/**
 * The single screen of the update flow: check, describe, download, hand over to the installer.
 *
 * It renders [UpdateUiState] and nothing else — every decision (which source, newer or not,
 * verified or not) stays in the controller, so the copy here can change without touching logic.
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val permissionFailure = (state.message as? UpdateMessage.Failure)
        ?.takeIf { it.failure == UpdateFailure.INSTALL_PERMISSION }

    AppDialog(
        show = true,
        title = stringResource(R.string.update_dialog_title),
        onDismissRequest = onDismiss,
        buttons = {
            when {
                state.phase is UpdatePhase.Available -> {
                    AppTextButton(
                        text = stringResource(R.string.update_action_later),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    AppTextButton(
                        text = stringResource(R.string.update_action_download),
                        modifier = Modifier.weight(1f),
                        onClick = onDownload,
                    )
                }

                permissionFailure != null -> {
                    AppTextButton(
                        text = stringResource(R.string.common_cancel),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    AppTextButton(
                        text = stringResource(R.string.update_action_settings),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismiss()
                            openInstallPermissionSettings(context, context.packageName)
                        },
                    )
                }

                state.phase is UpdatePhase.Checking || state.phase is UpdatePhase.Downloading -> {
                    AppTextButton(
                        text = stringResource(R.string.common_close),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                }

                else -> {
                    AppTextButton(
                        text = stringResource(R.string.common_close),
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    AppTextButton(
                        text = stringResource(R.string.update_action_retry),
                        modifier = Modifier.weight(1f),
                        onClick = onCheck,
                    )
                }
            }
        },
    ) {
        UpdateDialogBody(state)
    }
}

@Composable
private fun UpdateDialogBody(state: UpdateUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (val phase = state.phase) {
            UpdatePhase.Idle -> Unit

            UpdatePhase.Checking -> {
                Text(stringResource(R.string.update_checking))
                AppLinearProgressIndicator(
                    progress = null,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            is UpdatePhase.Available -> {
                Text(
                    text = stringResource(
                        R.string.update_available,
                        phase.info.versionName ?: phase.info.versionCode.toString(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                val notes = phase.info.releaseNotes?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.update_notes_fallback)
                // Release notes are author-written text, not Markdown-rendered content: a plain
                // scrollable block keeps a long changelog from pushing the buttons off screen.
                Text(
                    text = notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is UpdatePhase.Downloading -> {
                Text(
                    text = phase.progress?.let {
                        stringResource(R.string.update_downloading_progress, it)
                    } ?: stringResource(R.string.update_downloading),
                )
                AppLinearProgressIndicator(
                    progress = phase.progress?.let { it / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }

        val message = state.message
        if (message != null) {
            Text(
                text = message.describe(),
                modifier = Modifier.padding(top = if (state.phase is UpdatePhase.Idle) 0.dp else 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UpdateMessage.describe(): String = when (this) {
    UpdateMessage.UpToDate -> stringResource(R.string.update_up_to_date)
    UpdateMessage.InstallSubmitted -> stringResource(R.string.update_install_submitted)
    is UpdateMessage.Failure -> when (failure) {
        UpdateFailure.NETWORK -> stringResource(R.string.update_failure_network)
        UpdateFailure.MANIFEST -> stringResource(R.string.update_failure_manifest)
        UpdateFailure.NO_APK_FOR_DEVICE -> stringResource(R.string.update_failure_no_apk)
        UpdateFailure.DOWNLOAD -> stringResource(R.string.update_failure_download)
        UpdateFailure.VERIFY -> stringResource(R.string.update_failure_verify)
        UpdateFailure.INSTALL_PERMISSION -> stringResource(R.string.update_failure_install_permission)
        UpdateFailure.INSTALL -> stringResource(R.string.update_failure_install)
    }
}

/**
 * Sends the user to the per-app "install unknown apps" switch. Some ROMs replace that page, so a
 * plain app-details fallback keeps the tap from doing nothing at all.
 */
private fun openInstallPermissionSettings(context: android.content.Context, packageName: String) {
    val app = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val details = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(app)
    } catch (_: Exception) {
        try {
            context.startActivity(details)
        } catch (_: Exception) {
            android.util.Log.w("UpdateDialog", "No settings page accepted the install-permission intent")
        }
    }
}
