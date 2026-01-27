package com.cobfa.app.insights_ml.ml

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder
import kotlin.math.abs

object LrExplain {

    data class Contribution(val name: String, val score: Double)

    fun topContributions(row: WeeklyDatasetBuilder.Row, weights: DoubleArray): List<Contribution> {
        val x = LrFeatures.toX(row)
        val names = listOf("bias", "cnt7", "cnt30", "daysSinceLast", "sum7", "budgetUsage")

        val contribs = names.indices.map { i ->
            Contribution(names[i], weights[i] * x[i])
        }

        return contribs
            .filter { it.name != "bias" }
            .sortedByDescending { abs(it.score) }
            .take(3)
    }

    fun toReasonText(top: List<Contribution>, row: WeeklyDatasetBuilder.Row): String {
        fun fmt(c: Contribution): String = when (c.name) {
            "cnt7" -> "High frequency this week (${row.cnt7}x)."
            "cnt30" -> "Frequent over the month (${row.cnt30}x/30d)."
            "daysSinceLast" -> "Very recent activity (${row.daysSinceLast}d ago)."
            "sum7" -> "Higher spend this week (₹${row.sum7.toInt()})."
            "budgetUsage" -> {
                val pct = ((row.budgetUsagePct ?: 0.0) * 100).toInt()
                if (pct >= 80) "Budget pressure (${pct}%)." else "Budget context (${pct}%)."
            }
            else -> ""
        }

        return top.map { fmt(it) }.filter { it.isNotBlank() }.take(2).joinToString(" ")
    }
}
