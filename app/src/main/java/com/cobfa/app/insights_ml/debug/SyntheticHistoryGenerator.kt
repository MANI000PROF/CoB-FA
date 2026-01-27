package com.cobfa.app.insights_ml.debug

import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import java.util.Locale
import kotlin.random.Random

object SyntheticHistoryGenerator {

    data class Plan(
        val weeks: Int = 12,
        val seed: Int = 42,
        val baseTs: Long = System.currentTimeMillis(),
        val heavyCat: ExpenseCategory = ExpenseCategory.FOOD,
        val mediumCat: ExpenseCategory = ExpenseCategory.TRANSPORT,
        val lightCat: ExpenseCategory = ExpenseCategory.BILLS,
        val heavyPerWeek: Int = 8,
        val mediumPerWeek: Int = 3,
        val lightPerWeek: Int = 1
    )

    suspend fun generate(
        expenseDao: ExpenseDao,
        plan: Plan
    ) {
        val rnd = Random(plan.seed)
        val weekMs = 7L * 24 * 60 * 60 * 1000

        val expenses = mutableListOf<ExpenseEntity>()

        fun mk(
            ts: Long,
            cat: ExpenseCategory,
            amtRange: IntRange,
            merchant: String
        ) = ExpenseEntity(
            amount = rnd.nextInt(amtRange.first, amtRange.last + 1).toDouble(),
            type = ExpenseType.DEBIT,
            category = cat,
            merchant = merchant,
            timestamp = ts,
            source = ExpenseSource.MANUAL,
            status = ExpenseStatus.CONFIRMED,
            createdAt = System.currentTimeMillis(),
            smsHash = "SYN_${cat.name}_${ts}_${rnd.nextInt()}".uppercase(Locale.ROOT)
        )

        for (w in 0 until plan.weeks) {
            val weekStart = plan.baseTs - (w.toLong() * weekMs)

            repeat(plan.heavyPerWeek) { i ->
                val ts = weekStart - rnd.nextLong(0, weekMs)
                expenses += mk(ts, plan.heavyCat, 120..450, merchant = "SYN_SWIGGY")
            }
            repeat(plan.mediumPerWeek) {
                val ts = weekStart - rnd.nextLong(0, weekMs)
                expenses += mk(ts, plan.mediumCat, 30..180, merchant = "SYN_TGSRTC")
            }
            repeat(plan.lightPerWeek) {
                val ts = weekStart - rnd.nextLong(0, weekMs)
                expenses += mk(ts, plan.lightCat, 200..800, merchant = "SYN_BILLPAY")
            }
        }

        expenseDao.insertAll(expenses)
    }
}
