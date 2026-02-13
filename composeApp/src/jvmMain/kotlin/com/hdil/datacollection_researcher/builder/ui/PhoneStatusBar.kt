package com.hdil.datacollection_researcher.builder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun PhoneStatusBar(
    modifier: Modifier = Modifier,
) {
    var timeText by remember { mutableStateOf(currentTimeText()) }

    LaunchedEffect(Unit) {
        while (true) {
            timeText = currentTimeText()
            delay(30_000) // 30초마다 갱신
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
        )
        // 간단한 텍스트 아이콘으로 대체 (Desktop에서 리소스/아이콘 의존 최소화)
        Text(
            text = "5G  | | |  92%",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF334155),
        )
    }
}

private fun currentTimeText(): String {
    val formatter = DateTimeFormatter.ofPattern("H:mm")
    return LocalTime.now().format(formatter)
}
