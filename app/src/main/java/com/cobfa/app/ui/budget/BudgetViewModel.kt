package com.cobfa.app.ui.budget

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BudgetRowUi(
    val budget: BudgetEntity,
    val spent: Double,
    val progress: Double // 0..1 (clamped)
)

data class BudgetUiState(
    val monthLabel: String = "",
    val canGoNextMonth: Boolean = false,
    val isCurrentMonth: Boolean = true,
    val rows: List<BudgetRowUi> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@RequiresApi(Build.VERSION_CODES.O)
class BudgetViewModel(
    private val db: ExpenseDatabase,
    private val repo: BudgetRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val zoneId = ZoneId.systemDefault()
    private val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    private val _selectedMonth = MutableStateFlow(YearMonth.now(zoneId))
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun prevMonth() { _selectedMonth.value = _selectedMonth.value.minusMonths(1) }
    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        val now = YearMonth.now(zoneId)
        if (!next.isAfter(now)) _selectedMonth.value = next
    }

    private fun monthStartEndUtc(ym: YearMonth): Pair<Long, Long> {
        val tz = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(tz)

        cal.clear()
        cal.set(java.util.Calendar.YEAR, ym.year)
        cal.set(java.util.Calendar.MONTH, ym.monthValue - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis

        cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return start to end
    }

    val uiState: StateFlow<BudgetUiState> =
        selectedMonth.flatMapLatest { ym: YearMonth ->
            val (start, end) = monthStartEndUtc(ym)

            val budgetsFlow: Flow<List<BudgetEntity>> = repo.observeBudgetsForMonth(start)
            val spentFlow: Flow<List<ExpenseDao.CategorySpentRow>> = db.expenseDao().observeSpentByCategory(start, end)

            combine(budgetsFlow, spentFlow) { budgets, spentRows ->
                val spentMap = spentRows.associate { it.category to it.spent }

                val rows = budgets
                    .sortedBy { it.category.name }
                    .map { b ->
                    val spent = spentMap[b.category] ?: 0.0
                    val p = if (b.amount > 0) (spent / b.amount).coerceIn(0.0, 1.0) else 0.0
                    BudgetRowUi(budget = b, spent = spent, progress = p)
                }

                val nowYm = YearMonth.now(zoneId)
                BudgetUiState(
                    monthLabel = ym.format(monthFormatter),
                    canGoNextMonth = ym.isBefore(nowYm),
                    isCurrentMonth = ym == nowYm,
                    rows = rows
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BudgetUiState()
        )

    fun restoreBudgetsFromFirestoreOnce() {
        viewModelScope.launch {
            syncManager.restoreBudgetsFromFirestore()
        }
    }

    fun addOrUpdateBudget(category: ExpenseCategory, amount: Double) {
        val nowYm = YearMonth.now(zoneId)
        if (_selectedMonth.value != nowYm) return
        viewModelScope.launch {
            val ym = _selectedMonth.value
            val monthStart = monthStartEndUtc(ym).first
            repo.upsertBudget(category, amount, monthStart, syncManager = syncManager)
        }
    }

    fun deleteBudget(category: ExpenseCategory) {
        val nowYm = YearMonth.now(zoneId)
        if (_selectedMonth.value != nowYm) return
        viewModelScope.launch {
            val ym = _selectedMonth.value
            val monthStart = monthStartEndUtc(ym).first
            repo.deleteBudget(category, monthStart, syncManager = syncManager)
        }
    }
}
