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
import com.cobfa.app.insights_ml.ml.LogRegSgd
import com.cobfa.app.insights_ml.ml.LrExplain
import com.cobfa.app.insights_ml.ml.LrFeatures
import com.cobfa.app.insights_ml.reco.AlternativesCatalog
import java.time.ZoneId

// Cooldown constants so we can tune later
private const val ML_DISMISS_LOOKBACK_DAYS = 7L
private const val ML_SOFT_COOLDOWN_DAYS = 3L
private const val ML_STRICT_COOLDOWN_HOURS = 12L
private const val ML_MIN_TRAIN_WEEKS = 6
private const val ML_MIN_TRAIN_ROWS = 20
private const val ML_MAX_TOP_CATEGORIES = 3
private const val ML_RISK_THRESHOLD = 0.75

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

        // Require enough weeks and rows before training LR [web:582][web:578]
        val distinctWeeks = dataset.groupBy { it.weekStart }.keys.size
        val canTrainLr = distinctWeeks >= ML_MIN_TRAIN_WEEKS

        // Train LR ONCE (reused for all categories)
        val lrModel: LogRegSgd? = if (canTrainLr) {
            val trainRows = dataset.filter { it.weekStart < thisWeekStart }
            if (trainRows.size < ML_MIN_TRAIN_ROWS) null else {
                val xs = trainRows.map { LrFeatures.toX(it) }
                val ys = trainRows.map { it.labelRepeatNextWeek }
                LogRegSgd(dim = 6, lr = 0.05, l2 = 0.0005).apply {
                    fitWithHistory(xs, ys, epochs = 60)
                }
            }
        } else null

        val lrWeights: DoubleArray? = lrModel?.weights()

        // Anti-spam
        val sinceDismiss = nowMs - ML_DISMISS_LOOKBACK_DAYS * 24 * 60 * 60 * 1000

        val scored = mutableListOf<Pair<WeeklyDatasetBuilder.Row, Double>>()
        for (r in currentWeekRows) {
            val dismissed = try {
                nudgeDao.countDismissedSince("ml_risk_cat", r.category.name, sinceDismiss) > 0
            } catch (_: Throwable) {
                false
            }
            if (dismissed) continue

            val score = lrModel?.predictProb(LrFeatures.toX(r))
                ?: RfmRiskBaseline.score(r)

            scored.add(r to score)
        }

        val top = scored.sortedByDescending { it.second }.take(ML_MAX_TOP_CATEGORIES)
        if (top.isEmpty()) return emptyList()

        // Log "shown" with soft (3d) and strict (12h) cooldowns per category [web:574][web:570]
        val prefs = context.getSharedPreferences("cobfa_ml", Context.MODE_PRIVATE)
        val strictCooldownMs = ML_STRICT_COOLDOWN_HOURS * 60 * 60 * 1000
        val softCooldownMs = ML_SOFT_COOLDOWN_DAYS * 24 * 60 * 60 * 1000

        val shownNow = mutableSetOf<String>()

        top.forEach { (r, _) ->
            val key = "ml_shown_${r.category.name}"
            val last = prefs.getLong(key, 0L)
            val elapsed = nowMs - last

            // If we’ve shown in last 12h, skip completely
            if (elapsed in 0 until strictCooldownMs) return@forEach
            // Soft cooldown: if shown in last 3 days, skip unless there is no other category
            if (elapsed in strictCooldownMs until softCooldownMs && shownNow.isNotEmpty()) return@forEach

            nudgeDao.insert(
                NudgeEventEntity(
                    type = "ml_risk_cat",
                    category = r.category.name,
                    action = null,
                    timestamp = nowMs
                )
            )
            prefs.edit().putLong(key, nowMs).apply()
            shownNow.add(r.category.name)
        }

        return top.map { (r, risk) ->
            val pct = (risk * 100).toInt().coerceIn(0, 100)
            val severity = if (risk >= ML_RISK_THRESHOLD) InsightSeverity.RISK else InsightSeverity.WARN

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

            val reasonsText = reasons.ifBlank {
                "Based on your recent pattern in ${r.category.name}."
            }
            val suggestionsText = if (sug.isNotBlank()) " Suggestions: $sug" else ""

            PersonalizedInsight(
                key = "ml_risk_${r.category.name}",
                title = "${r.category.name}: high repeat risk (${pct}%)",
                message = reasonsText + suggestionsText,
                severity = severity
            )
        }
    }
}
