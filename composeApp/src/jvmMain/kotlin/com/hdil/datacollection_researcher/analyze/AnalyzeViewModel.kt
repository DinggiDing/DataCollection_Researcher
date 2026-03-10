package com.hdil.datacollection_researcher.analyze

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

class AnalyzeViewModel(
    private val analyzer: CsvAnalyzer,
    private val appDirProvider: AppDirProvider = DefaultAppDirProvider(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AnalyzeUiState())
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    fun runAnalyze(participantId: String? = null) {
        val outputRoot = File(appDirProvider.appDir(), "output")
        val normalizedId = participantId?.trim()?.takeIf { it.isNotBlank() }
        val participantDir = ParticipantOutputPaths.participantDir(outputRoot, normalizedId)

        scope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    logs = listOf(
                        "Analyze 시작…",
                        "DEBUG: requestedId=$participantId",
                        "대상 폴더: ${participantDir.absolutePath}",
                    ),
                )
            }

            analyzer.run(participantDir.absolutePath).collect { event ->
                val line = when (event) {
                    is AnalyzeLogEvent.Info -> event.message
                    is AnalyzeLogEvent.Error -> "오류: ${event.message}"
                    is AnalyzeLogEvent.FileStarted -> {
                        val relative = event.inputPath.replace(participantDir.absolutePath + File.separator, "")
                        "시작: $relative"
                    }
                    is AnalyzeLogEvent.FileFinished -> {
                        "완료: ${File(event.outputKoreaTimeCsv).name}, ${File(event.outputAnalysisCsv).name}"
                    }
                }

                _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }

                if (event is AnalyzeLogEvent.Error) {
                    _uiState.update { it.copy(isRunning = false) }
                }
            }

            _uiState.update { it.copy(isRunning = false, logs = it.logs + "Analyze 종료") }
        }
    }
    
    fun runAnalyzeAllParticipants() {
        val outputRoot = File(appDirProvider.appDir(), "output")
        val participantDirs = outputRoot.listFiles()
            ?.filter { it.isDirectory }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
        
        scope.launch {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    logs = listOf(
                        "Analyze(All) 시작…",
                        "대상 루트: ${outputRoot.absolutePath}",
                        "참가자 폴더 수: ${participantDirs.size}",
                    ),
                )
            }
            
            if (participantDirs.isEmpty()) {
                _uiState.update { it.copy(isRunning = false, logs = it.logs + "참가자 폴더가 없어요. Export를 먼저 실행해 주세요.") }
                return@launch
            }
            
            for (dir in participantDirs) {
                val participantId = dir.name
                _uiState.update { it.copy(logs = (it.logs + "=== Participant: $participantId ===").takeLast(500)) }
                
                analyzer.run(dir.absolutePath).collect { event ->
                    val line = when (event) {
                        is AnalyzeLogEvent.Info -> "[$participantId] ${event.message}"
                        is AnalyzeLogEvent.Error -> "[$participantId] 오류: ${event.message}"
                        is AnalyzeLogEvent.FileStarted -> {
                            val relative = event.inputPath.replace(dir.absolutePath + File.separator, "")
                            "[$participantId] 시작: $relative"
                        }
                        is AnalyzeLogEvent.FileFinished -> {
                            "[$participantId] 완료: ${File(event.outputKoreaTimeCsv).name}, ${File(event.outputAnalysisCsv).name}"
                        }
                    }
                    _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }
                }
            }
            
            _uiState.update { it.copy(isRunning = false, logs = it.logs + "Analyze(All) 종료") }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun close() {
        scope.cancel()
    }
}

data class AnalyzeUiState(
    val isRunning: Boolean = false,
    val logs: List<String> = emptyList(),
)
