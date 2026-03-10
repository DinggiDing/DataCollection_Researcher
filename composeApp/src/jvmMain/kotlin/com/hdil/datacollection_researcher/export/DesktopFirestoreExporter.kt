package com.hdil.datacollection_researcher.export

import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.GeoPoint
import com.google.cloud.firestore.Query
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.hdil.datacollection_researcher.config.DateRange
import com.hdil.datacollection_researcher.credentials.AppDirProvider
import com.hdil.datacollection_researcher.credentials.DefaultAppDirProvider
import com.hdil.datacollection_researcher.csv.CsvUtils
import com.hdil.datacollection_researcher.io.ParticipantOutputPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class DesktopFirestoreExporter(
    private val appDirProvider: AppDirProvider = DefaultAppDirProvider(),
) : FirestoreExporter {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    override fun export(request: ExportRequest): Flow<ExportLogEvent> = channelFlow {
        launch(Dispatchers.IO) {
            val outputDir = outputDir().apply { mkdirs() }
            if (!outputDir.exists() || !outputDir.isDirectory) {
                send(ExportLogEvent.Error("output 폴더를 만들 수 없어요: ${outputDir.absolutePath}"))
                return@launch
            }

            val credentialFile = File(request.credentialPath)
            if (!credentialFile.exists()) {
                send(ExportLogEvent.Error("Credentials 파일을 찾을 수 없어요: ${credentialFile.absolutePath}"))
                return@launch
            }

            send(ExportLogEvent.Info("Firestore 연결을 시작합니다…"))

            val firestore = runCatching { createFirestore(credentialFile) }
                .getOrElse { t ->
                    send(ExportLogEvent.Error("Firestore 연결에 실패했어요. 서비스 계정 키와 권한을 확인해 주세요. (${t.message})"))
                    return@launch
                }

            firestore.use { session ->
                val db = session.db
                when (val target = request.target) {
                    is ExportTarget.SingleParticipant -> {
                        val rawDocRoot = target.docRoot.trim()
                        if (rawDocRoot.isBlank()) {
                            send(ExportLogEvent.Error("docRoot가 비어 있어요. participantId를 입력해 주세요."))
                            return@use
                        }
                        exportSingleParticipant(
                            db = db,
                            rawDocRoot = rawDocRoot,
                            participantIdForOutput = target.participantId,
                            request = request,
                            outputDir = outputDir,
                        ) { event -> send(event) }
                    }
                    is ExportTarget.AllParticipants -> {
                        val rawRoot = target.participantsCollectionRoot.trim()
                        if (rawRoot.isBlank()) {
                            send(ExportLogEvent.Error("participantsCollectionRoot가 비어 있어요."))
                            return@use
                        }
                        exportAllParticipants(
                            db = db,
                            rawParticipantsCollectionRoot = rawRoot,
                            request = request,
                            outputDir = outputDir,
                        ) { event -> send(event) }
                    }
                }
            }

            send(ExportLogEvent.Info("Export 완료"))
        }
    }

    private data class CollectionExportResult(
        val outputCsvPath: String,
        val readCount: Long,
        val excludedByDateRangeCount: Long,
        val includedRowCount: Long,
    )
    
    private suspend fun exportSingleParticipant(
        db: Firestore,
        rawDocRoot: String,
        participantIdForOutput: String?,
        request: ExportRequest,
        outputDir: File,
        emit: suspend (ExportLogEvent) -> Unit,
    ) {
        val docRoot = normalizeFirestoreDocumentPath(rawDocRoot)
            .getOrElse { t ->
                emit(ExportLogEvent.Error(t.message ?: "docRoot 형식이 올바르지 않아요."))
                return
            }
        
        val documentRef = runCatching { db.document(docRoot) }
            .getOrElse { t ->
                emit(
                    ExportLogEvent.Error(
                        "docRoot 경로가 올바르지 않아요: '$rawDocRoot'\n" +
                            "예) studies/nursing-study-001/participants/<participantId>\n" +
                            "(${t.message})",
                    ),
                )
                return
            }
        
        val subCollections = runCatching { documentRef.listCollections().toList() }
            .getOrElse { t ->
                emit(ExportLogEvent.Error("하위 컬렉션 목록을 가져오지 못했어요. 권한/경로를 확인해 주세요. (${t.message})"))
                return
            }
        
        if (subCollections.isEmpty()) {
            emit(ExportLogEvent.Info("하위 컬렉션이 없어요: $docRoot"))
            return
        }
        
        emit(ExportLogEvent.Info("export 대상 컬렉션 ${subCollections.size}개를 찾았어요."))
        
        for (col in subCollections) {
            val collectionPath = col.path
            emit(ExportLogEvent.CollectionStarted(collectionPath))
            
            val result = exportSingleCollection(
                collection = col,
                request = request,
                outputDir = outputDir,
                participantIdForOutput = participantIdForOutput,
                onInfo = { msg -> emit(ExportLogEvent.Info(msg)) },
            ) { read, excluded, included ->
                emit(
                    ExportLogEvent.CollectionProgress(
                        collectionPath = collectionPath,
                        readCount = read,
                        excludedByDateRangeCount = excluded,
                        includedRowCount = included,
                    ),
                )
            }
            
            if (result != null) {
                emit(
                    ExportLogEvent.CollectionFinished(
                        collectionPath = collectionPath,
                        outputCsvPath = result.outputCsvPath,
                        readCount = result.readCount,
                        excludedByDateRangeCount = result.excludedByDateRangeCount,
                        includedRowCount = result.includedRowCount,
                    ),
                )
            }
        }
    }
    
    private suspend fun exportAllParticipants(
        db: Firestore,
        rawParticipantsCollectionRoot: String,
        request: ExportRequest,
        outputDir: File,
        emit: suspend (ExportLogEvent) -> Unit,
    ) {
        val collectionRoot = normalizeFirestoreCollectionPath(rawParticipantsCollectionRoot)
            .getOrElse { t ->
                emit(ExportLogEvent.Error(t.message ?: "participantsCollectionRoot 형식이 올바르지 않아요."))
                return
            }
        
        val participants = runCatching { db.collection(collectionRoot) }
            .getOrElse { t ->
                emit(ExportLogEvent.Error("participantsCollectionRoot 경로가 올바르지 않아요: '$rawParticipantsCollectionRoot' (${t.message})"))
                return
            }
        
        val participantRefs = runCatching { participants.listDocuments().toList() }
            .getOrElse { t ->
                emit(ExportLogEvent.Error("참가자 목록을 가져오지 못했어요. 권한/경로를 확인해 주세요. (${t.message})"))
                return
            }
        
        if (participantRefs.isEmpty()) {
            emit(ExportLogEvent.Info("참가자가 없어요: $collectionRoot"))
            return
        }
        
        emit(ExportLogEvent.Info("참가자 ${participantRefs.size}명을 찾았어요."))
        
        for ((index, participantRef) in participantRefs.withIndex()) {
            val participantId = participantRef.id
            emit(ExportLogEvent.Info("Participant ${index + 1}/${participantRefs.size} $participantId 시작"))
            
            val subCollections = runCatching { participantRef.listCollections().toList() }
                .getOrElse { t ->
                    emit(ExportLogEvent.Error("하위 컬렉션 목록을 가져오지 못했어요. ($participantId, ${t.message})"))
                    continue
                }
            
            if (subCollections.isEmpty()) {
                emit(ExportLogEvent.Info("하위 컬렉션이 없어요: ${participantRef.path}"))
                continue
            }
            
            emit(ExportLogEvent.Info("export 대상 컬렉션 ${subCollections.size}개를 찾았어요. ($participantId)"))
            
            for (col in subCollections) {
                val collectionPath = col.path
                emit(ExportLogEvent.CollectionStarted(collectionPath))
                
                val result = exportSingleCollection(
                    collection = col,
                    request = request,
                    outputDir = outputDir,
                    participantIdForOutput = participantId,
                    onInfo = { msg -> emit(ExportLogEvent.Info("[$participantId] $msg")) },
                ) { read, excluded, included ->
                    emit(
                        ExportLogEvent.CollectionProgress(
                            collectionPath = collectionPath,
                            readCount = read,
                            excludedByDateRangeCount = excluded,
                            includedRowCount = included,
                        ),
                    )
                }
                
                if (result != null) {
                    emit(
                        ExportLogEvent.CollectionFinished(
                            collectionPath = collectionPath,
                            outputCsvPath = result.outputCsvPath,
                            readCount = result.readCount,
                            excludedByDateRangeCount = result.excludedByDateRangeCount,
                            includedRowCount = result.includedRowCount,
                        ),
                    )
                }
            }
            
            emit(ExportLogEvent.Info("Participant $participantId 완료"))
        }
    }

    private suspend fun exportSingleCollection(
        collection: CollectionReference,
        request: ExportRequest,
        outputDir: File,
        participantIdForOutput: String?,
        onInfo: suspend (String) -> Unit,
        onProgress: suspend (read: Long, excluded: Long, included: Long) -> Unit,
    ): CollectionExportResult? = withContext(Dispatchers.IO) {
        val limit = request.limit.coerceAtLeast(1)
        val orderByField = request.orderByField.trim().ifBlank { "__name__" }

        val includedDocs = mutableListOf<Pair<String, Map<String, Any?>>>()
        val headerKeys = linkedSetOf<String>("id")

        val baseQuery: Query = buildBaseQuery(
            collection = collection,
            orderByField = orderByField,
            range = request.dateRange,
            limit = limit,
            onInfo = onInfo,
        )
        var lastDoc: DocumentSnapshot? = null

        var readCount = 0L
        var excluded = 0L

        while (true) {
            val effectiveQuery = if (lastDoc != null) baseQuery.startAfter(lastDoc) else baseQuery

            val snapshot = runCatching { effectiveQuery.get().await() }
                .getOrElse { t ->
                    onInfo("쿼리 실패: ${t.message}")
                    onProgress(readCount, excluded, includedDocs.size.toLong())
                    return@withContext null
                }

            if (snapshot.isEmpty) break

            for (doc in snapshot.documents) {
                readCount++

                val data = doc.data
                val include = shouldIncludeByDateRange(data, request.dateRange)
                if (!include) {
                    excluded++
                    continue
                }

                includedDocs += doc.id to data
                headerKeys.addAll(data.keys)
            }

            lastDoc = snapshot.documents.lastOrNull()
            onProgress(readCount, excluded, includedDocs.size.toLong())

            if (snapshot.size() < limit) break
        }

        val safePath = collection.path
            .replace("/", "__")
            .replace("\\", "__")

        // 참가자별 폴더 + 참가자ID 포함 파일명
        val outFile = ParticipantOutputPaths.buildCsvFile(
            baseOutputDir = outputDir,
            participantId = participantIdForOutput,
            filePrefix = "${safePath}_export",
        )

        writeCsv(outFile, headerKeys.toList(), includedDocs)

        CollectionExportResult(
            outputCsvPath = outFile.absolutePath,
            readCount = readCount,
            excludedByDateRangeCount = excluded,
            includedRowCount = includedDocs.size.toLong(),
        )
    }
    
    private suspend fun buildBaseQuery(
        collection: CollectionReference,
        orderByField: String,
        range: DateRange,
        limit: Int,
        onInfo: suspend (String) -> Unit,
    ): Query {
        val start = range.startMillisUtc
        val end = range.endMillisUtc
        val hasRange = start != null || end != null
        if (!hasRange || orderByField == "__name__") {
            return collection.orderBy(orderByField).limit(limit)
        }
        
        // Probe one doc to infer value type (Timestamp vs Number) to avoid returning 0 rows due to type mismatch.
        val probe = runCatching { collection.orderBy(orderByField).limit(1).get().await() }
            .getOrElse { t ->
                onInfo("timerange 서버 필터를 적용하지 못했어요(프로브 실패): ${t.message}")
                return collection.orderBy(orderByField).limit(limit)
            }
        
        val first = probe.documents.firstOrNull()
        val sample = first?.get(orderByField)
        if (sample == null) {
            onInfo("timerange 서버 필터를 적용하지 못했어요($orderByField 필드가 없어요).")
            return collection.orderBy(orderByField).limit(limit)
        }
        
        return when (sample) {
            is Timestamp -> {
                var q: Query = collection
                if (start != null) q = q.whereGreaterThanOrEqualTo(orderByField, timestampFromMillis(start))
                if (end != null) q = q.whereLessThan(orderByField, timestampFromMillis(end))
                onInfo("timerange 서버 필터 적용: field=$orderByField (Timestamp)")
                q.orderBy(orderByField).limit(limit)
            }
            is Number -> {
                val sampleLong = sample.toLong()
                val looksLikeSeconds = sampleLong in 1_000_000_000L..9_999_999_999L
                fun coerce(valueMillis: Long): Long = if (looksLikeSeconds) valueMillis / 1000L else valueMillis
                
                var q: Query = collection
                if (start != null) q = q.whereGreaterThanOrEqualTo(orderByField, coerce(start))
                if (end != null) q = q.whereLessThan(orderByField, coerce(end))
                onInfo(
                    "timerange 서버 필터 적용: field=$orderByField (Number, unit=${if (looksLikeSeconds) "seconds" else "millis"})",
                )
                q.orderBy(orderByField).limit(limit)
            }
            else -> {
                onInfo("timerange 서버 필터를 적용하지 못했어요($orderByField 타입 미지원: ${sample::class.simpleName}).")
                collection.orderBy(orderByField).limit(limit)
            }
        }
    }
    
    private fun timestampFromMillis(millisUtc: Long): Timestamp {
        val seconds = millisUtc.floorDiv(1000L)
        val nanos = (millisUtc % 1000L).toInt() * 1_000_000
        return Timestamp.ofTimeSecondsAndNanos(seconds, nanos)
    }

    private fun shouldIncludeByDateRange(data: Map<String, Any?>, range: DateRange): Boolean {
        val start = range.startMillisUtc
        val end = range.endMillisUtc
        if (start == null && end == null) return true

        val candidateKeys = listOf(
            "ingestedAt",
            "endedAt",
            "ended_at",
            "endAt",
            "createdAt",
            "startedAt",
            "startAt",
        )

        val millis = candidateKeys.firstNotNullOfOrNull { key ->
            parseEpochMillisOrNull(data[key])
        }

        if (millis == null) return true

        if (start != null && millis < start) return false
        if (end != null && millis >= end) return false
        return true
    }

    private fun parseEpochMillisOrNull(value: Any?): Long? {
        return when (value) {
            null -> null
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> {
                val trimmed = value.trim()
                trimmed.toLongOrNull() ?: runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
            }
            else -> null
        }
    }

    private fun writeCsv(
        file: File,
        headers: List<String>,
        rows: List<Pair<String, Map<String, Any?>>>,
    ) {
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine(headers.joinToString(",") { CsvUtils.escape(it) })
            for ((id, data) in rows) {
                val values = headers.map { key ->
                    when (key) {
                        "id" -> id
                        else -> serializeValue(data[key])
                    }
                }
                w.appendLine(values.joinToString(",") { CsvUtils.escape(it) })
            }
        }
    }

    private fun serializeValue(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is Timestamp -> DateTimeFormatter.ISO_INSTANT.format(value.toDate().toInstant())
            is GeoPoint -> json.encodeToString(
                JsonObject(
                    mapOf(
                        "latitude" to JsonPrimitive(value.latitude),
                        "longitude" to JsonPrimitive(value.longitude),
                    ),
                ),
            )
            is DocumentReference -> value.path
            is Map<*, *> -> jsonElementFromAny(value).toString()
            is List<*> -> jsonElementFromAny(value).toString()
            is Boolean, is Number, is String -> value.toString()
            else -> value.toString()
        }
    }

    private fun jsonElementFromAny(any: Any?): kotlinx.serialization.json.JsonElement = when (any) {
        null -> JsonNull
        is String -> JsonPrimitive(any)
        is Boolean -> JsonPrimitive(any)
        is Int -> JsonPrimitive(any)
        is Long -> JsonPrimitive(any)
        is Double -> JsonPrimitive(any)
        is Float -> JsonPrimitive(any.toDouble())
        is Timestamp -> JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(any.toDate().toInstant()))
        is GeoPoint -> buildJsonObject {
            put("latitude", JsonPrimitive(any.latitude))
            put("longitude", JsonPrimitive(any.longitude))
        }
        is DocumentReference -> JsonPrimitive(any.path)
        is Map<*, *> -> buildJsonObject {
            any.forEach { (k, v) ->
                if (k != null) put(k.toString(), jsonElementFromAny(v))
            }
        }
        is List<*> -> buildJsonArray {
            any.forEach { add(jsonElementFromAny(it)) }
        }
        else -> JsonPrimitive(any.toString())
    }

    private fun outputDir(): File = File(appDirProvider.appDir(), "output")

    private fun createFirestore(credentialFile: File): FirestoreSession {
        val credentials = credentialFile.inputStream().use { GoogleCredentials.fromStream(it) }
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        val appName = "researcher-export-${UUID.randomUUID()}"
        val app = FirebaseApp.initializeApp(options, appName)
        return FirestoreSession(
            db = FirestoreClient.getFirestore(app),
            app = app,
        )
    }

    private suspend fun <T> ApiFuture<T>.await(): T = withContext(Dispatchers.IO) {
        get()
    }

    private inline fun <T : Closeable, R> T.use(block: (T) -> R): R {
        try {
            return block(this)
        } finally {
            runCatching { close() }
        }
    }

    private class FirestoreSession(
        val db: Firestore,
        private val app: FirebaseApp,
    ) : Closeable {
        override fun close() {
            runCatching { db.close() }
            runCatching { app.delete() }
        }
    }
    
    private fun normalizeFirestoreCollectionPath(input: String): Result<String> {
        val trimmed = input.trim()
        // Firestore Java SDK는 "/a/b" 형태를 collection path로 받지 않고, "a/b"를 기대합니다.
        val noLeadingSlash = trimmed.trimStart('/')
        
        if (noLeadingSlash.isBlank()) {
            return Result.failure(IllegalArgumentException("participantsCollectionRoot가 비어 있어요."))
        }
        
        // collection/doc 가 번갈아야 하므로 segment 수는 홀수여야 컬렉션 경로입니다.
        val segments = noLeadingSlash.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty() || segments.size % 2 == 0) {
            return Result.failure(
                IllegalArgumentException(
                    "participantsCollectionRoot는 컬렉션 경로여야 해요(컬렉션/문서가 번갈아야 합니다).\n" +
                        "입력: '$input'\n" +
                        "예) studies/nursing-study-001/participants",
                ),
            )
        }
        
        return Result.success(segments.joinToString("/"))
    }

    private fun normalizeFirestoreDocumentPath(input: String): Result<String> {
        val trimmed = input.trim()
        // Firestore Java SDK는 "/a/b" 형태를 document path로 받지 않고, "a/b"를 기대합니다.
        val noLeadingSlash = trimmed.trimStart('/')

        if (noLeadingSlash.isBlank()) {
            return Result.failure(IllegalArgumentException("docRoot가 비어 있어요."))
        }

        // collection/doc 가 번갈아야 하므로 segment 수는 짝수여야 문서 경로입니다.
        val segments = noLeadingSlash.split('/').filter { it.isNotBlank() }
        if (segments.size < 2 || segments.size % 2 != 0) {
            return Result.failure(
                IllegalArgumentException(
                    "docRoot는 문서 경로여야 해요(컬렉션/문서가 번갈아야 합니다).\n" +
                        "입력: '$input'\n" +
                        "예) studies/nursing-study-001/participants/<participantId>",
                ),
            )
        }

        return Result.success(segments.joinToString("/"))
    }
}
