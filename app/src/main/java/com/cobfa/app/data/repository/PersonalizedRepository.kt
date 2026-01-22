package com.cobfa.app.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.dao.NudgeEventDao
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.domain.model.InsightSeverity
import com.cobfa.app.domain.model.PersonalizedInsight
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
class PersonalizationRepository(
    private val expenseDao: ExpenseDao,
    private val budgetRepo: BudgetRepository,
    private val nudgeDao: NudgeEventDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    suspend fun computeInsightsForCurrentMonth(): List<PersonalizedInsight> {
        val now = System.currentTimeMillis()
        val monthStart = monthStartMillis(now)
        val monthEnd = now

        val expenses = expenseDao.getExpensesBetween(monthStart, monthEnd)
            .filter { it.status == ExpenseStatus.CONFIRMED && it.type == ExpenseType.DEBIT }

        if (expenses.isEmpty()) {
            return listOf(
                PersonalizedInsight(
                    key = "no_data",
                    title = "No confirmed spends found",
                    message = "No CONFIRMED DEBIT expenses in the current month range yet.",
                    severity = InsightSeverity.INFO
                )
            )
        }

        val insights = mutableListOf<PersonalizedInsight>()

        val total = expenses.sumOf { it.amount }
        insights.add(
            PersonalizedInsight(
                key = "month_total",
                title = "Month spend so far",
                message = "You’ve spent ₹${total.toInt()} so far this month.",
                severity = InsightSeverity.INFO
            )
        )

        val top = expenses
            .filter { it.category != null }
            .groupBy { it.category!! }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .maxByOrNull { it.value }

        if (top != null && total > 0) {
            val pct = ((top.value / total) * 100).toInt()
            insights.add(
                PersonalizedInsight(
                    key = "top_category_share",
                    title = "Top category",
                    message = "${top.key.name} is ~${pct}% of your spend.",
                    severity = if (pct >= 40) InsightSeverity.WARN else InsightSeverity.INFO
                )
            )
        }

        val since7d = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val events7d = nudgeDao.getEventsSince(since7d)

        val goodActions = setOf("details", "adjust", "view_history", "set_budget")
        val dismissCount = events7d.count { it.action?.lowercase() == "dismiss" }
        val goodCount = events7d.count { it.action?.lowercase() in goodActions }

        val totalActions = dismissCount + goodCount
        if (totalActions > 0) {
            val rate = (goodCount * 100) / totalActions
            insights.add(
                PersonalizedInsight(
                    key = "action_rate_7d",
                    title = "Action follow-through (7d)",
                    message = "Good actions: ${rate}% (dismiss: $dismissCount).",
                    severity = if (rate >= 60) InsightSeverity.INFO else InsightSeverity.WARN
                )
            )
        }

        weekendSpikeInsight(expenses)?.let { insights.add(it) }

        topCategoryTrendingInsight(expenses)?.let { insights.add(it) }

        insights.addAll(budgetPaceInsights(monthStart).filterNotNull())

        return insights.toList()
    }

    private fun weekendSpikeInsight(expenses: List<com.cobfa.app.data.local.entity.ExpenseEntity>): PersonalizedInsight? {
        val byDow = expenses.groupBy { epochToLocalDate(it.timestamp).dayOfWeek }

        val weekend = (byDow[DayOfWeek.SATURDAY].orEmpty().sumOf { it.amount }) +
                (byDow[DayOfWeek.SUNDAY].orEmpty().sumOf { it.amount })

        val total = expenses.sumOf { it.amount }
        val weekday = total - weekend

        // Average per day (simple normalization)
        val avgWeekend = weekend / 2.0
        val avgWeekday = weekday / 5.0

        if (avgWeekday <= 0) return null
        val ratio = avgWeekend / avgWeekday

        return if (ratio >= 1.8) {
            PersonalizedInsight(
                key = "weekend_spike",
                title = "Weekend spike risk",
                message = "Weekend spend is ~${(ratio * 10).roundToInt() / 10.0}x your weekday average.",
                severity = InsightSeverity.WARN
            )
        } else null
    }

    private fun topCategoryTrendingInsight(expenses: List<com.cobfa.app.data.local.entity.ExpenseEntity>): PersonalizedInsight? {
        // Last 14 days buckets
        val endDay = LocalDate.now(zoneId)
        val startDay = endDay.minusDays(13)

        val dailyByCategory: Map<ExpenseCategory, Map<LocalDate, Double>> =
            expenses.filter { it.category != null }
                .groupBy { it.category!! }
                .mapValues { (_, items) ->
                    items.groupBy { epochToLocalDate(it.timestamp) }
                        .mapValues { (_, dayItems) -> dayItems.sumOf { it.amount } }
                }

        var best: Pair<ExpenseCategory, Double>? = null

        for ((cat, dayMap) in dailyByCategory) {
            val series = (0..13).map { d ->
                val day = startDay.plusDays(d.toLong())
                dayMap[day] ?: 0.0
            }

            // EMA(7) on last 14 values; alpha = 2/(N+1) [web:171][web:174]
            val ema7 = ema(series, alpha = 2.0 / (7.0 + 1.0))
            val first7Avg = series.take(7).average()
            val second7Avg = series.takeLast(7).average()

            // Trending if recent week > previous week by 25% and EMA not tiny
            if (first7Avg > 0 && second7Avg >= first7Avg * 1.25 && ema7 >= 200.0) {
                val score = (second7Avg / first7Avg)
                if (best == null || score > best!!.second) best = cat to score
            }
        }

        return best?.let { (cat, score) ->
            PersonalizedInsight(
                key = "trend_${cat.name}",
                title = "${cat.name} trending up",
                message = "Recent week is ~${(score * 10).roundToInt() / 10.0}x your prior week for ${cat.name}.",
                severity = InsightSeverity.RISK
            )
        }
    }

    private suspend fun budgetPaceInsights(monthStart: Long): List<PersonalizedInsight?> {
        val usages = budgetRepo.getBudgetUsageForMonth(monthStart, expenseDao)
            .filter { it.alertsEnabled && it.budgetAmount > 0 }

        if (usages.isEmpty()) return emptyList()

        val today = LocalDate.now(zoneId)
        val monthStartDate = epochToLocalDate(monthStart)
        val dayIndex = (today.toEpochDay() - monthStartDate.toEpochDay() + 1).toInt().coerceAtLeast(1)

        return usages.mapNotNull { u ->
            val dailyAvg = u.spentAmount / dayIndex.toDouble()
            if (dailyAvg <= 0) return@mapNotNull null

            val remainingTo80 = (0.8 * u.budgetAmount) - u.spentAmount
            if (remainingTo80 <= 0) return@mapNotNull null

            val daysTo80 = (remainingTo80 / dailyAvg).toInt()

            if (daysTo80 in 0..3) {
                PersonalizedInsight(
                    key = "pace_${u.category.name}",
                    title = "Budget pace warning",
                    message = "${u.category.name} likely hits 80% in ~${daysTo80 + 1} days at current pace.",
                    severity = InsightSeverity.WARN
                )
            } else null
        }
    }

    private fun ema(values: List<Double>, alpha: Double): Double {
        var e = values.firstOrNull() ?: 0.0
        for (i in 1 until values.size) {
            e = alpha * values[i] + (1 - alpha) * e
        }
        return e
    }

    private fun epochToLocalDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate() // explicit ZoneId conversion [web:181]

    private fun monthStartMillis(nowMs: Long): Long {
        val d = epochToLocalDate(nowMs)
        return d.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli() // [web:181]
    }
}
