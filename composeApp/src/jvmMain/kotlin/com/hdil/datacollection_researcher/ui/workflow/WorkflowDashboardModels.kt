package com.hdil.datacollection_researcher.ui.workflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.ui.SectionCard
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

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


