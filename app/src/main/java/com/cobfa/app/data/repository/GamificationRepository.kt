package com.cobfa.app.data.repository

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.cobfa.app.data.local.dao.AchievementDao
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.dao.NudgeEventDao
import com.cobfa.app.data.local.dao.PointsDao
import com.cobfa.app.data.local.entity.AchievementEntity
import com.cobfa.app.data.local.entity.NudgeEventEntity
import com.cobfa.app.data.local.entity.PointsEventEntity
import com.cobfa.app.domain.model.ExpenseType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

@RequiresApi(Build.VERSION_CODES.O)
class GamificationRepository(
    private val context: Context,
    private val nudgeDao: NudgeEventDao,
    private val pointsDao: PointsDao,
    private val achievementDao: AchievementDao,
    private val budgetRepo: BudgetRepository,
    private val expenseDao: ExpenseDao
) {
    fun observePointsBalance(): Flow<Int> = pointsDao.observePointsBalance()
    fun observeRecentPoints(): Flow<List<PointsEventEntity>> = pointsDao.observeRecentPoints(100)
    fun observeAchievements(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    private fun isMerchantBlockEvent(e: NudgeEventEntity): Boolean =
        e.type.equals("merchant_block_24h", ignoreCase = true) ||
                e.type.equals("MERCHANT_BLOCK_24H", ignoreCase = true)

    private fun blockWindowEnd(startMs: Long): Long = startMs + 24L * 60 * 60 * 1000

    suspend fun processNudgeEvents(events: List<NudgeEventEntity>) {
        // Award points for each nudge event (idempotent via unique sourceNudgeId index).
        for (e in events) {
            val pe = pointsEventFromNudge(e) ?: continue
            pointsDao.insert(pe)
        }

        // ✅ Delayed reward for merchant block success (award only after window ends AND no spend happened)
        val now = System.currentTimeMillis()
        for (e in events) {
            if (!isMerchantBlockEvent(e)) continue

            val end = blockWindowEnd(e.timestamp)
            if (now < end) continue // still in 24h window

            val spent = expenseDao.getExpensesBetween(e.timestamp, end)
                .any { it.type == ExpenseType.DEBIT && (it.merchant ?: "") == e.category }

            if (!spent) {
                // award once; idempotent via unique sourceNudgeId
                pointsDao.insert(
                    PointsEventEntity(
                        sourceNudgeId = e.id,
                        delta = 5,
                        reason = REASON_IMPULSE_SKIPPED,
                        details = e.category
                    )
                )
            }
        }

        // After processing, attempt to unlock achievements.
        checkAndUnlockAchievements()
    }

    suspend fun awardUnderBudgetDayIfEligible() {
        val todayKey = LocalDate.now(ZoneId.systemDefault()).toString()
        val prefs = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
        val lastAwarded = prefs.getString("under_budget_awarded_date", null)
        if (lastAwarded == todayKey) return

        val monthStart = System.currentTimeMillis()
        val usages = budgetRepo.getBudgetUsageForMonth(monthStart, expenseDao)

        if (usages.isEmpty()) return

        val hasExceeded = usages.any { it.alertsEnabled && it.percentageUsed >= 100 }
        val hasWarnings = usages.any { it.alertsEnabled && it.percentageUsed >= 80 }

        if (!hasExceeded && !hasWarnings) {
            pointsDao.insert(
                PointsEventEntity(
                    sourceNudgeId = null,
                    delta = 10,
                    reason = REASON_UNDER_BUDGET_DAY,
                    details = todayKey
                )
            )
            updateUnderBudgetStreak(todayKey)
            prefs.edit().putString("under_budget_awarded_date", todayKey).apply()
            checkAndUnlockAchievements()
        }
    }

    private fun pointsEventFromNudge(e: NudgeEventEntity): PointsEventEntity? {
        val type = e.type.uppercase()
        val action = e.action?.lowercase()

        return when {
            type == "BUDGET_100" || type == "BUDGET_100%" || type.startsWith("BUDGET_100") -> {
                PointsEventEntity(
                    sourceNudgeId = e.id,
                    delta = -5,
                    reason = REASON_BUDGET_EXCEEDED,
                    details = e.category
                )
            }

            // Impulse skipped = dismissed pattern alert
            action == "dismiss" && (
                    type.startsWith("MERCHANT_") ||
                            type.startsWith("CATEGORY_") ||
                            type.startsWith("HIGHVALUE_") ||
                            e.type.lowercase() in setOf("merchant_3x", "category_5x", "highvalue_3x")
                    ) -> {
                PointsEventEntity(
                    sourceNudgeId = e.id,
                    delta = 5,
                    reason = REASON_IMPULSE_SKIPPED,
                    details = e.category
                )
            }

            else -> null
        }
    }

    private suspend fun checkAndUnlockAchievements() {
        val totalEvents = pointsDao.countAll()
        val impulseSkipped = pointsDao.countByReason(REASON_IMPULSE_SKIPPED)
        val underBudgetDays = pointsDao.countByReason(REASON_UNDER_BUDGET_DAY)

        if (totalEvents >= 1) {
            achievementDao.insert(
                AchievementEntity(
                    key = "STARTER",
                    title = "First Steps",
                    description = "Earned your first points."
                )
            )
        }

        if (impulseSkipped >= 5) {
            achievementDao.insert(
                AchievementEntity(
                    key = "IMPULSE_SLAYER",
                    title = "Impulse Slayer",
                    description = "Skipped 5 impulse moments."
                )
            )
        }

        if (underBudgetDays >= 7) {
            achievementDao.insert(
                AchievementEntity(
                    key = "BUDGET_MASTER",
                    title = "Budget Master",
                    description = "7 under-budget days logged."
                )
            )
        }

        val streak = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
            .getInt("under_budget_streak", 0)

        if (streak >= 3) {
            achievementDao.insert(
                AchievementEntity(
                    key = "SAVINGS_STREAK",
                    title = "Savings Streak",
                    description = "3 under-budget days in a row."
                )
            )
        }

        if (totalEvents >= 20) {
            achievementDao.insert(
                AchievementEntity(
                    key = "CONSISTENT",
                    title = "Consistent",
                    description = "Logged 20 point events."
                )
            )
        }
    }

    private fun updateUnderBudgetStreak(todayKey: String) {
        val prefs = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
        val lastDay = prefs.getString("under_budget_last_day", null)
        val streak = prefs.getInt("under_budget_streak", 0)

        val newStreak = if (lastDay != null) {
            val last = LocalDate.parse(lastDay)
            val today = LocalDate.parse(todayKey)
            if (last.plusDays(1) == today) streak + 1 else 1
        } else 1

        prefs.edit()
            .putString("under_budget_last_day", todayKey)
            .putInt("under_budget_streak", newStreak)
            .apply()
    }

    companion object {
        const val REASON_UNDER_BUDGET_DAY = "UNDER_BUDGET_DAY"
        const val REASON_IMPULSE_SKIPPED = "IMPULSE_SKIPPED"
        const val REASON_BUDGET_EXCEEDED = "BUDGET_EXCEEDED"
    }
}
