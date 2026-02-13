package com.hdil.datacollection_researcher.dcc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object JsonProps {

    fun string(props: JsonObject, key: String, default: String = ""): String {
        return (props[key] as? JsonPrimitive)?.content ?: default
    }

    fun boolean(props: JsonObject, key: String, default: Boolean = false): Boolean {
        val raw = (props[key] as? JsonPrimitive)?.content ?: return default
        return raw.toBooleanStrictOrNull() ?: raw.toBoolean()
    }

    fun int(props: JsonObject, key: String, default: Int = 0): Int {
        val raw = (props[key] as? JsonPrimitive)?.content ?: return default
        return raw.toIntOrNull() ?: default
    }
}

