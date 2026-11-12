package org.example.moxia.data.update

import org.example.moxia.BuildConfig
import org.example.moxia.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a check learned, persisted. The About entry and its badge read this instead of the last
 * in-memory result, so the hint survives a process restart and does not flicker when one check
 * happens to fail.
 */
data class KnownUpdate(val versionCode: Int, val versionName: String)

/** Everything the update dialog needs; it never holds Android resources, only facts. */
data class UpdateUiState(
    val visible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val message: UpdateMessage? = null,
)

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data object Checking : UpdatePhase
    data class Available(val info: UpdateInfo) : UpdatePhase
    data class Downloading(val progress: Int?) : UpdatePhase
}

/** Outcomes worth telling the user about, mapped to localized copy by the dialog. */
sealed interface UpdateMessage {
    data object UpToDate : UpdateMessage
    data object InstallSubmitted : UpdateMessage
    data class Failure(val failure: UpdateFailure, val detail: String? = null) : UpdateMessage
}

/**
 * Application-scoped driver of the update flow.
 *
 * It lives outside the ViewModel on purpose: the dialog can be closed while a download keeps
 * running, and the cold-start check has no screen to attach to. The scope is never cancelled, so
 * state survives page recreation the way [UpdateRepository]'s download does.
 */
class UpdateController(
    private val repository: UpdateRepository,
    private val dataStore: DataStoreManager,
    private val localChannel: String = updateChannel(BuildConfig.APPLICATION_ID),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val knownUpdate: StateFlow<KnownUpdate?> =
        dataStore.knownUpdate.stateIn(scope, SharingStarted.Eagerly, null)

    /** False on canary/insider/debug: those rebuild too often to pin a release to, so the UI hides. */
    val supported: Boolean = isSelfUpdateChannel(localChannel)

    private var inFlight: Job? = null

    fun open() = _state.update { it.copy(visible = true) }

    fun dismiss() {
        // The download keeps running in [inFlight]; only the dialog goes away.
        _state.update { it.copy(visible = false) }
    }

    fun check() {
        if (!supported) return
        if (inFlight?.isActive == true) return
        _state.update { it.copy(visible = true, phase = UpdatePhase.Checking, message = null) }
        inFlight = scope.launch {
            repository.check().fold(
                onSuccess = { result ->
                    _state.update { current ->
                        when (result) {
                            is UpdateCheck.Available -> current.copy(
                                phase = UpdatePhase.Available(result.info),
                                message = null,
                            )

                            UpdateCheck.UpToDate -> current.copy(
                                phase = UpdatePhase.Idle,
                                message = UpdateMessage.UpToDate,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            phase = UpdatePhase.Idle,
                            message = UpdateMessage.Failure(error.asFailure(), error.message),
                        )
                    }
                },
            )
        }
    }

    /** Downloads, then hands the package straight to the system installer. */
    fun downloadAndInstall() {
        val info = (_state.value.phase as? UpdatePhase.Available)?.info ?: return
        if (inFlight?.isActive == true) return
        _state.update { it.copy(phase = UpdatePhase.Downloading(null), message = null) }
        inFlight = scope.launch {
            repository.download(info) { percent ->
                _state.update { it.copy(phase = UpdatePhase.Downloading(percent)) }
            }.fold(
                onSuccess = { apk ->
                    when (val outcome = repository.install(apk)) {
                        InstallOutcome.Submitted -> _state.update {
                            it.copy(phase = UpdatePhase.Idle, message = UpdateMessage.InstallSubmitted)
                        }

                        InstallOutcome.NeedsPermission -> _state.update {
                            it.copy(
                                phase = UpdatePhase.Idle,
                                message = UpdateMessage.Failure(UpdateFailure.INSTALL_PERMISSION),
                            )
                        }

                        is InstallOutcome.Failed -> _state.update {
                            it.copy(
                                phase = UpdatePhase.Idle,
                                message = UpdateMessage.Failure(UpdateFailure.INSTALL, outcome.reason),
                            )
                        }
                    }
                },
                onFailure = { error ->
                    // Back to Available so the retry button stays reachable.
                    _state.update {
                        it.copy(
                            phase = UpdatePhase.Available(info),
                            message = UpdateMessage.Failure(error.asFailure(), error.message),
                        )
                    }
                },
            )
        }
    }

    /**
     * Cold-start check, throttled to one per day and never shown: it only refreshes the persisted
     * "a newer version exists" fact that the About entry reads.
     */
    fun maybeAutoCheck() {
        if (!supported) return
        scope.launch {
            // A fresh install is by definition current; setup must be done first anyway.
            if (dataStore.setupCompleted.first() != true) return@launch
            val lastCheckAt = dataStore.updateLastCheckAt.first()
            if (!shouldAutoCheckUpdate(lastCheckAt, System.currentTimeMillis())) return@launch
            repository.check()
        }
    }

    private fun Throwable.asFailure(): UpdateFailure =
        (this as? UpdateException)?.failure ?: UpdateFailure.NETWORK
}

/** Resolves the app-scoped controller where DI plumbing would only add noise (deep composables). */
object UpdateHub {
    val controller: UpdateController
        get() = org.koin.core.context.GlobalContext.get().get<UpdateController>()
}
