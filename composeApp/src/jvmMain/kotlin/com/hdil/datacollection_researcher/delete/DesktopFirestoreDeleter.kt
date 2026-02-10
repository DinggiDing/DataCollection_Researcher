package com.hdil.datacollection_researcher.delete

import com.google.api.core.ApiFuture
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.CollectionReference
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class DesktopFirestoreDeleter : FirestoreDeleter {

    override fun deleteParticipant(request: FirestoreDeleteRequest): Flow<FirestoreDeleteLogEvent> = channelFlow {
        launch(Dispatchers.IO) {
            val credentialFile = File(request.credentialPath)
            if (!credentialFile.exists()) {
                send(FirestoreDeleteLogEvent.Error("Credentials 파일을 찾을 수 없어요: ${credentialFile.absolutePath}"))
                return@launch
            }

            val docRoot = normalizeFirestoreDocumentPath(request.docRoot)
                .getOrElse { t ->
                    send(FirestoreDeleteLogEvent.Error(t.message ?: "docRoot 형식이 올바르지 않아요."))
                    return@launch
                }

            send(FirestoreDeleteLogEvent.Info("Firestore 연결을 시작합니다…"))

            val firestore = runCatching { createFirestore(credentialFile) }
                .getOrElse { t ->
                    send(FirestoreDeleteLogEvent.Error("Firestore 연결에 실패했어요. 서비스 계정 키와 권한을 확인해 주세요. (${t.message})"))
                    return@launch
                }

            firestore.use { db ->
                val rootDoc: DocumentReference = runCatching { db.document(docRoot) }
                    .getOrElse { t ->
                        send(FirestoreDeleteLogEvent.Error("docRoot 경로가 올바르지 않아요: '${request.docRoot}' (${t.message})"))
                        return@use
                    }

                // 1) 하위 컬렉션 전체 삭제
                val subCollections = runCatching { rootDoc.listCollections().toList() }
                    .getOrElse { t ->
                        send(FirestoreDeleteLogEvent.Error("하위 컬렉션 목록을 가져오지 못했어요. 권한/경로를 확인해 주세요. (${t.message})"))
                        return@use
                    }

                val batchSize = request.batchSize.coerceIn(1, 500)

                var totalDeleted = 0
                for (col in subCollections) {
                    send(FirestoreDeleteLogEvent.SubcollectionStarted(col.path))
                    val deleted = deleteCollectionRecursively(col, batchSize = batchSize) { count ->
                        send(FirestoreDeleteLogEvent.SubcollectionProgress(col.path, count))
                    }
                    totalDeleted += deleted
                    send(FirestoreDeleteLogEvent.SubcollectionFinished(col.path, deleted))
                }

                // 2) 마지막으로 참가자 문서 삭제
                val rootDeleted = runCatching { rootDoc.delete().await(); true }.getOrElse { false }
                if (rootDeleted) totalDeleted += 1

                send(FirestoreDeleteLogEvent.Finished(deletedTotalCount = totalDeleted))
            }
        }
    }

    private suspend fun deleteCollectionRecursively(
        collection: CollectionReference,
        batchSize: Int,
        onProgress: suspend (deletedCount: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        // Firestore는 컬렉션 자체를 삭제하지 못하고, 문서를 삭제하면 컬렉션은 비게 됩니다.
        // 중요한 점: 어떤 문서가 하위 컬렉션을 가지고 있으면, 그 문서를 먼저 지우는 방식으로는
        // (환경/보안 규칙/SDK 동작에 따라) 하위 컬렉션 접근이 끊겨 삭제가 누락될 수 있습니다.
        // 따라서 "리프 컬렉션부터" 반복적으로 지우는 방식으로 끝까지 비워줍니다.

        var deletedTotal = 0

        while (true) {
            val snapshot = runCatching {
                collection.orderBy("__name__").limit(batchSize).get().await()
            }.getOrElse {
                return@withContext deletedTotal
            }

            if (snapshot.isEmpty) break

            // 1) 현재 페이지의 문서들에 대해, 하위 컬렉션을 먼저 삭제
            for (doc in snapshot.documents) {
                val subCols = runCatching { doc.reference.listCollections().toList() }
                    .getOrElse { emptyList() }
                for (sub in subCols) {
                    deletedTotal += deleteCollectionRecursively(sub, batchSize = batchSize, onProgress = onProgress)
                }
            }

            // 2) 이제 하위 컬렉션이 정리됐다고 가정하고, 문서들을 배치 삭제
            val batch = collection.firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }

            runCatching { batch.commit().await() }.getOrElse { return@withContext deletedTotal }
            deletedTotal += snapshot.size()
            onProgress(deletedTotal)

            // 다음 페이지로
            if (snapshot.size() < batchSize) break
        }

        deletedTotal
    }

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
        val noLeadingSlash = trimmed.trimStart('/')

        if (noLeadingSlash.isBlank()) {
            return Result.failure(IllegalArgumentException("docRoot가 비어 있어요."))
        }

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

    private companion object {
        const val DEFAULT_BATCH_SIZE = 400
    }
}
