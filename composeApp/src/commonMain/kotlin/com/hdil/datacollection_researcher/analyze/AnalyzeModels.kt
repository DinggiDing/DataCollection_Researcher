package com.hdil.datacollection_researcher.analyze

sealed class AnalyzeLogEvent {
    data class Info(val message: String) : AnalyzeLogEvent()
    data class Error(val message: String) : AnalyzeLogEvent()
    data class FileStarted(val inputPath: String) : AnalyzeLogEvent()
    data class FileFinished(val inputPath: String, val outputKoreaTimeCsv: String, val outputAnalysisCsv: String) : AnalyzeLogEvent()
}

interface CsvAnalyzer {
    fun run(outputDirAbsolutePath: String): kotlinx.coroutines.flow.Flow<AnalyzeLogEvent>
}
