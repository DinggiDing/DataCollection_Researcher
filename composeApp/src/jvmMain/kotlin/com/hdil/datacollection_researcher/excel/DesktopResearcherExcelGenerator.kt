package com.hdil.datacollection_researcher.excel

import com.hdil.datacollection_researcher.analyze.DesktopCsvAnalyzer
import com.hdil.datacollection_researcher.io.ParticipantOutputPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 5: *_korea_time.csv 파일들을 모아 연구자용 XLSX를 생성합니다.
 * - Summary 시트
 * - 컬렉션별 Raw 시트
 */
class DesktopResearcherExcelGenerator : ResearcherExcelGenerator {

    override fun generate(outputDirAbsolutePath: String, participantId: String?): Flow<ExcelLogEvent> = channelFlow {
        launch(Dispatchers.IO) {
            val outputDir = File(outputDirAbsolutePath)
            if (!outputDir.exists() || !outputDir.isDirectory) {
                send(ExcelLogEvent.Error("output 폴더를 찾을 수 없어요: ${outputDir.absolutePath}"))
                return@launch
            }

            val searchRoot = ParticipantOutputPaths.participantDir(outputDir, participantId)

            val koreaTimeCsvs = searchRoot.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() == "csv" }
                .filter { it.name.endsWith("_korea_time.csv") }
                .toList()
                .sortedBy { it.name }

            if (koreaTimeCsvs.isEmpty()) {
                send(ExcelLogEvent.Error("*_korea_time.csv 파일이 없어요. Analyze를 먼저 실행해 주세요."))
                return@launch
            }

            val outXlsx = ParticipantOutputPaths.buildXlsxFile(
                baseOutputDir = outputDir,
                participantId = participantId,
                filePrefix = "KoreaTime_ResearcherView",
            )

            send(ExcelLogEvent.Info("XLSX 생성 시작: ${outXlsx.name}"))

            val workbook = XSSFWorkbook()
            try {
                val headerStyle = workbook.createHeaderStyle()

                // 1) Summary sheet
                val summary = workbook.createSheet("Summary")
                var r = 0
                fun row(vararg values: String) {
                    val row = summary.createRow(r++)
                    values.forEachIndexed { idx, v ->
                        val cell = row.createCell(idx)
                        cell.setCellValue(v)
                        if (r == 1 || (r >= 5 && r == 5)) {
                            // no-op (header handled below)
                        }
                    }
                }

                val participant = participantId.orEmpty()
                row("participantId", participant)
                row("generatedAtUtc", Instant.now().toString())
                row("fileCount", koreaTimeCsvs.size.toString())
                r++

                val headerRow = summary.createRow(r++)
                listOf("collectionKey", "fileName", "records", "columns").forEachIndexed { i, h ->
                    val cell = headerRow.createCell(i)
                    cell.setCellValue(h)
                    cell.cellStyle = headerStyle
                }

                for (csvFile in koreaTimeCsvs) {
                    val loaded = loadCsv(csvFile)
                    val row = summary.createRow(r++)
                    row.createCell(0).setCellValue(loaded.collectionKey)
                    row.createCell(1).setCellValue(csvFile.name)
                    row.createCell(2).setCellValue(loaded.records.toDouble())
                    row.createCell(3).setCellValue(loaded.columns.toDouble())

                    // 2) raw sheet
                    val sheetName = safeSheetName("${loaded.collectionKey} (raw)")
                    val sheet = workbook.createSheet(sheetName)

                    val hRow = sheet.createRow(0)
                    loaded.header.forEachIndexed { i, h ->
                        val c = hRow.createCell(i)
                        c.setCellValue(h)
                        c.cellStyle = headerStyle
                    }

                    val maxRows = 50_000
                    loaded.rows.take(maxRows).forEachIndexed { idx, cells ->
                        val dataRow = sheet.createRow(idx + 1)
                        cells.forEachIndexed { i, v ->
                            dataRow.createCell(i).setCellValue(v)
                        }
                    }

                    // 가독성을 위해 첫 행 기준으로 간단한 auto-size (너무 많은 컬럼이면 느릴 수 있어 상한)
                    val autoSizeLimit = minOf(loaded.header.size, 25)
                    for (i in 0 until autoSizeLimit) {
                        runCatching { sheet.autoSizeColumn(i) }
                    }
                }

                // summary도 auto-size
                for (i in 0..3) runCatching { summary.autoSizeColumn(i) }

                FileOutputStream(outXlsx).use { fos ->
                    workbook.write(fos)
                }

                send(ExcelLogEvent.Finished(outXlsx.absolutePath))
            } catch (t: Throwable) {
                send(ExcelLogEvent.Error("XLSX 생성에 실패했어요: ${t.message}"))
            } finally {
                runCatching { workbook.close() }
            }
        }
    }

    private data class LoadedCsv(
        val collectionKey: String,
        val records: Int,
        val columns: Int,
        val header: List<String>,
        val rows: List<List<String>>,
    )

    private fun loadCsv(file: File): LoadedCsv {
        val csv = DesktopCsvAnalyzer.CsvFile.read(file)
        val header = csv.header
        val rows = csv.rows

        val collectionKey = extractCollectionKey(file.name)
        return LoadedCsv(
            collectionKey = collectionKey,
            records = rows.size,
            columns = header.size,
            header = header,
            rows = rows,
        )
    }

    private fun extractCollectionKey(fileName: String): String {
        val base = fileName.substringBefore("_export_")
        val tokens = base.split("__").filter { it.isNotBlank() }
        return tokens.lastOrNull() ?: base
    }

    private fun safeSheetName(raw: String): String {
        // Excel sheet name restrictions: <=31, cannot contain: : \\ / ? * [ ]
        val cleaned = raw.replace(Regex("[:\\\\/?*\\[\\]]"), " ").trim()
        return cleaned.take(31).ifBlank { "Sheet" }
    }

    private fun XSSFWorkbook.createHeaderStyle(): XSSFCellStyle {
        val font: Font = createFont().apply {
            bold = true
        }
        val style = createCellStyle() as XSSFCellStyle
        style.setFont(font)
        return style
    }
}
