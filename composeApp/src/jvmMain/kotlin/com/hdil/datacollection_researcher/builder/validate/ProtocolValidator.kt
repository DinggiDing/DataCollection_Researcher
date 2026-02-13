package com.hdil.datacollection_researcher.builder.validate

import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.ProtocolDocument

object ProtocolValidator {

    data class Issue(
        val severity: Severity,
        val message: String,
        val location: String? = null,
    )

    enum class Severity { ERROR, WARNING }

    fun validate(document: ProtocolDocument): List<Issue> {
        val issues = mutableListOf<Issue>()

        if (document.schemaVersion != 1) {
            issues += Issue(
                severity = Severity.ERROR,
                message = "지원하지 않는 schemaVersion=${document.schemaVersion} (지원: 1)",
                location = "schemaVersion",
            )
        }

        val screenIds = mutableSetOf<String>()
        document.screens.forEachIndexed { index, screen ->
            if (screen.id.isBlank()) {
                issues += Issue(Severity.ERROR, "screen.id 는 비어있을 수 없습니다.", "screens[$index].id")
            } else if (!screenIds.add(screen.id)) {
                issues += Issue(Severity.ERROR, "screen.id 중복: ${screen.id}", "screens[$index].id")
            }

            val blockIds = mutableSetOf<String>()
            screen.rootBlocks.forEach { block ->
                validateBlockTree(block, blockIds, issues, locationPrefix = "screens[$index].rootBlocks")
            }
        }

        return issues
    }

    private fun validateBlockTree(
        node: BlockNode,
        blockIds: MutableSet<String>,
        issues: MutableList<Issue>,
        locationPrefix: String,
    ) {
        val loc = "$locationPrefix/${node.id}"

        if (node.id.isBlank()) {
            issues += Issue(Severity.ERROR, "block.id 는 비어있을 수 없습니다.", loc)
        } else if (!blockIds.add(node.id)) {
            issues += Issue(Severity.ERROR, "block.id 중복: ${node.id}", loc)
        }

        node.visibility?.expression?.let { expr ->
            if (expr.isBlank()) {
                issues += Issue(Severity.WARNING, "visibility.expression 이 비어있습니다.", "$loc/visibility")
            }
        }

        node.children.forEach { child ->
            validateBlockTree(child, blockIds, issues, locationPrefix = "$loc/children")
        }
    }
}

