package com.hdil.datacollection_researcher.ui.workflow

import com.hdil.datacollection_researcher.analyze.AnalyzeUiState
import com.hdil.datacollection_researcher.config.AppConfigUiState
import com.hdil.datacollection_researcher.config.DateRange
import com.hdil.datacollection_researcher.credentials.CredentialsStatus
import com.hdil.datacollection_researcher.credentials.CredentialsUiState
import com.hdil.datacollection_researcher.excel.ExcelUiState
import com.hdil.datacollection_researcher.export.ExportUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowDashboardMapperTest {

    @Test
    fun `export enabled only when credentials saved and participant set`() {
        val state1 = WorkflowDashboardMapper.map(
            WorkflowDashboardMapper.Inputs(
                credentials = CredentialsUiState(status = CredentialsStatus.NotSaved),
                config = AppConfigUiState(participantId = "p1"),
                export = ExportUiState(isRunning = false),
                analyze = AnalyzeUiState(isRunning = false),
                excel = ExcelUiState(isRunning = false),
                outputDirectoryPath = "/tmp/out",
                exportFilesExist = false,
                koreaTimeFilesExist = false,
            ),
        )
        assertFalse(state1.steps.first { it.step == WorkflowStep.EXPORT }.enabled)

        val state2 = WorkflowDashboardMapper.map(
            WorkflowDashboardMapper.Inputs(
                credentials = CredentialsUiState(status = CredentialsStatus.Saved("/tmp/key.json")),
                config = AppConfigUiState(participantId = ""),
                export = ExportUiState(isRunning = false),
                analyze = AnalyzeUiState(isRunning = false),
                excel = ExcelUiState(isRunning = false),
                outputDirectoryPath = "/tmp/out",
                exportFilesExist = false,
                koreaTimeFilesExist = false,
            ),
        )
        assertFalse(state2.steps.first { it.step == WorkflowStep.EXPORT }.enabled)

        val state3 = WorkflowDashboardMapper.map(
            WorkflowDashboardMapper.Inputs(
                credentials = CredentialsUiState(status = CredentialsStatus.Saved("/tmp/key.json")),
                config = AppConfigUiState(participantId = "p1"),
                export = ExportUiState(isRunning = false),
                analyze = AnalyzeUiState(isRunning = false),
                excel = ExcelUiState(isRunning = false),
                outputDirectoryPath = "/tmp/out",
                exportFilesExist = false,
                koreaTimeFilesExist = false,
            ),
        )
        assertTrue(state3.steps.first { it.step == WorkflowStep.EXPORT }.enabled)
    }

    @Test
    fun `selectedRangeOption is mapped from preset`() {
        val ui = WorkflowDashboardMapper.map(
            WorkflowDashboardMapper.Inputs(
                credentials = CredentialsUiState(status = CredentialsStatus.NotSaved),
                config = AppConfigUiState(
                    participantId = "p1",
                    preset = com.hdil.datacollection_researcher.config.DateRangePreset.LAST_7D,
                    dateRangeResult = DateRange(),
                ),
                export = ExportUiState(isRunning = false),
                analyze = AnalyzeUiState(isRunning = false),
                excel = ExcelUiState(isRunning = false),
                outputDirectoryPath = "/tmp/out",
                exportFilesExist = false,
                koreaTimeFilesExist = false,
            ),
        )

        assertEquals("1W", ui.selectedRangeOption)
    }

    @Test
    fun `step consoles show placeholders when no logs`() {
        val ui = WorkflowDashboardMapper.map(
            WorkflowDashboardMapper.Inputs(
                credentials = CredentialsUiState(status = CredentialsStatus.NotSaved),
                config = AppConfigUiState(participantId = "p1"),
                export = ExportUiState(isRunning = false, logs = emptyList()),
                analyze = AnalyzeUiState(isRunning = false, logs = emptyList()),
                excel = ExcelUiState(isRunning = false, logs = emptyList()),
                outputDirectoryPath = "/tmp/out",
                exportFilesExist = false,
                koreaTimeFilesExist = false,
            ),
        )

        val exportConsole = ui.stepConsoles.first { it.step == WorkflowStep.EXPORT }
        val analyzeConsole = ui.stepConsoles.first { it.step == WorkflowStep.ANALYZE }
        val excelConsole = ui.stepConsoles.first { it.step == WorkflowStep.EXCEL }

        assertEquals(listOf("Waiting for export command..."), exportConsole.lines)
        assertEquals(listOf("Waiting for analysis..."), analyzeConsole.lines)
        assertEquals(listOf("Ready to generate report..."), excelConsole.lines)
    }
}
