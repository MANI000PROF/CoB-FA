package com.cobfa.app.ui.expense.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExpenseListViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    enum class SortMode { NEWEST, OLDEST }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<ExpenseCategory?>(null)
    val categoryFilter: StateFlow<ExpenseCategory?> = _categoryFilter.asStateFlow()

    private val _merchantFilter = MutableStateFlow<String?>(null)
    val merchantFilter: StateFlow<String?> = _merchantFilter.asStateFlow()

    private val _onlyUncategorized = MutableStateFlow(false)
    val onlyUncategorized: StateFlow<Boolean> = _onlyUncategorized.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NEWEST)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    enum class ExcludedMode { ACTIVE_ONLY, INCLUDE_EXCLUDED, EXCLUDED_ONLY }

    private val _excludedMode = MutableStateFlow(ExcludedMode.ACTIVE_ONLY)
    val excludedMode = _excludedMode.asStateFlow()

    fun setExcludedMode(m: ExcludedMode) { _excludedMode.value = m }

    private fun baseFlow(mode: ExcludedMode, sort: SortMode): Flow<List<ExpenseEntity>> {
        return when (mode) {
            ExcludedMode.ACTIVE_ONLY ->
                if (sort == SortMode.NEWEST) repository.observeConfirmedActiveNewest()
                else repository.observeConfirmedActiveOldest()

            ExcludedMode.INCLUDE_EXCLUDED ->
                if (sort == SortMode.NEWEST) repository.observeConfirmedAllNewest()
                else repository.observeConfirmedAllOldest()

            ExcludedMode.EXCLUDED_ONLY ->
                if (sort == SortMode.NEWEST) repository.observeDeletedNewest()
                else repository.observeDeletedOldest()
        }
    }

    private val debouncedQuery = _searchQuery.debounce(250)

    val expenses: StateFlow<List<ExpenseEntity>> =
        combine(excludedMode, sortMode) { mode, sort -> mode to sort }
            .flatMapLatest { (mode, sort) -> baseFlow(mode, sort) }
            .combine(debouncedQuery) { list, query -> list to query }
            .combine(categoryFilter) { (list, query), category -> Triple(list, query, category) }
            .combine(merchantFilter) { (list, query, category), merchant -> Quad(list, query, category, merchant) }
            .combine(onlyUncategorized) { q, onlyUncat ->
                val list = q.list
                val query = q.query
                val category = q.category
                val merchant = q.merchant

                list.filter { expense ->
                    val matchesSearch =
                        query.isBlank() ||
                                (expense.merchant?.contains(query, ignoreCase = true) == true) ||
                                (expense.category?.name?.contains(query, ignoreCase = true) == true)

                    val matchesCategory = category == null || expense.category == category
                    val matchesMerchant = merchant == null || expense.merchant?.equals(merchant, ignoreCase = true) == true
                    val matchesUncat = !onlyUncat || expense.category == null

                    matchesSearch && matchesCategory && matchesMerchant && matchesUncat
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private data class Quad(
        val list: List<ExpenseEntity>,
        val query: String,
        val category: ExpenseCategory?,
        val merchant: String?
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(category: ExpenseCategory?) {
        _categoryFilter.value = category
    }

    fun updateMerchantFilter(merchant: String?) {
        _merchantFilter.value = merchant
    }

    fun setOnlyUncategorized(enabled: Boolean) {
        _onlyUncategorized.value = enabled
    }

    fun toggleSort() {
        _sortMode.value =
            if (_sortMode.value == SortMode.NEWEST) SortMode.OLDEST else SortMode.NEWEST
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _categoryFilter.value = null
        _merchantFilter.value = null
        _onlyUncategorized.value = false
        _sortMode.value = SortMode.NEWEST
    }

    fun updateExpenseCategory(expenseId: Long, category: ExpenseCategory) {
        viewModelScope.launch {
            repository.updateExpenseCategory(expenseId, category)
        }
    }

    fun softDeleteExpense(id: Long) {
        viewModelScope.launch { repository.softDeleteExpense(id) }
    }
    fun restoreExpense(id: Long) {
        viewModelScope.launch { repository.restoreExpense(id) }
    }

    fun editExpense(id: Long, merchant: String?, amount: Double, timestamp: Long) {
        viewModelScope.launch {
            repository.editExpense(id, merchant, amount, timestamp)
        }
    }

}
