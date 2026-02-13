package com.hdil.datacollection_researcher.builder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.hdil.datacollection_researcher.builder.model.BlockType

private data class ComponentCategory(
    val title: String,
    val items: List<SidebarPaletteItem>
)

private data class SidebarPaletteItem(
    val type: BlockType,
    val label: String,
    val icon: ImageVector,
    val defaultProps: Map<String, String>
)

@Composable
fun BuildSidebar(
    modifier: Modifier = Modifier,
    onAddItem: (ComponentCatalogItem) -> Unit,
    onStartDrag: ((ComponentCatalogItem, Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onEndDrag: (() -> Unit)? = null
) {
    val categories = remember {
        listOf(
            ComponentCategory(
                title = "Content",
                items = listOf(
                    SidebarPaletteItem(
                        type = BlockType.TEXT,
                        label = "Header",
                        icon = Icons.Default.Title,
                        defaultProps = mapOf("text" to "New Header", "style" to "titleLarge")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.TEXT,
                        label = "Text Paragraph",
                        icon = Icons.Default.TextFields,
                        defaultProps = mapOf("text" to "Enter text here...", "style" to "bodyMedium")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.DIVIDER,
                        label = "Image",
                        icon = Icons.Default.Image,
                        defaultProps = mapOf("kind" to "border", "src" to "")
                    )
                )
            ),
            ComponentCategory(
                title = "Form",
                items = listOf(
                    SidebarPaletteItem(
                        type = BlockType.TEXT_FIELD,
                        label = "Input Field",
                        icon = Icons.Default.CheckBox,
                        defaultProps = mapOf("label" to "Label", "placeholder" to "Input...")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.TEXT_FIELD,
                        label = "Single Choice",
                        icon = Icons.Default.CheckCircle,
                        defaultProps = mapOf("label" to "Select one", "mode" to "toggle", "options" to "Yes,No")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.BUTTON,
                        label = "Button",
                        icon = Icons.Default.SmartButton,
                        defaultProps = mapOf("text" to "Submit", "actionId" to "submit")
                    )
                )
            ),
            ComponentCategory(
                title = "Layout",
                items = listOf(
                    SidebarPaletteItem(
                        type = BlockType.COLUMN,
                        label = "Column",
                        icon = Icons.Default.ViewStream,
                        defaultProps = mapOf("padding" to "8")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.ROW,
                        label = "Row",
                        icon = Icons.Default.ViewColumn,
                        defaultProps = mapOf("padding" to "8")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.SPACER,
                        label = "Spacer",
                        icon = Icons.Default.Minimize,
                        defaultProps = mapOf("heightDp" to "20")
                    ),
                    SidebarPaletteItem(
                        type = BlockType.DIVIDER,
                        label = "Divider",
                        icon = Icons.Default.Minimize,
                        defaultProps = mapOf("kind" to "line")
                    )
                )
            )
        )
    }

    Column(
        modifier = modifier
            .background(Color.White)
            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)))
            .fillMaxHeight()
    ) {
        // App Builder Title
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Build",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A)
            )
            Text(
                "Drag components to canvas",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }

        HorizontalDivider(color = Color(0xFFE2E8F0))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            categories.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        category.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF94A3B8)
                    )

                    // Grid or List? Let's use a dense list as before but cleaner
                    category.items.forEach { item ->
                        DraggableComponentCard(
                            item = item,
                            onStartDrag = onStartDrag,
                            onDrag = onDrag,
                            onEndDrag = onEndDrag,
                            onClick = {
                                onAddItem(
                                    ComponentCatalogItem(
                                        type = item.type,
                                        title = item.label,
                                        description = "",
                                        badge = "",
                                        presetProps = item.defaultProps
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableComponentCard(
    item: SidebarPaletteItem,
    onStartDrag: ((ComponentCatalogItem, Offset) -> Unit)?,
    onDrag: ((Offset) -> Unit)?,
    onEndDrag: (() -> Unit)?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val catalogItem = remember(item) {
        ComponentCatalogItem(
            type = item.type,
            title = item.label,
            description = "",
            badge = "",
            presetProps = item.defaultProps
        )
    }

    // Modern card style
    val bgColor = if (isHovered) Color(0xFFF8FAFC) else Color.White
    val borderColor = if (isHovered) Color(0xFF3B82F6) else Color(0xFFE2E8F0)

    // Track window Position for drag
    var itemBoundsInWindow by remember { mutableStateOf<IntRect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                itemBoundsInWindow = IntRect(
                    left = pos.x.toInt(),
                    top = pos.y.toInt(),
                    right = (pos.x + size.width).toInt(),
                    bottom = (pos.y + size.height).toInt()
                )
            }
            .pointerInput(catalogItem) {
                detectDragGestures(
                    onDragStart = { localStart ->
                        val rect = itemBoundsInWindow
                        val globalStart = if (rect != null) {
                            Offset(rect.left + localStart.x, rect.top + localStart.y)
                        } else localStart
                        onStartDrag?.invoke(catalogItem, globalStart)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val rect = itemBoundsInWindow
                        // Fix for drag coordinates: Add change.position (relative to element) to element's window origin
                        val globalPos = if (rect != null) {
                            Offset(rect.left + change.position.x, rect.top + change.position.y)
                        } else change.position
                        onDrag?.invoke(globalPos)
                    },
                    onDragEnd = { onEndDrag?.invoke() },
                    onDragCancel = { onEndDrag?.invoke() }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            item.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF334155)
        )
    }
}

