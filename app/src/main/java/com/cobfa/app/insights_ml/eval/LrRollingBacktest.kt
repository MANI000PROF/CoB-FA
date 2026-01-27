package com.cobfa.app.insights_ml.eval

import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder
import com.cobfa.app.insights_ml.debug.DebugFlags
import com.cobfa.app.insights_ml.ml.LogRegSgd
import com.cobfa.app.insights_ml.ml.LrFeatures

object LrRollingBacktest {

    data class Metrics(val weeks: Int, val precisionAtK: Double, val recallAtK: Double)

    fun eval(
        rows: List<WeeklyDatasetBuilder.Row>,
        k: Int = 2,
        minTrainWeeks: Int = 2
    ): Metrics {
        val byWeek = rows.groupBy { it.weekStart }
        val weeks = byWeek.keys.sorted()
        if (weeks.size <= minTrainWeeks) return Metrics(0, 0.0, 0.0)

        var evalWeeks = 0
        var pSum = 0.0
        var rSum = 0.0

        for (wi in minTrainWeeks until weeks.size) {
            val trainWeeks = weeks.take(wi)
            val testWeek = weeks[wi]

            val trainRows = trainWeeks.flatMap { byWeek[it].orEmpty() }
            val testRows = byWeek[testWeek].orEmpty()
            if (trainRows.isEmpty() || testRows.isEmpty()) continue

            val xs = trainRows.map { LrFeatures.toX(it) }
            val ys = trainRows.map { it.labelRepeatNextWeek }

            val model = LogRegSgd(dim = 6, lr = 0.05, l2 = 0.0005)
            model.fit(xs, ys, epochs = 60)

            if (DebugFlags.ENABLE_DEBUG_LOGS && wi == minTrainWeeks) {
                android.util.Log.d("ML_LR", "sample weights=" + model.weights().joinToString(prefix="[", postfix="]") { "%.3f".format(it) })
            }

            val scored = testRows
                .map { r -> r to model.predictProb(LrFeatures.toX(r)) }
                .sortedByDescending { it.second }

            val topK = scored.take(k).map { it.first }
            val relevantAll = testRows.filter { it.labelRepeatNextWeek == 1 }
            val hits = topK.count { it.labelRepeatNextWeek == 1 }

            val precision = hits.toDouble() / k.toDouble()
            val recall = if (relevantAll.isEmpty()) 0.0 else hits.toDouble() / relevantAll.size.toDouble()

            pSum += precision
            rSum += recall
            evalWeeks++
        }

        if (evalWeeks == 0) return Metrics(0, 0.0, 0.0)
        return Metrics(evalWeeks, pSum / evalWeeks, rSum / evalWeeks)
    }
}
