package com.cobfa.app.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.NudgeEventEntity
import com.cobfa.app.data.repository.AnalyticsRepository
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.MonthlySummary
import com.cobfa.app.utils.ExpenseLogger
import com.cobfa.app.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

class DashboardViewModel(
    private val analyticsRepo: AnalyticsRepository,
    private val syncManager: SyncManager,
    private val db: ExpenseDatabase,
    private val context: Context
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

    private val budgetRepo = BudgetRepository(db.budgetDao())
    private val expenseDao = db.expenseDao()
    private val nudgeEventDao = db.nudgeEventDao()

    private val _activeAlert = MutableStateFlow<BudgetAlert?>(null)
    val activeAlert: StateFlow<BudgetAlert?> = _activeAlert

    // ADD after activeAlert
    private val _budgetWarnings = MutableStateFlow<List<BudgetWarning>>(emptyList())
    val budgetWarnings: StateFlow<List<BudgetWarning>> = _budgetWarnings

    data class BudgetWarning(
        val category: String,
        val percentage: Int,
        val spent: Double,
        val budget: Double
    )

    data class BudgetAlert(
        val category: String,
        val percentage: Int,
        val ruleType: String,  // "BUDGET_80", "BUDGET_100"
        val message: String,
        val suggestedAction: String? = null  // "reduce_zomato", "check_groceries"
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    var onRefreshRequest: suspend () -> Unit = {}

    init {
        viewModelScope.launch {
            syncManager.restoreFromFirestore()
            syncManager.restoreBudgetsFromFirestore()
            // Check alerts after restore
            checkForBudgetAlerts()
        }
        startPeriodicSmsScanning()
    }

    /**
     * Manual refresh triggered by user pull-to-refresh gesture.
     */
    fun refreshSms() {
        viewModelScope.launch {
            _isRefreshing.value = true

            try {
                ExpenseLogger.logValidationFailed(
                    "refresh",
                    "manual",
                    "User triggered pull-to-refresh"
                )
                // Call the SMS scan function from UI layer
                onRefreshRequest()
                // ✅ NEW: Check alerts after SMS scan
                checkForBudgetAlerts()
            } catch (e: Exception) {
                ExpenseLogger.logDatabaseError("refreshSms", e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
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
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
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

    private var today80PercentAlertsShown = mutableSetOf<String>()

    private suspend fun checkForBudgetAlerts() {
        // Clear daily cache
        if (isNewDay()) today80PercentAlertsShown.clear()
        val monthStart = getMonthStart()
        val usages = budgetRepo.getBudgetUsageForMonth(monthStart, expenseDao)

        // Prioritize 100% alerts
        for (usage in usages) {
            if (usage.alertsEnabled && usage.percentageUsed >= 100) {
                _activeAlert.value = BudgetAlert(
                    category = usage.category.name,
                    percentage = usage.percentageUsed,
                    ruleType = "BUDGET_100",
                    message = "${usage.category.name} (₹${String.format("%.0f", usage.spentAmount)}/₹${String.format("%.0f", usage.budgetAmount)}) - ${usage.percentageUsed}% - EXCEEDED"
                )
                return
            }
        }

        // 80% → INLINE warnings (NO popup)
        val warnings = usages.filter {
            it.alertsEnabled && it.percentageUsed >= 80 && it.percentageUsed < 100 && !is80WarningDismissed(it.category.name)
        }.map {
            BudgetWarning(
                category = it.category.name,
                percentage = it.percentageUsed.toInt(),
                spent = it.spentAmount,
                budget = it.budgetAmount
            )
        }
        _budgetWarnings.value = warnings  // INLINE
        _activeAlert.value = null
        checkForPatternAlerts()
    }

    private suspend fun checkForPatternAlerts() {
        val todayStart = getTodayTimestampStart()
        val todayEnd = getTodayTimestampEnd()
        val todayExpenses = expenseDao.getExpensesBetween(todayStart, todayEnd)
            .filter { !isMerchantBlocked(it.merchant ?: "") } // ✅ EXISTS

        // 1. SAME MERCHANT 3x
        val merchantCounts = todayExpenses
            .filter { it.merchant != null }
            .groupBy { it.merchant!! }
            .filter { it.value.size >= 3 }

        merchantCounts.forEach { (merchant, expenses) ->
            _activeAlert.value = BudgetAlert(
                category = merchant,
                percentage = 0,
                ruleType = "MERCHANT_3X",
                message = "$merchant (${expenses.size}x today) - Pattern detected!",
                suggestedAction = "reduce_${merchant.lowercase()}"
            )
            logNudgeEvent("merchant_3x", merchant)
            return
        }

        // 2. SAME CATEGORY 5x
        val categoryCounts = todayExpenses
            .filter { it.category != null }
            .groupBy { it.category!!.name }
            .filter { it.value.size >= 5 }

        categoryCounts.forEach { (category, expenses) ->
            _activeAlert.value = BudgetAlert(
                category = category,
                percentage = 0,
                ruleType = "CATEGORY_5X",
                message = "$category (${expenses.size}x today) - Spending spree!"
            )
            logNudgeEvent("category_5x", category)
            return
        }

        // 3. HIGH VALUE 3x (₹500+)
        val highValue = todayExpenses.filter { it.amount >= 500.0 }
        val highValueCounts = highValue
            .filter { it.merchant != null }
            .groupBy { it.merchant!! }
            .filter { it.value.size >= 3 }

        highValueCounts.forEach { (merchant, expenses) ->
            val total = expenses.sumOf { it.amount }
            _activeAlert.value = BudgetAlert(
                category = merchant,
                percentage = 0,
                ruleType = "HIGHVALUE_3X",
                message = "$merchant (₹${String.format("%.0f", total)} today) - Big spender alert!"
            )
            logNudgeEvent("highvalue_3x", merchant)
            return
        }
    }


    private var lastCheckDay: String? = null

    private fun isNewDay(): Boolean {
        val today = getTodayDate()
        if (lastCheckDay != today) {
            lastCheckDay = today
            return true
        }
        return false
    }

    private fun getTodayDate(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    fun onAlertDismissed() {
        _activeAlert.value = null
    }

    fun onAlertActionTaken(action: String) {
        val alert = _activeAlert.value
        if (alert != null) {
            viewModelScope.launch {
                nudgeEventDao.insert(
                    NudgeEventEntity(
                        type = alert.ruleType,
                        category = alert.category,
                        action = action
                    )
                )
            }
        }
        _activeAlert.value = null
        logAlertAction("user_action", action)
    }

    private fun getTodayTimestampStart(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getTodayTimestampEnd(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    private fun logNudgeEvent(type: String, details: String) {
        viewModelScope.launch {
            nudgeEventDao.insert(
                NudgeEventEntity(
                    type = type,
                    category = details,
                    action = null
                )
            )
        }
    }

    fun logAlertAction(ruleType: String, action: String) {
        viewModelScope.launch {
            nudgeEventDao.insert(
                NudgeEventEntity(
                    type = ruleType,
                    category = _activeAlert.value?.category ?: "unknown",
                    action = action
                )
            )
        }
        _activeAlert.value = null
    }

    private val dismissedWarningsToday = mutableSetOf<String>()

    private fun getDismissedWarningsKey(): String = "dismissed_80_${getTodayDate()}"

    private fun is80WarningDismissed(category: String): Boolean {
        if (isNewDay()) {
            val prefs = context.getSharedPreferences("cobfa_alerts", Context.MODE_PRIVATE)
            val dismissed = prefs.getStringSet(getDismissedWarningsKey(), emptySet()) ?: emptySet()
            dismissedWarningsToday.addAll(dismissed)
        }
        return dismissedWarningsToday.contains(category)
    }

    fun dismiss80Warning(category: String) {
        dismissedWarningsToday.add(category)
        val prefs = context.getSharedPreferences("cobfa_alerts", Context.MODE_PRIVATE)
        val dismissed = (prefs.getStringSet(getDismissedWarningsKey(), emptySet()) ?: emptySet()).toMutableSet()
        dismissed.add(category)
        prefs.edit().putStringSet(getDismissedWarningsKey(), dismissed).apply()

        // ✅ FORCE RECOMPOSE - recheck warnings
        viewModelScope.launch {
            checkForBudgetAlerts()
        }
    }

    // Temp merchant blocking (24h)
    private fun getBlockedMerchantsKey(): String = "blocked_${getTodayDate()}"

    fun isMerchantBlocked(merchant: String): Boolean {
        val prefs = context.getSharedPreferences("cobfa_alerts", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet(getBlockedMerchantsKey(), emptySet()) ?: emptySet()
        return blocked.contains(merchant.lowercase())
    }

    fun blockMerchantFor24h(merchant: String) {
        val prefs = context.getSharedPreferences("cobfa_alerts", Context.MODE_PRIVATE)
        val blocked = (prefs.getStringSet(getBlockedMerchantsKey(), emptySet()) ?: emptySet()).toMutableSet()
        blocked.add(merchant.lowercase())
        prefs.edit().putStringSet(getBlockedMerchantsKey(), blocked).apply()
        logAlertAction("merchant_block_24h", merchant)
    }


    // Quick pattern budget
    fun createPatternBudget(merchant: String, amount: Double) {
        viewModelScope.launch {
            // Create budget for merchant as category
            logAlertAction("pattern_budget_set", "$merchant:₹$amount")
        }
    }

    // Log any pattern action
    fun logPatternAction(action: String, details: String) {
        logAlertAction("pattern_$action", details)
    }

    suspend fun suggestPatternBudget(merchant: String): Double {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val recent = expenseDao.getExpensesBetween(weekAgo, System.currentTimeMillis())
            .filter { it.merchant == merchant }

        return if (recent.isNotEmpty()) {
            recent.takeLast(3).map { it.amount }.average() * 0.8  // ✅ .map { it.amount }
        } else {
            300.0
        }
    }

}
