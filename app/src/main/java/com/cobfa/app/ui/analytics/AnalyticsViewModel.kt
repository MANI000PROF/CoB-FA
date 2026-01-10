package com.cobfa.app.dashboard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.ui.analytics.AnalyticsRange
import com.cobfa.app.ui.analytics.AnalyticsUiState
import com.cobfa.app.ui.analytics.CategorySpend
import com.cobfa.app.ui.analytics.TrendPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
class AnalyticsViewModel(
    db: ExpenseDatabase
) : ViewModel() {

    private val expenseDao = db.expenseDao()

    private val zoneId = ZoneId.systemDefault()

    private val _range = MutableStateFlow(AnalyticsRange.WEEK)
    val range: StateFlow<AnalyticsRange> = _range

    fun setRange(newRange: AnalyticsRange) {
        _range.value = newRange
    }

    val uiState: StateFlow<AnalyticsUiState> =
        range.flatMapLatest { selectedRange ->
            expenseDao.getAllExpenses().map { all ->
                buildUiState(all, selectedRange)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState()
        )

    private fun buildUiState(
        all: List<ExpenseEntity>,
        selectedRange: AnalyticsRange
    ): AnalyticsUiState {
        val now = System.currentTimeMillis()
        val start = when (selectedRange) {
            AnalyticsRange.WEEK -> startOfDayMillis(LocalDate.now(zoneId).minusDays(6))
            AnalyticsRange.MONTH -> monthStartMillis(now)
        }
        val end = now

        val confirmedDebits = all.asSequence()
            .filter { it.status == ExpenseStatus.CONFIRMED }
            .filter { it.type == ExpenseType.DEBIT }
            .filter { it.timestamp in start..end }
            .toList()

        // Pie: totals by category
        val categoryTotals = confirmedDebits
            .groupBy { it.category?.name ?: "Other" }
            .map { (cat, items) -> CategorySpend(cat, items.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

        // Line: totals by day (works for both week & month)
        val totalsByDay = confirmedDebits
            .groupBy { epochToLocalDate(it.timestamp) }
            .toSortedMap()

        val trend = totalsByDay.entries.map { (day, items) ->
            TrendPoint(label = day.dayOfMonth.toString(), amount = items.sumOf { it.amount })
        }

        val top5 = categoryTotals.take(5)

        val insights = buildInsights(confirmedDebits, categoryTotals)

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
