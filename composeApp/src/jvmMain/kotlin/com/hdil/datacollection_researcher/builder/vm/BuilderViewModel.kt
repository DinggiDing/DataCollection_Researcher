package com.hdil.datacollection_researcher.builder.vm

import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.BlockType
import com.hdil.datacollection_researcher.builder.model.ProtocolDocument
import com.hdil.datacollection_researcher.builder.model.ScreenDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BuilderViewModel(
    initial: ProtocolDocument = ProtocolDocument(
        screens = listOf(
            ScreenDocument(
                id = "screen-1",
                title = "Main",
                rootBlocks = listOf(
                    BlockNode(
                        id = "col-1",
                        type = BlockType.COLUMN,
                        children = listOf(
                            BlockNode(
                                id = "text-1",
                                type = BlockType.TEXT,
                                props = mapOf("text" to "Hello from Builder", "style" to "titleMedium"),
                            ),
                            BlockNode(
                                id = "border-1",
                                type = BlockType.DIVIDER,
                                props = mapOf("kind" to "border", "paddingVerticalDp" to "8"),
                            ),
                            BlockNode(
                                id = "textField-1",
                                type = BlockType.TEXT_FIELD,
                                props = mapOf("label" to "이름", "required" to "false", "placeholder" to "홍길동"),
                            ),
                            BlockNode(
                                id = "toggle-1",
                                type = BlockType.ROW,
                                props = mapOf("fillWidth" to "true"),
                                children = listOf(
                                    BlockNode(
                                        id = "toggleLabel-1",
                                        type = BlockType.TEXT,
                                        props = mapOf("text" to "동의합니다", "style" to "bodyMedium"),
                                    ),
                                    BlockNode(
                                        id = "toggleSwitch-1",
                                        type = BlockType.TEXT_FIELD,
                                        props = mapOf("label" to "", "mode" to "toggle", "default" to "false"),
                                    ),
                                )
                            ),
                            BlockNode(
                                id = "time-1",
                                type = BlockType.TEXT_FIELD,
                                props = mapOf("label" to "기상 시간", "mode" to "time", "required" to "true", "placeholder" to "07:30"),
                            ),
                            BlockNode(
                                id = "btn-1",
                                type = BlockType.BUTTON,
                                props = mapOf("text" to "다음", "actionId" to "next"),
                            ),
                        )
                    )
                )
            )
        )
    ),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(
        BuilderUiState(
            document = initial,
            selectedScreenId = initial.screens.firstOrNull()?.id,
            selectedBlockId = initial.screens.firstOrNull()?.rootBlocks?.firstOrNull()?.id,
        )
    )
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    fun close() {
        scope.cancel()
    }

    fun setDocument(document: ProtocolDocument) {
        _uiState.update {
            it.copy(
                document = document,
                selectedScreenId = document.screens.firstOrNull()?.id,
                selectedBlockId = null,
            )
        }
    }

    fun selectScreen(screenId: String) {
        _uiState.update {
            it.copy(
                selectedScreenId = screenId,
                selectedBlockId = null,
            )
        }
    }

    fun selectBlock(blockId: String?) {
        _uiState.update { it.copy(selectedBlockId = blockId) }
    }

    fun addBlockToRoot(type: BlockType) {
        addBlockToRoot(type = type, propsOverride = emptyMap())
    }

    fun addBlockToRoot(
        type: BlockType,
        propsOverride: Map<String, String>,
    ) {
        addBlockToRootAt(type = type, propsOverride = propsOverride, index = null)
    }

    /**
     * Canvas에서 드롭된 위치에 따라 rootBlocks의 특정 index에 삽입합니다.
     * index가 null이면 기존 동작처럼 맨 뒤에 추가합니다.
     */
    fun addBlockToRootAt(
        type: BlockType,
        propsOverride: Map<String, String>,
        index: Int?,
    ) {
        val screenId = _uiState.value.selectedScreenId ?: return
        val newId = generateId(type)

        // 기본 props 위에 override를 덮어쓴다(override 우선)
        val mergedProps = defaultProps(type).toMutableMap().apply { putAll(propsOverride) }
        val newNode = BlockNode(id = newId, type = type, props = mergedProps)

        _uiState.update { state ->
            val updated = state.document.screens.map { screen ->
                if (screen.id != screenId) return@map screen

                val list = screen.rootBlocks.toMutableList()
                val safeIndex = index?.coerceIn(0, list.size) ?: list.size
                list.add(safeIndex, newNode)
                screen.copy(rootBlocks = list)
            }
            state.copy(document = state.document.copy(screens = updated), selectedBlockId = newId)
        }
    }

    fun moveRootBlock(blockId: String, direction: MoveDirection) {
        val screenId = _uiState.value.selectedScreenId ?: return
        _uiState.update { state ->
            val updatedScreens = state.document.screens.map { screen ->
                if (screen.id != screenId) return@map screen

                val index = screen.rootBlocks.indexOfFirst { it.id == blockId }
                if (index == -1) return@map screen

                val newIndex = when (direction) {
                    MoveDirection.UP -> (index - 1).coerceAtLeast(0)
                    MoveDirection.DOWN -> (index + 1).coerceAtMost(screen.rootBlocks.lastIndex)
                }
                if (newIndex == index) return@map screen

                val mutable = screen.rootBlocks.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(newIndex, item)
                screen.copy(rootBlocks = mutable)
            }
            state.copy(document = state.document.copy(screens = updatedScreens))
        }
    }

    fun deleteRootBlock(blockId: String) {
        val screenId = _uiState.value.selectedScreenId ?: return
        _uiState.update { state ->
            val updatedScreens = state.document.screens.map { screen ->
                if (screen.id != screenId) return@map screen
                screen.copy(rootBlocks = screen.rootBlocks.filterNot { it.id == blockId })
            }
            state.copy(
                document = state.document.copy(screens = updatedScreens),
                selectedBlockId = state.selectedBlockId.takeIf { it != blockId },
            )
        }
    }

    fun updateSelectedProp(key: String, value: String) {
        val screenId = _uiState.value.selectedScreenId ?: return
        val blockId = _uiState.value.selectedBlockId ?: return

        _uiState.update { state ->
            val updatedScreens = state.document.screens.map { screen ->
                if (screen.id != screenId) return@map screen

                val updatedBlocks = screen.rootBlocks.map { node ->
                    if (node.id != blockId) return@map node
                    node.copy(props = node.props.toMutableMap().apply { put(key, value) })
                }
                screen.copy(rootBlocks = updatedBlocks)
            }

            state.copy(document = state.document.copy(screens = updatedScreens))
        }
    }

    fun updateSelectedVisibilityExpression(expression: String) {
        val screenId = _uiState.value.selectedScreenId ?: return
        val blockId = _uiState.value.selectedBlockId ?: return

        _uiState.update { state ->
            val updatedScreens = state.document.screens.map { screen ->
                if (screen.id != screenId) return@map screen

                val updatedBlocks = screen.rootBlocks.map { node ->
                    if (node.id != blockId) return@map node
                    val vis = if (expression.isBlank()) null else com.hdil.datacollection_researcher.builder.model.VisibilityRule(expression)
                    node.copy(visibility = vis)
                }
                screen.copy(rootBlocks = updatedBlocks)
            }

            state.copy(document = state.document.copy(screens = updatedScreens))
        }
    }

    private fun defaultProps(type: BlockType): Map<String, String> = when (type) {
        BlockType.TEXT -> mapOf("text" to "텍스트")
        BlockType.SPACER -> mapOf("heightDp" to "12")
        BlockType.BUTTON -> mapOf("text" to "버튼", "actionId" to "")
        BlockType.TEXT_FIELD -> mapOf("label" to "입력", "required" to "false")
        BlockType.DIVIDER -> emptyMap()
        BlockType.COLUMN, BlockType.ROW -> emptyMap()
    }

    private fun generateId(type: BlockType): String {
        val prefix = when (type) {
            BlockType.COLUMN -> "col"
            BlockType.ROW -> "row"
            BlockType.TEXT -> "text"
            BlockType.SPACER -> "spacer"
            BlockType.BUTTON -> "btn"
            BlockType.TEXT_FIELD -> "textField"
            BlockType.DIVIDER -> "div"
        }
        return "$prefix-${UUID.randomUUID().toString().take(8)}"
    }
}

enum class MoveDirection { UP, DOWN }

data class BuilderUiState(
    val document: ProtocolDocument,
    val selectedScreenId: String? = null,
    val selectedBlockId: String? = null,
)
