package com.hdil.datacollection_researcher.excel

import com.hdil.datacollection_researcher.config.AppConfigRepository
import com.hdil.datacollection_researcher.credentials.AppDirProvider
import com.hdil.datacollection_researcher.credentials.DefaultAppDirProvider
import com.hdil.datacollection_researcher.io.ParticipantOutputPaths
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

class ExcelViewModel(
    private val generator: ResearcherExcelGenerator,
    private val appConfigRepository: AppConfigRepository,
    private val appDirProvider: AppDirProvider = DefaultAppDirProvider(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(ExcelUiState())
    val uiState: StateFlow<ExcelUiState> = _uiState.asStateFlow()

    fun runExcel(participantIdOverride: String? = null) {
        val outputRoot = File(appDirProvider.appDir(), "output")
        scope.launch {
            _uiState.update { it.copy(isRunning = true, logs = listOf("Excel(리포트) 생성 시작…")) }

            val config = appConfigRepository.loadOrDefault()
            val participantId = participantIdOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: config.participantId.trim().takeIf { it.isNotBlank() }

            _uiState.update { it.copy(logs = it.logs + "DEBUG: requestedWithId=$participantIdOverride, resolvedId=$participantId") }

            val participantDir = ParticipantOutputPaths.participantDir(outputRoot, participantId)
            _uiState.update { it.copy(logs = it.logs + "대상 폴더: ${participantDir.absolutePath}") }

            generator.generate(participantDir.absolutePath, participantId).collect { event ->
                val line = when (event) {
                    is ExcelLogEvent.Info -> event.message
                    is ExcelLogEvent.Error -> "오류: ${event.message}"
                    is ExcelLogEvent.Finished -> "완료: ${File(event.outputXlsxPath).name}"
                }
                _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }

                if (event is ExcelLogEvent.Error) {
                    _uiState.update { it.copy(isRunning = false) }
                }
            }

            _uiState.update { it.copy(isRunning = false, logs = it.logs + "Excel(리포트) 생성 종료") }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun close() {
        scope.cancel()
    }
}

data class ExcelUiState(
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList(),
)
