package com.hdil.datacollection_researcher.export

import com.hdil.datacollection_researcher.config.AppConfig
import com.hdil.datacollection_researcher.config.DateRange

sealed interface ExportTarget {
    data class SingleParticipant(
        val participantId: String,
        /** 참가자 문서 경로. 예) studies/nursing-study-001/participants/<participantId> */
        val docRoot: String,
    ) : ExportTarget
    
    data class AllParticipants(
        /** participants 컬렉션 경로. 예) studies/nursing-study-001/participants */
        val participantsCollectionRoot: String,
    ) : ExportTarget
}
    
data class ExportRequest(
    val credentialPath: String,
    val target: ExportTarget,
    val dateRange: DateRange,
    val limit: Int = AppConfig.Defaults.LIMIT,
    val orderByField: String = AppConfig.Defaults.ORDER_BY_FIELD,
)

sealed class ExportLogEvent {
    data class Info(val message: String) : ExportLogEvent()
    data class Error(val message: String) : ExportLogEvent()

    data class CollectionStarted(
        val collectionPath: String,
    ) : ExportLogEvent()

    data class CollectionProgress(
        val collectionPath: String,
        val readCount: Long,
        val excludedByDateRangeCount: Long,
        val includedRowCount: Long,
    ) : ExportLogEvent()

    data class CollectionFinished(
        val collectionPath: String,
        val outputCsvPath: String,
        val readCount: Long,
        val excludedByDateRangeCount: Long,
        val includedRowCount: Long,
    ) : ExportLogEvent()
}

data class ExportResult(
    val outputDir: String,
    val generatedFiles: List<String>,
)
