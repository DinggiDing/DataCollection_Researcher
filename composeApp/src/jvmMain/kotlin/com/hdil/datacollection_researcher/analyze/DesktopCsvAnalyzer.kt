package com.hdil.datacollection_researcher.analyze

import com.hdil.datacollection_researcher.csv.CsvUtils
import com.hdil.datacollection_researcher.io.ParticipantOutputPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DesktopCsvAnalyzer : CsvAnalyzer {

    override fun run(outputDirAbsolutePath: String): Flow<AnalyzeLogEvent> = channelFlow {
        launch(Dispatchers.IO) {
            val outputDir = File(outputDirAbsolutePath)
            if (!outputDir.exists() || !outputDir.isDirectory) {
                send(AnalyzeLogEvent.Error("output 폴더를 찾을 수 없어요: ${outputDir.absolutePath}"))
                return@launch
            }

            // Step2: Export 결과(_export)가 들어있는 CSV만 입력으로 취급합니다.
            // 파일명 끝에 시간이 있든 없든 상관없이 동작해야 합니다.
            val inputFiles = outputDir.listFiles()
                ?.filter { it.isFile }
                ?.filter { it.extension.equals("csv", ignoreCase = true) }
                ?.filter { it.name.contains("_export", ignoreCase = true) }
                ?.filter { !it.name.contains("_korea_time", ignoreCase = true) }
                ?.filter { !it.name.contains("collection_analysis", ignoreCase = true) }
                .orEmpty()
                .sortedBy { it.name }

            if (inputFiles.isEmpty()) {
                send(AnalyzeLogEvent.Info("분석할 CSV가 없어요. (export 후 다시 시도해 주세요)"))
                return@launch
            }

            for (file in inputFiles) {
                send(AnalyzeLogEvent.FileStarted(file.absolutePath))
                val result = runCatching { analyzeSingleFile(file, File(outputDirAbsolutePath)) }
                    .getOrElse { t ->
                        send(AnalyzeLogEvent.Error("분석 실패: ${file.name} (${t.message})"))
                        continue
                    }

                send(
                    AnalyzeLogEvent.FileFinished(
                        inputPath = file.absolutePath,
                        outputKoreaTimeCsv = result.koreaTimeCsv.absolutePath,
                        outputAnalysisCsv = result.analysisCsv.absolutePath,
                    ),
                )
            }

            send(AnalyzeLogEvent.Info("Analyze 완료"))
        }
    }

    private data class AnalyzeResult(
        val koreaTimeCsv: File,
        val analysisCsv: File,
    )

    private fun analyzeSingleFile(input: File, outputDir: File): AnalyzeResult {
        val parsed = CsvFile.read(input)
        val header = parsed.header
        val rows = parsed.rows

        val participantId = input.parentFile?.name

        val timestampRegex = Regex(
            "ingestedAt|endedAt|endAt|startedAt|startAt|createdAt|updatedAt|timestamp|timeStamp|eventTime|eventTimestamp|recordedAt|receivedAt|uploadedAt",
            RegexOption.IGNORE_CASE,
        )

        val timestampColumns = header.filter { col ->
            timestampRegex.containsMatchIn(col)
        }

        // 1) KST 변환 CSV 생성
        val koreaTimeFile = ParticipantOutputPaths.buildCsvFile(
            baseOutputDir = outputDir,
            participantId = participantId,
            filePrefix = input.nameWithoutExtension + "_korea_time",
        )
        koreaTimeFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine(header.joinToString(",") { CsvUtils.escape(it) })
            for (row in rows) {
                val out = header.mapIndexed { idx, col ->
                    val v = row.getOrNull(idx).orEmpty()
                    if (timestampColumns.any { it.equals(col, ignoreCase = true) }) {
                        convertToKstStringOrOriginal(v)
                    } else {
                        v
                    }
                }
                w.appendLine(out.joinToString(",") { CsvUtils.escape(it) })
            }
        }

        // 2) 1분 커버리지 분석
        val priorityCols = listOf("ingestedAt", "endedAt", "endAt", "createdAt", "startedAt", "startAt")
        val chosenCol = priorityCols.firstOrNull { p -> header.any { it.equals(p, ignoreCase = true) } }

        val kstZone = ZoneId.of("Asia/Seoul")
        val minuteKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        val minuteKeys = mutableListOf<String>()
        val epochByMinute = linkedMapOf<String, Long>()

        if (chosenCol != null) {
            val colIndex = header.indexOfFirst { it.equals(chosenCol, ignoreCase = true) }
            for (row in rows) {
                val raw = row.getOrNull(colIndex)
                val epoch = parseEpochMillisOrNull(raw)
                if (epoch != null) {
                    val minute = Instant.ofEpochMilli(epoch)
                        .atZone(kstZone)
                        .withSecond(0)
                        .withNano(0)
                    val key = minuteKeyFormatter.format(minute)
                    if (!epochByMinute.containsKey(key)) {
                        epochByMinute[key] = epoch
                        minuteKeys += key
                    }
                }
            }
        }

        val sortedMinutes = minuteKeys.distinct().sorted()

        val missing = mutableListOf<String>()
        for (i in 1 until sortedMinutes.size) {
            val prev = LocalDateTime.parse(sortedMinutes[i - 1], minuteKeyFormatter)
            val cur = LocalDateTime.parse(sortedMinutes[i], minuteKeyFormatter)
            val diff = java.time.Duration.between(prev, cur).toMinutes()
            if (diff > 1) {
                missing += "${sortedMinutes[i - 1]} -> ${sortedMinutes[i]} (missing ${diff - 1}m)"
            }
        }

        val analysisFile = ParticipantOutputPaths.buildCsvFile(
            baseOutputDir = outputDir,
            participantId = participantId,
            filePrefix = "collection_analysis",
        )

        analysisFile.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine("key,value".split(',').joinToString(",") { CsvUtils.escape(it) })
            w.appendLine(listOf("inputFile", input.name).joinToString(",") { CsvUtils.escape(it) })
            w.appendLine(listOf("recordCount", rows.size.toString()).joinToString(",") { CsvUtils.escape(it) })
            w.appendLine(listOf("timestampField", (chosenCol ?: "(none)")).joinToString(",") { CsvUtils.escape(it) })
            w.appendLine(listOf("coveredMinutes", sortedMinutes.size.toString()).joinToString(",") { CsvUtils.escape(it) })

            if (sortedMinutes.isNotEmpty()) {
                w.appendLine(listOf("minMinute", sortedMinutes.first()).joinToString(",") { CsvUtils.escape(it) })
                w.appendLine(listOf("maxMinute", sortedMinutes.last()).joinToString(",") { CsvUtils.escape(it) })
            }

            w.appendLine("".trim())
            w.appendLine("minute".let { CsvUtils.escape(it) })
            for (m in sortedMinutes) {
                w.appendLine(CsvUtils.escape(m))
            }

            w.appendLine("".trim())
            w.appendLine(CsvUtils.escape("gaps"))
            if (missing.isEmpty()) {
                w.appendLine(CsvUtils.escape("(none)"))
            } else {
                missing.forEach { w.appendLine(CsvUtils.escape(it)) }
            }
        }

        return AnalyzeResult(koreaTimeCsv = koreaTimeFile, analysisCsv = analysisFile)
    }

    private fun convertToKstStringOrOriginal(raw: String): String {
        val epoch = parseEpochMillisOrNull(raw) ?: return raw
        val kst = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("Asia/Seoul"))
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(kst)
    }

    private fun parseEpochMillisOrNull(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().trim('"')

        // 1) epoch millis / seconds
        val asLong = t.toLongOrNull()
        if (asLong != null) {
            // 10자리면 seconds로 보고 ms로 변환(대충 2001~2286 범위)
            return if (asLong in 1_000_000_000L..9_999_999_999L) asLong * 1000L else asLong
        }

        // 2) ISO-8601
        // Instant.parse는 "2020-01-01T00:00:00Z"는 되지만 "2020-01-01 00:00:00Z" 같은 변형은 실패할 수 있어 보정
        val normalized = t.replace(' ', 'T')
        return runCatching { Instant.parse(normalized).toEpochMilli() }
            .getOrNull()
    }

    data class CsvFile(val header: List<String>, val rows: List<List<String>>) {
        companion object {
            fun read(file: File): CsvFile {
                val lines = file.readLines(Charsets.UTF_8)
                if (lines.isEmpty()) return CsvFile(emptyList(), emptyList())
                val header = parseCsvLine(lines.first())
                val rows = lines.drop(1).filter { it.isNotBlank() }.map { parseCsvLine(it) }
                return CsvFile(header, rows)
            }

            private fun parseCsvLine(line: String): List<String> {
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
    }
}
