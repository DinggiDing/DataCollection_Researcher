package com.hdil.datacollection_researcher.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Desktop 연구용 툴 느낌(차분/클린)을 목표로 한 라이트 테마.
 *
 * - 배경을 너무 어둡게 하지 않아 흰 글씨가 필요 없는 구성을 기본으로 합니다.
 */
@Composable
fun ResearcherTheme(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = LightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        ),
        content = content,
    )
}

// 툴스러운 중립 라이트 팔레트(잔잔한 회색 배경 + 선명한 카드)
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),

    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFFFFFFFF),

    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F172A),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),

    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),

    outline = Color(0xFFE2E8F0),
)
