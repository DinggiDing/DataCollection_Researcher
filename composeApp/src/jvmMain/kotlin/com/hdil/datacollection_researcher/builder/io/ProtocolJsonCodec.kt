package com.hdil.datacollection_researcher.builder.io

import com.hdil.datacollection_researcher.builder.model.ProtocolDocument
import kotlinx.serialization.json.Json

object ProtocolJsonCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(document: ProtocolDocument): String = json.encodeToString(ProtocolDocument.serializer(), document)

    fun decode(raw: String): ProtocolDocument = json.decodeFromString(ProtocolDocument.serializer(), raw)
}

