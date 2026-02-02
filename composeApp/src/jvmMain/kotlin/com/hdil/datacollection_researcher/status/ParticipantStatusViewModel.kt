package com.hdil.datacollection_researcher.status

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface ParticipantStatusUiState {
    data object Idle : ParticipantStatusUiState
    data object Loading : ParticipantStatusUiState
    data class Loaded(val items: List<ParticipantDataStatus>) : ParticipantStatusUiState
    data class Error(val message: String) : ParticipantStatusUiState
}

class ParticipantStatusViewModel(
    private val repository: ParticipantStatusRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<ParticipantStatusUiState>(ParticipantStatusUiState.Idle)
    val uiState: StateFlow<ParticipantStatusUiState> = _uiState.asStateFlow()

    fun refresh(outputDir: File) {
        _uiState.value = ParticipantStatusUiState.Loading
        scope.launch {
            val result = runCatching { repository.loadStatuses(outputDir) }
            result
                .onSuccess { list -> _uiState.value = ParticipantStatusUiState.Loaded(list) }
                .onFailure { t -> _uiState.value = ParticipantStatusUiState.Error(t.message ?: "알 수 없는 오류") }
        }
    }

    fun close() {
        scope.cancel()
    }
}
