package com.cobfa.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.MonthlySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses WHERE smsHash = :hash")
    suspend fun countBySmsHash(hash: String): Int

    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY timestamp DESC")
    fun getExpensesByStatus(status: ExpenseStatus): Flow<List<ExpenseEntity>>

    @Query("UPDATE expenses SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ExpenseStatus)

    @Query("UPDATE expenses SET status = :status, category = :category WHERE id = :id")
    suspend fun confirmExpense(id: Long, category: ExpenseCategory, status: ExpenseStatus)

    @Query("SELECT * FROM expenses WHERE timestamp BETWEEN :start AND :end AND status = 'CONFIRMED'")
    suspend fun getExpensesBetween(start: Long, end: Long): List<ExpenseEntity>

    @Query("""
    SELECT
        :start AS monthStart,
        IFNULL(SUM(CASE WHEN type = 'CREDIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0) AS income,
        IFNULL(SUM(CASE WHEN type = 'DEBIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0) AS expense,
        IFNULL(
            SUM(CASE WHEN type = 'CREDIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0
        ) -
        IFNULL(
            SUM(CASE WHEN type = 'DEBIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0
        ) AS balance
    FROM expenses
    WHERE timestamp BETWEEN :start AND :end AND status = 'CONFIRMED'
""")
    fun observeMonthlySummary(
        start: Long,
        end: Long
    ): Flow<MonthlySummary>

    // ADD THIS METHOD to ExpenseDao
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    /**
     * Get total spent amount for a category within date range.
     * Only counts CONFIRMED DEBIT expenses.
     */
    @Query("""
    SELECT IFNULL(SUM(amount), 0.0) 
    FROM expenses 
    WHERE status = 'CONFIRMED' 
      AND type = 'DEBIT' 
      AND category = :category 
      AND timestamp BETWEEN :start AND :end
""")
    suspend fun getSpentAmountByCategory(
        category: ExpenseCategory,
        start: Long,
        end: Long
    ): Double

}
