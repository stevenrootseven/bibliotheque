package com.stevenroot.mabibliotheque.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevenroot.mabibliotheque.data.repository.CollectionImporter
import com.stevenroot.mabibliotheque.data.repository.ImportProgress
import com.stevenroot.mabibliotheque.data.repository.ImportResult
import com.stevenroot.mabibliotheque.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val progress: ImportProgress) : ImportUiState
    data class Done(val result: ImportResult) : ImportUiState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val collectionImporter: CollectionImporter,
) : ViewModel() {

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    val importAvailable: Boolean get() = collectionImporter.isAvailable()

    init {
        viewModelScope.launch {
            _apiKeyInput.value = settingsRepository.currentTmdbApiKey().orEmpty()
        }
    }

    fun onInputChanged(value: String) {
        _apiKeyInput.value = value
        _saved.value = false
    }

    fun save() = viewModelScope.launch {
        settingsRepository.setTmdbApiKey(_apiKeyInput.value)
        _saved.value = true
    }

    fun runImport() = viewModelScope.launch {
        _importState.value = ImportUiState.Running(ImportProgress(0, 1, ""))
        val result = collectionImporter.import { progress ->
            _importState.value = ImportUiState.Running(progress)
        }
        _importState.value = ImportUiState.Done(result)
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val collectionImporter: CollectionImporter,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, collectionImporter) as T
    }
}
