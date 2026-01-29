package com.cobfa.app.insights_ml.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.dao.NudgeEventDao
import com.cobfa.app.data.local.entity.NudgeEventEntity
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.domain.model.InsightSeverity
import com.cobfa.app.domain.model.PersonalizedInsight
import com.cobfa.app.insights_ml.baselines.RfmRiskBaseline
import com.cobfa.app.insights_ml.data.TimeBucketing
import com.cobfa.app.insights_ml.data.WeeklyDatasetBuilder
import com.cobfa.app.insights_ml.debug.DebugFlags
import com.cobfa.app.insights_ml.eval.LrLearningCurve
import com.cobfa.app.insights_ml.eval.LrRollingBacktest
import com.cobfa.app.insights_ml.eval.RollingBacktest
import com.cobfa.app.insights_ml.ml.LogRegSgd
import com.cobfa.app.insights_ml.ml.LrExplain
import com.cobfa.app.insights_ml.ml.LrFeatures
import com.cobfa.app.insights_ml.reco.AlternativesCatalog
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
class MlInsightsEngine(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val budgetRepo: BudgetRepository,
    private val nudgeDao: NudgeEventDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    suspend fun computeMlInsights(nowMs: Long): List<PersonalizedInsight> {
        val historyDays = 365
        val start = nowMs - historyDays.toLong() * 24 * 60 * 60 * 1000

        val expenses = try {
            expenseDao.getConfirmedDebitsBetween(start, nowMs)
        } catch (_: Throwable) {
            expenseDao.getExpensesBetween(start, nowMs)
        }.filter { it.category != null }

        if (expenses.size < 10) return emptyList()

        val dataset = WeeklyDatasetBuilder(zoneId).buildRows(expenses, nowMs)
        if (dataset.isEmpty()) return emptyList()

        if (DebugFlags.ENABLE_DEBUG_LOGS) {
            LrLearningCurve.runMultiWeekCurve(context, dataset, epochs = 60, k = 3, minTrainWeeks = 6)
        }

        // ---- Debug backtests (kept here for now) ----
        if (DebugFlags.ENABLE_DEBUG_LOGS) {
            val rfmFull = RollingBacktest.evalPrecisionRecallAtK(dataset, k = 3) { row ->
                RfmRiskBaseline.score(row, useMoney = true, useBudget = true)
            }
            val rfmNoMoney = RollingBacktest.evalPrecisionRecallAtK(dataset, k = 3) { row ->
                RfmRiskBaseline.score(row, useMoney = false, useBudget = true)
            }
            val rfmNoBudget = RollingBacktest.evalPrecisionRecallAtK(dataset, k = 3) { row ->
                RfmRiskBaseline.score(row, useMoney = true, useBudget = false)
            }

            Log.d(
                "ML_BACKTEST",
                "weeks=${rfmFull.weeks} " +
                        "RFM(full P@3=${"%.2f".format(rfmFull.precisionAtK)} R@3=${"%.2f".format(rfmFull.recallAtK)}) " +
                        "RFM(-money P@3=${"%.2f".format(rfmNoMoney.precisionAtK)} R@3=${"%.2f".format(rfmNoMoney.recallAtK)}) " +
                        "RFM(-budget P@3=${"%.2f".format(rfmNoBudget.precisionAtK)} R@3=${"%.2f".format(rfmNoBudget.recallAtK)})"
            )

            val lrBt = LrRollingBacktest.eval(dataset, k = 3, minTrainWeeks = 2)
            Log.d(
                "ML_BACKTEST",
                "LR(weeks=${lrBt.weeks} P@3=${"%.2f".format(lrBt.precisionAtK)} R@3=${"%.2f".format(lrBt.recallAtK)})"
            )
        }
        // --------------------------------------------

        val thisWeekStart = TimeBucketing.isoWeekStartMillis(nowMs, zoneId)
        val thisMonthStart = TimeBucketing.monthStartMillis(nowMs, zoneId)

        // budget usage map for current month
        val usages = budgetRepo.getBudgetUsageForMonth(thisMonthStart, expenseDao)
        val usageMap = usages.associate { u ->
            val pct = if (u.budgetAmount > 0) (u.spentAmount / u.budgetAmount) else 0.0
            u.category.name to pct
        }

        val currentWeekRows = dataset
            .filter { it.weekStart == thisWeekStart }
            .map { r -> r.copy(budgetUsagePct = usageMap[r.category.name]) }

        val canTrainLr = dataset.groupBy { it.weekStart }.keys.size >= 6

        // Train LR ONCE (reused for all categories)
        val lrModel: LogRegSgd? = if (canTrainLr) {
            val trainRows = dataset.filter { it.weekStart < thisWeekStart }
            if (trainRows.size < 20) null else {
                val xs = trainRows.map { LrFeatures.toX(it) }
                val ys = trainRows.map { it.labelRepeatNextWeek }
                LogRegSgd(dim = 6, lr = 0.05, l2 = 0.0005).apply {
                    fitWithHistory(xs, ys, epochs = 60)
                }
            }
        } else null

        val lrWeights: DoubleArray? = lrModel?.weights()

        // Anti-spam
        val since = nowMs - 7L * 24 * 60 * 60 * 1000

        val scored = mutableListOf<Pair<WeeklyDatasetBuilder.Row, Double>>()
        for (r in currentWeekRows) {
            val dismissed = try {
                nudgeDao.countDismissedSince("ml_risk_cat", r.category.name, since) > 0
            } catch (_: Throwable) {
                false
            }
            if (dismissed) continue

            val score = lrModel?.predictProb(LrFeatures.toX(r))
                ?: RfmRiskBaseline.score(r)

            scored.add(r to score)
        }

        val top = scored.sortedByDescending { it.second }.take(3)
        if (top.isEmpty()) return emptyList()

        // Log "shown" with 12h cooldown
        val prefs = context.getSharedPreferences("cobfa_ml", Context.MODE_PRIVATE)
        val cooldownMs = 12L * 60 * 60 * 1000

        top.forEach { (r, _) ->
            val key = "ml_shown_${r.category.name}"
            val last = prefs.getLong(key, 0L)
            if (nowMs - last < cooldownMs) return@forEach

            nudgeDao.insert(
                NudgeEventEntity(
                    type = "ml_risk_cat",
                    category = r.category.name,
                    action = null,
                    timestamp = nowMs
                )
            )
            prefs.edit().putLong(key, nowMs).apply()
        }

        return top.map { (r, risk) ->
            val pct = (risk * 100).toInt().coerceIn(0, 100)
            val severity = if (risk >= 0.75) InsightSeverity.RISK else InsightSeverity.WARN

            val reasons = if (lrWeights != null) {
                val topContribs = LrExplain.topContributions(r, lrWeights)
                LrExplain.toReasonText(topContribs, r)
            } else {
                buildList {
                    if (r.cnt7 >= 2) add("High frequency this week (${r.cnt7}x).")
                    if (r.daysSinceLast <= 2) add("Very recent activity (${r.daysSinceLast}d ago).")
                    if ((r.budgetUsagePct ?: 0.0) >= 0.8) add("Budget pressure (≥80%).")
                }.take(2).joinToString(" ")
            }

            val useRemoteAi = com.cobfa.app.insights_ml.debug.MlDevPrefs.isRemoteAiEnabled(context)

            val sugList = if (useRemoteAi) {
                // Still return offline suggestions, but log the anonymized prompt for demo
                val prompt = com.cobfa.app.insights_ml.reco.RemoteAiProviderStub.buildAnonymizedPrompt(
                    category = r.category,
                    riskPct = pct,
                    cnt7 = r.cnt7,
                    daysSinceLast = r.daysSinceLast,
                    budgetUsagePct = r.budgetUsagePct
                )
                Log.d("ML_REMOTE_AI_PROMPT", prompt)
                AlternativesCatalog.suggestionsFor(r.category, r.budgetUsagePct)
            } else {
                AlternativesCatalog.suggestionsFor(r.category, r.budgetUsagePct)
            }

            val sug = sugList.take(2).joinToString(" ") { "• ${it.title}: ${it.detail}" }

            PersonalizedInsight(
                key = "ml_risk_${r.category.name}",
                title = "${r.category.name}: repeat risk $pct%",
                message = (reasons.ifBlank { "Based on your recent pattern in ${r.category.name}." }) +
                        " " + sug,
                severity = severity
            )
        }
    }
}
