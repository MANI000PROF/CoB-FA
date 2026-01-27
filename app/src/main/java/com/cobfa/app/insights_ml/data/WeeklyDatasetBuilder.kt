package com.cobfa.app.insights_ml.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import java.time.Instant
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
class WeeklyDatasetBuilder(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    data class Row(
        val weekStart: Long,
        val category: ExpenseCategory,
        val cnt7: Int,
        val cnt30: Int,
        val daysSinceLast: Int,
        val sum7: Double,
        val budgetUsagePct: Double?,
        val labelRepeatNextWeek: Int
    )

    fun buildRows(
        expenses: List<ExpenseEntity>,
        nowMs: Long
    ): List<Row> {
        val confirmed = expenses
            .filter { it.category != null }
            .sortedBy { it.timestamp }

        if (confirmed.isEmpty()) return emptyList()

        // Build ISO week timeline from first expense weekStart to current weekStart
        val firstWeek = TimeBucketing.isoWeekStartMillis(confirmed.first().timestamp, zoneId)
        val lastWeek = TimeBucketing.isoWeekStartMillis(nowMs, zoneId)

        // weekStart -> category -> list(expenses)
        val byWeekCat: Map<Long, Map<ExpenseCategory, List<ExpenseEntity>>> =
            confirmed.groupBy { TimeBucketing.isoWeekStartMillis(it.timestamp, zoneId) }
                .mapValues { (_, items) ->
                    items.groupBy { it.category!! }
                }

        val allCategories: Set<ExpenseCategory> =
            confirmed.mapNotNull { it.category }.toSet()

        val weekStarts = mutableListOf<Long>()
        run {
            var d = Instant.ofEpochMilli(firstWeek).atZone(zoneId).toLocalDate()
            val endD = Instant.ofEpochMilli(lastWeek).atZone(zoneId).toLocalDate()
            while (!d.isAfter(endD)) {
                weekStarts.add(d.atStartOfDay(zoneId).toInstant().toEpochMilli())
                d = d.plusWeeks(1)
            }
        }

        fun countInWindow(cat: ExpenseCategory, endExclusive: Long, days: Int): Int {
            val start = endExclusive - days.toLong() * 24 * 60 * 60 * 1000
            return confirmed.count { e ->
                e.category == cat && e.timestamp in start until endExclusive
            }
        }

        fun sumInWindow(cat: ExpenseCategory, endExclusive: Long, days: Int): Double {
            val start = endExclusive - days.toLong() * 24 * 60 * 60 * 1000
            return confirmed.filter { e ->
                e.category == cat && e.timestamp in start until endExclusive
            }.sumOf { it.amount }
        }

        fun daysSinceLast(cat: ExpenseCategory, endExclusive: Long): Int {
            val last = confirmed.lastOrNull { it.category == cat && it.timestamp < endExclusive } ?: return 999
            val diffMs = (endExclusive - last.timestamp).coerceAtLeast(0)
            return (diffMs / (24L * 60 * 60 * 1000)).toInt()
        }

        val rows = mutableListOf<Row>()

        for (weekStart in weekStarts) {
            val weekEndExclusive = weekStart + 7L * 24 * 60 * 60 * 1000
            val nextWeekStart = weekEndExclusive
            val nextWeekEndExclusive = nextWeekStart + 7L * 24 * 60 * 60 * 1000

            for (cat in allCategories) {
                val label = if ((byWeekCat[nextWeekStart]?.get(cat) ?: emptyList()).isNotEmpty()) 1 else 0

                rows.add(
                    Row(
                        weekStart = weekStart,
                        category = cat,
                        cnt7 = countInWindow(cat, weekEndExclusive, 7),
                        cnt30 = countInWindow(cat, weekEndExclusive, 30),
                        daysSinceLast = daysSinceLast(cat, weekEndExclusive),
                        sum7 = sumInWindow(cat, weekEndExclusive, 7),
                        budgetUsagePct = null, // filled in engine using BudgetRepository (Sprint 1)
                        labelRepeatNextWeek = label
                    )
                )
            }
        }
        return rows
    }
}
