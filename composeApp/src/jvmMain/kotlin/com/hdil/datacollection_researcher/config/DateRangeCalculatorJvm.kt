package com.hdil.datacollection_researcher.config

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class DateRangePreset { LAST_1D, LAST_7D, LAST_30D }

object DateRangeCalculatorJvm {
    /**
     * Phase 2의 내부 표현은 UTC millis입니다.
     * 프리셋은 "지금으로부터 N일 전"(rolling window) 기준으로 start/end(현재 시각) 밀리초를 만듭니다.
     */
    fun presetToUtcMillis(preset: DateRangePreset, nowUtcMillis: Long = System.currentTimeMillis()): DateRange {
        val days = when (preset) {
            DateRangePreset.LAST_1D -> 1
            DateRangePreset.LAST_7D -> 7
            DateRangePreset.LAST_30D -> 30
        }

        val end = nowUtcMillis
        val start = Instant.ofEpochMilli(nowUtcMillis)
            .minusSeconds(days.toLong() * 24L * 60L * 60L)
            .toEpochMilli()

        return DateRange(startMillisUtc = start, endMillisUtc = end)
    }

    /**
     * Custom 날짜(YYYY-MM-DD)는 UTC의 해당 날짜 00:00:00.000 기준으로 계산합니다.
     * endMillisUtc는 "종료일 다음날 00:00"으로 설정해 inclusive end를 쉽게 표현합니다.
     */
    fun customDatesToUtcMillis(start: LocalDate?, end: LocalDate?): DateRange {
        if (start == null && end == null) return DateRange()

        val startMillis = start?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        val endExclusiveMillis = end?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

        if (startMillis != null && endExclusiveMillis != null && startMillis > endExclusiveMillis) {
            throw DateRangeInputError.StartAfterEnd()
        }

        return DateRange(startMillisUtc = startMillis, endMillisUtc = endExclusiveMillis)
    }
}
