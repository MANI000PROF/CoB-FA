package com.cobfa.app.insights_ml.eval

import android.content.Context
import android.util.Log
import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder
import com.cobfa.app.insights_ml.debug.MetricsCsvWriter
import com.cobfa.app.insights_ml.ml.LogRegSgd
import com.cobfa.app.insights_ml.ml.LrFeatures
import kotlin.math.ln
import kotlin.math.sqrt

object LrLearningCurve {

    data class Row(
        val epoch: Int,
        val trainLoss: Double,

        val meanPAtK: Double,
        val stdPAtK: Double,
        val meanRAtK: Double,
        val stdRAtK: Double,
        val meanNdcgAtK: Double,
        val stdNdcgAtK: Double,

        val baseMeanPAtK: Double,
        val baseStdPAtK: Double,
        val baseMeanRAtK: Double,
        val baseStdRAtK: Double,
        val baseMeanNdcgAtK: Double,
        val baseStdNdcgAtK: Double,

        val testWeeks: Int,
        val avgRelevantAll: Double,
        val avgTestRows: Double
    )

    private fun mean(xs: List<Double>): Double {
        if (xs.isEmpty()) return Double.NaN
        return xs.sum() / xs.size.toDouble()
    }

    private fun std(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = mean(xs)
        val v = xs.sumOf { (it - m) * (it - m) } / (xs.size - 1).toDouble()
        return sqrt(v)
    }

    private fun log2(x: Double): Double = ln(x) / ln(2.0)

    /**
     * NDCG@K for binary relevance.
     * sortedRelevances is the relevance list in ranked order (1 for relevant, 0 otherwise).
     * NDCG is a standard ranking metric that accounts for rank position and normalizes by the ideal ranking. [web:387]
     */
    private fun ndcgAtK(sortedRelevances: List<Int>, k: Int): Double {
        val kk = minOf(k, sortedRelevances.size)
        if (kk == 0) return Double.NaN

        var dcg = 0.0
        for (i in 0 until kk) {
            val rel = sortedRelevances[i]
            if (rel > 0) dcg += 1.0 / log2((i + 2).toDouble()) // i=0 -> log2(2)=1
        }

        val ideal = sortedRelevances.sortedDescending()
        var idcg = 0.0
        for (i in 0 until kk) {
            val rel = ideal[i]
            if (rel > 0) idcg += 1.0 / log2((i + 2).toDouble())
        }

        return if (idcg == 0.0) Double.NaN else dcg / idcg
    }

    /**
     * Multi-week learning curve:
     * For each epoch e, for each test week, train on all weeks < testWeek, evaluate on testWeek,
     * then average metrics over weeks (macro-average over "queries/weeks"), which is standard for ranking metrics. [web:57]
     */
    fun runMultiWeekCurve(
        ctx: Context,
        dataset: List<WeeklyDatasetBuilder.Row>,
        epochs: Int = 60,
        k: Int = 5,
        minTrainWeeks: Int = 6
    ): List<Row> {
        val byWeek = dataset.groupBy { it.weekStart }
        val weeks = byWeek.keys.sorted()
        if (weeks.size < (minTrainWeeks + 1)) return emptyList()

        val candidateTestWeeks = weeks.filter { w ->
            val rows = byWeek[w].orEmpty()
            rows.size >= (k + 2) && rows.any { it.labelRepeatNextWeek == 1 }
        }

        val eligibleTestWeeks = candidateTestWeeks.filter { w ->
            weeks.count { it < w } >= minTrainWeeks
        }

        if (eligibleTestWeeks.isEmpty()) {
            Log.d("ML_CURVE", "No eligible test weeks (need positives + >=$minTrainWeeks train weeks)")
            return emptyList()
        }

        val out = ArrayList<Row>(epochs)

        // For pooled training loss curve (optional but nice for Figure 1)
        val pooledTrainWeeks = weeks.dropLast(1)
        val pooledTrainRows = pooledTrainWeeks.flatMap { byWeek[it].orEmpty() }
        val pooledXs = pooledTrainRows.map { LrFeatures.toX(it) }
        val pooledYs = pooledTrainRows.map { it.labelRepeatNextWeek }

        var bestEpoch = 1
        var bestMeanNdcg = Double.NEGATIVE_INFINITY

        for (e in 1..epochs) {
            val pList = ArrayList<Double>(eligibleTestWeeks.size)
            val rList = ArrayList<Double>(eligibleTestWeeks.size)
            val ndcgList = ArrayList<Double>(eligibleTestWeeks.size)

            val bpList = ArrayList<Double>(eligibleTestWeeks.size)
            val brList = ArrayList<Double>(eligibleTestWeeks.size)
            val bndcgList = ArrayList<Double>(eligibleTestWeeks.size)

            var relevantSum = 0.0
            var testRowsSum = 0.0

            for (testWeek in eligibleTestWeeks) {
                val trainWeeks = weeks.filter { it < testWeek }
                val trainRows = trainWeeks.flatMap { byWeek[it].orEmpty() }
                val testRows = byWeek[testWeek].orEmpty()
                if (trainRows.isEmpty() || testRows.isEmpty()) continue

                val relevantAll = testRows.count { it.labelRepeatNextWeek == 1 }
                if (relevantAll == 0) continue // should not happen by eligibility, but keep safe

                val xs = trainRows.map { LrFeatures.toX(it) }
                val ys = trainRows.map { it.labelRepeatNextWeek }

                // Train model for e epochs
                val m = LogRegSgd(dim = 6, lr = 0.05, l2 = 0.0005)
                m.fitWithHistory(xs, ys, epochs = e)

                // --- Logistic Regression ranking ---
                val scored = testRows
                    .map { r -> r to m.predictProb(LrFeatures.toX(r)) }
                    .sortedByDescending { it.second }

                val topK = scored.take(k).map { it.first }
                val hits = topK.count { it.labelRepeatNextWeek == 1 }

                val pAtK = hits.toDouble() / k.toDouble()
                val rAtK = hits.toDouble() / relevantAll.toDouble()
                val relList = scored.map { it.first.labelRepeatNextWeek }
                val ndcg = ndcgAtK(relList, k)

                pList += pAtK
                rList += rAtK
                ndcgList += ndcg

                // --- Baseline: rank by cnt30 (most frequent recently) ---
                val rng = java.util.Random(testWeek) // deterministic per week
                val baselineRanked = testRows.shuffled(rng)
                val bTopK = baselineRanked.take(k)
                val bHits = bTopK.count { it.labelRepeatNextWeek == 1 }

                val bPAtK = bHits.toDouble() / k.toDouble()
                val bRAtK = bHits.toDouble() / relevantAll.toDouble()
                val bRelList = baselineRanked.map { it.labelRepeatNextWeek }
                val bNdcg = ndcgAtK(bRelList, k)

                bpList += bPAtK
                brList += bRAtK
                bndcgList += bNdcg

                relevantSum += relevantAll.toDouble()
                testRowsSum += testRows.size.toDouble()
            }

            // Pooled training loss after e epochs (for a clean decreasing curve)
            val pooledModel = LogRegSgd(dim = 6, lr = 0.05, l2 = 0.0005)
            val pooledLossHist = pooledModel.fitWithHistory(pooledXs, pooledYs, epochs = e)
            val trainLoss = pooledLossHist.last()

            val testWeeksUsed = pList.size
            val meanP = mean(pList)
            val meanR = mean(rList)
            val meanNdcg = mean(ndcgList)

            if (!meanNdcg.isNaN() && meanNdcg > bestMeanNdcg) {
                bestMeanNdcg = meanNdcg
                bestEpoch = e
            }

            out += Row(
                epoch = e,
                trainLoss = trainLoss,

                meanPAtK = meanP,
                stdPAtK = std(pList),
                meanRAtK = meanR,
                stdRAtK = std(rList),
                meanNdcgAtK = meanNdcg,
                stdNdcgAtK = std(ndcgList),

                baseMeanPAtK = mean(bpList),
                baseStdPAtK = std(bpList),
                baseMeanRAtK = mean(brList),
                baseStdRAtK = std(brList),
                baseMeanNdcgAtK = mean(bndcgList),
                baseStdNdcgAtK = std(bndcgList),

                testWeeks = testWeeksUsed,
                avgRelevantAll = if (testWeeksUsed == 0) Double.NaN else relevantSum / testWeeksUsed.toDouble(),
                avgTestRows = if (testWeeksUsed == 0) Double.NaN else testRowsSum / testWeeksUsed.toDouble()
            )
        }

        val csv = buildString {
            append(
                "epoch,train_logloss," +
                        "mean_p_at_k,std_p_at_k,mean_r_at_k,std_r_at_k,mean_ndcg_at_k,std_ndcg_at_k," +
                        "base_mean_p_at_k,base_std_p_at_k,base_mean_r_at_k,base_std_r_at_k,base_mean_ndcg_at_k,base_std_ndcg_at_k," +
                        "k,test_weeks,avg_relevant_all,avg_test_rows\n"
            )
            out.forEach {
                append(
                    "${it.epoch}," +
                            "${"%.6f".format(it.trainLoss)}," +
                            "${"%.6f".format(it.meanPAtK)}," +
                            "${"%.6f".format(it.stdPAtK)}," +
                            "${"%.6f".format(it.meanRAtK)}," +
                            "${"%.6f".format(it.stdRAtK)}," +
                            "${"%.6f".format(it.meanNdcgAtK)}," +
                            "${"%.6f".format(it.stdNdcgAtK)}," +
                            "${"%.6f".format(it.baseMeanPAtK)}," +
                            "${"%.6f".format(it.baseStdPAtK)}," +
                            "${"%.6f".format(it.baseMeanRAtK)}," +
                            "${"%.6f".format(it.baseStdRAtK)}," +
                            "${"%.6f".format(it.baseMeanNdcgAtK)}," +
                            "${"%.6f".format(it.baseStdNdcgAtK)}," +
                            "${k}," +
                            "${it.testWeeks}," +
                            "${"%.3f".format(it.avgRelevantAll)}," +
                            "${"%.3f".format(it.avgTestRows)}\n"
                )
            }
        }

        // Write a new filename to avoid mixing old vs new
        val file = MetricsCsvWriter.writeText(ctx, "lr_learning_curve_multiweek.csv", csv)
        Log.d(
            "ML_CURVE",
            "Saved LR multi-week curve CSV at: ${file.absolutePath} " +
                    "(eligibleTestWeeks=${eligibleTestWeeks.size}, k=$k, minTrainWeeks=$minTrainWeeks, bestEpoch=$bestEpoch bestMeanNdcg=$bestMeanNdcg)"
        )

        return out
    }
}
