package com.cobfa.app.insights_ml.ml

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder

object LrFeatures {

    // dim = 6: [bias, cnt7, cnt30, daysSinceLast, sum7, budgetUsagePct]
    fun toX(row: WeeklyDatasetBuilder.Row): DoubleArray {
        val budget = (row.budgetUsagePct ?: 0.0).coerceIn(0.0, 2.0)

        return doubleArrayOf(
            1.0,
            (row.cnt7 / 10.0).coerceIn(0.0, 1.0),
            (row.cnt30 / 30.0).coerceIn(0.0, 1.0),
            (row.daysSinceLast / 30.0).coerceIn(0.0, 1.0),
            (row.sum7 / 5000.0).coerceIn(0.0, 1.0),
            (budget / 2.0).coerceIn(0.0, 1.0)
        )
    }
}
