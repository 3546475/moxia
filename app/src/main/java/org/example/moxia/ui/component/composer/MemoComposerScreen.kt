package org.example.moxia.ui.component.composer

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import org.example.moxia.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.example.moxia.ui.component.ProfileBackButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.moxia.R
import org.example.moxia.model.Memo
import org.example.moxia.viewmodel.MemosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoComposerScreen(
    onDismiss: () -> Unit,
    viewModel: MemosViewModel,
    hostUrl: String,
    title: String,
    initialMemo: Memo? = null,
    parentMemo: Memo? = null,
    editorSession: EditorSession,
    onSubmitted: () -> Unit,
    mode: ComposerMode = when {
        initialMemo != null -> ComposerMode.UPDATE
        parentMemo != null -> ComposerMode.COMMENT
        else -> ComposerMode.PUBLISH
    }
) {
    val uiState by viewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                navigationIcon = {
                    ProfileBackButton(onClick = onDismiss)
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Neither the gesture bar nor the keyboard inset is applied here. Both are consumed
            // inside the composer: the text pads for the keyboard, the action bar pads for whichever
            // of the two is larger. Doing it in one place keeps the bar from jumping when the
            // keyboard closes (it used to drop below its resting place and snap back).
            contentAlignment = Alignment.TopCenter
        ) {
            androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                if (editorSession.submitFailed || editorSession.saveFailed) {
                    Text(stringResource(R.string.common_operation_failed),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp))
                }
                MemoComposer(
                    onPublish = { content, visibility, attachments, location, space ->
                        scope.launch {
                            if (!editorSession.beginSubmission()) return@launch
                            val failed: () -> Unit = {
                                editorSession.submissionFailed()
                            }
                            if (!editorSession.prepareSubmission()) {
                                failed()
                                return@launch
                            }
                            when {
                                initialMemo != null -> viewModel.memoActionDelegate.updateMemo(
                                    initialMemo, content, visibility, attachments, location,
                                    space = space,
                                    linkedMemoNames = editorSession.state.linkedMemoNames.value,
                                    onError = failed, onSuccess = onSubmitted)
                                parentMemo != null -> viewModel.memoActionDelegate.createComment(
                                    parentMemo, content, onError = failed, onSuccess = onSubmitted)
                                else -> viewModel.memoActionDelegate.createMemo(content, visibility,
                                    attachments, location,
                                    space = space,
                                    memoId = java.util.UUID.nameUUIDFromBytes(editorSession.request.draftId.toByteArray()).toString(),
                                    linkedMemoNames = editorSession.state.linkedMemoNames.value,
                                    onError = failed, onSuccess = onSubmitted)
                            }
                        }
                    },
                    onUploadFile = { uri, context ->
                        viewModel.memoActionDelegate.uploadAttachment(uri, context)
                    },
                    onQueueUpload = { uri, context ->
                        viewModel.memoActionDelegate.queueAttachment(uri, context)
                    },
                    availableTags = uiState.session.userStats?.tagCount ?: emptyMap(),
                    token = uiState.session.token,
                    hostUrl = hostUrl,
                    isPosting = uiState.isPosting || editorSession.busy,
                    editorState = editorSession.state,
                    linkableMemos = uiState.userMemoList.list.items,
                    spaces = uiState.space.spaces,
                    mode = mode,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    onDiscardQueuedUpload = { clientId ->
                        viewModel.memoActionDelegate.discardQueuedUploadIfOrphaned(clientId)
                    }
                )
            }
        }
    }
}
