package com.cobfa.app.insights_ml.baselines

import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder
import com.cobfa.app.insights_ml.data.TimeBucketing

object EwmaCountScorer {

    /**
     * Score = EWMA of recent weekly counts for this category.
     * Higher EWMA => higher near-term repeat likelihood (simple baseline). [web:127]
     */
    fun score(
        row: WeeklyDatasetBuilder.Row,
        allRows: List<WeeklyDatasetBuilder.Row>,
        alpha: Double = 0.5
    ): Double {
        val cat = row.category
        val weekStart = row.weekStart

        val series = allRows
            .filter { it.category == cat && it.weekStart <= weekStart }
            .sortedBy { it.weekStart }
            .map { it.cnt7.toDouble() }

        val e = EwmaBaseline.ewma(series.takeLast(8), alpha) // last 8 weeks max
        return (e / 10.0).coerceIn(0.0, 1.0) // scale: 10 txns/week -> 1.0
    }
}
