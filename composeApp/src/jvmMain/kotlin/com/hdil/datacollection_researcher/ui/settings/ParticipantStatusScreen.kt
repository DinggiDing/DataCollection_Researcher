package com.hdil.datacollection_researcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.status.OutputFileCategory
import com.hdil.datacollection_researcher.status.ParticipantDataStatus
import com.hdil.datacollection_researcher.status.ParticipantStatusUiState
import com.hdil.datacollection_researcher.status.ParticipantStatusViewModel
import com.hdil.datacollection_researcher.ui.SectionCard
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantStatusScreen(
    outputDir: File,
    viewModel: ParticipantStatusViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedParticipantId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(outputDir.absolutePath) {
        viewModel.refresh(outputDir)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Settings — Participant Data Status", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = { viewModel.refresh(outputDir) }) { Text("Refresh") }
        }

        Text(
            text = "output 아래의 CSV/XLSX 파일을 스캔해 참가자별로 요약합니다. (파일명 또는 폴더명에서 participantId를 추정)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search participant") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        when (val s = uiState) {
            ParticipantStatusUiState.Idle -> {
                SectionCard(title = "Status") { Text("대기 중") }
            }

            ParticipantStatusUiState.Loading -> {
                SectionCard(title = "Loading") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
                        Text("스캔 중…")
                    }
                }
            }

            is ParticipantStatusUiState.Error -> {
                SectionCard(title = "Error") {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is ParticipantStatusUiState.Loaded -> {
                val filtered = s.items.filter { it.participantId.contains(query, ignoreCase = true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(0.62f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionCard(
                            title = "Participants (${filtered.size})",
                            subtitle = "클릭하면 우측에 파일 상세가 표시됩니다.",
                        ) {
                            ParticipantStatusTable(
                                items = filtered,
                                selectedParticipantId = selectedParticipantId,
                                onSelect = { selectedParticipantId = it },
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val selected = filtered.firstOrNull { it.participantId == selectedParticipantId }
                            ?: filtered.firstOrNull().also { selectedParticipantId = it?.participantId }

                        SectionCard(
                            title = "Details",
                            subtitle = selected?.participantId ?: "(none)",
                        ) {
                            if (selected == null) {
                                Text("표시할 참가자가 없어요.")
                            } else {
                                ParticipantStatusDetails(selected)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantStatusTable(
    items: List<ParticipantDataStatus>,
    selectedParticipantId: String?,
    onSelect: (String) -> Unit,
) {
    val headerStyle = MaterialTheme.typography.labelSmall
    val cellStyle = MaterialTheme.typography.bodySmall

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Participant", style = headerStyle, modifier = Modifier.weight(0.40f))
            Text("Last", style = headerStyle, modifier = Modifier.weight(0.22f))
            Text("Records", style = headerStyle, modifier = Modifier.weight(0.14f))
            Text("Files", style = headerStyle, modifier = Modifier.weight(0.12f))
            Text("Size", style = headerStyle, modifier = Modifier.weight(0.12f))
        }

        val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

        items.sortedWith(
            compareByDescending<ParticipantDataStatus> { it.lastTimestamp ?: Instant.EPOCH }
                .thenBy { it.participantId },
        ).forEach { item ->
            val selected = item.participantId == selectedParticipantId
            val shape = MaterialTheme.shapes.small
            val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

            val totalSizeBytes = item.files.sumOf { it.sizeBytes }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clickable { onSelect(item.participantId) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.participantId, style = cellStyle, modifier = Modifier.weight(0.40f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.lastTimestamp?.let { fmt.format(it) } ?: "-", style = cellStyle, modifier = Modifier.weight(0.22f))
                    Text(item.totalRecordCount.toString(), style = cellStyle, modifier = Modifier.weight(0.14f))
                    Text(item.fileCount.toString(), style = cellStyle, modifier = Modifier.weight(0.12f))
                    Text(formatBytes(totalSizeBytes), style = cellStyle, modifier = Modifier.weight(0.12f))
                }
            }
        }
    }
}

@Composable
private fun ParticipantStatusDetails(item: ParticipantDataStatus) {
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))

    val totalSizeBytes = item.files.sumOf { it.sizeBytes }
    val byCategory = item.files.groupBy { it.category }

    Text(
        text = "files=${item.fileCount}, records=${item.totalRecordCount}, size=${formatBytes(totalSizeBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    val order = listOf(OutputFileCategory.EXPORT, OutputFileCategory.ANALYZE, OutputFileCategory.EXCEL, OutputFileCategory.OTHER)
    order.forEach { cat ->
        val list = byCategory[cat].orEmpty()
        if (list.isEmpty()) return@forEach

        Text(
            text = "${cat.name} (${list.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))

        list.sortedByDescending { it.lastTimestamp ?: Instant.EPOCH }
            .forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(f.file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "records=${f.recordCount}, last=${f.lastTimestamp?.let { fmt.format(it) } ?: "-"}, modified=${fmt.format(f.lastModified)}, size=${formatBytes(f.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

        Spacer(Modifier.height(8.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1fMB", mb)
    val gb = mb / 1024.0
    return String.format("%.1fGB", gb)
}
