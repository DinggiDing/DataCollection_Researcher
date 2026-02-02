package com.hdil.datacollection_researcher.status

import java.io.File
import java.time.Instant

data class ParticipantDataStatus(
    val participantId: String,
    val fileCount: Int,
    val totalRecordCount: Long,
    val lastTimestamp: Instant?,
    val gapCount: Int,
    val missingMinuteCount: Long,
    val files: List<ParticipantStatusFileInfo>,
)

data class ParticipantStatusFileInfo(
    val file: File,
    val category: OutputFileCategory,
    val recordCount: Long,
    val lastTimestamp: Instant?,
    val lastModified: Instant,
    val sizeBytes: Long,
)

enum class OutputFileCategory {
    EXPORT,
    ANALYZE,
    EXCEL,
    OTHER,
}
