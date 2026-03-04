package com.cobfa.app.insights_ml.ml

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder

// Scaling constants for LR features [web:591][web:595]
private const val LR_BIAS = 1.0
private const val LR_CNT7_SCALE = 10.0
private const val LR_CNT30_SCALE = 30.0
private const val LR_DAYS_SINCE_LAST_SCALE = 30.0
private const val LR_SUM7_SCALE = 5000.0
private const val LR_BUDGET_MAX = 2.0

object LrFeatures {

    // dim = 6: [bias, cnt7, cnt30, daysSinceLast, sum7, budgetUsagePct]
    fun toX(row: WeeklyDatasetBuilder.Row): DoubleArray {
        val budget = (row.budgetUsagePct ?: 0.0).coerceIn(0.0, LR_BUDGET_MAX)

        return doubleArrayOf(
            LR_BIAS,
            (row.cnt7 / LR_CNT7_SCALE).coerceIn(0.0, 1.0),
            (row.cnt30 / LR_CNT30_SCALE).coerceIn(0.0, 1.0),
            (row.daysSinceLast / LR_DAYS_SINCE_LAST_SCALE).coerceIn(0.0, 1.0),
            (row.sum7 / LR_SUM7_SCALE).coerceIn(0.0, 1.0),
            (budget / LR_BUDGET_MAX).coerceIn(0.0, 1.0)
        )
    }
}
