package com.hdil.datacollection_researcher.dcc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject

/**
 * DataCollection의 uijson/v1/render/components 쪽 스타일을 참고해서,
 * Desktop(JVM)에서 동작하는 최소 컴포넌트들을 흉내낸 버전(MVP).
 */
object DcLikeComponents {

    @Composable
    fun TextComponent(
        props: JsonObject,
    ) {
        val text = JsonProps.string(props, key = "text")
        val styleName = JsonProps.string(props, key = "style", default = "bodyMedium")

        val textStyle = when (styleName) {
            "displayLarge" -> MaterialTheme.typography.displayLarge
            "displayMedium" -> MaterialTheme.typography.displayMedium
            "displaySmall" -> MaterialTheme.typography.displaySmall
            "headlineLarge" -> MaterialTheme.typography.headlineLarge
            "headlineMedium" -> MaterialTheme.typography.headlineMedium
            "headlineSmall" -> MaterialTheme.typography.headlineSmall
            "titleLarge" -> MaterialTheme.typography.titleLarge
            "titleMedium" -> MaterialTheme.typography.titleMedium
            "titleSmall" -> MaterialTheme.typography.titleSmall
            "bodyLarge" -> MaterialTheme.typography.bodyLarge
            "bodySmall" -> MaterialTheme.typography.bodySmall
            "labelLarge" -> MaterialTheme.typography.labelLarge
            "labelMedium" -> MaterialTheme.typography.labelMedium
            "labelSmall" -> MaterialTheme.typography.labelSmall
            else -> MaterialTheme.typography.bodyMedium
        }

        Text(
            text = text,
            style = textStyle,
            color = Color.Unspecified,
        )
    }

    @Composable
    fun SpacerComponent(
        props: JsonObject,
    ) {
        val height = JsonProps.int(props, key = "heightDp", default = 12).coerceIn(0, 400)
        Spacer(Modifier.height(height.dp))
    }

    @Composable
    fun ColumnComponent(
        props: JsonObject,
        content: @Composable () -> Unit,
    ) {
        val padding = JsonProps.int(props, key = "padding", default = 0).coerceIn(0, 64)
        val fillWidth = JsonProps.boolean(props, key = "fillWidth", default = false)

        val verticalArrangementName = JsonProps.string(props, key = "verticalArrangement", default = "Top")
        val horizontalAlignmentName = JsonProps.string(props, key = "horizontalAlignment", default = "Start")

        val arrangement = when (verticalArrangementName) {
            "Center" -> Arrangement.Center
            "Bottom" -> Arrangement.Bottom
            "SpaceBetween" -> Arrangement.SpaceBetween
            "SpaceAround" -> Arrangement.SpaceAround
            "SpaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Top
        }

        val alignment = when (horizontalAlignmentName) {
            "CenterHorizontally" -> Alignment.CenterHorizontally
            "End" -> Alignment.End
            else -> Alignment.Start
        }

        var modifier: Modifier = Modifier.Companion
        if (fillWidth) modifier = modifier.fillMaxWidth()
        if (padding > 0) modifier = modifier.padding(padding.dp)

        Column(
            modifier = modifier,
            verticalArrangement = arrangement,
            horizontalAlignment = alignment,
        ) {
            content()
        }
    }

    @Composable
    fun TextFieldComponent(
        props: JsonObject,
    ) {
        val label = JsonProps.string(props, key = "label")
        val placeholder = JsonProps.string(props, key = "placeholder")
        val required = JsonProps.boolean(props, key = "required", default = false)

        val valueState = remember { mutableStateOf("") }
        OutlinedTextField(
            value = valueState.value,
            onValueChange = { valueState.value = it },
            label = {
                Text(if (required && label.isNotBlank()) "$label *" else label)
            },
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    @Composable
    fun ButtonComponent(
        props: JsonObject,
        onClick: () -> Unit,
    ) {
        val text = JsonProps.string(props, key = "text", default = "Button")
        Button(onClick = onClick) {
            Text(text)
        }
    }

    @Composable
    fun DividerComponent() {
        HorizontalDivider()
    }

    @Composable
    fun RowComponent(
        props: JsonObject,
        content: @Composable () -> Unit,
    ) {
        val padding = JsonProps.int(props, key = "padding", default = 0).coerceIn(0, 64)
        val fillWidth = JsonProps.boolean(props, key = "fillWidth", default = false)

        var modifier: Modifier = Modifier.Companion
        if (fillWidth) modifier = modifier.fillMaxWidth()
        if (padding > 0) modifier = modifier.padding(padding.dp)

        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }

    @Composable
    fun BorderComponent(
        props: JsonObject,
    ) {
        val paddingVertical = JsonProps.int(props, key = "paddingVerticalDp", default = 8).coerceIn(0, 48)
        Spacer(modifier = Modifier.height(paddingVertical.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(paddingVertical.dp))
    }

    @Composable
    fun ToggleComponent(
        props: JsonObject,
    ) {
        val label = JsonProps.string(props, key = "label")
        val defaultChecked = JsonProps.boolean(props, key = "default", default = false)

        val checkedState = remember { mutableStateOf(defaultChecked) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checkedState.value,
                onCheckedChange = { checkedState.value = it },
            )
        }
    }

    @Composable
    fun TimeFieldComponent(
        props: JsonObject,
    ) {
        val label = JsonProps.string(props, key = "label")
        val placeholder = JsonProps.string(props, key = "placeholder", default = "HH:mm")
        val required = JsonProps.boolean(props, key = "required", default = false)

        val valueState = remember { mutableStateOf("") }
        val errorState = remember { mutableStateOf<String?>(null) }

        fun isValidTime(time: String): Boolean {
            val regex = Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
            return regex.matches(time)
        }

        Column {
            OutlinedTextField(
                value = valueState.value,
                onValueChange = { newValue ->
                    valueState.value = newValue
                    errorState.value = when {
                        newValue.isBlank() -> if (required) "필수 항목입니다." else null
                        isValidTime(newValue) -> null
                        else -> "잘못된 포맷입니다. HH:mm (예: 09:30)"
                    }
                },
                label = { Text(if (required && label.isNotBlank()) "$label *" else label) },
                placeholder = { Text(placeholder) },
                isError = errorState.value != null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            errorState.value?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
