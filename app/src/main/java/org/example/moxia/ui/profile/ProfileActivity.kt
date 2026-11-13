package org.example.moxia.ui.profile

import android.os.Bundle
import android.content.Intent
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.moxia.data.DataStoreManager
import org.example.moxia.model.MemoContentType
import org.example.moxia.ui.component.ArchivedMemosScreen
import org.example.moxia.ui.component.ConflictDialog
import org.example.moxia.ui.component.LocalNetworkPermission
import org.example.moxia.ui.component.MemoTypeMemosScreen
import org.example.moxia.ui.component.NotificationsScreen
import org.example.moxia.ui.component.item.markdown.LinkPreviewEnvironment
import org.example.moxia.ui.component.item.markdown.LocalLinkPreviews
import org.example.moxia.ui.component.item.markdown.LocalMarkdownFormat
import org.example.moxia.ui.component.item.markdown.MarkdownFormat
import org.example.moxia.ui.component.item.media.AccountMediaIdentity
import org.example.moxia.ui.component.item.media.LocalAccountMediaIdentity
import org.example.moxia.ui.component.rememberLocalNetworkPermission
import org.example.moxia.ui.nav.SettingsScreen
import org.example.moxia.ui.nav.SettingsSection
import org.example.moxia.ui.setup.SetupActivity
import org.example.moxia.ui.theme.AppCircularProgressIndicator
import org.example.moxia.ui.theme.SavedMoxiaTheme
import org.example.moxia.ui.theme.ProfileTheme
import org.example.moxia.viewmodel.MemosViewModel
import org.koin.android.ext.android.inject

/** Each destination owns its UI lifetime; persisted repositories carry changes between activities. */
abstract class ProfileActivity : ComponentActivity() {
    protected open val requiredAccountId: String? = null
    private val dataStore: DataStoreManager by inject()
    private val viewModel: MemosViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SavedMoxiaTheme(dataStore) {
                ProfileTheme {
                    val permission = rememberLocalNetworkPermission()
                    val accounts by dataStore.accounts.collectAsStateWithLifecycle(initialValue = null)
                    val active = accounts?.firstOrNull { it.isActive }
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val sessionReady by viewModel.sessionReady.collectAsStateWithLifecycle()
                    val previews by viewModel.linkPreviews.collectAsStateWithLifecycle()
                    val previewEnvironment = remember(previews, uiState.appSettings.linkPreviewEnabled, uiState.isOnline) {
                        previews?.takeIf { uiState.appSettings.linkPreviewEnabled }
                            ?.let { LinkPreviewEnvironment(it, uiState.isOnline) }
                    }

                    LaunchedEffect(active?.id, active?.hostUrl, active?.accessToken) {
                        if (accounts != null) {
                            if (active == null || (requiredAccountId != null && active.id != requiredAccountId)) finish()
                            else {
                                permission.ensureAccess(active.hostUrl)
                                // Every overlay used to re-read the account list and refresh the
                                // user on open; only do it when this instance is out of sync.
                                val state = viewModel.uiState.value
                                val bound = state.accounts.firstOrNull { it.isActive }
                                val outOfSync = bound?.id != active.id ||
                                    state.session.hostUrl != active.hostUrl ||
                                    state.session.token != active.accessToken
                                if (outOfSync) viewModel.userDelegate.updateCurrentAccountInList()
                            }
                        }
                    }
                    var previouslyGranted by remember { mutableStateOf(permission.granted) }
                    LaunchedEffect(permission.granted) {
                        if (permission.granted && !previouslyGranted) viewModel.retryAccountConnection()
                        previouslyGranted = permission.granted
                    }
                    val boundAccount = uiState.accounts.firstOrNull { it.isActive }
                    val accountMatches = active != null && (requiredAccountId == null || active.id == requiredAccountId) && boundAccount?.id == active.id &&
                        uiState.session.hostUrl == active.hostUrl && uiState.session.token == active.accessToken

                    CompositionLocalProvider(
                        LocalNetworkPermission provides permission,
                        LocalAccountMediaIdentity provides active?.let {
                            AccountMediaIdentity(it.id, uiState.accountGeneration)
                        },
                        LocalLinkPreviews provides previewEnvironment,
                        LocalMarkdownFormat provides remember(
                            uiState.appSettings.codeLineNumbers,
                            uiState.appSettings.codeWrapLines,
                            uiState.appSettings.paragraphIndent,
                            uiState.appSettings.cjkSpacing
                        ) {
                            MarkdownFormat(
                                codeLineNumbers = uiState.appSettings.codeLineNumbers,
                                codeWrapLines = uiState.appSettings.codeWrapLines,
                                paragraphIndent = uiState.appSettings.paragraphIndent,
                                cjkSpacing = uiState.appSettings.cjkSpacing
                            )
                        }
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            if (!sessionReady || !accountMatches) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    AppCircularProgressIndicator()
                                }
                            } else {
                                key(uiState.accountGeneration) {
                                    Destination(viewModel) { onBackPressedDispatcher.onBackPressed() }
                                    uiState.conflict?.let { conflict ->
                                        ConflictDialog(
                                            conflict = conflict,
                                            onResolve = { resolution, merged -> viewModel.resolveConflict(resolution, merged) },
                                            onDismiss = viewModel::dismissConflict
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
    }

    @Composable
    protected abstract fun Destination(viewModel: MemosViewModel, onBack: () -> Unit)
}

class NotificationsActivity : ProfileActivity() {
    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        NotificationsScreen(viewModel = viewModel, onBack = onBack)
    }
}

class ArchivedMemosActivity : ProfileActivity() {
    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        ArchivedMemosScreen(viewModel = viewModel, onBack = onBack)
    }
}

/** The memos of one kind (links, code, todos), filtered on this device. */
class MemoTypeMemosActivity : ProfileActivity() {
    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        val type = MemoContentType.entries
            .firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) }
            ?: MemoContentType.LINK
        MemoTypeMemosScreen(viewModel = viewModel, type = type, onBack = onBack)
    }

    companion object {
        private const val EXTRA_TYPE = "memo_content_type"

        fun createIntent(context: Context, type: MemoContentType): Intent =
            Intent(context, MemoTypeMemosActivity::class.java).putExtra(EXTRA_TYPE, type.name)
    }
}

class SettingsActivity : ProfileActivity() {
    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        val section = SettingsSection.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SECTION) }
        val openSpaceCreate = intent.getBooleanExtra(EXTRA_CREATE_SPACE, false)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshAfterProfileDetails()
        }
        SettingsScreen(
            viewModel = viewModel,
            onBack = onBack,
            section = section,
            openSpaceCreate = openSpaceCreate,
            onOpenLicenses = {
                launcher.launch(Intent(this, OpenSourceLicensesActivity::class.java))
            },
            onShowSetup = {
                launcher.launch(SetupActivity.createIntent(this, fromSettings = true))
            },
            onOpenSection = { destination ->
                launcher.launch(createIntent(this, destination))
            }
        )    }

    companion object {
        private const val EXTRA_SECTION = "settings_section"
        private const val EXTRA_CREATE_SPACE = "settings_create_space"

        fun createIntent(context: Context, section: SettingsSection): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_SECTION, section.name)

        fun spacesIntent(context: Context, create: Boolean): Intent =
            createIntent(context, SettingsSection.SPACES).putExtra(EXTRA_CREATE_SPACE, create)
    }
}

class OpenSourceLicensesActivity : ProfileActivity() {
    @Composable
    override fun Destination(viewModel: MemosViewModel, onBack: () -> Unit) {
        org.example.moxia.ui.nav.OpenSourceLicensesScreen(onBack)
    }
}
