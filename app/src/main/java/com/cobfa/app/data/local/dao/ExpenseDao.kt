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

    @Query("UPDATE expenses SET status = :status, category = :category WHERE id = :id")
    suspend fun confirmExpense(id: Long, category: ExpenseCategory, status: ExpenseStatus): Int

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

    @Query("""
    SELECT * FROM expenses 
    WHERE timestamp BETWEEN :start AND :end
      AND status = 'CONFIRMED'
      AND type = 'DEBIT'
    ORDER BY timestamp ASC
""")
    suspend fun getConfirmedDebitsBetween(start: Long, end: Long): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @androidx.room.Transaction
    suspend fun confirmExpenseSafe(id: Long, category: ExpenseCategory): Int {
        // Optional: verify it exists first (better logs)
        val existing = getExpenseById(id) ?: return 0
        if (existing.status == ExpenseStatus.CONFIRMED) return 1
        return confirmExpense(id, category, ExpenseStatus.CONFIRMED)
    }

    @Query("UPDATE expenses SET status = :status, category = :category WHERE smsHash = :smsHash")
    suspend fun confirmExpenseBySmsHash(
        smsHash: String,
        category: ExpenseCategory,
        status: ExpenseStatus = ExpenseStatus.CONFIRMED
    ): Int

    @Query("SELECT * FROM expenses WHERE smsHash = :smsHash LIMIT 1")
    suspend fun getExpenseBySmsHash(smsHash: String): ExpenseEntity?

    @Query("UPDATE expenses SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: ExpenseCategory): Int

    @Query("UPDATE expenses SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ExpenseStatus): Int

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Query("""
    UPDATE expenses 
    SET merchant = :merchant,
        amount = :amount,
        timestamp = :timestamp,
        editedAt = :editedAt
    WHERE id = :id
""")
    suspend fun updateExpenseCore(
        id: Long,
        merchant: String?,
        amount: Double,
        timestamp: Long,
        editedAt: Long
    ): Int

    @Query("SELECT * FROM expenses WHERE status = 'CONFIRMED' ORDER BY timestamp DESC")
    fun observeConfirmedNewest(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'CONFIRMED' ORDER BY timestamp ASC")
    fun observeConfirmedOldest(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status IN ('CONFIRMED','DELETED') ORDER BY timestamp DESC")
    fun observeConfirmedIncludingDeletedNewest(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status IN ('CONFIRMED','DELETED') ORDER BY timestamp ASC")
    fun observeConfirmedIncludingDeletedOldest(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'DELETED' ORDER BY timestamp DESC")
    fun observeDeletedNewest(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'DELETED' ORDER BY timestamp ASC")
    fun observeDeletedOldest(): Flow<List<ExpenseEntity>>

    @Query("""
    SELECT * FROM expenses 
    WHERE timestamp BETWEEN :start AND :end
      AND status = 'CONFIRMED'
      AND type = 'DEBIT'
    ORDER BY timestamp ASC
""")
    fun observeConfirmedDebitsBetween(start: Long, end: Long): Flow<List<ExpenseEntity>>

    data class CategorySpentRow(
        val category: ExpenseCategory?,
        val spent: Double
    )

    @Query("""
  SELECT category AS category, IFNULL(SUM(amount), 0.0) AS spent
  FROM expenses
  WHERE status = 'CONFIRMED'
    AND type = 'DEBIT'
    AND timestamp BETWEEN :start AND :end
  GROUP BY category
""")
    fun observeSpentByCategory(start: Long, end: Long): Flow<List<CategorySpentRow>>
}
