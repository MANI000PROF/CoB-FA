package com.cobfa.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.repository.AnalyticsRepository
import com.cobfa.app.domain.model.MonthlySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(
    private val analyticsRepo: AnalyticsRepository
) : ViewModel() {

    private val _summary = MutableStateFlow<MonthlySummary?>(null)
    val summary: StateFlow<MonthlySummary?> = _summary

    fun loadCurrentMonthSummary() {
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()

        viewModelScope.launch {
            val result = analyticsRepo.getMonthlySummary(start, end)
            _summary.value = result
        }
    }
}
