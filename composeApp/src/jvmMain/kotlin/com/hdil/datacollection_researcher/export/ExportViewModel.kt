package com.hdil.datacollection_researcher.export

import com.hdil.datacollection_researcher.config.AppConfigRepository
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

class ExportViewModel(
    private val credentialsRepository: CredentialsRepository,
    private val appConfigRepository: AppConfigRepository,
    private val exporter: FirestoreExporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun runExport() {
        scope.launch {
            _uiState.update { it.copy(isRunning = true, logs = listOf("Export 시작…")) }

            val credentialPath = when (val status = credentialsRepository.loadStatus()) {
                CredentialsStatus.NotSaved -> {
                    _uiState.update { it.copy(isRunning = false, logs = it.logs + "Credentials가 저장되어 있지 않아요. Phase 1에서 먼저 저장해 주세요.") }
                    return@launch
                }
                is CredentialsStatus.Saved -> status.credentialPath
            }

            val config = appConfigRepository.loadOrDefault()
            val participantId = config.participantId.trim()
            val docRoot = config.resolvedDocRoot().trim()

            if (participantId.isBlank() && config.docRoot.isNullOrBlank()) {
                _uiState.update { it.copy(isRunning = false, logs = it.logs + "participantId 또는 docRoot를 입력해 주세요.") }
                return@launch
            }

            val request = ExportRequest(
                credentialPath = credentialPath,
                participantId = participantId,
                docRoot = docRoot,
                dateRange = config.dateRange,
                limit = config.limit,
                orderByField = config.orderByField,
            )

            exporter.export(request).collect { event ->
                val line = when (event) {
                    is ExportLogEvent.Info -> event.message
                    is ExportLogEvent.Error -> "오류: ${event.message}"
                    is ExportLogEvent.CollectionStarted -> "[${event.collectionPath}] 시작"
                    is ExportLogEvent.CollectionProgress -> "[${event.collectionPath}] 읽음=${event.readCount}, 제외=${event.excludedByDateRangeCount}, row=${event.includedRowCount}"
                    is ExportLogEvent.CollectionFinished -> "[${event.collectionPath}] 완료 -> ${event.outputCsvPath} (읽음=${event.readCount}, 제외=${event.excludedByDateRangeCount}, row=${event.includedRowCount})"
                }
                _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }

                if (event is ExportLogEvent.Error) {
                    _uiState.update { it.copy(isRunning = false) }
                }
            }

            _uiState.update { it.copy(isRunning = false, logs = it.logs + "Export 종료") }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun close() {
        scope.cancel()
    }
}

data class ExportUiState(
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList(),
)
