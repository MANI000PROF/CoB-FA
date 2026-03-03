package com.cobfa.app.ui.analytics

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@RequiresApi(Build.VERSION_CODES.O)
class AnalyticsViewModel(
    db: ExpenseDatabase
) : ViewModel() {

    private val expenseDao = db.expenseDao()
    private val zoneId = ZoneId.systemDefault()
    private val locale = Locale.getDefault()

    private val _range = MutableStateFlow(AnalyticsRange.WEEK)
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()

    fun setRange(newRange: AnalyticsRange) {
        _range.value = newRange
    }

    private val _selectedMonth = MutableStateFlow(YearMonth.now(zoneId))
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun prevMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        val now = YearMonth.now(zoneId)
        if (!next.isAfter(now)) _selectedMonth.value = next // prevent future months
    }

    val uiState: StateFlow<AnalyticsUiState> =
        combine(range, selectedMonth) { r, m -> r to m }
            .flatMapLatest { (r, m) ->
                val nowMs = System.currentTimeMillis()
                val (start, end) = computeRangeMillis(r, m, nowMs)

                expenseDao.observeConfirmedDebitsBetween(start, end)
                    .map { debits ->
                        buildUiStateFromDebits(
                            debits = debits,
                            selectedRange = r,
                            startMs = start,
                            endMs = end
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    private fun computeRangeMillis(
        range: AnalyticsRange,
        selectedMonth: YearMonth,
        nowMs: Long
    ): Pair<Long, Long> {
        return when (range) {
            AnalyticsRange.WEEK -> {
                val start = startOfDayMillis(LocalDate.now(zoneId).minusDays(6))
                start to nowMs
            }

            AnalyticsRange.MONTH -> {
                val nowYm = YearMonth.now(zoneId)

                val startDay = selectedMonth.atDay(1)
                val start = startOfDayMillis(startDay)

                // month-to-date if current month, else full month
                val endDay = if (selectedMonth == nowYm) {
                    LocalDate.now(zoneId)
                } else {
                    selectedMonth.atEndOfMonth()
                }
                val end = endOfDayMillis(endDay)
                start to end
            }
        }
    }

    private fun endOfDayMillis(day: LocalDate): Long {
        return day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    }

    private fun buildUiStateFromDebits(
        debits: List<ExpenseEntity>,
        selectedRange: AnalyticsRange,
        startMs: Long,
        endMs: Long
    ): AnalyticsUiState {

        // Pie: totals by category
        val categoryTotals = debits
            .groupBy { it.category?.name ?: "Uncategorized" }
            .map { (cat, items) -> CategorySpend(cat, items.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        // Trend: continuous day series (fills missing days with 0)
        val totalsByDate: Map<LocalDate, Double> = debits
            .groupBy { epochToLocalDate(it.timestamp) }
            .mapValues { (_, items) -> items.sumOf { it.amount } }

        val startDay = epochToLocalDate(startMs)
        val endDay = epochToLocalDate(endMs)

        val trend = buildList {
            var d = startDay
            while (!d.isAfter(endDay)) {
                val amt = totalsByDate[d] ?: 0.0
                val label = when (selectedRange) {
                    AnalyticsRange.WEEK -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) // Mon, Tue
                    AnalyticsRange.MONTH -> d.dayOfMonth.toString() // 1..31
                }
                add(TrendPoint(label = label, amount = amt))
                d = d.plusDays(1)
            }
        }

        val top5 = categoryTotals.take(5)
        val insights = buildInsights(debits, categoryTotals)

        val rangeLabel = when (selectedRange) {
            AnalyticsRange.WEEK -> "Last 7 days"
            AnalyticsRange.MONTH -> "This month"
        }

        return AnalyticsUiState(
            rangeLabel = rangeLabel,
            categoryBreakdown = categoryTotals,
            trend = trend,
            topCategories = top5,
            insights = insights
        )
    }

    private fun buildInsights(
        expenses: List<ExpenseEntity>,
        categoryTotals: List<CategorySpend>
    ): List<String> {
        if (expenses.isEmpty()) return emptyList()

        val byDay = expenses.groupBy { epochToLocalDate(it.timestamp).dayOfWeek }
        val weekendSpend = (byDay[DayOfWeek.SATURDAY].orEmpty().sumOf { it.amount }) +
                (byDay[DayOfWeek.SUNDAY].orEmpty().sumOf { it.amount })

        val weekdaySpend = expenses.sumOf { it.amount } - weekendSpend

        val insights = mutableListOf<String>()

        val avgWeekend = weekendSpend / 2.0
        val avgWeekday = weekdaySpend / 5.0
        if (avgWeekday > 0 && avgWeekend >= 2.0 * avgWeekday) {
            val x = (avgWeekend / avgWeekday)
            insights.add("You spend ~${(x * 10).roundToInt() / 10.0}x more on weekends.")
        }

        val total = categoryTotals.sumOf { it.amount }.takeIf { it > 0 } ?: return insights
        val top = categoryTotals.firstOrNull()
        if (top != null) {
            val share = (top.amount / total) * 100.0
            if (share >= 40.0) {
                insights.add("${top.label} is ~${share.roundToInt()}% of your spending.")
            }
        }

        return insights
    }

    private fun epochToLocalDate(epochMs: Long): LocalDate {
        return Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
    }

    private fun startOfDayMillis(day: LocalDate): Long {
        return day.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun monthStartMillis(nowMs: Long): Long {
        val d = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val first = d.withDayOfMonth(1)
        return first.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
