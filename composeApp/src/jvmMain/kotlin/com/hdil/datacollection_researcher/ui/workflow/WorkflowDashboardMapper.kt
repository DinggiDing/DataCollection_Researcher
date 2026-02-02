package com.hdil.datacollection_researcher.ui.workflow

import androidx.compose.ui.graphics.Color
import com.hdil.datacollection_researcher.analyze.AnalyzeUiState
import com.hdil.datacollection_researcher.config.AppConfigUiState
import com.hdil.datacollection_researcher.config.DateRangePreset
import com.hdil.datacollection_researcher.credentials.CredentialsStatus
import com.hdil.datacollection_researcher.credentials.CredentialsUiState
import com.hdil.datacollection_researcher.excel.ExcelUiState
import com.hdil.datacollection_researcher.export.ExportUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WorkflowDashboardMapper {

    data class Inputs(
        val credentials: CredentialsUiState,
        val config: AppConfigUiState,
        val export: ExportUiState,
        val analyze: AnalyzeUiState,
        val excel: ExcelUiState,
        val outputDirectoryPath: String,
        val exportFilesExist: Boolean,
        val koreaTimeFilesExist: Boolean,
    )

    fun map(inputs: Inputs): WorkflowDashboardUiState {
        val credentialsSaved = inputs.credentials.status is CredentialsStatus.Saved
        val participantReady = inputs.config.participantId.trim().isNotEmpty()

        val serviceKeyStatusText = if (credentialsSaved) "Active" else "Not set"
        val serviceKeyStatusColor = if (credentialsSaved) Color(0xFF16A34A) else Color(0xFFDC2626)
        val serviceKeyPath = (inputs.credentials.status as? CredentialsStatus.Saved)?.credentialPath
            ?: inputs.credentials.lastSelectedPath

        val (rangeStartText, rangeEndText) = formatRangeTexts(inputs)

        val exportEnabled = credentialsSaved && participantReady && !inputs.export.isRunning
        val exportHint = when {
            !credentialsSaved -> "먼저 Credentials(서비스 계정 키)를 선택해 주세요."
            !participantReady -> "participantId를 입력해 주세요."
            else -> null
        }

        val analyzeEnabled = inputs.exportFilesExist && !inputs.analyze.isRunning
        val analyzeHint = if (!inputs.exportFilesExist) "Export를 먼저 실행해서 *_export_*.csv를 생성해 주세요." else null

        val excelEnabled = inputs.koreaTimeFilesExist && !inputs.excel.isRunning
        val excelHint = if (!inputs.koreaTimeFilesExist) "Analyze를 먼저 실행해서 *_korea_time.csv를 생성해 주세요." else null

        val steps = listOf(
            StepCardUiModel(
                index = 1,
                step = WorkflowStep.EXPORT,
                title = "01. EXPORT DATA",
                description = "Fetch raw data from Firestore and convert to local CSV format.",
                status = statusFor(isRunning = inputs.export.isRunning, logs = inputs.export.logs),
                enabled = exportEnabled,
                primaryActionText = "Run",
                secondaryActionText = "Clear",
                hint = exportHint,
                badgeText = if (inputs.exportFilesExist) "Ready" else "Idle",
            ),
            StepCardUiModel(
                index = 2,
                step = WorkflowStep.ANALYZE,
                title = "02. ANALYZE",
                description = "Process raw CSV data to extract meaningful metrics.",
                status = statusFor(isRunning = inputs.analyze.isRunning, logs = inputs.analyze.logs),
                enabled = analyzeEnabled,
                primaryActionText = "Analyze",
                secondaryActionText = "Clear",
                hint = analyzeHint,
                badgeText = if (inputs.koreaTimeFilesExist) "Ready" else "Idle",
            ),
            StepCardUiModel(
                index = 3,
                step = WorkflowStep.EXCEL,
                title = "03. GENERATE REPORT",
                description = "Compile analysis results into a researcher-friendly Excel report.",
                status = statusFor(isRunning = inputs.excel.isRunning, logs = inputs.excel.logs),
                enabled = excelEnabled,
                primaryActionText = "Generate",
                secondaryActionText = "Clear",
                hint = excelHint,
                badgeText = null,
            ),
        )

        val readinessTitle = if (participantReady) "Ready to Process" else "Setup Required"
        val readinessSubtitle = if (participantReady) {
            "Participant ${inputs.config.participantId.trim()} selected. ${steps.count { it.enabled }} modules available."
        } else {
            "Participant ID가 필요합니다. 좌측 설정에서 입력해 주세요."
        }

        val stepConsoles = listOf(
            StepConsoleUiModel(
                step = WorkflowStep.EXPORT,
                title = "CONSOLE OUTPUT",
                lines = buildStepConsoleLines(
                    stepPrefix = "export",
                    logs = inputs.export.logs,
                    placeholder = "Waiting for export command...",
                ),
            ),
            StepConsoleUiModel(
                step = WorkflowStep.ANALYZE,
                title = "CONSOLE OUTPUT",
                lines = buildStepConsoleLines(
                    stepPrefix = "analysis",
                    logs = inputs.analyze.logs,
                    placeholder = "Waiting for analysis...",
                ),
            ),
            StepConsoleUiModel(
                step = WorkflowStep.EXCEL,
                title = "CONSOLE OUTPUT",
                lines = buildStepConsoleLines(
                    stepPrefix = "report",
                    logs = inputs.excel.logs,
                    placeholder = "Ready to generate report...",
                ),
            ),
        )

        return WorkflowDashboardUiState(
            serviceKeyLabel = "SERVICE KEY",
            serviceKeyStatusText = serviceKeyStatusText,
            serviceKeyStatusColor = serviceKeyStatusColor,
            serviceKeyPath = serviceKeyPath,
            participantId = inputs.config.participantId,
            rangeQuickOptions = listOf("1D", "1W", "1M", "3M", "All"),
            selectedRangeOption = inputs.config.preset?.toQuickOptionLabel(),
            rangeStartText = rangeStartText,
            rangeEndText = rangeEndText,
            outputDirectoryPath = inputs.outputDirectoryPath,
            readinessTitle = readinessTitle,
            readinessSubtitle = readinessSubtitle,
            totalSteps = steps.size,
            steps = steps,
            stepConsoles = stepConsoles,
        )
    }

    private fun statusFor(isRunning: Boolean, logs: List<String>): StepRunStatus {
        if (isRunning) return StepRunStatus.Running
        val last = logs.lastOrNull().orEmpty()
        return when {
            last.startsWith("오류") || last.startsWith("Error") -> StepRunStatus.Error(last)
            last.contains("종료") || last.contains("완료") || last.contains("Finished") -> StepRunStatus.Success
            else -> StepRunStatus.Idle
        }
    }

    private fun formatRangeTexts(inputs: Inputs): Pair<String, String> {
        val range = inputs.config.dateRangeResult
        val startMillis = range.startMillisUtc
        val endMillis = range.endMillisUtc

        // Prefer showing custom text when provided.
        if (inputs.config.customStartDate.isNotBlank() || inputs.config.customEndDate.isNotBlank()) {
            return inputs.config.customStartDate.ifBlank { "(none)" } to inputs.config.customEndDate.ifBlank { "(none)" }
        }

        if (startMillis == null && endMillis == null) {
            return "(none)" to "(none)"
        }

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"))
        val startText = startMillis?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: "(none)"

        // endMillis is exclusive in model; show inclusive day for readability
        val endInclusive = endMillis?.let { it - 24L * 60L * 60L * 1000L }
        val endText = endInclusive?.let { fmt.format(Instant.ofEpochMilli(it)) } ?: "(none)"

        return startText to endText
    }

    private fun DateRangePreset.toQuickOptionLabel(): String = when (this) {
        DateRangePreset.LAST_1D -> "1D"
        DateRangePreset.LAST_7D -> "1W"
        DateRangePreset.LAST_30D -> "1M"
    }

    private fun buildStepConsoleLines(
        stepPrefix: String,
        logs: List<String>,
        placeholder: String,
    ): List<String> {
        if (logs.isEmpty()) return listOf(placeholder)

        // Keep it small and terminal-like.
        return logs
            .takeLast(300)
            .map { it.ifBlank { "" } }
            .ifEmpty { listOf(placeholder) }
    }
}
