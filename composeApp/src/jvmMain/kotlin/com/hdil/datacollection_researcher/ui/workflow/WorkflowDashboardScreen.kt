package com.hdil.datacollection_researcher.ui.workflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.ui.SectionCard
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun WorkflowDashboardScreen(
    uiState: WorkflowDashboardUiState,
    onChangeServiceKey: () -> Unit,
    onParticipantIdChanged: (String) -> Unit,
    onSelectRangeQuickOption: (String) -> Unit,
    onRangeStartChanged: (String) -> Unit,
    onRangeEndChanged: (String) -> Unit,
    onClickOutputDirectory: () -> Unit,
    onRunStep: (WorkflowStep) -> Unit,
    onClearStepLogs: (WorkflowStep) -> Unit,
    onSync: () -> Unit,
    onRunAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DashboardTopBar(
            workspaceTitle = uiState.workspaceTitle,
            pageTitle = uiState.pageTitle,
            onSync = onSync,
            onRunAll = onRunAll,
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left column: configuration
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConfigurationCard(
                    uiState = uiState,
                    onChangeServiceKey = onChangeServiceKey,
                    onParticipantIdChanged = onParticipantIdChanged,
                    onSelectRangeQuickOption = onSelectRangeQuickOption,
                    onRangeStartChanged = onRangeStartChanged,
                    onRangeEndChanged = onRangeEndChanged,
                )

                OutputDirectoryCard(
                    path = uiState.outputDirectoryPath,
                    onClick = onClickOutputDirectory,
                )
            }

            // Right side: readiness + (step card + console) rows
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ReadinessCard(
                    title = uiState.readinessTitle,
                    subtitle = uiState.readinessSubtitle,
                    totalSteps = uiState.totalSteps,
                )

                val stepByType = uiState.steps.associateBy { it.step }
                val consoleByType = uiState.stepConsoles.associateBy { it.step }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    WorkflowStep.entries.forEach { stepType ->
                        val step = stepByType[stepType] ?: return@forEach
                        val console = consoleByType[stepType]

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(modifier = Modifier.weight(0.46f)) {
                                StepCard(
                                    step = step,
                                    onRun = { onRunStep(step.step) },
                                    onClear = { onClearStepLogs(step.step) },
                                )
                            }

                            Box(modifier = Modifier.weight(0.54f)) {
                                ConsoleCard(
                                    title = console?.title ?: "CONSOLE OUTPUT",
                                    lines = console?.lines.orEmpty(),
                                    modifier = Modifier.height(220.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    workspaceTitle: String,
    pageTitle: String,
    onSync: () -> Unit,
    onRunAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "$workspaceTitle  ›  $pageTitle",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Button(
            onClick = onSync,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("Sync", color = MaterialTheme.colorScheme.onSurface)
        }

        Button(
            onClick = onRunAll,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Run All")
        }
    }
}

private val ConfigControlHeight = 40.dp
private val ConfigControlShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigurationCard(
    uiState: WorkflowDashboardUiState,
    onChangeServiceKey: () -> Unit,
    onParticipantIdChanged: (String) -> Unit,
    onSelectRangeQuickOption: (String) -> Unit,
    onRangeStartChanged: (String) -> Unit,
    onRangeEndChanged: (String) -> Unit,
) {
    SectionCard(
        title = "CONFIGURATION",
        subtitle = null,
    ) {
        val outline = MaterialTheme.colorScheme.outline
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = outline,
            unfocusedBorderColor = outline,
            disabledBorderColor = outline.copy(alpha = 0.5f),
        )

        Text(
            text = uiState.serviceKeyLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill(text = uiState.serviceKeyStatusText, color = uiState.serviceKeyStatusColor)

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onChangeServiceKey,
                shape = ConfigControlShape,
                modifier = Modifier.height(ConfigControlHeight),
                border = BorderStroke(1.dp, outline),
            ) {
                Text("Change", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        InputLikeRow(
            text = uiState.serviceKeyPath ?: "Select a service key…",
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "PARTICIPANT ID",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.participantId,
            onValueChange = onParticipantIdChanged,
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = true,
            label = { Text("participantId") },
            shape = ConfigControlShape,
            colors = fieldColors,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "TIME RANGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.rangeQuickOptions.forEach { option ->
                val selected = option == uiState.selectedRangeOption

                val colors = if (selected) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = { onSelectRangeQuickOption(option) },
                    shape = ConfigControlShape,
                    modifier = Modifier.height(ConfigControlHeight),
                    colors = colors,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Text(option, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = uiState.rangeStartText,
                onValueChange = onRangeStartChanged,
                modifier = Modifier
                    .weight(1f),
                singleLine = true,
                label = { Text("Start") },
                shape = ConfigControlShape,
                colors = fieldColors,
            )
            OutlinedTextField(
                value = uiState.rangeEndText,
                onValueChange = onRangeEndChanged,
                modifier = Modifier
                    .weight(1f),
                singleLine = true,
                label = { Text("End") },
                shape = ConfigControlShape,
                colors = fieldColors,
            )
        }
    }
}

@Composable
private fun OutputDirectoryCard(
    path: String,
    onClick: () -> Unit,
) {
    SectionCard(
        title = "OUTPUT DIRECTORY",
        subtitle = null,
    ) {
        Text(
            text = "TARGET PATH",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f))
                .padding(12.dp),
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.surface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text("Open in Finder", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ReadinessCard(
    title: String,
    subtitle: String,
    totalSteps: Int,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("i", style = MaterialTheme.typography.titleMedium)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalSteps.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "TOTAL STEPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: StepCardUiModel,
    onRun: () -> Unit,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, shape)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                step.badgeText?.let { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            step.hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(step.status)

                Spacer(Modifier.weight(1f))

                Button(
                    enabled = step.enabled,
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    StepVectorIcon(
                        step = step.step,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 18.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(step.primaryActionText)
                }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    enabled = step.status !is StepRunStatus.Running,
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(step.secondaryActionText, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: StepRunStatus) {
    val (text, bg, fg) = when (status) {
        StepRunStatus.Idle -> Triple("IDLE", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        StepRunStatus.Running -> Triple("RUNNING", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        StepRunStatus.Success -> Triple("SUCCESS", Color(0xFFDCFCE7), Color(0xFF166534))
        is StepRunStatus.Error -> Triple("ERROR", Color(0xFFFEE2E2), Color(0xFF991B1B))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun ConsoleCard(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = Color(0xFF1F1F1F)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, Color(0xFF2A2A2A), shape)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header line with prompt, like the screenshot.
            Text(
                text = ">_  $title",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFFA3A3A3),
            )

            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.takeLast(300).forEachIndexed { index, line ->
                    val isPlaceholderLike = index == 0 && (line.startsWith("Waiting") || line.startsWith("Ready"))
                    val textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = if (isPlaceholderLike) FontStyle.Italic else FontStyle.Normal,
                    )
                    val color = if (isPlaceholderLike) {
                        Color(0xFFE5E5E5).copy(alpha = 0.55f)
                    } else {
                        Color(0xFFE5E5E5)
                    }

                    Text(
                        text = line,
                        style = textStyle,
                        color = color,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// Replace stepIcon(...) with a tiny Canvas-based vector icon
@Composable
private fun StepVectorIcon(
    step: WorkflowStep,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        // DrawScope.size (px)
        val s = this.size
        when (step) {
            WorkflowStep.EXPORT -> {
                val path = Path().apply {
                    moveTo(s.width * 0.34f, s.height * 0.24f)
                    lineTo(s.width * 0.34f, s.height * 0.76f)
                    lineTo(s.width * 0.78f, s.height * 0.50f)
                    close()
                }
                drawPath(path = path, color = tint)
            }

            WorkflowStep.ANALYZE -> {
                val p = Path().apply {
                    moveTo(s.width * 0.55f, s.height * 0.12f)
                    lineTo(s.width * 0.35f, s.height * 0.54f)
                    lineTo(s.width * 0.56f, s.height * 0.54f)
                    lineTo(s.width * 0.42f, s.height * 0.88f)
                    lineTo(s.width * 0.66f, s.height * 0.44f)
                    lineTo(s.width * 0.46f, s.height * 0.44f)
                    close()
                }
                drawPath(path = p, color = tint)
            }

            WorkflowStep.EXCEL -> {
                val strokeWidth = s.minDimension * 0.10f
                val rectLeft = s.width * 0.22f
                val rectTop = s.height * 0.16f
                val rectW = s.width * 0.56f
                val rectH = s.height * 0.68f

                drawRoundRect(
                    color = tint,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectW, rectH),
                    cornerRadius = CornerRadius(
                        x = s.minDimension * 0.12f,
                        y = s.minDimension * 0.12f,
                    ),
                    style = Stroke(width = strokeWidth),
                )

                drawLine(
                    color = tint,
                    start = Offset(s.width * 0.32f, s.height * 0.42f),
                    end = Offset(s.width * 0.68f, s.height * 0.42f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(s.width * 0.32f, s.height * 0.58f),
                    end = Offset(s.width * 0.62f, s.height * 0.58f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// restore Pill(...) used by service key status
@Composable
private fun Pill(
    text: String,
    color: Color,
    textColor: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
private fun InputLikeRow(
    text: String,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = ConfigControlShape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ConfigControlHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
