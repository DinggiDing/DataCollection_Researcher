package com.hdil.datacollection_researcher.builder.ui

// Compose foundation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.KeyboardOptions

// Material icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.SpaceBar

// Material3
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

// Runtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// UI
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.LayoutCoordinates

import com.hdil.datacollection_researcher.builder.io.ProtocolJsonCodec
import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.BlockType
import com.hdil.datacollection_researcher.builder.validate.ProtocolValidator
import com.hdil.datacollection_researcher.builder.vm.BuilderViewModel
import com.hdil.datacollection_researcher.credentials.DesktopFilePicker
import com.hdil.datacollection_researcher.credentials.DesktopOpenFolder

private enum class DragState { NONE, DRAGGING }



@Composable
fun BuilderScreen(
    appDir: File,
    viewModel: BuilderViewModel,
    modifier: Modifier = Modifier,
) {
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var infoText by remember { mutableStateOf("") }

    var draggingItem by remember { mutableStateOf<ComponentCatalogItem?>(null) }
    var dragState by remember { mutableStateOf(DragState.NONE) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isCanvasHover by remember { mutableStateOf(false) }

    // 드롭 타깃(폰 콘텐츠 스크롤 영역) 전역 bounds
    var dropTargetBounds by remember { mutableStateOf<IntRect?>(null) }
    // 드롭 시점의 Y(window 좌표). drop index 계산에 사용
    var latestDragYInWindowPx by remember { mutableStateOf<Float?>(null) }

    // 편집(Edit) 모드에서는 블록 선택/삭제/드롭에 집중하고, 프리뷰(Preview) 모드에서는 입력 상호작용에 집중.
    // 요구사항의 "Interactive Preview"(입력 반응)과 "선택/하이라이트"(편집) 충돌을 피하기 위한 토글.
    var isPreviewMode by rememberSaveable { mutableStateOf(true) }

    fun showInfo(message: String) {
        infoText = message
        showInfoDialog = true
    }

    // 루트 컨테이너(Box)의 window 좌표계 오프셋(px). DragGhost가 window 좌표를 그대로 쓰면 화면 밖으로 튈 수 있어 로컬로 변환한다.
    var rootPosInWindowPx by remember { mutableStateOf(Offset.Zero) }

    Surface(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates: LayoutCoordinates ->
                    rootPosInWindowPx = coordinates.positionInWindow()
                }
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
                BuilderTopBar(
                    appDir = appDir,
                    viewModel = viewModel,
                    onInfo = ::showInfo,
                    isPreviewMode = isPreviewMode,
                    onTogglePreviewMode = { isPreviewMode = it },
                )

                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // CanvasPanel에서 계산한 index를 드롭 시 사용하기 위해 Row 스코프에 둡니다.
                    var computedDropIndex by remember { mutableStateOf<Int?>(null) }

                    // Left: Components
                    BuildSidebar(
                        modifier = Modifier.width(280.dp).fillMaxHeight(), // Slightly narrower for modern look
                        onAddItem = { item ->
                            viewModel.addBlockToRootAt(
                                type = item.type,
                                propsOverride = item.presetProps,
                                index = null
                            )
                        },
                        onStartDrag = { item, startPos ->
                            draggingItem = item
                            dragState = DragState.DRAGGING
                            dragOffset = startPos
                            latestDragYInWindowPx = startPos.y
                        },
                        onDrag = { pos ->
                            if (dragState == DragState.DRAGGING) {
                                dragOffset = pos
                                latestDragYInWindowPx = pos.y

                                val rect = dropTargetBounds
                                isCanvasHover = if (rect == null) {
                                    false
                                } else {
                                    val x = pos.x.toInt()
                                    val y = pos.y.toInt()
                                    x in rect.left..rect.right && y in rect.top..rect.bottom
                                }
                            }
                        },
                        onEndDrag = {
                            val item = draggingItem
                            val insertIndex = computedDropIndex

                            if (dragState == DragState.DRAGGING && isCanvasHover && item != null) {
                                viewModel.addBlockToRootAt(
                                    type = item.type,
                                    propsOverride = item.presetProps,
                                    index = insertIndex,
                                )
                            }

                            dragState = DragState.NONE
                            draggingItem = null
                            isCanvasHover = false
                            latestDragYInWindowPx = null
                            computedDropIndex = null
                        },
                    )

                    // Center: Canvas (Phone)
                    BuilderPanel(
                        title = "Canvas",
                        subtitle = "Preview",
                        modifier = Modifier.width(560.dp).fillMaxHeight(),
                        contentPadding = 12.dp,
                    ) {
                        CanvasPanel(
                            viewModel = viewModel,
                            isDragging = dragState == DragState.DRAGGING,
                            isHovered = isCanvasHover,
                            onDropTargetBoundsChanged = { dropTargetBounds = it },
                            dragYInWindowPx = latestDragYInWindowPx,
                            onComputedDropIndex = { computedDropIndex = it },
                            isPreviewMode = isPreviewMode,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Right: Properties
                    BuilderPanel(
                        title = "Properties",
                        subtitle = "",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        trailing = {
                            IconButton(onClick = { viewModel.selectBlock(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    ) {
                        PropertiesPanel(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            val ghostItem = draggingItem
            if (dragState == DragState.DRAGGING && ghostItem != null) {
                // window(px) -> root local(px)
                val localPx = Offset(
                    x = dragOffset.x - rootPosInWindowPx.x,
                    y = dragOffset.y - rootPosInWindowPx.y,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1_000f)
                        // 고스트가 클릭/드래그를 방해하지 않도록 입력은 통과(비활성 포인터 레이어)
                        .pointerInput(Unit) { /* no-op */ },
                ) {
                    DragGhost(
                        title = ghostItem.title,
                        badge = ghostItem.badge,
                        offsetInRootPx = localPx,
                    )
                }
            }

            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("Validation / Info") },
                    text = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        ) {
                            Text(infoText)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) { Text("OK") }
                    }
                )
            }
        }
    }
}

@Composable
private fun BuilderTopBar(
    appDir: File,
    viewModel: BuilderViewModel,
    onInfo: (String) -> Unit,
    isPreviewMode: Boolean,
    onTogglePreviewMode: (Boolean) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Protocol Builder (Preview)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Components · Canvas · Properties",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPreviewMode) "Interactive Preview" else "Edit Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    Switch(
                        checked = isPreviewMode,
                        onCheckedChange = onTogglePreviewMode,
                        colors = SwitchDefaults.colors(),
                    )
                }

                OutlinedButton(
                    onClick = {
                        // Reset: 현재 선택된 스크린의 rootBlocks만 비웁니다(안전한 초기화)
                        val current = uiState.document
                        val screenId = uiState.selectedScreenId
                        if (screenId == null) return@OutlinedButton

                        val updated = current.copy(
                            screens = current.screens.map { s ->
                                if (s.id == screenId) s.copy(rootBlocks = emptyList()) else s
                            }
                        )
                        viewModel.setDocument(updated)
                        viewModel.selectBlock(null)
                    }
                ) {
                    Text("Reset")
                }

                OutlinedButton(
                    onClick = {
                        val issues = ProtocolValidator.validate(uiState.document)
                        val text = if (issues.isEmpty()) {
                            "No issues found."
                        } else {
                            issues.joinToString("\n") { "[${it.severity}] ${it.location ?: ""} ${it.message}".trim() }
                        }
                        onInfo(text)
                    }
                ) {
                    Text("Validate")
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            val outFile = File(appDir, "protocol.json")
                            outFile.parentFile?.mkdirs()
                            outFile.writeText(ProtocolJsonCodec.encode(uiState.document))
                            runCatching { DesktopOpenFolder.openFolder(outFile.parentFile ?: appDir) }
                            onInfo("Exported to: ${outFile.absolutePath}")
                        }.onFailure {
                            onInfo("Export failed: ${it.message}")
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export JSON")
                }

                Button(
                    onClick = {
                        val selected = DesktopFilePicker.pickJsonFile(initialDirectory = appDir.absolutePath)
                        if (selected != null) {
                            runCatching {
                                val raw = selected.readText()
                                val doc = ProtocolJsonCodec.decode(raw)
                                viewModel.setDocument(doc)
                                onInfo("Imported: ${selected.absolutePath}")
                            }.onFailure {
                                onInfo("Import failed: ${it.message}")
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun BuilderPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 14.dp,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                if (trailing != null) trailing()
            }

            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}



data class ComponentCatalogItem(
    val type: BlockType,
    val title: String,
    val description: String,
    val badge: String,
    val presetProps: Map<String, String> = emptyMap(),
)

// 스샷 느낌의 컴포넌트 아이콘 매핑
private fun catalogIconFor(title: String): ImageVector = when (title) {
    "Header" -> Icons.Default.Title
    "Text Block" -> Icons.Default.TextFields
    "Input Field" -> Icons.Default.CheckBox // Was TextFields
    "Action Button" -> Icons.Default.SmartButton // Was Check
    "Image" -> Icons.Default.Image
    "Single Choice" -> Icons.Default.CheckCircle // Was RadioButtonChecked
    "Spacer" -> Icons.Default.Minimize // Was Outlined.SpaceBar
    else -> Icons.Default.Add
}

@Composable
private fun BadgePill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFEFF6FF), RoundedCornerShape(999.dp))
            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasPanel(
    viewModel: BuilderViewModel,
    isDragging: Boolean,
    isHovered: Boolean,
    onDropTargetBoundsChanged: (IntRect) -> Unit,
    dragYInWindowPx: Float?,
    onComputedDropIndex: (Int?) -> Unit,
    isPreviewMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    // selectedScreenId 가 null/불일치면 첫 screen으로 fallback
    val resolvedScreen = remember(uiState.document, uiState.selectedScreenId) {
        uiState.document.screens.firstOrNull { it.id == uiState.selectedScreenId }
            ?: uiState.document.screens.firstOrNull()
    }

    // 선택 상태 보정(문서가 바뀌었거나 초기화로 null이 된 경우)
    LaunchedEffect(resolvedScreen?.id, uiState.selectedScreenId) {
        val id = resolvedScreen?.id ?: return@LaunchedEffect
        if (uiState.selectedScreenId != id) {
            viewModel.selectScreen(id)
        }
    }

    val screen = resolvedScreen

    // DEBUG: canvas에 데이터가 없어서 안 보이는지/렌더러가 문제인지 구분
    val rootCount = screen?.rootBlocks?.size ?: 0

    val phoneScrollState = rememberScrollState()
    val previewState = rememberPreviewState()

    val density = LocalDensity.current

    // 드롭 타깃(폰 콘텐츠 스크롤 뷰포트) 전역 bounds
    var viewportBoundsInWindow by remember { mutableStateOf<IntRect?>(null) }

    // 블록별 bounds(윈도우 기준). drop index를 실제 경계 기반으로 계산한다.
    val blockBounds = remember { mutableStateMapOf<String, IntRect>() }

    fun computeInsertIndex(dropYInWindowPx: Float): Int? {
        val blocks = screen?.rootBlocks ?: return null
        if (blocks.isEmpty()) return 0

        // bounds가 충분히 모이지 않으면(초기 프레임) null 처리 → 드롭 시 append
        val boundsList = blocks.mapNotNull { b -> blockBounds[b.id]?.let { b.id to it } }
        if (boundsList.isEmpty()) return null

        // 화면에 보이는 순서대로 정렬(top 기준)
        val sorted = boundsList.sortedBy { it.second.top }
        val y = dropYInWindowPx

        for ((index, pair) in sorted.withIndex()) {
            val rect = pair.second
            val mid = (rect.top + rect.bottom) / 2f
            if (y < mid) {
                // sorted 순서가 실제 rootBlocks 순서와 다를 수 있으니, 해당 블록 id를 기준으로 rootBlocks index로 매핑
                val id = pair.first
                return blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: index
            }
        }
        return blocks.size
    }

    // dragY 변화에 맞춰 index를 계속 계산해서 상위에서 drop 시점에 사용 가능하게 함
    LaunchedEffect(dragYInWindowPx, screen?.rootBlocks?.map { it.id }, blockBounds.keys.toSet()) {
        val idx = dragYInWindowPx?.let { computeInsertIndex(it) }
        onComputedDropIndex(idx)
    }

    // 드래그 중 가장자리 근처 자동 스크롤
    LaunchedEffect(isDragging, isHovered, dragYInWindowPx, viewportBoundsInWindow) {
        if (!isDragging || !isHovered) return@LaunchedEffect
        val bounds = viewportBoundsInWindow ?: return@LaunchedEffect
        val y = dragYInWindowPx ?: return@LaunchedEffect

        val edgePx = with(density) { 28.dp.toPx() }
        val maxStepPx = with(density) { 18.dp.toPx() }

        while (isActive && isDragging && isHovered) {
            val topDist = (y - bounds.top).toFloat()
            val bottomDist = (bounds.bottom - y).toFloat()

            val delta = when {
                topDist in 0f..edgePx -> {
                    val t = 1f - (topDist / edgePx)
                    -maxStepPx * t
                }

                bottomDist in 0f..edgePx -> {
                    val t = 1f - (bottomDist / edgePx)
                    maxStepPx * t
                }

                else -> 0f
            }

            if (delta != 0f) {
                phoneScrollState.scrollTo((phoneScrollState.value + delta).roundToInt().coerceAtLeast(0))
            }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // DEBUG overlay
        Text(
            text = "screen=${uiState.selectedScreenId ?: "null"} blocks=$rootCount",
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).zIndex(2_000f),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B),
        )

        // 폰 주변 하이라이트를 주기 위해 프레임 바깥 Box
        Box(
            modifier = Modifier
                .wrapContentSize()
                .background(
                    color = if (isDragging && isHovered) Color(0xFFDBEAFE) else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(10.dp)
        ) {
            PhonePreviewFrame(
                modifier = Modifier.wrapContentSize(),
                phoneWidth = 375.dp,
                phoneHeight = 812.dp,
            ) {
                PreviewAppTheme {
                    Column {
                        TopAppBar(
                            title = { Text(screen?.title?.ifBlank { "Preview" } ?: "Preview") },
                            navigationIcon = {
                                IconButton(onClick = { /* preview only */ }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        )

                        // Content Area: overflow-y-auto 느낌으로 스크롤 + 스크롤바
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                        ) {
                            Box {
                                // 뷰포트 bounds는 스크롤 가능한 영역 자체(Box)에 건다.
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .onGloballyPositioned { coords ->
                                            val rect = coords.boundsInWindow()
                                            viewportBoundsInWindow = IntRect(
                                                left = rect.left.toInt(),
                                                top = rect.top.toInt(),
                                                right = rect.right.toInt(),
                                                bottom = rect.bottom.toInt(),
                                            )
                                            onDropTargetBoundsChanged(viewportBoundsInWindow!!)
                                        }
                                        // 빈 공간 클릭 시 선택 해제(편집 모드에서만)
                                        .clickable(
                                            enabled = !isPreviewMode,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            viewModel.selectBlock(null)
                                        },
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(phoneScrollState)
                                        .padding(top = 12.dp)
                                ) {
                                    if (screen == null) {
                                        Text("No screen")
                                    } else {
                                        RenderEditableBlocks(
                                            blocks = screen.rootBlocks,
                                            selectedId = uiState.selectedBlockId,
                                            isPreviewMode = isPreviewMode,
                                            computedDropIndex = if (isDragging && isHovered) {
                                                dragYInWindowPx?.let { computeInsertIndex(it) }
                                            } else {
                                                null
                                            },
                                            onSelect = { viewModel.selectBlock(it) },
                                            onDelete = { viewModel.deleteRootBlock(it) },
                                            onNodeBounds = { id, rect ->
                                                blockBounds[id] = rect
                                            },
                                            previewInteraction = if (isPreviewMode) PreviewInteraction.Enabled else PreviewInteraction.Disabled,
                                            previewState = previewState,
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }

                                // Desktop에서 스크롤이 잘 보이도록 얇은 스크롤바 제공
                                VerticalScrollbar(
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    adapter = rememberScrollbarAdapter(phoneScrollState),
                                    style = ScrollbarStyle(
                                        minimalHeight = 24.dp,
                                        thickness = 6.dp,
                                        shape = RoundedCornerShape(999.dp),
                                        hoverDurationMillis = 400,
                                        unhoverColor = Color(0x33000000),
                                        hoverColor = Color(0x55000000),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (isDragging) {
                CanvasDropOverlay(isActive = isHovered)
            }
        }
    }
}

@Composable
private fun RenderEditableBlocks(
    blocks: List<BlockNode>,
    selectedId: String?,
    isPreviewMode: Boolean,
    computedDropIndex: Int?,
    onSelect: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onNodeBounds: (String, IntRect) -> Unit,
    previewInteraction: PreviewInteraction,
    previewState: PreviewState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, node ->
            if (computedDropIndex == index) {
                DropIndicatorLine()
            }
            EditableBlockChrome(
                node = node,
                isSelected = node.id == selectedId,
                isPreviewMode = isPreviewMode,
                onSelect = onSelect,
                onDelete = onDelete,
                onBounds = onNodeBounds,
                previewInteraction = previewInteraction,
                previewState = previewState,
            )
        }
        if (computedDropIndex == blocks.size) {
            DropIndicatorLine()
        }
    }
}

@Composable
private fun DropIndicatorLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF3B82F6), RoundedCornerShape(999.dp)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableBlockChrome(
    node: BlockNode,
    isSelected: Boolean,
    isPreviewMode: Boolean,
    onSelect: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onBounds: (String, IntRect) -> Unit,
    previewInteraction: PreviewInteraction,
    previewState: PreviewState,
) {
    // hover는 hoverable + InteractionSource로 처리
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        isSelected -> Color(0xFF3B82F6) // border-blue-500
        isHovered && !isPreviewMode -> Color(0xFF93C5FD)
        else -> Color(0xFFE2E8F0)
    }
    val bgColor = if (isSelected && !isPreviewMode) Color(0x1A3B82F6) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val rect = coords.boundsInWindow()
                onBounds(
                    node.id,
                    IntRect(
                        left = rect.left.toInt(),
                        top = rect.top.toInt(),
                        right = rect.right.toInt(),
                        bottom = rect.bottom.toInt(),
                    )
                )
            }
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            // Preview 모드에서는 입력이 우선. Edit 모드에서만 클릭으로 선택.
            .clickable(
                enabled = !isPreviewMode,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onSelect(node.id) }
            .padding(10.dp)
    ) {
        Column {
            BuiltInRenderer(
                screenTitle = "",
                blocks = listOf(node),
                interaction = previewInteraction,
                state = previewState,
            )
        }

        if (isSelected && !isPreviewMode) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { onDelete(node.id) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun PropertiesPanel(
    viewModel: BuilderViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val screen = uiState.document.screens.firstOrNull { it.id == uiState.selectedScreenId }
    val selected = screen?.rootBlocks?.firstOrNull { it.id == uiState.selectedBlockId }

    Column(modifier) {
        if (selected == null) {
            Text("Select a component", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Click a block in the phone preview to edit its properties.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            return
        }

        // 상단 pill(이미지 느낌)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BadgePill("${selected.type.name.lowercase().replace('_', ' ')}")
            Text("Changes save automatically", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(Modifier.height(12.dp))

        when (selected.type) {
            BlockType.TEXT -> {
                PropertiesSection(title = "CONTENT") {
                    MultilineTextField(
                        label = "Text",
                        value = selected.props["text"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("text", it) },
                        minLines = 4,
                    )
                }

                Spacer(Modifier.height(12.dp))
                PropertiesSection(title = "STYLE") {
                    EnumDropdownField(
                        label = "Style",
                        value = selected.props["style"].orEmpty(),
                        candidates = listOf("titleLarge", "titleMedium", "titleSmall", "bodyLarge", "bodyMedium", "bodySmall"),
                        onSelected = { viewModel.updateSelectedProp("style", it) },
                    )
                }
            }

            BlockType.TEXT_FIELD -> {
                PropertiesSection(title = "CONTENT") {
                    SingleLineTextField(
                        label = "Label",
                        value = selected.props["label"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("label", it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleLineTextField(
                        label = "Placeholder",
                        value = selected.props["placeholder"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("placeholder", it) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                PropertiesSection(title = "BEHAVIOR") {
                    BooleanField(
                        label = "Required",
                        value = selected.props["required"]?.toBooleanStrictOrNull() ?: false,
                        onValueChange = { viewModel.updateSelectedProp("required", it.toString()) },
                    )
                    Spacer(Modifier.height(10.dp))
                    EnumDropdownField(
                        label = "Mode",
                        value = selected.props["mode"].orEmpty(),
                        candidates = listOf("", "text", "time", "toggle"),
                        onSelected = { viewModel.updateSelectedProp("mode", it) },
                    )
                }
            }

            BlockType.BUTTON -> {
                PropertiesSection(title = "CONTENT") {
                    SingleLineTextField(
                        label = "Text",
                        value = selected.props["text"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("text", it) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                PropertiesSection(title = "ACTION") {
                    SingleLineTextField(
                        label = "Action Id",
                        value = selected.props["actionId"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("actionId", it) },
                    )
                }
            }

            BlockType.SPACER -> {
                PropertiesSection(title = "SIZE") {
                    NumberField(
                        label = "Height (dp)",
                        value = selected.props["heightDp"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("heightDp", it) },
                    )
                }
            }

            BlockType.DIVIDER -> {
                PropertiesSection(title = "STYLE") {
                    EnumDropdownField(
                        label = "Kind",
                        value = selected.props["kind"].orEmpty(),
                        candidates = listOf("", "border"),
                        onSelected = { viewModel.updateSelectedProp("kind", it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        label = "Padding Vertical (dp)",
                        value = selected.props["paddingVerticalDp"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("paddingVerticalDp", it) },
                    )
                }
            }

            BlockType.COLUMN, BlockType.ROW -> {
                PropertiesSection(title = "LAYOUT") {
                    BooleanField(
                        label = "Fill width",
                        value = selected.props["fillWidth"]?.toBooleanStrictOrNull() ?: false,
                        onValueChange = { viewModel.updateSelectedProp("fillWidth", it.toString()) },
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleLineTextField(
                        label = "Padding",
                        value = selected.props["padding"].orEmpty(),
                        onValueChange = { viewModel.updateSelectedProp("padding", it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PropertiesSection(title = "VISIBILITY") {
            SingleLineTextField(
                label = "Expression",
                value = selected.visibility?.expression.orEmpty(),
                onValueChange = { viewModel.updateSelectedVisibilityExpression(it) },
                placeholder = "always / q1 == 'yes'",
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "MVP: visibility expression is not evaluated yet; it only shows a hint in preview.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun PropertiesSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(0.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SingleLineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun MultilineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minLines = minLines,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next: String ->
            // 숫자/빈값만 허용
            if (next.isBlank() || next.all { ch -> ch.isDigit() }) {
                onValueChange(next)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun BooleanField(
    label: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = value, onCheckedChange = onValueChange)
    }
}

@Composable
private fun EnumDropdownField(
    label: String,
    value: String,
    candidates: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onSelected(it) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            candidates.forEach { c ->
                DropdownMenuItem(
                    text = { Text(if (c.isBlank()) "(default)" else c) },
                    onClick = {
                        onSelected(c)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DragGhost(
    title: String,
    badge: String,
    offsetInRootPx: Offset,
) {
    val density = LocalDensity.current

    // root local px -> dp로 변환 후 offset 적용
    val xDp = with(density) { offsetInRootPx.x.toDp() }
    val yDp = with(density) { offsetInRootPx.y.toDp() }

    // 포인터를 가리지 않도록 살짝 이동
    val dx = (xDp + 14.dp).coerceAtLeast(0.dp)
    val dy = (yDp + 14.dp).coerceAtLeast(0.dp)

    Row(
        modifier = Modifier
            .zIndex(1_000f)
            .offset(dx, dy)
            .shadow(14.dp, RoundedCornerShape(12.dp))
            .background(Color(0xEEFFFFFF), RoundedCornerShape(12.dp))
            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(26.dp)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(catalogIconFor(title), contentDescription = null, tint = Color(0xFF334155))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(badge, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun PreviewAppTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme.copy(
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = {
            Surface(color = colorScheme.background) {
                content()
            }
        }
    )
}

@Composable
private fun CanvasDropOverlay(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    // 드롭존 시각 피드백(간단): 활성 시 파란색 느낌의 테두리/배경을 더해준다.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isActive) Color(0x1A3B82F6) else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (isActive) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(8.dp),
    )
}
