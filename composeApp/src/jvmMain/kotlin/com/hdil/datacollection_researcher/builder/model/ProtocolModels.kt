package com.hdil.datacollection_researcher.builder.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * protocol.json 최상위 문서.
 *
 * MVP 스코프:
 * - N개의 screen(화면)
 * - 각 screen은 block 리스트로 구성
 * - block은 type + props(key/value) + children(트리)
 */
@Serializable
data class ProtocolDocument(
    val schemaVersion: Int = 1,
    val protocolId: String = "protocol",
    val title: String = "Untitled Protocol",
    val screens: List<ScreenDocument> = listOf(ScreenDocument()),
    val settings: ProtocolSettings = ProtocolSettings(),
)

@Serializable
data class ProtocolSettings(
    val studyPeriodDays: Int = 14,
    val collectSensor: Boolean = true,
    val collectHealth: Boolean = true,
    val collectSurvey: Boolean = true,
)

@Serializable
data class ScreenDocument(
    val id: String = "screen-1",
    val title: String = "Screen 1",
    val rootBlocks: List<BlockNode> = emptyList(),
)

@Serializable
data class BlockNode(
    val id: String,
    val type: BlockType,
    val props: Map<String, String> = emptyMap(),
    val children: List<BlockNode> = emptyList(),
    val visibility: VisibilityRule? = null,
)

@Serializable
enum class BlockType {
    @SerialName("column") COLUMN,
    @SerialName("row") ROW,
    @SerialName("text") TEXT,
    @SerialName("spacer") SPACER,
    @SerialName("button") BUTTON,
    @SerialName("textField") TEXT_FIELD,
    @SerialName("divider") DIVIDER,
}

@Serializable
data class VisibilityRule(
    val expression: String,
)

