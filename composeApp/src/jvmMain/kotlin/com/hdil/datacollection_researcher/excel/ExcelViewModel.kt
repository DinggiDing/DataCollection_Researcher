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

            // generator는 base outputRoot를 기준으로 participantId 폴더를 찾습니다.
            generator.generate(
                outputRoot.absolutePath,
                participantId,
                config.dateRange,
            ).collect { event ->
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
    
    fun runExcelAllParticipants() {
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
                        "Excel(All) 생성 시작…",
                        "대상 루트: ${outputRoot.absolutePath}",
                        "참가자 폴더 수: ${participantDirs.size}",
                    ),
                )
            }
            val config = appConfigRepository.loadOrDefault()
            
            if (participantDirs.isEmpty()) {
                _uiState.update { it.copy(isRunning = false, logs = it.logs + "참가자 폴더가 없어요. Export/Analyze를 먼저 실행해 주세요.") }
                return@launch
            }
            
            for (dir in participantDirs) {
                val participantId = dir.name
                _uiState.update { it.copy(logs = (it.logs + "=== Participant: $participantId ===").takeLast(500)) }
                
                generator.generate(
                    outputRoot.absolutePath,
                    participantId,
                    config.dateRange,
                ).collect { event ->
                    val line = when (event) {
                        is ExcelLogEvent.Info -> "[$participantId] ${event.message}"
                        is ExcelLogEvent.Error -> "[$participantId] 오류: ${event.message}"
                        is ExcelLogEvent.Finished -> "[$participantId] 완료: ${File(event.outputXlsxPath).name}"
                    }
                    _uiState.update { it.copy(logs = (it.logs + line).takeLast(500)) }
                }
            }
            
            _uiState.update { it.copy(isRunning = false, logs = it.logs + "Excel(All) 생성 종료") }
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
