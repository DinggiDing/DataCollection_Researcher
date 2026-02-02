package com.hdil.datacollection_researcher

import androidx.compose.foundation.layout.Arrangement
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

        val credentialsReady = credentialsState.status is CredentialsStatus.Saved
        val participantReady = configState.participantId.trim().isNotEmpty()

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

        val range = configState.dateRangeResult
        val preset = configState.preset
        val rangeSummary = remember(range.startMillisUtc, range.endMillisUtc, preset, configState.customStartDate, configState.customEndDate) {
            when {
                preset != null && range.startMillisUtc != null && range.endMillisUtc != null -> {
                    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"))
                    val s = fmt.format(Instant.ofEpochMilli(range.startMillisUtc))
                    val e = fmt.format(Instant.ofEpochMilli(range.endMillisUtc))
                    "Preset: ${preset.name.removePrefix("LAST_")} / $s ~ $e (UTC)"
                }
                (configState.customStartDate.isNotBlank() || configState.customEndDate.isNotBlank()) -> {
                    val s = configState.customStartDate.ifBlank { "(none)" }
                    val e = configState.customEndDate.ifBlank { "(none)" }
                    "Custom: $s ~ $e"
                }
                range.startMillisUtc == null && range.endMillisUtc == null -> "Date Range: (none)"
                else -> {
                    "Date Range: start=${range.startMillisUtc}, end=${range.endMillisUtc}"
                }
            }
        }

        var section by remember { mutableStateOf(AppSection.WORKFLOW) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppSidebar(
                selected = section,
                onSelect = { section = it },
            )

            Spacer(Modifier.width(4.dp))

            when (section) {
                AppSection.WORKFLOW -> {
                    // 기존: Left(설정) + Right(워크플로우)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 기존 Row(Left/Right)를 그대로 넣음
                        Row(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Left
                            Column(
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "DataCollection Researcher",
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                SectionCard(
                                    title = "Credentials",
                                    subtitle = "Firestore 접근을 위한 서비스 계정 키(JSON)를 선택해 주세요.",
                                ) {
                                    val selectedPath = (credentialsState.lastSelectedPath
                                        ?: (credentialsState.status as? CredentialsStatus.Saved)?.credentialPath)

                                    Text(
                                        text = when (credentialsState.status) {
                                            CredentialsStatus.NotSaved -> "상태: 미설정"
                                            is CredentialsStatus.Saved -> "상태: 설정됨"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Text(
                                        text = selectedPath?.let { "선택됨: $it" } ?: "아직 선택된 키가 없어요.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    credentialsState.message?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            enabled = !credentialsState.isLoading,
                                            onClick = {
                                                val initialDir = credentialsState.lastSelectedPath
                                                    ?.let { File(it).parentFile }
                                                    ?.absolutePath
                                                val selected = DesktopFilePicker.pickJsonFile(initialDirectory = initialDir)
                                                credentialsViewModel.saveFromSelectedFile(selected)
                                            },
                                        ) { Text("Select") }

                                        Button(
                                            enabled = !credentialsState.isLoading,
                                            onClick = { credentialsViewModel.refresh() },
                                        ) { Text("Reload") }

                                        if (credentialsState.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            enabled = !credentialsState.isLoading,
                                            onClick = {
                                                runCatching { DesktopOpenFolder.openFolder(appDirProvider.appDir()) }
                                                    .onFailure { t -> println(t.message ?: "폴더를 여는 중 문제가 발생했어요.") }
                                            },
                                        ) { Text("Open App Folder") }
                                    }
                                }

                                SectionCard(
                                    title = "Participant",
                                    subtitle = "participantId만 입력하면 docRoot는 자동으로 구성돼요.",
                                ) {
                                    OutlinedTextField(
                                        value = configState.participantId,
                                        onValueChange = configViewModel::onParticipantIdChanged,
                                        label = { Text("participantId") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                    )

                                    Text("Date Range", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        rangeSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ToggleButton(
                                            text = "1D",
                                            selected = configState.preset == DateRangePreset.LAST_1D,
                                            enabled = !configState.isLoading,
                                            onClick = { configViewModel.selectPreset(DateRangePreset.LAST_1D) },
                                        )
                                        ToggleButton(
                                            text = "7D",
                                            selected = configState.preset == DateRangePreset.LAST_7D,
                                            enabled = !configState.isLoading,
                                            onClick = { configViewModel.selectPreset(DateRangePreset.LAST_7D) },
                                        )
                                        ToggleButton(
                                            text = "30D",
                                            selected = configState.preset == DateRangePreset.LAST_30D,
                                            enabled = !configState.isLoading,
                                            onClick = { configViewModel.selectPreset(DateRangePreset.LAST_30D) },
                                        )
                                        ToggleButton(
                                            text = "Clear",
                                            selected = false,
                                            enabled = !configState.isLoading,
                                            onClick = { configViewModel.clearDateRange() },
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = configState.customStartDate,
                                            onValueChange = configViewModel::onCustomStartChanged,
                                            label = { Text("Start (YYYY-MM-DD)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = configState.customEndDate,
                                            onValueChange = configViewModel::onCustomEndChanged,
                                            label = { Text("End (YYYY-MM-DD)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                        )
                                    }

                                    configState.dateRangeError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Button(
                                            enabled = !configState.isLoading,
                                            onClick = { configViewModel.save() },
                                        ) { Text("Save") }

                                        if (configState.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                    }

                                    configState.message?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                SectionCard(
                                    title = "Output",
                                    subtitle = "생성된 파일은 이 폴더에 저장돼요.",
                                ) {
                                    Text(
                                        outputDir.absolutePath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = {
                                            runCatching { DesktopOpenFolder.openFolder(outputDir) }
                                                .onFailure { t -> println(t.message ?: "폴더를 여는 중 문제가 발생했어요.") }
                                        },
                                    ) { Text("Open Output Folder") }
                                }
                            }

                            // Right
                            Column(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("Workflow", style = MaterialTheme.typography.titleLarge)

                                // 디버그/상태 표시: Step3 비활성 원인 빠른 확인용
                                Text(
                                    text = "Participant Output: ${participantOutputDir.absolutePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Detect: export=$exportFilesExist, korea_time=$koreaTimeFilesExist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                val exportEnabled = credentialsReady && participantReady && !exportState.isRunning
                                val exportHint = when {
                                    !credentialsReady -> "먼저 Credentials(서비스 계정 키)를 선택해 주세요."
                                    !participantReady -> "participantId를 입력해 주세요."
                                    else -> null
                                }

                                val analyzeEnabled = exportFilesExist && !analyzeState.isRunning
                                val analyzeHint = if (!exportFilesExist) "Export를 먼저 실행해서 *_export_*.csv를 생성해 주세요." else null

                                val excelEnabled = koreaTimeFilesExist && !excelState.isRunning
                                val excelHint = if (!koreaTimeFilesExist) "Analyze를 먼저 실행해서 *_korea_time.csv를 생성해 주세요." else null

                                StepPanel(
                                    title = "Step 1 — Export",
                                    hint = exportHint,
                                    isRunning = exportState.isRunning,
                                    primaryText = "Run Export",
                                    primaryEnabled = exportEnabled,
                                    onPrimary = { exportViewModel.runExport() },
                                    secondaryText = "Clear Logs",
                                    secondaryEnabled = !exportState.isRunning,
                                    onSecondary = { exportViewModel.clearLogs() },
                                    logs = exportState.logs,
                                )

                                StepPanel(
                                    title = "Step 2 — Analyze",
                                    hint = analyzeHint,
                                    isRunning = analyzeState.isRunning,
                                    primaryText = "Run Analyze",
                                    primaryEnabled = analyzeEnabled,
                                    onPrimary = { analyzeViewModel.runAnalyze(participantId) },
                                    secondaryText = "Clear Logs",
                                    secondaryEnabled = !analyzeState.isRunning,
                                    onSecondary = { analyzeViewModel.clearLogs() },
                                    logs = analyzeState.logs,
                                )

                                StepPanel(
                                    title = "Step 3 — Excel",
                                    hint = excelHint,
                                    isRunning = excelState.isRunning,
                                    primaryText = "Run Excel",
                                    primaryEnabled = excelEnabled,
                                    onPrimary = { excelViewModel.runExcel() },
                                    secondaryText = "Clear Logs",
                                    secondaryEnabled = !excelState.isRunning,
                                    onSecondary = { excelViewModel.clearLogs() },
                                    logs = excelState.logs,
                                )
                            }
                        }
                    }
                }

                AppSection.SETTINGS -> {
                    ParticipantStatusScreen(
                        outputDir = outputDir,
                        viewModel = participantStatusViewModel,
                        modifier = Modifier.weight(1f),
                    )
                }

                AppSection.OUTPUT -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Output", style = MaterialTheme.typography.titleLarge)
                        SectionCard(title = "Output Folder", subtitle = outputDir.absolutePath) {
                            Button(
                                onClick = {
                                    runCatching { DesktopOpenFolder.openFolder(outputDir) }
                                        .onFailure { t -> println(t.message ?: "폴더를 여는 중 문제가 발생했어요.") }
                                },
                            ) { Text("Open Output Folder") }
                        }
                    }
                }

                AppSection.ABOUT -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("About", style = MaterialTheme.typography.titleLarge)
                        SectionCard(title = "Info") {
                            Text(
                                "DataCollection Researcher Tool (KMP Desktop)\n" +
                                    "- Export: Firestore → CSV\n" +
                                    "- Analyze: KST + coverage\n" +
                                    "- Excel: ResearcherView XLSX\n",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
