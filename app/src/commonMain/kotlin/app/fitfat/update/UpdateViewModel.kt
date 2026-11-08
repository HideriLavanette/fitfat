package app.fitfat.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.BuildValues
import app.fitfat.platform.Platform
import app.fitfat.platform.Prefs
import app.fitfat.time.nowMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Checking(val silent: Boolean) : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Ready(val update: AppUpdate, val file: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class UpdateViewModel : ViewModel() {
    private val prefs = Prefs("updates")
    private val repository = UpdateRepository(BuildValues.UPDATE_REPO)
    private val installer = UpdateInstaller()
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    var betaChannel by mutableStateOf(prefs.getBoolean(KEY_BETA, false))
        private set

    fun setBeta(enabled: Boolean) {
        betaChannel = enabled
        prefs.edit { putBoolean(KEY_BETA, enabled) }
    }

    fun autoCheck() {
        if (!Platform.supportsSelfUpdate) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (nowMillis() - last < CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    fun check(silent: Boolean) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking(silent)
        viewModelScope.launch {
            runCatching { repository.latest(betaChannel) }
                .onSuccess { update ->
                    prefs.edit { putLong(KEY_LAST_CHECK, nowMillis()) }
                    val skipped = prefs.getInt(KEY_SKIPPED, -1)
                    val isNewer = update != null && update.versionCode > Platform.versionCode
                    _state.value = when {
                        isNewer && !(silent && update!!.versionCode == skipped) -> UpdateState.Available(update!!)
                        silent -> UpdateState.Idle
                        else -> UpdateState.UpToDate
                    }
                }
                .onFailure { e ->
                    _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Нет соединения")
                }
        }
    }

    fun download(update: AppUpdate) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.value = UpdateState.Downloading(update, 0f)
            installer.download(update)
                .catch { e -> _state.value = UpdateState.Failed(e.message ?: "Ошибка загрузки") }
                .collect { ev ->
                    _state.value = when (ev) {
                        is DownloadEvent.Progress -> UpdateState.Downloading(update, ev.fraction)
                        is DownloadEvent.Done -> UpdateState.Ready(update, ev.file)
                    }
                }
        }
    }

    fun canInstall() = installer.canInstall()
    fun openInstallPermission() = installer.openInstallPermission()
    fun install(file: String) = installer.install(file)

    /** «Позже»: эту версию больше не предлагаем автоматически, но ручная проверка её покажет. */
    fun skip(update: AppUpdate) {
        prefs.edit { putInt(KEY_SKIPPED, update.versionCode) }
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val KEY_BETA = "beta"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SKIPPED = "skipped_code"
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
