package com.hdil.datacollection_researcher.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface ParticipantDataRepository {
    /**
     * 참가자 ID에 해당하는 output 데이터를 삭제합니다.
     *
     * @return 실제로 삭제된 파일/폴더 엔트리 수(대략치)
     */
    suspend fun deleteParticipantData(outputDir: File, participantId: String): Int
}

class DesktopParticipantDataRepository : ParticipantDataRepository {

    override suspend fun deleteParticipantData(outputDir: File, participantId: String): Int = withContext(Dispatchers.IO) {
        val trimmedId = participantId.trim()
        require(trimmedId.isNotBlank()) { "participantId is blank" }

        val safeDirName = sanitizeSegment(trimmedId)
        val participantDir = File(outputDir, safeDirName)

        if (!participantDir.exists()) return@withContext 0

        if (!participantDir.isDirectory) {
            val deleted = participantDir.delete()
            return@withContext if (deleted) 1 else 0
        }

        val entries = participantDir.walkTopDown().count() // includes root
        participantDir.deleteRecursively()
        entries
    }

    private fun sanitizeSegment(raw: String): String {
        // ParticipantOutputPaths 와 동일 규칙(복사) - 삭제에서는 mkdirs() 부작용을 피하기 위함
        return raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
