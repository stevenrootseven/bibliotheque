package com.stevenroot.mabibliotheque.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.remote.SmbClient
import com.stevenroot.mabibliotheque.data.remote.SmbEntry
import com.stevenroot.mabibliotheque.data.repository.DigitalLibraryScanner
import com.stevenroot.mabibliotheque.data.repository.DigitalScanProgress
import com.stevenroot.mabibliotheque.data.repository.DigitalScanResult
import com.stevenroot.mabibliotheque.data.repository.NetworkShareConfig
import com.stevenroot.mabibliotheque.data.repository.NetworkShareSettingsRepository
import com.stevenroot.mabibliotheque.data.repository.SeriesScanProgress
import com.stevenroot.mabibliotheque.data.repository.SeriesScanResult
import com.stevenroot.mabibliotheque.data.repository.SeriesScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data object Success : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

sealed interface DigitalScanUiState {
    data object Idle : DigitalScanUiState
    data class Running(val progress: DigitalScanProgress) : DigitalScanUiState
    data class Done(val result: DigitalScanResult) : DigitalScanUiState
    data class Failed(val message: String) : DigitalScanUiState
}

sealed interface SeriesScanUiState {
    data object Idle : SeriesScanUiState
    data class Running(val progress: SeriesScanProgress) : SeriesScanUiState
    data class Done(val result: SeriesScanResult) : SeriesScanUiState
    data class Failed(val message: String) : SeriesScanUiState
}

enum class FolderBrowserMode { MOVIES, SERIES }

class NetworkShareSettingsViewModel(
    private val settingsRepository: NetworkShareSettingsRepository,
    private val smbClient: SmbClient,
    private val digitalLibraryScanner: DigitalLibraryScanner,
    private val seriesScanner: SeriesScanner,
) : ViewModel() {

    private val _form = MutableStateFlow(settingsRepository.config.value)
    val form: StateFlow<NetworkShareConfig> = _form.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    // Folder browser state
    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _entries = MutableStateFlow<List<SmbEntry>>(emptyList())
    val entries: StateFlow<List<SmbEntry>> = _entries.asStateFlow()

    private val _browsing = MutableStateFlow(false)
    val browsing: StateFlow<Boolean> = _browsing.asStateFlow()

    private val _browseError = MutableStateFlow<String?>(null)
    val browseError: StateFlow<String?> = _browseError.asStateFlow()

    private val _browserMode = MutableStateFlow(FolderBrowserMode.MOVIES)
    val browserMode: StateFlow<FolderBrowserMode> = _browserMode.asStateFlow()

    private val _scanState = MutableStateFlow<DigitalScanUiState>(DigitalScanUiState.Idle)
    val scanState: StateFlow<DigitalScanUiState> = _scanState.asStateFlow()

    private val _retryUnmatchedState = MutableStateFlow<DigitalScanUiState>(DigitalScanUiState.Idle)
    val retryUnmatchedState: StateFlow<DigitalScanUiState> = _retryUnmatchedState.asStateFlow()

    private val _seriesScanState = MutableStateFlow<SeriesScanUiState>(SeriesScanUiState.Idle)
    val seriesScanState: StateFlow<SeriesScanUiState> = _seriesScanState.asStateFlow()

    fun updateHost(value: String) = updateForm { it.copy(host = value) }
    fun updateShare(value: String) = updateForm { it.copy(shareName = value) }
    fun updateUsername(value: String) = updateForm { it.copy(username = value) }
    fun updatePassword(value: String) = updateForm { it.copy(password = value) }

    private inline fun updateForm(transform: (NetworkShareConfig) -> NetworkShareConfig) {
        _form.value = transform(_form.value)
        _testState.value = ConnectionTestState.Idle
    }

    fun saveCredentials() {
        settingsRepository.save(_form.value)
    }

    fun testConnection() = viewModelScope.launch {
        _testState.value = ConnectionTestState.Testing
        settingsRepository.save(_form.value)
        smbClient.testConnection(_form.value).fold(
            onSuccess = { _testState.value = ConnectionTestState.Success },
            onFailure = { _testState.value = ConnectionTestState.Failure(it.message ?: "Connexion impossible") },
        )
    }

    fun openFolder(path: String, mode: FolderBrowserMode = _browserMode.value) = viewModelScope.launch {
        _browserMode.value = mode
        _browsing.value = true
        _browseError.value = null
        smbClient.listDirectory(_form.value, path).fold(
            onSuccess = { list ->
                _currentPath.value = path
                _entries.value = list.filter { it.isDirectory }
            },
            onFailure = { _browseError.value = it.message ?: "Erreur de lecture du dossier" },
        )
        _browsing.value = false
    }

    fun navigateUp() {
        val parent = _currentPath.value.substringBeforeLast('/', "")
        openFolder(parent)
    }

    fun toggleFolderIncluded(path: String) {
        val current = _form.value.includedFolders
        val updated = if (path in current) current - path else current + path
        updateForm { it.copy(includedFolders = updated) }
        settingsRepository.save(_form.value)
    }

    fun useSeriesFolder(path: String) {
        updateForm { it.copy(seriesFolder = path) }
        settingsRepository.save(_form.value)
    }

    private var scanJob: Job? = null

    fun rescan() {
        scanJob = viewModelScope.launch {
            _scanState.value = DigitalScanUiState.Running(DigitalScanProgress("Démarrage…", 0, 0))
            digitalLibraryScanner.scan(_form.value) { progress ->
                _scanState.value = DigitalScanUiState.Running(progress)
            }.fold(
                onSuccess = { _scanState.value = DigitalScanUiState.Done(it) },
                onFailure = { _scanState.value = DigitalScanUiState.Failed(it.message ?: "Échec du scan") },
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = DigitalScanUiState.Idle
    }

    private var retryUnmatchedJob: Job? = null

    fun retryUnmatched() {
        retryUnmatchedJob = viewModelScope.launch {
            _retryUnmatchedState.value = DigitalScanUiState.Running(DigitalScanProgress("Démarrage…", 0, 0))
            digitalLibraryScanner.retryUnmatched { progress ->
                _retryUnmatchedState.value = DigitalScanUiState.Running(progress)
            }.fold(
                onSuccess = { _retryUnmatchedState.value = DigitalScanUiState.Done(it) },
                onFailure = { _retryUnmatchedState.value = DigitalScanUiState.Failed(it.message ?: "Échec") },
            )
        }
    }

    fun cancelRetryUnmatched() {
        retryUnmatchedJob?.cancel()
        _retryUnmatchedState.value = DigitalScanUiState.Idle
    }

    private var seriesScanJob: Job? = null

    fun rescanSeries() {
        seriesScanJob = viewModelScope.launch {
            _seriesScanState.value = SeriesScanUiState.Running(SeriesScanProgress("Démarrage…", 0, 0))
            seriesScanner.scan(_form.value) { progress ->
                _seriesScanState.value = SeriesScanUiState.Running(progress)
            }.fold(
                onSuccess = { _seriesScanState.value = SeriesScanUiState.Done(it) },
                onFailure = { _seriesScanState.value = SeriesScanUiState.Failed(it.message ?: "Échec du scan") },
            )
        }
    }

    fun cancelSeriesScan() {
        seriesScanJob?.cancel()
        _seriesScanState.value = SeriesScanUiState.Idle
    }

    class Factory(
        private val settingsRepository: NetworkShareSettingsRepository,
        private val smbClient: SmbClient,
        private val digitalLibraryScanner: DigitalLibraryScanner,
        private val seriesScanner: SeriesScanner,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NetworkShareSettingsViewModel(settingsRepository, smbClient, digitalLibraryScanner, seriesScanner) as T
    }
}
