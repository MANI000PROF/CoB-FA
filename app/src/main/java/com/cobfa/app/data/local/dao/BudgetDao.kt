package com.cobfa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE category = :category AND monthStart = :monthStart")
    suspend fun deleteBudget(category: ExpenseCategory, monthStart: Long): Int

    @Query("""
        SELECT * FROM budgets 
        WHERE monthStart = :monthStart 
        ORDER BY category ASC
    """)
    fun getBudgetsForMonth(monthStart: Long): Flow<List<BudgetEntity>>

    @Query("""
        SELECT * FROM budgets 
        WHERE category = :category AND monthStart = :monthStart 
        LIMIT 1
    """)
    suspend fun getBudgetForCategory(category: ExpenseCategory, monthStart: Long): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY monthStart DESC, category ASC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("""
    SELECT * FROM budgets 
    WHERE monthStart = :monthStart 
    ORDER BY category ASC
""")
    suspend fun getCurrentBudgetsForMonth(monthStart: Long): List<BudgetEntity>
}
