package com.hdil.datacollection_researcher.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val participantId: String = "",
    /** true면 참가자 1명이 아니라 participants 컬렉션의 모든 참가자를 대상으로 실행합니다. */
    val allParticipants: Boolean = false,
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
    
    /**
     * allParticipants 모드에서 사용할 participants 컬렉션 경로를 반환합니다.
     *
     * - docRoot가 문서 경로(segments 짝수)이면 parent 컬렉션으로 올립니다.
     * - docRoot가 컬렉션 경로(segments 홀수)이면 그대로 사용합니다.
     * - docRoot가 비어 있으면 기본값(studies/nursing-study-001/participants)을 사용합니다.
     */
    fun resolvedParticipantsCollectionRoot(): String {
        val raw = docRoot?.trim().orEmpty()
        if (raw.isBlank()) return "/studies/nursing-study-001/participants"
        val normalized = raw.trim().trimStart('/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return "/studies/nursing-study-001/participants"
        val collectionSegments = if (segments.size % 2 == 0) segments.dropLast(1) else segments
        return "/" + collectionSegments.joinToString("/")
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
