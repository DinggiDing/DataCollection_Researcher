package com.hdil.datacollection_researcher.builder

import com.hdil.datacollection_researcher.builder.io.ProtocolJsonCodec
import com.hdil.datacollection_researcher.builder.model.BlockNode
import com.hdil.datacollection_researcher.builder.model.BlockType
import com.hdil.datacollection_researcher.builder.model.ProtocolDocument
import com.hdil.datacollection_researcher.builder.model.ScreenDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolJsonCodecTest {

    @Test
    fun roundTrip_encodeDecode_preservesDocument() {
        val doc = ProtocolDocument(
            schemaVersion = 1,
            protocolId = "p1",
            title = "T",
            screens = listOf(
                ScreenDocument(
                    id = "s1",
                    title = "S",
                    rootBlocks = listOf(
                        BlockNode(
                            id = "b1",
                            type = BlockType.TEXT,
                            props = mapOf("text" to "hi"),
                        ),
                    ),
                )
            )
        )

        val raw = ProtocolJsonCodec.encode(doc)
        val decoded = ProtocolJsonCodec.decode(raw)

        assertEquals(doc, decoded)
    }
}

