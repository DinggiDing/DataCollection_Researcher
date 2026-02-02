package com.hdil.datacollection_researcher.credentials

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

class CredentialsViewModel(
    private val repository: CredentialsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(
        CredentialsUiState(
            status = CredentialsStatus.NotSaved,
            lastSelectedPath = repository.getLastSelectedPathOrNull(),
        ),
    )
    val uiState: StateFlow<CredentialsUiState> = _uiState.asStateFlow()

    fun refresh() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.loadStatus() }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = status,
                            lastSelectedPath = repository.getLastSelectedPathOrNull(),
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(isLoading = false, message = toUserMessage(t))
                    }
                }
        }
    }

    fun saveFromSelectedFile(file: File?) {
        if (file == null) return

        scope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.saveFromSelectedFile(file) }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            status = status,
                            lastSelectedPath = repository.getLastSelectedPathOrNull(),
                            message = "저장했어요: ${file.absolutePath}",
                        )
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(isLoading = false, message = toUserMessage(t)) }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun close() {
        scope.cancel()
    }

    private fun toUserMessage(t: Throwable): String {
        val msg = t.message
        return if (!msg.isNullOrBlank()) msg else "문제가 발생했어요. 다시 시도해 주세요."
    }
}

data class CredentialsUiState(
    val isLoading: Boolean = false,
    val status: CredentialsStatus,
    val lastSelectedPath: String? = null,
    val message: String? = null,
)
