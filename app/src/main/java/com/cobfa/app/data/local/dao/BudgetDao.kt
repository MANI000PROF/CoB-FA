package com.cobfa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * Insert or update budget for a specific category + month.
     * Uses REPLACE conflict strategy so setting same category/month updates amount.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity): Long

    /**
     * Delete budget for specific category + month.
     */
    @Query("DELETE FROM budgets WHERE category = :category AND monthStart = :monthStart")
    suspend fun deleteBudget(category: ExpenseCategory, monthStart: Long): Int

    /**
     * Get all budgets for a specific month.
     */
    @Query("""
        SELECT * FROM budgets 
        WHERE monthStart = :monthStart 
        ORDER BY category ASC
    """)
    fun getBudgetsForMonth(monthStart: Long): Flow<List<BudgetEntity>>

    /**
     * Get budget for specific category + month (nullable).
     */
    @Query("""
        SELECT * FROM budgets 
        WHERE category = :category AND monthStart = :monthStart 
        LIMIT 1
    """)
    suspend fun getBudgetForCategory(category: ExpenseCategory, monthStart: Long): BudgetEntity?

    /**
     * Get ALL budgets ever created (for history screen later).
     */
    @Query("SELECT * FROM budgets ORDER BY monthStart DESC, category ASC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    /**
     * Get budgets for month (suspend - blocks until current value emitted).
     */
    @Query("""
    SELECT * FROM budgets 
    WHERE monthStart = :monthStart 
    ORDER BY category ASC
""")
    suspend fun getCurrentBudgetsForMonth(monthStart: Long): List<BudgetEntity>

}
