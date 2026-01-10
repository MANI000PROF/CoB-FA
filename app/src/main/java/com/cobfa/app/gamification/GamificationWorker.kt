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
            // Process recent nudges → points/badges
            val events = db.nudgeEventDao().getRecentEventsSnapshot()
            repo.processNudgeEvents(events)

            // Daily +10 (if eligible)
            repo.awardUnderBudgetDayIfEligible()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
