package com.hdil.datacollection_researcher.status

import java.io.File
import java.time.Instant
import java.time.LocalDate

data class ParticipantDataStatus(
    val participantId: String,
    val fileCount: Int,
    val totalRecordCount: Long,
    val lastTimestamp: Instant?,
    val gapCount: Int,
    val missingMinuteCount: Long,
    val files: List<ParticipantStatusFileInfo>,
    val dailySummaries: Map<LocalDate, DailyDataSummary> = emptyMap(),
)

data class DailyDataSummary(
    val date: LocalDate,
    val sensorRecordCount: Long = 0,
    val healthRecordCount: Long = 0,
    val surveyRecordCount: Long = 0,
) {
    val totalCount get() = sensorRecordCount + healthRecordCount + surveyRecordCount

    // Simple heuristic for status
    val status: CoverageStatus get() = when {
        totalCount > 100 -> CoverageStatus.OK
        totalCount > 0 -> CoverageStatus.PARTIAL
        else -> CoverageStatus.MISSING
    }
}

enum class CoverageStatus {
    OK, PARTIAL, MISSING, UNKNOWN
}

data class ParticipantStatusFileInfo(
    val file: File,
    val category: OutputFileCategory,
    val recordCount: Long,
    val lastTimestamp: Instant?,
    val lastModified: Instant,
    val sizeBytes: Long,
    val dailyCounts: Map<LocalDate, Long> = emptyMap(),
)

enum class OutputFileCategory {
    EXPORT,
    ANALYZE,
    EXCEL,
    OTHER,
}
