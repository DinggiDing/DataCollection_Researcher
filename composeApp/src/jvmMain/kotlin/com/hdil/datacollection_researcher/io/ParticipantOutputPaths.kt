package com.hdil.datacollection_researcher.io

import java.io.File

/**
 * output/<participantId>/ 아래에 파일을 저장하고,
 * 파일명은 prefix만으로 구성합니다.
 *
 * - participantId는 폴더명으로만 사용합니다.
 * - 동일한 prefix 파일이 이미 있으면 덮어쓰지 않고 " (2)", " (3)" 처럼 suffix를 붙여 저장합니다.
 */
object ParticipantOutputPaths {

    fun participantDir(baseOutputDir: File, participantId: String?): File {
        val id = participantId?.trim().orEmpty().ifBlank { "unknown" }
        val dir = File(baseOutputDir, sanitizeSegment(id))
        dir.mkdirs()
        return dir
    }

    fun buildCsvFile(
        baseOutputDir: File,
        participantId: String?,
        filePrefix: String,
    ): File {
        val dir = participantDir(baseOutputDir, participantId)
        val prefix = sanitizeSegment(filePrefix).ifBlank { "export" }
        return uniqueFile(dir = dir, baseName = prefix, extension = "csv")
    }

    fun buildXlsxFile(
        baseOutputDir: File,
        participantId: String?,
        filePrefix: String,
    ): File {
        val dir = participantDir(baseOutputDir, participantId)
        val prefix = sanitizeSegment(filePrefix).ifBlank { "report" }
        return uniqueFile(dir = dir, baseName = prefix, extension = "xlsx")
    }

    private fun uniqueFile(dir: File, baseName: String, extension: String): File {
        val initial = File(dir, "$baseName.$extension")
        if (!initial.exists()) return initial

        var n = 2
        while (true) {
            val candidate = File(dir, "$baseName ($n).$extension")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private fun sanitizeSegment(raw: String): String {
        // 파일/폴더명에 위험한 문자 제거(Windows/macOS 공통 고려)
        return raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
