package com.cobfa.app.insights_ml.eval

import android.util.Log
import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder

object RollingBacktest {

    data class Metrics(
        val weeks: Int,
        val precisionAtK: Double,
        val recallAtK: Double
    )

    /**
     * Evaluate ranking quality per week:
     * - Score each category for week t
     * - Recommend top K
     * - Relevant = labelRepeatNextWeek == 1
     */
    fun evalPrecisionRecallAtK(
        rows: List<WeeklyDatasetBuilder.Row>,
        k: Int = 3,
        scorer: (WeeklyDatasetBuilder.Row) -> Double
    ): Metrics {
        if (rows.isEmpty()) return Metrics(0, 0.0, 0.0)

        val byWeek: Map<Long, List<WeeklyDatasetBuilder.Row>> = rows.groupBy { it.weekStart }
        val weeksSorted = byWeek.keys.sorted()
        val totalRows = rows.size
        val distinctCats = rows.map { it.category }.distinct().size
        val distinctWeeks = weeksSorted.size
        Log.d("ML_BACKTEST", "rows=$totalRows cats=$distinctCats weeks=$distinctWeeks firstWeek=${weeksSorted.firstOrNull()} lastWeek=${weeksSorted.lastOrNull()}")
        if (weeksSorted.size < 2) return Metrics(0, 0.0, 0.0)

        var weekCount = 0
        var precisionSum = 0.0
        var recallSum = 0.0

        for (w in weeksSorted) {
            val weekRows = byWeek[w].orEmpty()
            if (weekRows.isEmpty()) continue

            val scored = weekRows.map { r -> r to scorer(r) }
                .sortedByDescending { it.second }

            val topK = scored.take(k).map { it.first }
            val relevantAll = weekRows.filter { it.labelRepeatNextWeek == 1 }
            val hits = topK.count { it.labelRepeatNextWeek == 1 }

            val precision = hits.toDouble() / k.toDouble()
            val recall = if (relevantAll.isEmpty()) 0.0 else hits.toDouble() / relevantAll.size.toDouble()

            precisionSum += precision
            recallSum += recall
            weekCount++
        }

        if (weekCount == 0) return Metrics(0, 0.0, 0.0)

        return Metrics(
            weeks = weekCount,
            precisionAtK = precisionSum / weekCount.toDouble(),
            recallAtK = recallSum / weekCount.toDouble()
        )
    }
}
