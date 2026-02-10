package com.hdil.datacollection_researcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppSection(
    val title: String,
) {
    WORKFLOW("Workflow"),
    WORKSPACE("Workspace"),
    CONFIGURATION("Configuration"),
    DELETE("Delete"),
    DOCUMENTATION("Documentation"),
    LOGS("Logs")
}

@Composable
fun AppSidebar(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp, start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF4F46E5), RoundedCornerShape(8.dp)), // Indigo color
                    contentAlignment = Alignment.Center
                ) {
                    Text("R", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ResearchOS",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "v0.0.1 (Stable)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // MAIN Section
            Text(
                text = "MAIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            SidebarItem(
                section = AppSection.WORKFLOW,
                isSelected = selected == AppSection.WORKFLOW,
                onClick = { onSelect(AppSection.WORKFLOW) }
            )
            SidebarItem(
                section = AppSection.WORKSPACE,
                isSelected = selected == AppSection.WORKSPACE,
                onClick = { onSelect(AppSection.WORKSPACE) }
            )
            SidebarItem(
                section = AppSection.CONFIGURATION,
                isSelected = selected == AppSection.CONFIGURATION,
                onClick = { onSelect(AppSection.CONFIGURATION) }
            )
            SidebarItem(
                section = AppSection.DELETE,
                isSelected = selected == AppSection.DELETE,
                onClick = { onSelect(AppSection.DELETE) }
            )

            // SYSTEM Section
            Text(
                text = "SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
            )

            SidebarItem(
                section = AppSection.DOCUMENTATION,
                isSelected = selected == AppSection.DOCUMENTATION,
                onClick = { onSelect(AppSection.DOCUMENTATION) }
            )
            SidebarItem(
                section = AppSection.LOGS,
                isSelected = selected == AppSection.LOGS,
                onClick = { onSelect(AppSection.LOGS) }
            )
        }

        // User Profile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SB", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Seongjae Bae",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "HDIL (Yonsei)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), CircleShape) // Green status dot
                )
            }
        }
    }
}

@Composable
fun SidebarItem(
    section: AppSection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    // Command shortcut label (mock)
    val shortcut = when (section) {
        AppSection.WORKFLOW -> "⌘1"
        AppSection.WORKSPACE -> "⌘2"
        AppSection.CONFIGURATION -> "⌘,"
        AppSection.DELETE -> "⌘⌫"
        else -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        SectionIcon(section, contentColor, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = section.title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (shortcut.isNotEmpty()) {
             Box(
                modifier = Modifier
                    .background(if (isSelected) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                 Text(
                     text = shortcut,
                     style = MaterialTheme.typography.labelSmall,
                     color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
                 )
             }
        }
    }
}

@Composable
fun SectionIcon(section: AppSection, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = w.coerceAtMost(h)
        val stroke = s * 0.1f

        when (section) {
            AppSection.WORKFLOW -> {
                // Dashboard-like grid
                val gap = s * 0.15f
                val rectS = (s - gap) / 2
                drawRoundRect(color = tint, topLeft = Offset(0f, 0f), size = Size(rectS, rectS), cornerRadius = CornerRadius(2f))
                drawRoundRect(color = tint, topLeft = Offset(rectS + gap, 0f), size = Size(rectS, rectS), cornerRadius = CornerRadius(2f))
                drawRoundRect(color = tint, topLeft = Offset(0f, rectS + gap), size = Size(rectS, rectS), cornerRadius = CornerRadius(2f))
                drawRoundRect(color = tint, topLeft = Offset(rectS + gap, rectS + gap), size = Size(rectS, rectS), cornerRadius = CornerRadius(2f))
            }
            AppSection.WORKSPACE -> {
                // Folder
                val p = Path().apply {
                    moveTo(0f, h * 0.2f)
                    lineTo(w * 0.4f, h * 0.2f)
                    lineTo(w * 0.5f, h * 0.3f) // tab slant
                    lineTo(w, h * 0.3f)
                    lineTo(w, h * 0.85f)
                    lineTo(0f, h * 0.85f)
                    close()
                }
                drawPath(p, tint, style = Stroke(width = stroke))
            }
            AppSection.CONFIGURATION -> {
                // Settings (Circle with hole)
                drawCircle(tint, radius = s * 0.4f, style = Stroke(width = stroke))
                drawCircle(tint, radius = s * 0.15f, style = Stroke(width = stroke))
            }
            AppSection.DOCUMENTATION -> {
                // Info (Circle with i)
                drawCircle(tint, radius = s * 0.45f, style = Stroke(width = stroke))
                drawCircle(tint, center = center.copy(y = h * 0.35f), radius = s * 0.05f) // dot
                drawLine(tint, start = center.copy(y = h * 0.45f), end = center.copy(y = h * 0.75f), strokeWidth = stroke)
            }
            AppSection.LOGS -> {
                // List
                val lineH = h * 0.2f
                drawLine(tint, start = Offset(0f, lineH), end = Offset(w, lineH), strokeWidth = stroke)
                drawLine(tint, start = Offset(0f, lineH * 2.5f), end = Offset(w, lineH * 2.5f), strokeWidth = stroke)
                drawLine(tint, start = Offset(0f, lineH * 4f), end = Offset(w * 0.7f, lineH * 4f), strokeWidth = stroke)
            }
            AppSection.DELETE -> {
                // Trash can
                val stroke2 = stroke
                // lid
                drawLine(tint, start = Offset(w * 0.25f, h * 0.25f), end = Offset(w * 0.75f, h * 0.25f), strokeWidth = stroke2)
                // body
                val left = w * 0.3f
                val right = w * 0.7f
                val top = h * 0.3f
                val bottom = h * 0.85f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(2f),
                    style = Stroke(width = stroke2),
                )
                // handle
                drawLine(tint, start = Offset(w * 0.45f, h * 0.18f), end = Offset(w * 0.55f, h * 0.18f), strokeWidth = stroke2)
            }
        }
    }
}
