package com.hdil.datacollection_researcher.builder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.BlockType
import com.hdil.datacollection_researcher.dcc.DcLikeComponents
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun BuiltInRenderer(
    screenTitle: String,
    blocks: List<BlockNode>,
    interaction: PreviewInteraction = PreviewInteraction.Enabled,
    state: PreviewState = rememberPreviewState(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (screenTitle.isNotBlank()) {
            Text(screenTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
        }

        blocks.forEach { node ->
            RenderBlock(
                node = node,
                interaction = interaction,
                state = state,
            )
        }
    }
}

sealed interface PreviewInteraction {
    data object Enabled : PreviewInteraction
    data object Disabled : PreviewInteraction
}

class PreviewState internal constructor(
    internal val textByNodeId: MutableMap<String, MutableState<String>>,
    internal val toggleByNodeId: MutableMap<String, MutableState<Boolean>>,
) {
    fun textState(nodeId: String): MutableState<String> {
        return textByNodeId.getOrPut(nodeId) { mutableStateOf("") }
    }

    fun toggleState(nodeId: String, initial: Boolean): MutableState<Boolean> {
        return toggleByNodeId.getOrPut(nodeId) { mutableStateOf(initial) }
    }
}

@Composable
fun rememberPreviewState(): PreviewState {
    val text = remember { mutableStateMapOf<String, MutableState<String>>() }
    val toggle = remember { mutableStateMapOf<String, MutableState<Boolean>>() }
    return remember { PreviewState(textByNodeId = text, toggleByNodeId = toggle) }
}

@Composable
private fun RenderBlock(
    node: BlockNode,
    interaction: PreviewInteraction,
    state: PreviewState,
) {
    // MVP: visibility expression은 실제 평가하지 않고, 존재하면 회색 배경으로 표시만.
    val visHint = node.visibility?.expression?.takeIf { it.isNotBlank() }

    val containerModifier = if (visHint != null) {
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F3F5))
            .padding(8.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    when (node.type) {
        BlockType.COLUMN -> {
            Column(containerModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (visHint != null) {
                    Text("visibleIf: $visHint", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                node.children.forEach { RenderBlock(it, interaction = interaction, state = state) }
            }
        }

        BlockType.ROW -> {
            Row(containerModifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { RenderBlock(it, interaction = interaction, state = state) }
            }
        }

        BlockType.TEXT -> {
            val text = node.props["text"].orEmpty()
            val styleName = node.props["style"]
            val style = when (styleName) {
                "headlineSmall" -> MaterialTheme.typography.headlineSmall
                "titleLarge" -> MaterialTheme.typography.titleLarge
                "titleMedium" -> MaterialTheme.typography.titleMedium
                "bodyLarge" -> MaterialTheme.typography.bodyLarge
                "bodyMedium" -> MaterialTheme.typography.bodyMedium
                "labelSmall" -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.bodyMedium
            }

            Column(containerModifier) {
                if (visHint != null) {
                    Text("visibleIf: $visHint", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Text(text.ifBlank { "(empty text)" }, style = style)
            }
        }

        BlockType.TEXT_FIELD -> {
            val mode = node.props["mode"].orEmpty()
            if (mode == "toggle") {
                // Single choice / toggle placeholder
                val label = node.props["label"].orEmpty().ifBlank { "Option" }
                val initial = node.props["default"].orEmpty().toBoolean()
                val checkedState = remember(node.id) { state.toggleState(node.id, initial) }

                val enabled = interaction is PreviewInteraction.Enabled
                Column(containerModifier) {
                    if (visHint != null) {
                        Text("visibleIf: $visHint", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    OutlinedCard(
                        onClick = {
                            if (enabled) checkedState.value = !checkedState.value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(
                                selected = checkedState.value,
                                onClick = {
                                    if (enabled) checkedState.value = true
                                },
                                enabled = enabled,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Single choice (MVP)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            } else {
                val label = node.props["label"].orEmpty().ifBlank { "Label" }
                val placeholder = node.props["placeholder"].orEmpty()
                val valueState = remember(node.id) { state.textState(node.id) }

                val enabled = interaction is PreviewInteraction.Enabled
                Column(containerModifier) {
                    if (visHint != null) {
                        Text("visibleIf: $visHint", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    OutlinedTextField(
                        value = valueState.value,
                        onValueChange = { if (enabled) valueState.value = it },
                        label = { Text(label) },
                        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = enabled,
                        readOnly = !enabled,
                    )
                }
            }
        }

        BlockType.BUTTON -> {
            val text = node.props["text"].orEmpty().ifBlank { "Button" }
            Column(containerModifier) {
                if (visHint != null) {
                    Text("visibleIf: $visHint", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Button(onClick = { /* preview only */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(text)
                }
            }
        }

        BlockType.SPACER -> {
            val heightDp = node.props["heightDp"]?.toIntOrNull()?.coerceIn(0, 240) ?: 12
            Spacer(Modifier.height(heightDp.dp))
        }

        BlockType.DIVIDER -> {
            val kind = node.props["kind"].orEmpty()
            if (kind == "border") {
                // Image placeholder (MVP)
                Card(
                    modifier = containerModifier,
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Image", fontWeight = FontWeight.SemiBold, color = Color(0xFF475569), fontSize = 14.sp)
                    }
                }
            } else {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RenderDcLike(
    node: BlockNode,
    interaction: PreviewInteraction,
) {
    val props = node.props.toJsonObject()
    val enabled = interaction is PreviewInteraction.Enabled
    when (node.type) {
        BlockType.COLUMN -> {
            DcLikeComponents.ColumnComponent(props) {
                node.children.forEach { RenderDcLike(it, interaction = interaction) }
            }
        }

        BlockType.ROW -> {
            DcLikeComponents.RowComponent(props) {
                node.children.forEach { RenderDcLike(it, interaction = interaction) }
            }
        }

        BlockType.TEXT -> DcLikeComponents.TextComponent(props)

        BlockType.TEXT_FIELD -> {
            val mode = props["mode"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
            if (mode == "time") {
                DcLikeComponents.TimeFieldComponent(props)
            } else {
                // NOTE: DcLikeComponents.TextFieldComponent는 enabled/readOnly 파라미터가 없어서
                // 편집(Edit) 모드에서의 입력 차단은 상위(Builder)에서 PreviewInteraction을 Disabled로 내려
                // Built-in 렌더러를 사용하거나, DcLikeComponents 쪽에 옵션을 추가해야 합니다.
                DcLikeComponents.TextFieldComponent(props)
            }
        }

        BlockType.BUTTON -> DcLikeComponents.ButtonComponent(props, onClick = { /* preview only */ })

        BlockType.DIVIDER -> {
            val kind = props["kind"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
            if (kind == "border") {
                DcLikeComponents.BorderComponent(props)
            } else {
                DcLikeComponents.DividerComponent()
            }
        }

        BlockType.SPACER -> DcLikeComponents.SpacerComponent(
            if (props.containsKey("heightDp")) props else JsonObject(mapOf("heightDp" to JsonPrimitive("12")))
        )
    }
}

private fun Map<String, String>.toJsonObject(): JsonObject {
    return JsonObject(
        entries.associate { (k, v) -> k to JsonPrimitive(v) }
    )
}
