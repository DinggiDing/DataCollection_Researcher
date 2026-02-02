package com.hdil.datacollection_researcher.status

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface ParticipantStatusRepository {
    suspend fun loadStatuses(outputDir: File): List<ParticipantDataStatus>
}

class DesktopParticipantStatusRepository : ParticipantStatusRepository {

    private val participantIdFromFileNameRegex = Regex(
        """studies__[^_]+__participants__([^_]+)__""",
        RegexOption.IGNORE_CASE,
    )

    override suspend fun loadStatuses(outputDir: File): List<ParticipantDataStatus> = withContext(Dispatchers.IO) {
        if (!outputDir.exists() || !outputDir.isDirectory) return@withContext emptyList()

        val allFiles = outputDir.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.name.startsWith(".") }
            .toList()

        if (allFiles.isEmpty()) return@withContext emptyList()

        val grouped = allFiles
            .filter { it.extension.equals("csv", ignoreCase = true) || it.extension.equals("xlsx", ignoreCase = true) }
            .groupBy { f ->
                extractParticipantId(f) ?: "(unknown)"
            }

        grouped.entries
            .mapNotNull { (participantId, files) ->
                // 참가자 파일이 하나도 없으면 제외
                if (files.isEmpty()) return@mapNotNull null
                runCatching { buildStatus(participantId, files) }.getOrNull()
            }
            .sortedBy { it.participantId }
    }

    private fun extractParticipantId(file: File): String? {
        // 1) 파일명이 studies__...__participants__<id>__... 인 경우(id를 추출)
        participantIdFromFileNameRegex.find(file.name)?.let { m ->
            return m.groupValues.getOrNull(1)
        }

        // 2) 폴더 구조가 output/<participantId>/... 형태인 경우
        val parent = file.parentFile?.name
        if (!parent.isNullOrBlank() && parent != "output") return parent

        return null
    }

    private fun buildStatus(participantId: String, files: List<File>): ParticipantDataStatus {
        val fileInfos = files
            .sortedBy { it.name }
            .map { f -> summarizeFile(f) }

        val totalRecordCount = fileInfos.sumOf { it.recordCount }
        val lastTimestamp = fileInfos.mapNotNull { it.lastTimestamp }.maxOrNull()

        // gap은 "분 단위 연속성" 분석이 가능한 korea_time CSV(/분석 csv)에서만 정밀하게 하는 게 맞지만,
        // 여기서는 현재 화면 목적(한눈에 보는 상태)상 간단 버전으로 유지합니다.
        val (gapCount, missingMinuteCount) = computeMinuteGaps(fileInfos)

        return ParticipantDataStatus(
            participantId = participantId,
            fileCount = fileInfos.size,
            totalRecordCount = totalRecordCount,
            lastTimestamp = lastTimestamp,
            gapCount = gapCount,
            missingMinuteCount = missingMinuteCount,
            files = fileInfos,
        )
    }

    private fun summarizeFile(file: File): ParticipantStatusFileInfo {
        val category = classify(file)
        val lastModified = Instant.ofEpochMilli(file.lastModified())
        val sizeBytes = file.length()

        // xlsx는 CSV처럼 라인/타임스탬프 파싱을 하지 않습니다.
        if (file.extension.equals("xlsx", ignoreCase = true)) {
            return ParticipantStatusFileInfo(
                file = file,
                category = category,
                recordCount = 0L,
                lastTimestamp = null,
                lastModified = lastModified,
                sizeBytes = sizeBytes,
            )
        }

        var recordCount = 0L
        var header: List<String>? = null
        var lastTimestamp: Instant? = null

        file.bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.forEachIndexed { idx, line ->
                if (idx == 0) {
                    header = DesktopCsvAnalyzerLineParser.parseCsvLine(line)
                    return@forEachIndexed
                }
                if (line.isBlank()) return@forEachIndexed

                recordCount++
                val parsedHeader = header.orEmpty()
                val values = DesktopCsvAnalyzerLineParser.parseCsvLine(line)

                val ts = extractTimestampInstant(parsedHeader, values)
                if (ts != null) {
                    if (lastTimestamp == null || ts.isAfter(lastTimestamp)) lastTimestamp = ts
                }
            }
        }

        return ParticipantStatusFileInfo(
            file = file,
            category = category,
            recordCount = recordCount,
            lastTimestamp = lastTimestamp,
            lastModified = lastModified,
            sizeBytes = sizeBytes,
        )
    }

    private fun classify(file: File): OutputFileCategory {
        val name = file.name.lowercase()
        return when {
            file.extension.equals("xlsx", ignoreCase = true) -> OutputFileCategory.EXCEL
            name.contains("collection_analysis") || name.contains("_korea_time") || name.contains("analy") -> OutputFileCategory.ANALYZE
            name.contains("_export") || name.contains("export") -> OutputFileCategory.EXPORT
            else -> OutputFileCategory.OTHER
        }
    }

    private fun extractTimestampInstant(header: List<String>, row: List<String>): Instant? {
        if (header.isEmpty() || row.isEmpty()) return null

        val candidates = listOf(
            "ingestedAt",
            "endedAt",
            "endAt",
            "createdAt",
            "startedAt",
            "startAt",
            "timestamp",
            "eventTime",
            "eventTimestamp",
        )

        val idx = candidates.firstNotNullOfOrNull { name ->
            header.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
        } ?: return null

        val raw = row.getOrNull(idx).orEmpty()

        // 1) _korea_time.csv의 기본 포맷
        parseKstDateTimeOrNull(raw)?.let { return it }

        // 2) 기타(혹시 epoch/ISO가 들어오는 경우)
        DesktopCsvAnalyzerLineParser.parseEpochMillisOrNull(raw)?.let { return Instant.ofEpochMilli(it) }

        return null
    }

    private fun parseKstDateTimeOrNull(raw: String?): Instant? {
        val t = raw?.trim()?.trim('"').orEmpty()
        if (t.isBlank()) return null

        val zone = ZoneId.of("Asia/Seoul")
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
        )

        for (p in patterns) {
            val fmt = DateTimeFormatter.ofPattern(p)
            val parsed = runCatching { LocalDateTime.parse(t, fmt) }.getOrNull() ?: continue
            return parsed.atZone(zone).toInstant()
        }

        return null
    }

    private fun computeMinuteGaps(fileInfos: List<ParticipantStatusFileInfo>): Pair<Int, Long> {
        val minutes = fileInfos.mapNotNull { it.lastTimestamp }
            .map { it.atZone(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0).toInstant() }
            .distinct()
            .sorted()

        if (minutes.size < 2) return 0 to 0L

        var gapCount = 0
        var missingMinuteCount = 0L
        for (i in 1 until minutes.size) {
            val prev = minutes[i - 1]
            val cur = minutes[i]
            val diffMin = java.time.Duration.between(prev, cur).toMinutes()
            if (diffMin > 1) {
                gapCount++
                missingMinuteCount += (diffMin - 1)
            }
        }
        return gapCount to missingMinuteCount
    }
}

/**
 * DesktopCsvAnalyzer의 CSV line 파서를 재사용(복붙 최소화)하려고 분리한 유틸.
 * 현재는 ParticipantStatusRepository에서만 사용합니다.
 */
internal object DesktopCsvAnalyzerLineParser {

    fun parseEpochMillisOrNull(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().trim('"')

        val asLong = t.toLongOrNull()
        if (asLong != null) {
            return if (asLong in 1_000_000_000L..9_999_999_999L) asLong * 1000L else asLong
        }

        val normalized = t.replace(' ', 'T')
        return runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull()
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString()
        return result
    }
}
