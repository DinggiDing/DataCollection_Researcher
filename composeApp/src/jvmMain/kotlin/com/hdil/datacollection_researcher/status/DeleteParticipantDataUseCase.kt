package com.hdil.datacollection_researcher.status

import java.io.File

class DeleteParticipantDataUseCase(
    private val repository: ParticipantDataRepository,
) {
    suspend operator fun invoke(outputDir: File, participantId: String): Int {
        val id = participantId.trim()
        require(id.isNotBlank()) { "participantId is blank" }
        require(id != "(unknown)" && id != "unknown") { "Cannot delete unknown participant group" }

        return repository.deleteParticipantData(outputDir, id)
    }
}
