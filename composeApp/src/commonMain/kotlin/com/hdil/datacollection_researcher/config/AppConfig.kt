package com.hdil.datacollection_researcher.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val participantId: String = "",
    val docRoot: String? = null,
    val dateRange: DateRange = DateRange(),
    val limit: Int = Defaults.LIMIT,
    val orderByField: String = Defaults.ORDER_BY_FIELD,
    val maxBatch: Int = Defaults.MAX_BATCH,
) {
    fun resolvedDocRoot(): String {
        val trimmed = docRoot?.trim().orEmpty()
        if (trimmed.isNotBlank()) return trimmed

        val id = participantId.trim()
        return "/studies/nursing-study-001/participants/$id"
    }

    object Defaults {
        const val LIMIT: Int = 500
        const val ORDER_BY_FIELD: String = "__name__"
        const val MAX_BATCH: Int = 1000
    }
}

@Serializable
data class DateRange(
    val startMillisUtc: Long? = null,
    val endMillisUtc: Long? = null,
)
