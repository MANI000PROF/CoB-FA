package com.cobfa.app.ui.budget

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import java.util.Locale
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.DirectionsCar
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(vm: BudgetViewModel) {
    val ui by vm.uiState.collectAsState()
    val isCurrentMonth = ui.isCurrentMonth

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedBudgetForDelete by remember { mutableStateOf<BudgetEntity?>(null) }
    var editBudget by remember { mutableStateOf<BudgetEntity?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        vm.restoreBudgetsFromFirestoreOnce()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Budgets") },
                actions = {
                    if (ui.isCurrentMonth) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add budget")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                BudgetHeroCard(
                    isCurrentMonth = ui.isCurrentMonth,
                    monthLabel = ui.monthLabel,
                    budgetCount = ui.rows.size,
                    onAddBudget = { showAddDialog = true }
                )
            }

            item {
                BudgetMonthNavigator(
                    monthLabel = ui.monthLabel,
                    canGoNextMonth = ui.canGoNextMonth,
                    onPrevMonth = vm::prevMonth,
                    onNextMonth = vm::nextMonth
                )
            }

            if (ui.rows.isEmpty()) {
                item {
                    BudgetEmptyState(
                        canAddBudget = ui.isCurrentMonth,
                        onAddBudget = { showAddDialog = true }
                    )
                }
            } else {
                items(ui.rows, key = { it.budget.id }) { row ->
                    BudgetRow(
                        budget = row.budget,
                        spentAmount = row.spent,
                        progress = row.progress,
                        canEdit = isCurrentMonth,
                        canDelete = isCurrentMonth,
                        onEdit = { editBudget = row.budget },
                        onDelete = { selectedBudgetForDelete = row.budget }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onSave = { cat, amt ->
                vm.addOrUpdateBudget(cat, amt)
                showAddDialog = false
            }
        )
    }

    editBudget?.let { b ->
        EditBudgetDialog(
            budget = b,
            onDismiss = { editBudget = null },
            onUpdate = { amt ->
                vm.addOrUpdateBudget(b.category, amt)
                editBudget = null
            }
        )
    }

    selectedBudgetForDelete?.let { budget ->
        DeleteBudgetDialog(
            budget = budget,
            onDismiss = { selectedBudgetForDelete = null },
            onConfirm = {
                vm.deleteBudget(budget.category)
                selectedBudgetForDelete = null
            }
        )
    }
}

@Composable
private fun BudgetHeroCard(
    isCurrentMonth: Boolean,
    monthLabel: String,
    budgetCount: Int,
    onAddBudget: () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.budget_anim)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(88.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (budgetCount > 0) {
                        "$budgetCount budget categories being tracked"
                    } else {
                        "Set focused limits and track spending smarter"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrentMonth) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onAddBudget, contentPadding = PaddingValues(0.dp)) {
                        Text("Add budget")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetMonthNavigator(
    monthLabel: String,
    canGoNextMonth: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }

            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(onClick = onNextMonth, enabled = canGoNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }
    }
}

@Composable
private fun BudgetEmptyState(
    canAddBudget: Boolean,
    onAddBudget: () -> Unit
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.budget_track_anim)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(160.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No budgets set yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Create category-wise limits and keep spending under control.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canAddBudget) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onAddBudget) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create first budget")
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    budget: BudgetEntity,
    spentAmount: Double,
    progress: Double,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (budget.amount > 0) (spentAmount / budget.amount * 100.0) else 0.0
    val isWarn = pct >= 80.0 && pct < 100.0
    val isOver = pct >= 100.0

    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "budgetProgress"
    )

    val containerColor = when {
        isOver -> MaterialTheme.colorScheme.errorContainer
        isWarn -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val accentColor = when {
        isOver -> MaterialTheme.colorScheme.error
        isWarn -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = budgetCategoryIcon(budget.category),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = budget.category.name
                                .replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Budget ₹${budget.amount.roundToInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, enabled = canEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit budget")
                    }
                    IconButton(onClick = onDelete, enabled = canDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete budget")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(100)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val remaining = budget.amount - spentAmount
            val remainingText = if (remaining >= 0) {
                "₹${remaining.roundToInt()} left"
            } else {
                "₹${(-remaining).roundToInt()} over"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.10f)
                ) {
                    Text(
                        text = remainingText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                }

                Text(
                    text = "₹${spentAmount.roundToInt()} / ₹${budget.amount.roundToInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory, Double) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var amountText by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name?.replace("_", " ")
                            ?.lowercase()
                            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseCategory.entries
                            .sortedBy { it.name }
                            .forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name.replace("_", " ")) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        amountText = filtered
                        error = null
                    },
                    label = { Text("Budget amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    supportingText = {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedCategory != null && amountText.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (selectedCategory == null || amt == null || amt <= 0) {
                        error = "Enter a valid category and amount"
                        return@Button
                    }
                    onSave(selectedCategory!!, amt)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditBudgetDialog(
    budget: BudgetEntity,
    onDismiss: () -> Unit,
    onUpdate: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(budget.amount.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Category: ${budget.category.name.replace("_", " ")}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        error = null
                    },
                    label = { Text("Budget amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    supportingText = {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        error = "Enter a valid amount"
                        return@Button
                    }
                    onUpdate(amt)
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteBudgetDialog(
    budget: BudgetEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Budget") },
        text = { Text("Delete ${budget.category.name.replace("_", " ")} budget?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun budgetCategoryIcon(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FOOD -> Icons.Default.LocalDining
    ExpenseCategory.TRANSPORT -> Icons.Default.DirectionsCar
    ExpenseCategory.SHOPPING -> Icons.Default.ShoppingBag
    ExpenseCategory.ENTERTAINMENT -> Icons.Default.Movie
    ExpenseCategory.BILLS -> Icons.Default.ReceiptLong
    ExpenseCategory.HEALTH -> Icons.Default.LocalHospital
    ExpenseCategory.EDUCATION -> Icons.Default.School
    ExpenseCategory.GROCERIES -> Icons.Default.LocalGroceryStore
    ExpenseCategory.OTHER -> Icons.Default.Category
}
