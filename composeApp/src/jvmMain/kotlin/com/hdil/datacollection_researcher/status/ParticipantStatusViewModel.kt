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

data class ParticipantStatusActionState(
    val deletingParticipantId: String? = null,
    val message: String? = null,
)

class ParticipantStatusViewModel(
    private val repository: ParticipantStatusRepository,
    private val deleteParticipantData: DeleteParticipantDataUseCase,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<ParticipantStatusUiState>(ParticipantStatusUiState.Idle)
    val uiState: StateFlow<ParticipantStatusUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(ParticipantStatusActionState())
    val actionState: StateFlow<ParticipantStatusActionState> = _actionState.asStateFlow()

    fun refresh(outputDir: File) {
        _uiState.value = ParticipantStatusUiState.Loading
        scope.launch {
            val result = runCatching { repository.loadStatuses(outputDir) }
            result
                .onSuccess { list -> _uiState.value = ParticipantStatusUiState.Loaded(list) }
                .onFailure { t -> _uiState.value = ParticipantStatusUiState.Error(t.message ?: "알 수 없는 오류") }
        }
    }

    fun deleteParticipant(outputDir: File, participantId: String) {
        val id = participantId.trim()
        if (id.isBlank()) {
            _actionState.update { it.copy(message = "Participant ID가 비어있습니다.") }
            return
        }

        scope.launch {
            _actionState.update { it.copy(deletingParticipantId = id, message = null) }
            runCatching { deleteParticipantData(outputDir, id) }
                .onSuccess { deletedCount ->
                    _actionState.update {
                        it.copy(
                            deletingParticipantId = null,
                            message = "삭제 완료: $id ($deletedCount 개 항목)"
                        )
                    }
                    refresh(outputDir)
                }
                .onFailure { t ->
                    _actionState.update {
                        it.copy(
                            deletingParticipantId = null,
                            message = t.message ?: "삭제 중 오류가 발생했습니다."
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        _actionState.update { it.copy(message = null) }
    }

    fun close() {
        scope.cancel()
    }
}
