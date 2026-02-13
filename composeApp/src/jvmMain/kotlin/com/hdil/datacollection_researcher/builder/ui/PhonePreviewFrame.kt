package com.hdil.datacollection_researcher.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PhonePreviewFrame(
    modifier: Modifier = Modifier,
    phoneWidth: Dp = 414.dp,
    phoneHeight: Dp = 896.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(phoneWidth, phoneHeight)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(34.dp), clip = false)
                .clip(RoundedCornerShape(34.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(34.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1220), Color(0xFF111827))
                    )
                )
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 상태바(시간/네트워크/배터리) 느낌
                PhoneStatusBar(
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // notch/status bar 영역 (디바이스 느낌)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(160.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 상태바+노치 안전영역
                        .padding(top = 44.dp)
                        .padding(contentPadding)
                ) {
                    content()
                }

                // 홈 인디케이터(하단)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .width(140.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33000000))
                )
            }
        }
    }
}
