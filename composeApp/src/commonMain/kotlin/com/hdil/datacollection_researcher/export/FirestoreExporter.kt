package com.hdil.datacollection_researcher.export

import kotlinx.coroutines.flow.Flow

interface FirestoreExporter {
    fun export(request: ExportRequest): Flow<ExportLogEvent>
}
