package com.hdil.datacollection_researcher.ui.delete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.delete.FirestoreDeleteViewModel
import com.hdil.datacollection_researcher.status.ParticipantDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteScreen(
    outputDir: File,
    firestoreDeleteViewModel: FirestoreDeleteViewModel,
    participantDataRepository: ParticipantDataRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var participantId by rememberSaveable { mutableStateOf("") }
    var batchSizeText by rememberSaveable { mutableStateOf("400") }
    var localDeleteMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLocalDeleting by rememberSaveable { mutableStateOf(false) }

    val defaultDocRoot = remember(participantId) {
        val id = participantId.trim()
        if (id.isBlank()) "" else "studies/nursing-study-001/participants/$id"
    }
    var docRoot by rememberSaveable(participantId) { mutableStateOf(defaultDocRoot) }

    val parsedBatchSize = remember(batchSizeText) {
        batchSizeText.trim().toIntOrNull()?.coerceIn(1, 500)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Delete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "참가자 ID를 기준으로 로컬(output) 파일 삭제 또는 Firebase(Firestore) 데이터 삭제를 실행합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )

        HorizontalDivider()

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = participantId,
                    onValueChange = { participantId = it },
                    label = { Text("Participant ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = docRoot,
                    onValueChange = { docRoot = it },
                    label = { Text("Firestore docRoot (문서 경로)") },
                    placeholder = { Text("studies/<studyId>/participants/<participantId>") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = batchSizeText,
                    onValueChange = { batchSizeText = it },
                    label = { Text("Firebase batchSize (1~500)") },
                    placeholder = { Text("400") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (localDeleteMessage != null) {
                    Text(localDeleteMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = participantId.trim().isNotBlank() && !isLocalDeleting,
                        onClick = {
                            val id = participantId.trim()
                            isLocalDeleting = true
                            localDeleteMessage = null
                            scope.launch {
                                val deleted = runCatching {
                                    withContext(Dispatchers.IO) {
                                        participantDataRepository.deleteParticipantData(outputDir, id)
                                    }
                                }.getOrElse { -1 }

                                localDeleteMessage = if (deleted >= 0) {
                                    "로컬 삭제 완료: $id ($deleted 개 항목)"
                                } else {
                                    "로컬 삭제 실패: $id"
                                }
                                isLocalDeleting = false
                            }
                        }
                    ) {
                        if (isLocalDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("로컬 output 삭제")
                    }

                    Button(
                        enabled = docRoot.trim().isNotBlank() && parsedBatchSize != null,
                        onClick = { firestoreDeleteViewModel.runDelete(docRoot.trim(), batchSize = parsedBatchSize ?: 400) }
                    ) {
                        Text("Firebase 삭제")
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = { firestoreDeleteViewModel.clearLogs() }) {
                        Text("로그 비우기")
                    }
                }

                Text(
                    "주의: Firebase 삭제는 되돌릴 수 없습니다. 서비스 계정 권한을 꼭 확인하세요.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            val uiState by firestoreDeleteViewModel.uiState.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Firebase Delete Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.isRunning) "실행 중…" else "대기 중",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(min = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (uiState.logs.isEmpty()) {
                        Text("로그가 없습니다.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        uiState.logs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
