package com.cobfa.app.utils

import android.content.Context
import androidx.work.*
import com.cobfa.app.gamification.GamificationWorker
import java.util.concurrent.TimeUnit

object GamificationScheduler {
    private const val UNIQUE_PERIODIC = "cobfa_gamification_worker_periodic"
    private const val UNIQUE_ONETIME = "cobfa_gamification_worker_onetime"

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<GamificationWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    fun runNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<GamificationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONETIME,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
    }
}
