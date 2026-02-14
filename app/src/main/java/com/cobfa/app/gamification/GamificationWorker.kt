package com.cobfa.app.gamification

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.GamificationRepository
import com.cobfa.app.data.repository.SyncManager

@RequiresApi(Build.VERSION_CODES.O)
class GamificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = ExpenseDatabase.getInstance(context)

        val repo = GamificationRepository(
            context = context,
            nudgeDao = db.nudgeEventDao(),
            pointsDao = db.pointsDao(),
            achievementDao = db.achievementDao(),
            budgetRepo = BudgetRepository(db.budgetDao()),
            expenseDao = db.expenseDao()
        )

        return try {
            val prefs = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
            var lastTs = prefs.getLong("last_nudge_processed_ts", 0L)

            val events = db.nudgeEventDao().getEventsSince(lastTs)
            if (events.isNotEmpty()) {
                repo.processNudgeEvents(events)

                // Note: events are DESC in your query; still safe:
                lastTs = maxOf(lastTs, events.maxOf { it.timestamp })
                prefs.edit().putLong("last_nudge_processed_ts", lastTs).apply()
            }

            repo.awardUnderBudgetDayIfEligible()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
