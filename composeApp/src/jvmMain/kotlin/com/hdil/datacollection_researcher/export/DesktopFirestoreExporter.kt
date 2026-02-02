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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

            firestore.use { db ->
                val rawDocRoot = request.docRoot.trim()
                if (rawDocRoot.isBlank()) {
                    send(ExportLogEvent.Error("docRoot가 비어 있어요. participantId를 입력해 주세요."))
                    return@use
                }

                val docRoot = normalizeFirestoreDocumentPath(rawDocRoot)
                    .getOrElse { t ->
                        send(ExportLogEvent.Error(t.message ?: "docRoot 형식이 올바르지 않아요."))
                        return@use
                    }

                val documentRef: DocumentReference = runCatching { db.document(docRoot) }
                    .getOrElse { t ->
                        send(
                            ExportLogEvent.Error(
                                "docRoot 경로가 올바르지 않아요: '$rawDocRoot'\n" +
                                    "예) studies/nursing-study-001/participants/<participantId>\n" +
                                    "(${t.message})",
                            ),
                        )
                        return@use
                    }

                val subCollections = runCatching { documentRef.listCollections().toList() }
                    .getOrElse { t ->
                        send(ExportLogEvent.Error("하위 컬렉션 목록을 가져오지 못했어요. 권한/경로를 확인해 주세요. (${t.message})"))
                        return@use
                    }

                if (subCollections.isEmpty()) {
                    send(ExportLogEvent.Info("하위 컬렉션이 없어요: $docRoot"))
                    return@use
                }

                send(ExportLogEvent.Info("export 대상 컬렉션 ${subCollections.size}개를 찾았어요."))

                for (col in subCollections) {
                    val collectionPath = col.path
                    send(ExportLogEvent.CollectionStarted(collectionPath))

                    val result = exportSingleCollection(
                        collection = col,
                        request = request,
                        outputDir = outputDir,
                    ) { read, excluded, included ->
                        send(
                            ExportLogEvent.CollectionProgress(
                                collectionPath = collectionPath,
                                readCount = read,
                                excludedByDateRangeCount = excluded,
                                includedRowCount = included,
                            ),
                        )
                    }

                    if (result != null) {
                        send(
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

            send(ExportLogEvent.Info("Export 완료"))
        }
    }

    private data class CollectionExportResult(
        val outputCsvPath: String,
        val readCount: Long,
        val excludedByDateRangeCount: Long,
        val includedRowCount: Long,
    )

    private suspend fun exportSingleCollection(
        collection: CollectionReference,
        request: ExportRequest,
        outputDir: File,
        onProgress: suspend (read: Long, excluded: Long, included: Long) -> Unit,
    ): CollectionExportResult? = withContext(Dispatchers.IO) {
        val limit = request.limit.coerceAtLeast(1)
        val orderByField = request.orderByField.ifBlank { "__name__" }

        val includedDocs = mutableListOf<Pair<String, Map<String, Any?>>>()
        val headerKeys = linkedSetOf<String>("id")

        val baseQuery: Query = collection.orderBy(orderByField).limit(limit)
        var lastDoc: DocumentSnapshot? = null

        var readCount = 0L
        var excluded = 0L

        while (true) {
            val effectiveQuery = if (lastDoc != null) baseQuery.startAfter(lastDoc) else baseQuery

            val snapshot = runCatching { effectiveQuery.get().await() }
                .getOrElse {
                    onProgress(readCount, excluded, includedDocs.size.toLong())
                    return@withContext null
                }

            if (snapshot.isEmpty) break

            for (doc in snapshot.documents) {
                readCount++

                val data = doc.data.orEmpty()
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

        val timestampPart = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())

        val safePath = collection.path
            .replace("/", "__")
            .replace("\\", "__")

        // 참가자별 폴더 + 참가자ID 포함 파일명
        val outFile = ParticipantOutputPaths.buildCsvFile(
            baseOutputDir = outputDir,
            participantId = request.participantId,
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

    private fun createFirestore(credentialFile: File): Firestore {
        val usedApp = FirebaseApp.getApps().firstOrNull()
        if (usedApp != null) {
            return FirestoreClient.getFirestore(usedApp)
        }

        val credentials = credentialFile.inputStream().use { GoogleCredentials.fromStream(it) }
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        val app = FirebaseApp.initializeApp(options)
        return FirestoreClient.getFirestore(app)
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
