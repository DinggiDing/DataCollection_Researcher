package com.hdil.datacollection_researcher.config

import kotlinx.datetime.LocalDate

sealed class DateRangeInputError(message: String) : IllegalArgumentException(message) {
    class InvalidFormat(fieldName: String) : DateRangeInputError("$fieldName 날짜 형식이 올바르지 않아요. YYYY-MM-DD 형식으로 입력해 주세요.")
    class StartAfterEnd : DateRangeInputError("시작일이 종료일보다 늦어요. 날짜를 확인해 주세요.")
}

fun parseLocalDateOrNull(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()
