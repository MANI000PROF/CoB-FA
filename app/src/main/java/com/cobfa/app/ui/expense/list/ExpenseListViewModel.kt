package com.cobfa.app.ui.expense.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class ExpenseListViewModel(
    repository: ExpenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<ExpenseCategory?>(null)
    val categoryFilter: StateFlow<ExpenseCategory?> = _categoryFilter.asStateFlow()

    private val allExpenses = repository
        .getConfirmedExpenses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val expenses: StateFlow<List<com.cobfa.app.data.local.entity.ExpenseEntity>> = combine(
        allExpenses,
        _searchQuery,
        _categoryFilter
    ) { expenses, query, category ->
        expenses.filter { expense ->
            // Search: merchant OR category name contains query (case insensitive)
            val matchesSearch = query.isBlank() ||
                    expense.merchant?.contains(query, ignoreCase = true) == true ||
                    expense.category?.name?.contains(query, ignoreCase = true) == true

            // Category filter
            val matchesCategory = category == null || expense.category == category

            matchesSearch && matchesCategory
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(category: ExpenseCategory?) {
        _categoryFilter.value = category
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _categoryFilter.value = null
    }
}
