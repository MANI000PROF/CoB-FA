package com.cobfa.app.ui.dashboard

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.NudgeEventEntity
import com.cobfa.app.data.repository.AnalyticsRepository
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.GamificationRepository
import com.cobfa.app.data.repository.PersonalizedRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.domain.model.MonthlySummary
import com.cobfa.app.domain.model.PersonalizedInsight
import com.cobfa.app.insights_ml.debug.SyntheticHistoryGenerator
import com.cobfa.app.sms.SmsFilters
import com.cobfa.app.sms.SmsInboxReader
import com.cobfa.app.sms.SmsProcessor
import com.cobfa.app.utils.ExpenseLogger
import com.cobfa.app.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
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

    private val _budgetWarnings = MutableStateFlow<List<BudgetWarning>>(emptyList())
    val budgetWarnings: StateFlow<List<BudgetWarning>> = _budgetWarnings

    private val _personalizedInsights = MutableStateFlow<List<PersonalizedInsight>>(emptyList())
    val personalizedInsights: StateFlow<List<PersonalizedInsight>> = _personalizedInsights

    data class BudgetWarning(
        val category: String,
        val percentage: Int,
        val spent: Double,
        val budget: Double
    )

    data class BudgetAlert(
        val category: String,
        val percentage: Int,
        val ruleType: String,
        val message: String,
        val suggestedAction: String? = null
    )

    data class BudgetHealthUi(
        val totalBudgets: Int = 0,
        val withinBudget: Int = 0,
        val overBudget: Int = 0
    )

    data class BalanceStep(
        val label: String,
        val amount: Double,
        val remainingAfter: Double
    )

    private val _budgetHealth = MutableStateFlow(BudgetHealthUi())
    val budgetHealth: StateFlow<BudgetHealthUi> = _budgetHealth

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isInitialLoadDone = MutableStateFlow(false)
    val isInitialLoadDone: StateFlow<Boolean> = _isInitialLoadDone

    private val _isBudgetHealthResolved = MutableStateFlow(false)
    val isBudgetHealthResolved: StateFlow<Boolean> = _isBudgetHealthResolved

    private val _isInsightsResolved = MutableStateFlow(false)
    val isInsightsResolved: StateFlow<Boolean> = _isInsightsResolved

    var onRefreshRequest: suspend () -> Unit = {}

    private val gamificationRepo = GamificationRepository(
        context = context,
        nudgeDao = db.nudgeEventDao(),
        pointsDao = db.pointsDao(),
        achievementDao = db.achievementDao(),
        budgetRepo = BudgetRepository(db.budgetDao()),
        expenseDao = db.expenseDao()
    )

    init {
        onRefreshRequest = { scanSmsAndSync() }

        viewModelScope.launch {
            try {
                syncManager.restoreFromFirestore()
                syncManager.restoreBudgetsFromFirestore()

                checkForBudgetAlerts()
                refreshPersonalizedInsights()
                refreshBudgetHealth()
            } finally {
                _isInitialLoadDone.value = true
            }
        }

        viewModelScope.launch {
            val prefs = context.getSharedPreferences("cobfa_gamification", Context.MODE_PRIVATE)
            var lastTs = prefs.getLong("last_nudge_processed_ts", 0L)

            val initial = db.nudgeEventDao().getEventsSince(lastTs)
            if (initial.isNotEmpty()) {
                gamificationRepo.processNudgeEvents(initial)
                lastTs = initial.maxOf { it.timestamp }
                prefs.edit().putLong("last_nudge_processed_ts", lastTs).apply()
            }

            while (isActive) {
                val newer = db.nudgeEventDao().getEventsSince(lastTs)
                if (newer.isNotEmpty()) {
                    gamificationRepo.processNudgeEvents(newer)
                    lastTs = newer.maxOf { it.timestamp }
                    prefs.edit().putLong("last_nudge_processed_ts", lastTs).apply()
                }
                delay(5_000)
            }
        }
    }

    suspend fun computeBalanceBreakdown(): List<BalanceStep> {
        val monthStart = getMonthStart()
        val monthEnd = getMonthEnd()

        val summaryNow = summary.value ?: return emptyList()
        val income = summaryNow.income

        val totals = expenseDao.getExpensesBetween(monthStart, monthEnd)
            .filter { it.status == ExpenseStatus.CONFIRMED && it.type == ExpenseType.DEBIT }
            .groupBy { it.category?.name ?: "Other" }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val steps = mutableListOf<BalanceStep>()
        var remaining = income

        steps.add(
            BalanceStep(
                label = "Start (income)",
                amount = 0.0,
                remainingAfter = remaining
            )
        )

        for ((label, amt) in totals) {
            remaining -= amt
            steps.add(
                BalanceStep(
                    label = label,
                    amount = amt,
                    remainingAfter = remaining
                )
            )
        }

        return steps
    }

    fun refreshSms() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                ExpenseLogger.logValidationFailed(
                    "refresh",
                    "manual",
                    "User triggered pull-to-refresh"
                )
                onRefreshRequest()
                checkForBudgetAlerts()
                refreshPersonalizedInsights()
                refreshBudgetHealth()
            } catch (e: Exception) {
                ExpenseLogger.logDatabaseError("refreshSms", e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun refreshBudgetHealth() {
        viewModelScope.launch {
            _isBudgetHealthResolved.value = false
            try {
                val monthStart = getMonthStart()
                val usages = budgetRepo.getBudgetUsageForMonth(monthStart, expenseDao)

                val total = usages.size
                val over = usages.count { it.percentageUsed >= 100 }
                val within = total - over

                _budgetHealth.value = BudgetHealthUi(
                    totalBudgets = total,
                    withinBudget = within,
                    overBudget = over
                )
            } finally {
                _isBudgetHealthResolved.value = true
            }
        }
    }

    private fun refreshPersonalizedInsights() {
        viewModelScope.launch {
            _isInsightsResolved.value = false
            try {
                _personalizedInsights.value = personalizationRepo.computeInsightsForCurrentMonth()
            } finally {
                _isInsightsResolved.value = true
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
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    private var today80PercentAlertsShown = mutableSetOf<String>()

    private suspend fun checkForBudgetAlerts() {
        if (isNewDay()) today80PercentAlertsShown.clear()
        val monthStart = getMonthStart()
        val usages = budgetRepo.getBudgetUsageForMonth(monthStart, expenseDao)

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

        val warnings = usages.filter {
            it.alertsEnabled &&
                    it.percentageUsed >= 80 &&
                    it.percentageUsed < 100 &&
                    !is80WarningDismissed(it.category.name) &&
                    !hasRecentGoodBudgetAction(it.category.name)
        }.map {
            BudgetWarning(
                category = it.category.name,
                percentage = it.percentageUsed.toInt(),
                spent = it.spentAmount,
                budget = it.budgetAmount
            )
        }

        _budgetWarnings.value = warnings
        _activeAlert.value = null
        checkForPatternAlerts()
    }

    private suspend fun hasRecentGoodBudgetAction(category: String): Boolean {
        val since = System.currentTimeMillis() - 12L * 60 * 60 * 1000
        return nudgeEventDao.getEventsSince(since).any {
            it.type.equals("BUDGET_80", true) &&
                    it.category == normalizeCategoryKey(category) &&
                    it.action == "details"
        }
    }

    private suspend fun checkForPatternAlerts() {
        val todayStart = getTodayTimestampStart()
        val todayEnd = getTodayTimestampEnd()
        val todayExpenses = expenseDao.getExpensesBetween(todayStart, todayEnd)
            .filter { !isMerchantBlocked(it.merchant ?: "") }

        val merchantCounts = todayExpenses
            .filter { it.merchant != null }
            .groupBy { it.merchant!! }
            .filter { it.value.size >= 3 }

        merchantCounts.forEach { (merchant, expenses) ->
            val since = getTodayTimestampStart()
            val catKey = expenses.firstOrNull()?.category?.name ?: "UNKNOWN"
            val merchantKey = normalizeMerchantKey(merchant)
            val typeKey = "merchant_3x|$merchantKey"

            if (nudgeEventDao.countDismissedSince(typeKey, catKey, since) > 0) return@forEach

            _activeAlert.value = BudgetAlert(
                category = merchant,
                percentage = 0,
                ruleType = "MERCHANT_3X",
                message = "$merchant (${expenses.size}x today) - Pattern detected!",
                suggestedAction = "reduce_${merchant.lowercase()}"
            )
            logNudgeEvent(typeKey, catKey)
            return
        }

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
            logNudgeEvent("category_5x", normalizeCategoryKey(category))
            return
        }

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
            val catKey = expenses.firstOrNull()?.category?.name ?: "UNKNOWN"
            val merchantKey = normalizeMerchantKey(merchant)
            logNudgeEvent("highvalue_3x|$merchantKey", catKey)
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
                        action = normalizeAction(action)
                    )
                )
            }
        }
        _activeAlert.value = null
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

    private fun logNudgeEvent(type: String, category: String) {
        viewModelScope.launch {
            val cat = normalizeCategoryKey(category)
            val cooldownMs = 6L * 60 * 60 * 1000
            val since = System.currentTimeMillis() - cooldownMs

            val alreadyLogged = nudgeEventDao.countSameNudgeSince(type, cat, since) > 0
            if (alreadyLogged) return@launch

            nudgeEventDao.insert(
                NudgeEventEntity(
                    type = type,
                    category = cat,
                    action = null
                )
            )
        }
    }

    fun logGenericNudge(type: String, category: String, action: String?) {
        viewModelScope.launch {
            nudgeEventDao.insert(
                NudgeEventEntity(
                    type = type,
                    category = normalizeCategoryKey(category),
                    action = normalizeAction(action)
                )
            )
        }
    }

    fun logAlertAction(ruleType: String, action: String) {
        viewModelScope.launch {
            nudgeEventDao.insert(
                NudgeEventEntity(
                    type = ruleType,
                    category = normalizeCategoryKey(_activeAlert.value?.category ?: "UNKNOWN"),
                    action = normalizeAction(action)
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
            dismissedWarningsToday.clear()
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

        viewModelScope.launch {
            checkForBudgetAlerts()
        }
    }

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

    fun createPatternBudget(merchant: String, amount: Double) {
        viewModelScope.launch {
            logAlertAction("pattern_budget_set", "$merchant:₹$amount")
        }
    }

    fun logPatternAction(action: String, details: String) {
        logAlertAction("pattern_$action", details)
    }

    suspend fun suggestPatternBudget(merchant: String): Double {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val recent = expenseDao.getExpensesBetween(weekAgo, System.currentTimeMillis())
            .filter { it.merchant == merchant }

        return if (recent.isNotEmpty()) {
            recent.takeLast(3).map { it.amount }.average() * 0.8
        } else {
            300.0
        }
    }

    private val personalizationRepo = PersonalizedRepository(context, expenseDao, budgetRepo, nudgeEventDao)

    private fun normalizeAction(action: String?): String? =
        action?.trim()?.lowercase(Locale.ROOT)

    private fun normalizeMerchantKey(raw: String?): String =
        (raw ?: "UNKNOWN").trim().uppercase(Locale.ROOT)

    private fun normalizeCategoryKey(raw: String?): String =
        (raw ?: "UNKNOWN").trim().uppercase(Locale.ROOT)

    fun debugGenerateHistory(weeks: Int = 12) {
        viewModelScope.launch {
            SyntheticHistoryGenerator.generate(
                expenseDao = expenseDao,
                plan = SyntheticHistoryGenerator.Plan(weeks = weeks)
            )
            refreshPersonalizedInsights()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun scanSmsAndSync() {
        val context = this.context
        val db = this.db
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ExpenseLogger.logValidationFailed("permission", "READ_SMS", "not granted")
            return
        }

        ExpenseLogger.logScanStart("DashboardScreen")

        val now = System.currentTimeMillis()
        val lastTs0 = PreferenceManager.getLastSmsTimestamp(context)
        val bootstrapStart = now - 90L * 24 * 60 * 60 * 1000
        val base = if (lastTs0 == 0L) bootstrapStart else lastTs0
        val overlapMs = 2L * 60 * 60 * 1000
        val since = (base - overlapMs).coerceAtLeast(0L)

        val messages = SmsInboxReader.readRecentSmsSince(context, sinceMs = since, limit = 200)
        ExpenseLogger.logSmsRead(messages.size)

        val repo = ExpenseRepository(db.expenseDao(), syncManager)

        var processedCount = 0
        var skippedCount = 0

        for (sms in messages) {
            if (SmsFilters.isBlocked(sms.body)) {
                skippedCount++
                continue
            }

            val inserted = SmsProcessor.processWithDedup(
                sender = sms.address,
                body = sms.body,
                timestamp = sms.timestamp,
                repo = repo,
                syncManager = syncManager
            )

            if (inserted) processedCount++ else skippedCount++
        }

        val newestTs = messages.maxOfOrNull { it.timestamp } ?: 0L
        if (newestTs > 0) PreferenceManager.setLastSmsTimestamp(context, newestTs)

        ExpenseLogger.logScanComplete(processedCount, skippedCount, "DashboardScreen")
    }

    private fun insightPrefs() =
        context.getSharedPreferences("cobfa_insights", Context.MODE_PRIVATE)

    private fun dismissedInsightsKey(): String = "dismissed_insights"
    private fun doneInsightsKey(): String = "done_insights"

    private fun getStringSetSafe(key: String): MutableSet<String> {
        val raw = insightPrefs().getStringSet(key, emptySet()) ?: emptySet()
        return raw.toMutableSet()
    }

    private fun putStringSet(key: String, values: Set<String>) {
        insightPrefs().edit().putStringSet(key, values.toSet()).apply()
    }

    fun markInsightDone(key: String) {
        val done = getStringSetSafe(doneInsightsKey())
        done.add(key)
        putStringSet(doneInsightsKey(), done)
        refreshPersonalizedInsights()
    }

    fun dismissInsight(key: String) {
        val dismissed = getStringSetSafe(dismissedInsightsKey())
        dismissed.add(key)
        putStringSet(dismissedInsightsKey(), dismissed)
        refreshPersonalizedInsights()
    }
}
