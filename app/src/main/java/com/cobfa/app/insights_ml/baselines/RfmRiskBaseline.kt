package com.cobfa.app.insights_ml.baselines

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder

object RfmRiskBaseline {
    fun score(
        row: WeeklyDatasetBuilder.Row,
        useMoney: Boolean = true,
        useBudget: Boolean = true
    ): Double {
        // Simple monotonic risk score:
        // - more frequent in 30d => higher risk
        // - more recent => higher risk (lower daysSinceLast)
        // - higher last-7d spend => higher risk
        val f = (row.cnt30.coerceAtMost(30) / 30.0)
        val r = 1.0 / (1.0 + row.daysSinceLast.coerceAtLeast(0)) // 1, 1/2, 1/3...
        val m = if (useMoney) (row.sum7 / 2000.0).coerceIn(0.0, 1.0) else 0.0

        val budgetBoost = if (!useBudget) 0.0 else when {
            row.budgetUsagePct == null -> 0.0
            row.budgetUsagePct >= 1.0 -> 0.25
            row.budgetUsagePct >= 0.8 -> 0.15
            else -> 0.0
        }

        return (0.45 * f + 0.35 * r + 0.20 * m + budgetBoost).coerceIn(0.0, 1.0)
    }
}
