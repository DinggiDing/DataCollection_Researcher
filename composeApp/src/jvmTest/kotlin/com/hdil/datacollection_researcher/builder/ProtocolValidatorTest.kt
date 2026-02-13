package com.hdil.datacollection_researcher.builder

import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.BlockType
import com.hdil.datacollection_researcher.builder.model.ProtocolDocument
import com.hdil.datacollection_researcher.builder.model.ScreenDocument
import com.hdil.datacollection_researcher.builder.validate.ProtocolValidator
import kotlin.test.Test
import kotlin.test.assertTrue

class ProtocolValidatorTest {

    @Test
    fun validate_duplicateBlockIds_reportsError() {
        val doc = ProtocolDocument(
            screens = listOf(
                ScreenDocument(
                    id = "s1",
                    rootBlocks = listOf(
                        BlockNode(id = "dup", type = BlockType.TEXT),
                        BlockNode(id = "dup", type = BlockType.TEXT_FIELD),
                    )
                )
            )
        )

        val issues = ProtocolValidator.validate(doc)
        assertTrue(issues.any { it.severity == ProtocolValidator.Severity.ERROR && it.message.contains("block.id 중복") })
    }
}

