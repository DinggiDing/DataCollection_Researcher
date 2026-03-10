package com.hdil.datacollection_researcher.excel

import com.hdil.datacollection_researcher.config.DateRange

sealed class ExcelLogEvent {
    data class Info(val message: String) : ExcelLogEvent()
    data class Error(val message: String) : ExcelLogEvent()
    data class Finished(val outputXlsxPath: String) : ExcelLogEvent()
}

interface ResearcherExcelGenerator {
    fun generate(
        outputDirAbsolutePath: String,
        participantId: String?,
        dateRange: DateRange,
    ): kotlinx.coroutines.flow.Flow<ExcelLogEvent>
}
