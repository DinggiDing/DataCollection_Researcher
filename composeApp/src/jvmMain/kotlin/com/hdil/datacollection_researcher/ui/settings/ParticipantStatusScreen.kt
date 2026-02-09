package com.hdil.datacollection_researcher.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdil.datacollection_researcher.status.CoverageStatus
import com.hdil.datacollection_researcher.status.DailyDataSummary
import com.hdil.datacollection_researcher.status.ParticipantDataStatus
import com.hdil.datacollection_researcher.status.ParticipantStatusUiState
import com.hdil.datacollection_researcher.status.ParticipantStatusViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private fun calculateDailyStatus(
    summary: DailyDataSummary?,
    showSensor: Boolean,
    showHealth: Boolean,
    showSurvey: Boolean
): CoverageStatus {
    if (summary == null) return CoverageStatus.UNKNOWN
    var total = 0L
    if (showSensor) total += summary.sensorRecordCount
    if (showHealth) total += summary.healthRecordCount
    if (showSurvey) total += summary.surveyRecordCount

    if (!showSensor && !showHealth && !showSurvey) return CoverageStatus.UNKNOWN

    return when {
        total > 100 -> CoverageStatus.OK
        total > 0 -> CoverageStatus.PARTIAL
        else -> CoverageStatus.MISSING
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantStatusScreen(
    outputDir: File,
    viewModel: ParticipantStatusViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(outputDir) {
        viewModel.refresh(outputDir)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                DashboardTopBar(onRefresh = { viewModel.refresh(outputDir) })
                HorizontalDivider()
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F7FA))) {
            when (val s = uiState) {
                ParticipantStatusUiState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ready") }
                ParticipantStatusUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is ParticipantStatusUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.message, color = MaterialTheme.colorScheme.error) }
                is ParticipantStatusUiState.Loaded -> {
                    DashboardContent(
                        participants = s.items,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Research Data Coverage Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Overview of participant data collection status", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Button(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Refresh Data")
        }
    }
}

@Composable
fun DashboardContent(
    participants: List<ParticipantDataStatus>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedParticipant by remember { mutableStateOf<ParticipantDataStatus?>(null) }

    // Filters
    var showSensor by remember { mutableStateOf(true) }
    var showHealth by remember { mutableStateOf(true) }
    var showSurvey by remember { mutableStateOf(true) }

    // Default to first one if none selected
    LaunchedEffect(participants) {
        if (selectedParticipant == null && participants.isNotEmpty()) {
            selectedParticipant = participants.first()
        }
    }

    val filteredParticipants = remember(participants, searchQuery) {
        if (searchQuery.isBlank()) participants
        else participants.filter { it.participantId.contains(searchQuery, ignoreCase = true) }
    }

    // Stats
    val totalParticipants = participants.size

    // Helper to get status based on filters
    fun getStatus(s: DailyDataSummary?): CoverageStatus {
        return calculateDailyStatus(s, showSensor, showHealth, showSurvey)
    }

    val atRiskCount = participants.count { p ->
        val sorted = p.dailySummaries.entries.sortedBy { it.key }
        val size = sorted.size
        // Check last 3 days
        val recent = if (size > 3) sorted.subList(size - 3, size) else sorted
        recent.any { getStatus(it.value) == CoverageStatus.MISSING }
    }

    val avgCoverage = if (participants.isNotEmpty()) {
        participants.map { p ->
            val totalDays = p.dailySummaries.size.coerceAtLeast(1)
            val okCount = p.dailySummaries.values.count { getStatus(it) == CoverageStatus.OK }
            (okCount.toFloat() / totalDays) * 100
        }.average().roundToInt()
    } else 0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Filters Row
        FiltersRow(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            showSensor = showSensor, onSensorChange = { showSensor = it },
            showHealth = showHealth, onHealthChange = { showHealth = it },
            showSurvey = showSurvey, onSurveyChange = { showSurvey = it }
        )

        // KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard(
                title = "Overall Coverage",
                value = "$avgCoverage%",
                trend = "Avg per participant",
                color = Color(0xFF43A047),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Participants at Risk",
                value = atRiskCount.toString(),
                trend = "Missing recent data",
                color = Color(0xFFE53935),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Total Participants",
                value = totalParticipants.toString(),
                trend = "Active",
                color = Color(0xFF1E88E5),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Data Freshness",
                value = "94%",
                trend = "Assumed OK",
                color = Color(0xFFFB8C00),
                modifier = Modifier.weight(1f)
            )
        }

        // Main Content Split
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left: Grid
            Card(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Participant Data Coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))

                    CoverageHeatmap(
                        participants = filteredParticipants,
                        selectedId = selectedParticipant?.participantId,
                        onSelect = { selectedParticipant = it },
                        statusProvider = { getStatus(it) }
                    )

                    Spacer(Modifier.height(24.dp))
                    Text("Missing Data Trends", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    SimpleTrendChart(
                        participants,
                        Modifier.fillMaxWidth().height(150.dp),
                        statusProvider = { getStatus(it) }
                    )
                }
            }

            // Right: Details
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                 if (selectedParticipant != null) {
                    ParticipantDetailPanel(
                        participant = selectedParticipant!!,
                        modifier = Modifier.fillMaxSize()
                    )
                 } else {
                     Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("Select a participant")
                     }
                 }
            }
        }
    }
}

@Composable
fun FiltersRow(
    query: String,
    onQueryChange: (String) -> Unit,
    showSensor: Boolean, onSensorChange: (Boolean) -> Unit,
    showHealth: Boolean, onHealthChange: (Boolean) -> Unit,
    showSurvey: Boolean, onSurveyChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(onClick = {}) {
            Text("Study: ALL")
            Icon(Icons.Default.ArrowDropDown, null)
        }
        OutlinedButton(onClick = {}) {
            Text("Date: Last 15 Days")
            Icon(Icons.Default.ArrowDropDown, null)
        }

        VerticalDivider(modifier = Modifier.height(24.dp))

        FilterCheckbox("Sensor", showSensor, onSensorChange)
        FilterCheckbox("Health", showHealth, onHealthChange)
        FilterCheckbox("Survey", showSurvey, onSurveyChange)

        Spacer(Modifier.weight(1f))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search Participant...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.width(250.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
    }
}

@Composable
fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 4.dp).size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun KpiCard(title: String, value: String, trend: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(trend, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun CoverageHeatmap(
    participants: List<ParticipantDataStatus>,
    selectedId: String?,
    onSelect: (ParticipantDataStatus) -> Unit,
    statusProvider: (DailyDataSummary?) -> CoverageStatus
) {
    val today = LocalDate.now()
    val days = (0..14).map { today.minusDays(it.toLong()) }.reversed()
    val dateFormat = DateTimeFormatter.ofPattern("MM-dd")

    Column {
        // Header
        Row(
            Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(100.dp)) // Participant col
            days.forEach { date ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(date.format(dateFormat), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                }
            }
        }

        // Rows
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxHeight(0.6f) // limit height
        ) {
            items(participants) { p ->
                val isSelected = p.participantId == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFFE3F2FD) else Color.Transparent)
                        .clickable { onSelect(p) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        p.participantId,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(100.dp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )

                    days.forEach { date ->
                        val status = statusProvider(p.dailySummaries[date])
                        Box(Modifier.weight(1f).padding(1.dp)) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(statusColor(status))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantDetailPanel(participant: ParticipantDataStatus, modifier: Modifier) {
    Column(modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(participant.participantId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Default.MoreHoriz, null) }
        }
        Text("Group: Unknown | Device: Unknown", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        Text("Category Breakdown (Last 7 Days)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))

        val last7Days = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()

        CategoryRow("Sensor", last7Days) { date ->
           val s = participant.dailySummaries[date]
            if (s != null && s.sensorRecordCount > 50) CoverageStatus.OK else if ((s?.sensorRecordCount ?: 0) > 0) CoverageStatus.PARTIAL else CoverageStatus.MISSING
        }
        CategoryRow("Health", last7Days) { date ->
             val s = participant.dailySummaries[date]
            if (s != null && s.healthRecordCount > 10) CoverageStatus.OK else if ((s?.healthRecordCount ?: 0) > 0) CoverageStatus.PARTIAL else CoverageStatus.MISSING
        }
        CategoryRow("Survey", last7Days) { date ->
             val s = participant.dailySummaries[date]
            if (s != null && s.surveyRecordCount > 0) CoverageStatus.OK else CoverageStatus.MISSING
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        InfoRow(Icons.Default.CheckCircle, "Last Data", participant.lastTimestamp?.toString() ?: "Never")
        InfoRow(Icons.Default.Warning, "Total Records", "${participant.totalRecordCount}")
        InfoRow(Icons.Default.BatteryAlert, "Gaps found", "${participant.gapCount} gaps")

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Check Files") }
        }
    }
}

@Composable
fun CategoryRow(label: String, days: List<LocalDate>, statusProvider: (LocalDate) -> CoverageStatus) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
        days.forEach { date ->
             Box(Modifier.weight(1f).padding(1.dp).height(20.dp)) {
                Box(
                    Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor(statusProvider(date)))
                )
             }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun statusColor(status: CoverageStatus): Color {
    return when (status) {
        CoverageStatus.OK -> Color(0xFF66BB6A)
        CoverageStatus.PARTIAL -> Color(0xFFFFCA28)
        CoverageStatus.MISSING -> Color(0xFFEF5350)
        CoverageStatus.UNKNOWN -> Color(0xFFE0E0E0)
    }
}

@Composable
fun SimpleTrendChart(
    participants: List<ParticipantDataStatus>,
    modifier: Modifier,
    statusProvider: (DailyDataSummary?) -> CoverageStatus
) {
    val days = (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
    val missingCounts = days.map { date ->
        participants.count {
            statusProvider(it.dailySummaries[date]) == CoverageStatus.MISSING
        }
    }
    val max = missingCounts.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (days.size - 1).coerceAtLeast(1)

        val points = missingCounts.mapIndexed { index, count ->
            Offset(index * stepX, h - (count / max * h * 0.8f))
        }

        if (points.isNotEmpty()) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, Color(0xFF5C6BC0), style = Stroke(width = 3.dp.toPx()))

            points.forEach {
                drawCircle(Color(0xFF5C6BC0), radius = 5.dp.toPx(), center = it)
            }
        }

        drawLine(Color.Gray, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
    }
}
