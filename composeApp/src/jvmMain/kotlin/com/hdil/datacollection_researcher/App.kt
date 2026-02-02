package com.hdil.datacollection_researcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.analyze.AnalyzeViewModel
import com.hdil.datacollection_researcher.analyze.DesktopCsvAnalyzer
import com.hdil.datacollection_researcher.config.AppConfigViewModel
import com.hdil.datacollection_researcher.config.DateRangePreset
import com.hdil.datacollection_researcher.config.DesktopAppConfigRepository
import com.hdil.datacollection_researcher.credentials.CredentialsStatus
import com.hdil.datacollection_researcher.credentials.CredentialsViewModel
import com.hdil.datacollection_researcher.credentials.DefaultAppDirProvider
import com.hdil.datacollection_researcher.credentials.DesktopCredentialsRepository
import com.hdil.datacollection_researcher.credentials.DesktopFilePicker
import com.hdil.datacollection_researcher.credentials.DesktopOpenFolder
import com.hdil.datacollection_researcher.export.DesktopFirestoreExporter
import com.hdil.datacollection_researcher.export.ExportViewModel
import com.hdil.datacollection_researcher.excel.DesktopResearcherExcelGenerator
import com.hdil.datacollection_researcher.excel.ExcelViewModel
import com.hdil.datacollection_researcher.io.ParticipantOutputPaths
import com.hdil.datacollection_researcher.ui.AppSection
import com.hdil.datacollection_researcher.ui.AppSidebar
import com.hdil.datacollection_researcher.ui.ResearcherTheme
import com.hdil.datacollection_researcher.ui.SectionCard
import com.hdil.datacollection_researcher.ui.StepPanel
import com.hdil.datacollection_researcher.ui.ToggleButton
import com.hdil.datacollection_researcher.status.DesktopParticipantStatusRepository
import com.hdil.datacollection_researcher.status.ParticipantStatusViewModel
import com.hdil.datacollection_researcher.ui.settings.ParticipantStatusScreen
import com.hdil.datacollection_researcher.ui.workflow.WorkflowDashboardMapper
import com.hdil.datacollection_researcher.ui.workflow.WorkflowDashboardScreen
import com.hdil.datacollection_researcher.ui.workflow.WorkflowStep
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun App() {
    ResearcherTheme(dark = false) {
        val appDirProvider = remember { DefaultAppDirProvider() }

        val credentialsRepository = remember { DesktopCredentialsRepository(appDirProvider) }
        val credentialsViewModel = remember { CredentialsViewModel(credentialsRepository) }

        val configRepository = remember { DesktopAppConfigRepository(appDirProvider) }
        val configViewModel = remember { AppConfigViewModel(configRepository) }

        val exporter = remember { DesktopFirestoreExporter(appDirProvider) }
        val exportViewModel = remember {
            ExportViewModel(
                credentialsRepository = credentialsRepository,
                appConfigRepository = configRepository,
                exporter = exporter,
            )
        }

        val analyzer = remember { DesktopCsvAnalyzer() }
        val analyzeViewModel = remember { AnalyzeViewModel(analyzer, appDirProvider) }

        val excelGenerator = remember { DesktopResearcherExcelGenerator() }
        val excelViewModel = remember { ExcelViewModel(excelGenerator, configRepository, appDirProvider) }

        val participantStatusRepository = remember { DesktopParticipantStatusRepository() }
        val participantStatusViewModel = remember { ParticipantStatusViewModel(participantStatusRepository) }

        DisposableEffect(Unit) {
            onDispose {
                credentialsViewModel.close()
                configViewModel.close()
                exportViewModel.close()
                analyzeViewModel.close()
                excelViewModel.close()
                participantStatusViewModel.close()
            }
        }

        LaunchedEffect(Unit) {
            credentialsViewModel.refresh()
            configViewModel.load()
        }

        val credentialsState by credentialsViewModel.uiState.collectAsState()
        val configState by configViewModel.uiState.collectAsState()
        val exportState by exportViewModel.uiState.collectAsState()
        val analyzeState by analyzeViewModel.uiState.collectAsState()
        val excelState by excelViewModel.uiState.collectAsState()

        val outputDir = remember(appDirProvider) { File(appDirProvider.appDir(), "output") }
        val participantId = configState.participantId.trim().takeIf { it.isNotBlank() }
        val participantOutputDir = remember(outputDir, participantId) {
            ParticipantOutputPaths.participantDir(outputDir, participantId)
        }

        val exportFilesExist = remember(participantOutputDir, exportState.logs.size) {
            participantOutputDir.walkTopDown()
                .any { it.isFile && it.extension.equals("csv", ignoreCase = true) && it.name.contains("_export", ignoreCase = true) }
        }
        val koreaTimeFilesExist = remember(participantOutputDir, analyzeState.logs.size) {
            participantOutputDir.walkTopDown()
                .any { it.isFile && it.name.contains("_korea_time", ignoreCase = true) && it.extension.equals("csv", ignoreCase = true) }
        }

        var section by remember { mutableStateOf(AppSection.WORKFLOW) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background), // Darker background for main area?
        ) {
            AppSidebar(
                selected = section,
                onSelect = { section = it },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(24.dp)
            ) {
                when (section) {
                    AppSection.WORKFLOW -> {
                        val uiState = remember(credentialsState, configState, exportState, analyzeState, excelState, exportFilesExist, koreaTimeFilesExist) {
                            WorkflowDashboardMapper.map(
                                WorkflowDashboardMapper.Inputs(
                                    credentials = credentialsState,
                                    config = configState,
                                    export = exportState,
                                    analyze = analyzeState,
                                    excel = excelState,
                                    outputDirectoryPath = outputDir.absolutePath,
                                    exportFilesExist = exportFilesExist,
                                    koreaTimeFilesExist = koreaTimeFilesExist,
                                )
                            )
                        }

                        WorkflowDashboardScreen(
                            uiState = uiState,
                            onChangeServiceKey = {
                                val initialDir = credentialsState.lastSelectedPath?.let { File(it).parentFile }?.absolutePath
                                val selected = DesktopFilePicker.pickJsonFile(initialDirectory = initialDir)
                                credentialsViewModel.saveFromSelectedFile(selected)
                            },
                            onParticipantIdChanged = configViewModel::onParticipantIdChanged,
                            onSelectRangeQuickOption = { option ->
                                when (option) {
                                    "1D" -> configViewModel.selectPreset(DateRangePreset.LAST_1D)
                                    "1W" -> configViewModel.selectPreset(DateRangePreset.LAST_7D)
                                    "1M" -> configViewModel.selectPreset(DateRangePreset.LAST_30D)
                                    // "3M", "All" not supported in backend yet, ignore or clear
                                    else -> configViewModel.clearDateRange()
                                }
                            },
                            onRangeStartChanged = configViewModel::onCustomStartChanged,
                            onRangeEndChanged = configViewModel::onCustomEndChanged,
                            onClickOutputDirectory = {
                                runCatching { DesktopOpenFolder.openFolder(outputDir) }
                            },
                            onRunStep = { step ->
                                when (step) {
                                    WorkflowStep.EXPORT -> exportViewModel.runExport()
                                    WorkflowStep.ANALYZE -> analyzeViewModel.runAnalyze(participantId)
                                    WorkflowStep.EXCEL -> excelViewModel.runExcel()
                                }
                            },
                            onClearStepLogs = { step ->
                                when (step) {
                                    WorkflowStep.EXPORT -> exportViewModel.clearLogs()
                                    WorkflowStep.ANALYZE -> analyzeViewModel.clearLogs()
                                    WorkflowStep.EXCEL -> excelViewModel.clearLogs()
                                }
                            },
                            onSync = {
                                credentialsViewModel.refresh()
                                configViewModel.load()
                            },
                            onRunAll = {
                                // TODO: Implement sequence
                                exportViewModel.runExport()
                                // Ideally disable others until finished, or chain them.
                                // For now just start export is better than nothing or crashing.
                            }
                        )
                    }

                    AppSection.WORKSPACE -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Workspace Section (Coming Soon)")
                        }
                    }

                    AppSection.CONFIGURATION -> {
                         ParticipantStatusScreen(
                            outputDir = outputDir,
                            viewModel = participantStatusViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    AppSection.DOCUMENTATION -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Documentation Section")
                        }
                    }

                    AppSection.LOGS -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("System Logs")
                        }
                    }
                }
            }
        }
    }
}
