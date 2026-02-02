package com.hdil.datacollection_researcher.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AppConfigViewModel(
    private val repository: AppConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(AppConfigUiState())
    val uiState: StateFlow<AppConfigUiState> = _uiState.asStateFlow()

    fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.loadOrDefault() }
                .onSuccess { config ->
                    _uiState.update {
                        val base = it.copy(
                            isLoading = false,
                            participantId = config.participantId,
                            docRootOverride = config.docRoot.orEmpty(),
                            dateRangeResult = config.dateRange,
                            dateRangeError = null,
                            limit = config.limit.toString(),
                            orderByField = config.orderByField,
                            maxBatch = config.maxBatch.toString(),
                        )

                        base.restoreDateRangeInputsFromPersisted(config.dateRange)
                    }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(isLoading = false, message = toUserMessage(t)) }
                }
        }
    }

    fun onParticipantIdChanged(value: String) {
        _uiState.update { it.copy(participantId = value) }
    }

    fun onDocRootOverrideChanged(value: String) {
        _uiState.update { it.copy(docRootOverride = value) }
    }

    fun selectPreset(preset: DateRangePreset) {
        val range = DateRangeCalculatorJvm.presetToUtcMillis(preset)
        _uiState.update {
            it.copy(
                preset = preset,
                customStartDate = "",
                customEndDate = "",
                dateRangeResult = range,
                dateRangeError = null,
            )
        }
    }

    fun onCustomStartChanged(value: String) {
        _uiState.update { it.copy(customStartDate = value, preset = null) }
        recalcCustomDateRange()
    }

    fun onCustomEndChanged(value: String) {
        _uiState.update { it.copy(customEndDate = value, preset = null) }
        recalcCustomDateRange()
    }

    fun clearDateRange() {
        _uiState.update {
            it.copy(
                preset = null,
                customStartDate = "",
                customEndDate = "",
                dateRangeResult = DateRange(),
                dateRangeError = null,
            )
        }
    }

    fun onLimitChanged(value: String) {
        _uiState.update { it.copy(limit = value) }
    }

    fun onOrderByFieldChanged(value: String) {
        _uiState.update { it.copy(orderByField = value) }
    }

    fun onMaxBatchChanged(value: String) {
        _uiState.update { it.copy(maxBatch = value) }
    }

    fun save() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }

            val current = _uiState.value

            val limit = current.limit.trim().toIntOrNull() ?: AppConfig.Defaults.LIMIT
            val maxBatch = current.maxBatch.trim().toIntOrNull() ?: AppConfig.Defaults.MAX_BATCH

            val config = AppConfig(
                participantId = current.participantId.trim(),
                docRoot = current.docRootOverride.trim().takeIf { it.isNotBlank() },
                dateRange = current.dateRangeResult,
                limit = limit,
                orderByField = current.orderByField.trim().ifBlank { AppConfig.Defaults.ORDER_BY_FIELD },
                maxBatch = maxBatch,
            )

            runCatching { repository.save(config) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, message = "설정을 저장했어요.") }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(isLoading = false, message = toUserMessage(t)) }
                }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun recalcCustomDateRange() {
        val state = _uiState.value
        val startText = state.customStartDate.trim().takeIf { it.isNotBlank() }
        val endText = state.customEndDate.trim().takeIf { it.isNotBlank() }

        val startDate = startText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val endDate = endText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        if (startText != null && startDate == null) {
            _uiState.update { it.copy(dateRangeError = DateRangeInputError.InvalidFormat("Start Date").message) }
            return
        }
        if (endText != null && endDate == null) {
            _uiState.update { it.copy(dateRangeError = DateRangeInputError.InvalidFormat("End Date").message) }
            return
        }

        runCatching { DateRangeCalculatorJvm.customDatesToUtcMillis(startDate, endDate) }
            .onSuccess { range ->
                _uiState.update { it.copy(dateRangeResult = range, dateRangeError = null) }
            }
            .onFailure { t ->
                _uiState.update { it.copy(dateRangeError = toUserMessage(t)) }
            }
    }

    private fun AppConfigUiState.restoreDateRangeInputsFromPersisted(range: DateRange): AppConfigUiState {
        val start = range.startMillisUtc
        val end = range.endMillisUtc

        // 아무 범위도 없으면 초기화
        if (start == null && end == null) {
            return copy(preset = null, customStartDate = "", customEndDate = "")
        }

        // preset 판별(대략적인 rolling-window): end가 now와 가깝고, start~end 차이가 1/7/30일 근처면 프리셋으로 표시
        val now = System.currentTimeMillis()
        val endIsNearNow = end != null && kotlin.math.abs(end - now) <= 10L * 60L * 1000L // 10분
        val durationMillis = if (start != null && end != null) end - start else null

        if (endIsNearNow && durationMillis != null) {
            val oneDay = 24L * 60L * 60L * 1000L
            val preset = when {
                kotlin.math.abs(durationMillis - oneDay) <= 3L * 60L * 1000L -> DateRangePreset.LAST_1D
                kotlin.math.abs(durationMillis - 7L * oneDay) <= 3L * 60L * 1000L -> DateRangePreset.LAST_7D
                kotlin.math.abs(durationMillis - 30L * oneDay) <= 3L * 60L * 1000L -> DateRangePreset.LAST_30D
                else -> null
            }
            if (preset != null) {
                return copy(preset = preset, customStartDate = "", customEndDate = "")
            }
        }

        // Custom: UTC millis를 YYYY-MM-DD로 역변환하여 텍스트에 표시
        val startLocalDate = start?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        val endExclusiveLocalDate = end?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

        // endMillisUtc는 "다음날 00:00"(exclusive)로 저장했으므로, 화면에는 (end-1day) 표시
        val endInclusiveDate = endExclusiveLocalDate?.minusDays(1)

        return copy(
            preset = null,
            customStartDate = startLocalDate?.toString().orEmpty(),
            customEndDate = endInclusiveDate?.toString().orEmpty(),
        )
    }

    private fun toUserMessage(t: Throwable): String {
        val msg = t.message
        return if (!msg.isNullOrBlank()) msg else "문제가 발생했어요. 다시 시도해 주세요."
    }
}

data class AppConfigUiState(
    val isLoading: Boolean = false,
    val message: String? = null,

    val participantId: String = "",
    val docRootOverride: String = "",

    val preset: DateRangePreset? = null,
    val customStartDate: String = "",
    val customEndDate: String = "",
    val dateRangeResult: DateRange = DateRange(),
    val dateRangeError: String? = null,

    val limit: String = AppConfig.Defaults.LIMIT.toString(),
    val orderByField: String = AppConfig.Defaults.ORDER_BY_FIELD,
    val maxBatch: String = AppConfig.Defaults.MAX_BATCH.toString(),
)
