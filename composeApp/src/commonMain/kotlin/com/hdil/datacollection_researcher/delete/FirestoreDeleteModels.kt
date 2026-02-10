package com.hdil.datacollection_researcher.delete

data class FirestoreDeleteRequest(
    val credentialPath: String,
    /** 참가자 문서 경로. 예) studies/nursing-study-001/participants/<participantId> */
    val docRoot: String,
    /** batch commit 당 삭제 문서 수(1..500). 기본 400 */
    val batchSize: Int = 400,
)

sealed class FirestoreDeleteLogEvent {
    data class Info(val message: String) : FirestoreDeleteLogEvent()
    data class Error(val message: String) : FirestoreDeleteLogEvent()

    data class SubcollectionStarted(val collectionPath: String) : FirestoreDeleteLogEvent()
    data class SubcollectionProgress(val collectionPath: String, val deletedCount: Int) : FirestoreDeleteLogEvent()
    data class SubcollectionFinished(val collectionPath: String, val deletedCount: Int) : FirestoreDeleteLogEvent()

    data class Finished(val deletedTotalCount: Int) : FirestoreDeleteLogEvent()
}

interface FirestoreDeleter {
    fun deleteParticipant(request: FirestoreDeleteRequest): kotlinx.coroutines.flow.Flow<FirestoreDeleteLogEvent>
}
