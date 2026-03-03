package com.cobfa.app.ui.achievements

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.GamificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class AchievementsViewModel(
    private val context: Context,
    db: ExpenseDatabase
) : ViewModel() {

    private val repo = GamificationRepository(
        context = context,
        nudgeDao = db.nudgeEventDao(),
        pointsDao = db.pointsDao(),
        achievementDao = db.achievementDao(),
        budgetRepo = BudgetRepository(db.budgetDao()),
        expenseDao = db.expenseDao()
    )

    val pointsBalance: StateFlow<Int> =
        repo.observePointsBalance().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentPoints =
        repo.observeRecentPoints().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val achievements =
        repo.observeAchievements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
            var lastTs = prefs.getLong("last_nudge_processed_ts", 0L)

            // Initial catch-up (process only events since lastTs)
            val initial = db.nudgeEventDao().getEventsSince(lastTs)
            if (initial.isNotEmpty()) {
                repo.processNudgeEvents(initial)
                lastTs = initial.maxOf { it.timestamp }
                prefs.edit().putLong("last_nudge_processed_ts", lastTs).apply()
            }

            // Live updates: re-check periodically (simple + stable MVP)
            while (true) {
                val newer = db.nudgeEventDao().getEventsSince(lastTs)
                if (newer.isNotEmpty()) {
                    repo.processNudgeEvents(newer)
                    lastTs = newer.maxOf { it.timestamp }
                    prefs.edit().putLong("last_nudge_processed_ts", lastTs).apply()
                }
                kotlinx.coroutines.delay(5_000)
            }
        }

        viewModelScope.launch {
            repo.awardUnderBudgetDayIfEligible()
        }
    }

}
