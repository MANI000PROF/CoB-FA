package com.cobfa.app.insights_ml.baselines

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder

// Tunable RFM-style weights and scales [web:586][web:592]
private const val RFM_MAX_CNT30 = 30
private const val RFM_RECENCY_BASE = 1.0
private const val RFM_SUM7_SCALE = 2000.0

private const val RFM_WEIGHT_F = 0.45
private const val RFM_WEIGHT_R = 0.35
private const val RFM_WEIGHT_M = 0.20

private const val RFM_BUDGET_BOOST_OVER = 0.25
private const val RFM_BUDGET_BOOST_HIGH = 0.15
private const val RFM_BUDGET_USAGE_HIGH = 1.0      // 100%
private const val RFM_BUDGET_USAGE_WARN = 0.8      // 80%

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
        val f = (row.cnt30.coerceAtMost(RFM_MAX_CNT30) / RFM_MAX_CNT30.toDouble())
        val r = RFM_RECENCY_BASE / (RFM_RECENCY_BASE + row.daysSinceLast.coerceAtLeast(0)) // 1, 1/2, 1/3...
        val m = if (useMoney) (row.sum7 / RFM_SUM7_SCALE).coerceIn(0.0, 1.0) else 0.0

        val budgetBoost = if (!useBudget) 0.0 else when {
            row.budgetUsagePct == null -> 0.0
            row.budgetUsagePct >= RFM_BUDGET_USAGE_HIGH -> RFM_BUDGET_BOOST_OVER
            row.budgetUsagePct >= RFM_BUDGET_USAGE_WARN -> RFM_BUDGET_BOOST_HIGH
            else -> 0.0
        }

        return (RFM_WEIGHT_F * f + RFM_WEIGHT_R * r + RFM_WEIGHT_M * m + budgetBoost).coerceIn(0.0, 1.0)
    }
}
