package com.cobfa.app.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.repository.AnalyticsRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.MonthlySummary
import com.cobfa.app.utils.ExpenseLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(
    private val analyticsRepo: AnalyticsRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val summary: StateFlow<MonthlySummary?> = analyticsRepo
        .observeMonthlySummary(
            start = getMonthStart(),
            end = getMonthEnd()
        )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    // ✅ NEW: Track refresh state for pull-to-refresh UI
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // ✅ NEW: Callback for when refresh is triggered
    var onRefreshRequest: suspend () -> Unit = {}

    // ✅ NEW: Periodic background scanning (10s interval)
    init {
        viewModelScope.launch {
            Log.d("DASHBOARD_VM", "Starting Firestore restore on app launch")
            syncManager.restoreFromFirestore()
            Log.d("DASHBOARD_VM", "Firestore restore completed")
        }
        startPeriodicSmsScanning()
    }

    /**
     * Manual refresh triggered by user pull-to-refresh gesture.
     */
    fun refreshSms() {
        viewModelScope.launch {
            Log.d("REFRESH_DEBUG", "refreshSms() called")
            _isRefreshing.value = true
            Log.d("REFRESH_DEBUG", "isRefreshing set to TRUE")

            try {
                ExpenseLogger.logValidationFailed(
                    "refresh",
                    "manual",
                    "User triggered pull-to-refresh"
                )
                Log.d("REFRESH_DEBUG", "Calling onRefreshRequest()")
                // Call the SMS scan function from UI layer
                onRefreshRequest()
                Log.d("REFRESH_DEBUG", "onRefreshRequest() completed")
            } catch (e: Exception) {
                android.util.Log.e("REFRESH_DEBUG", "Error in refreshSms: ${e.message}")
                ExpenseLogger.logDatabaseError("refreshSms", e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
                Log.d("REFRESH_DEBUG", "isRefreshing set to FALSE")
            }
        }
    }

    /**
     * Start periodic SMS scanning in background (every 10 seconds).
     * Runs silently without showing refresh indicator.
     * Auto-stops when ViewModel is cleared.
     */
    private fun startPeriodicSmsScanning() {
        viewModelScope.launch {
            while (true) {
                try {
                    // Wait 10 seconds before scanning
                    delay(10000)

                    // Call the SMS scan function from UI layer
                    onRefreshRequest()

                } catch (e: Exception) {
                    ExpenseLogger.logDatabaseError(
                        "periodicScan",
                        e.message ?: "Unknown error"
                    )
                    // Continue scanning even if one iteration fails
                }
            }
        }
    }

    private fun getMonthStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    private fun getMonthEnd(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        return calendar.timeInMillis
    }
}
