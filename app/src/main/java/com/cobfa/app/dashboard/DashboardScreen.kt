package com.cobfa.app.dashboard

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import android.Manifest
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.sms.SmsInboxReader
import com.cobfa.app.sms.SmsProcessor
import com.cobfa.app.ui.expense.list.CategoryPickerBottomSheet
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel

@Composable
fun DashboardScreen(
    navController: NavController,
    onLogout: () -> Unit) {

    val context = LocalContext.current
    val autoTrackingEnabled =
        PreferenceManager.isAutoTrackingEnabled(context)

    var autoTracking by remember {
        mutableStateOf(autoTrackingEnabled)
    }

    val db = remember {
        ExpenseDatabase.getInstance(context)
    }

    val pendingVm = remember {
        PendingExpensesViewModel(
            ExpenseRepository(db.expenseDao())
        )
    }

    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(context)
    )

    //Launch Effects
    LaunchedEffect(Unit) {
        vm.loadCurrentMonthSummary()
    }

    val summary by vm.summary.collectAsState()

    LaunchedEffect(summary) {
        summary?.let {
            Log.d(
                "ANALYTICS",
                "Income=${it.income}, Expense=${it.expense}, Balance=${it.balance}"
            )
        }
    }

    LaunchedEffect(autoTracking) {
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

        if (autoTracking && granted) {
            Log.d("SMS_FLOW", "Auto-tracking ON → scanning inbox")

            val messages = SmsInboxReader.readRecentSms(context)

            Log.d("SMS_FLOW", "Read ${messages.size} SMS messages")

            val lastTs = PreferenceManager.getLastSmsTimestamp(context)
            var maxTs = lastTs

            messages.forEach { sms ->
                if (sms.timestamp > lastTs) {
                    SmsProcessor.process(
                        context = context,
                        sender = sms.address,
                        body = sms.body,
                        timestamp = sms.timestamp
                    )
                    if (sms.timestamp > maxTs) {
                        maxTs = sms.timestamp
                    }
                }
            }

            if (maxTs > lastTs) {
                PreferenceManager.setLastSmsTimestamp(context, maxTs)
            }

        }
    }

    //Main Column
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        //Auto tracking toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Expense Tracking",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Detect expenses from bank & UPI SMS",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Switch(
                    checked = autoTracking,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_SMS
                                ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                PreferenceManager.setAutoTrackingEnabled(context, true)
                                autoTracking = true
                            } else {
                                PreferenceManager.setPendingAutoTracking(context, true)
                                navController.navigate("sms_permission")
                            }
                        } else {
                            PreferenceManager.setAutoTrackingEnabled(context, false)
                            autoTracking = false
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineSmall
        )

        //Summary card
        summary?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                SummaryCard(
                    title = "Income",
                    amount = it.income,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                SummaryCard(
                    title = "Expense",
                    amount = it.expense,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )

                SummaryCard(
                    title = "Balance",
                    amount = it.balance,
                    color = if (it.balance >= 0)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Welcome to CoB-FA")

        Spacer(Modifier.height(16.dp))

        //Pending expenses
        PendingExpensesSection(vm = pendingVm)

        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            onLogout()
        }) {
            Text("Logout")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController.navigate("expenses") }
        ) {
            Text("View Expenses")
        }
    }
}

@Composable
fun PendingExpensesSection(
    vm: PendingExpensesViewModel
) {
    val expenses by vm.pendingExpenses.collectAsState(initial = emptyList())
    var selectedExpenseId by remember { mutableStateOf<Long?>(null) }

    if (expenses.isEmpty()) return

    Column {
        Text("Pending expenses", style = MaterialTheme.typography.titleMedium)

        expenses.forEach { e ->
            Card(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${e.type.name}  ₹${e.amount}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(e.merchant ?: "Unknown", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { selectedExpenseId = e.id }) {
                        Text("Confirm")
                    }
                }
                selectedExpenseId?.let { expenseId ->
                    CategoryPickerBottomSheet(
                        onCategorySelected = { category ->
                            vm.confirm(expenseId, category)
                            selectedExpenseId = null
                        },
                        onDismiss = {
                            selectedExpenseId = null
                        }
                    )
                }

            }
        }
    }
}
