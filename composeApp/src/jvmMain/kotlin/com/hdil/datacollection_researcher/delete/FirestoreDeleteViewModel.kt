package com.hdil.datacollection_researcher.delete

import com.hdil.datacollection_researcher.credentials.CredentialsRepository
import com.hdil.datacollection_researcher.credentials.CredentialsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirestoreDeleteUiState(
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList(),
)

class FirestoreDeleteViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val deleter: FirestoreDeleter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(FirestoreDeleteUiState())
    val uiState: StateFlow<FirestoreDeleteUiState> = _uiState.asStateFlow()

    fun runDelete(docRoot: String, batchSize: Int = 400) {
        scope.launch {
            _uiState.update { it.copy(isRunning = true, logs = listOf("Firestore 삭제 시작…")) }

            val credentialPath = when (val status = credentialsRepository.loadStatus()) {
                CredentialsStatus.NotSaved -> {
                    _uiState.update { it.copy(isRunning = false, logs = it.logs + "Credentials가 저장되어 있지 않아요. Phase 1에서 먼저 저장해 주세요.") }
                    return@launch
                }
                is CredentialsStatus.Saved -> status.credentialPath
            }

            if (docRoot.isBlank()) {
                _uiState.update { it.copy(isRunning = false, logs = it.logs + "docRoot가 비어 있어요.") }
                return@launch
            }

            val request = FirestoreDeleteRequest(
                credentialPath = credentialPath,
                docRoot = docRoot,
                batchSize = batchSize,
            )

            deleter.deleteParticipant(request).collect { event ->
                val line = when (event) {
                    is FirestoreDeleteLogEvent.Info -> event.message
                    is FirestoreDeleteLogEvent.Error -> "오류: ${event.message}"
                    is FirestoreDeleteLogEvent.SubcollectionStarted -> "[${event.collectionPath}] 삭제 시작"
                    is FirestoreDeleteLogEvent.SubcollectionProgress -> "[${event.collectionPath}] 삭제됨=${event.deletedCount}"
                    is FirestoreDeleteLogEvent.SubcollectionFinished -> "[${event.collectionPath}] 삭제 완료 (총 ${event.deletedCount})"
                    is FirestoreDeleteLogEvent.Finished -> "삭제 완료: 총 ${event.deletedTotalCount}개 문서"
                }

                _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }

                if (event is FirestoreDeleteLogEvent.Error) {
                    _uiState.update { it.copy(isRunning = false) }
                }
                if (event is FirestoreDeleteLogEvent.Finished) {
                    _uiState.update { it.copy(isRunning = false) }
                }
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun close() {
        scope.cancel()
    }
}
