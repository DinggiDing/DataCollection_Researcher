package com.hdil.datacollection_researcher.ui.workflow
import androidx.compose.ui.graphics.Color

/** Dashboard-level representation of a workflow step. */
enum class WorkflowStep {
    EXPORT,
    ANALYZE,
    EXCEL,
}

sealed class StepRunStatus {
    data object Idle : StepRunStatus()
    data object Running : StepRunStatus()
    data object Success : StepRunStatus()
    data class Error(val message: String) : StepRunStatus()
}

data class StepCardUiModel(
    val index: Int,
    val step: WorkflowStep,
    val title: String,
    val description: String,
    val status: StepRunStatus,
    val enabled: Boolean,
    val primaryActionText: String,
    val secondaryActionText: String,
    val hint: String? = null,
    val badgeText: String? = null,
)

data class StepConsoleUiModel(
    val step: WorkflowStep,
    val title: String,
    val lines: List<String>,
)

data class WorkflowDashboardUiState(
    val workspaceTitle: String = "Workspace",
    val pageTitle: String = "Workflow Dashboard",

    val serviceKeyLabel: String,
    val serviceKeyStatusText: String,
    val serviceKeyStatusColor: Color,
    val serviceKeyPath: String?,

    val participantId: String,
    val allParticipants: Boolean,
    val rangeQuickOptions: List<String>,
    val selectedRangeOption: String?,
    val rangeStartText: String,
    val rangeEndText: String,

    val outputDirectoryPath: String,

    val readinessTitle: String,
    val readinessSubtitle: String,
    val totalSteps: Int,

    val steps: List<StepCardUiModel>,
    val stepConsoles: List<StepConsoleUiModel>,
)


