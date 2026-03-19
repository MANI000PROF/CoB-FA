package com.cobfa.app.ui.expense.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.ui.expense.category.CategoryPickerBottomSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_FILTER_AMOUNT = 50000f

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ExpenseListScreen(vm: ExpenseListViewModel) {
    val expenses by vm.expenses.collectAsState()
    val isInitialLoading = expenses == null
    val expenseList = expenses.orEmpty()
    val searchQuery by vm.searchQuery.collectAsState()
    val categoryFilter by vm.categoryFilter.collectAsState()
    val onlyUncat by vm.onlyUncategorized.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val merchantFilter by vm.merchantFilter.collectAsState()
    val excludedMode by vm.excludedMode.collectAsState()
    val amountRange by vm.amountRange.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    val noExpensesAtAll = expenseList.isEmpty() &&
            searchQuery.isBlank() &&
            categoryFilter == null &&
            merchantFilter == null &&
            !onlyUncat &&
            excludedMode == ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY &&
            sortMode == ExpenseListViewModel.SortMode.NEWEST &&
            amountRange.start == 0f &&
            amountRange.endInclusive == MAX_FILTER_AMOUNT

    val hasActiveFilters =
        searchQuery.isNotBlank() ||
                categoryFilter != null ||
                merchantFilter != null ||
                onlyUncat ||
                excludedMode != ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY ||
                sortMode != ExpenseListViewModel.SortMode.NEWEST ||
                amountRange.start > 0f ||
                amountRange.endInclusive < MAX_FILTER_AMOUNT

    val activeFilterCount = buildList {
        if (searchQuery.isNotBlank()) add("search")
        if (categoryFilter != null) add("category")
        if (merchantFilter != null) add("merchant")
        if (onlyUncat) add("uncategorized")
        if (excludedMode != ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY) add("excluded")
        if (sortMode != ExpenseListViewModel.SortMode.NEWEST) add("sort")
        if (amountRange.start > 0f || amountRange.endInclusive < MAX_FILTER_AMOUNT) add("amount")
    }.size

    val dayFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val grouped = remember(expenseList) { expenseList.groupBy { dayFmt.format(Date(it.timestamp)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "search_filters") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = vm::updateSearchQuery,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Search expenses") },
                                placeholder = { Text("Merchant or category") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { vm.updateSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { showFilterSheet = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        if (activeFilterCount > 0) {
                                            "Filters ($activeFilterCount)"
                                        } else {
                                            "Filters"
                                        }
                                    )
                                }

                                if (hasActiveFilters) {
                                    TextButton(onClick = { vm.clearFilters() }) {
                                        Text("Clear all")
                                    }
                                }
                            }

                            if (hasActiveFilters) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    merchantFilter?.let { ActiveFilterChip(text = it) }

                                    categoryFilter?.let {
                                        ActiveFilterChip(
                                            text = it.name.replace("_", " ")
                                                .lowercase()
                                                .replaceFirstChar { c -> c.uppercase() }
                                        )
                                    }

                                    if (onlyUncat) {
                                        ActiveFilterChip("Uncategorized")
                                    }

                                    if (sortMode == ExpenseListViewModel.SortMode.OLDEST) {
                                        ActiveFilterChip("Oldest first")
                                    }

                                    when (excludedMode) {
                                        ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED ->
                                            ActiveFilterChip("Include excluded")

                                        ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY ->
                                            ActiveFilterChip("Excluded only")

                                        else -> Unit
                                    }

                                    if (amountRange.start > 0f || amountRange.endInclusive < MAX_FILTER_AMOUNT) {
                                        ActiveFilterChip(
                                            text = "₹${amountRange.start.toInt()} - ₹${amountRange.endInclusive.toInt()}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isInitialLoading) {
                    item(key = "initial_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ExpenseListLoadingState()
                        }
                    }
                } else if (expenseList.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (noExpensesAtAll) {
                                ExpenseEmptyState(
                                    rawRes = R.raw.expenses_empty,
                                    title = "No expenses yet",
                                    subtitle = "Your recorded transactions will appear here once expenses are added."
                                )
                            } else {
                                ExpenseEmptyState(
                                    rawRes = R.raw.expenses_search_empty,
                                    title = "No matching expenses",
                                    subtitle = "Try adjusting search or filters to find what you need.",
                                    action = {
                                        TextButton(onClick = { vm.clearFilters() }) {
                                            Text("Clear filters")
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    grouped.forEach { (day, itemsForDay) ->
                        stickyHeader(key = "day:$day") {
                            Surface(
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                        }

                        items(itemsForDay, key = { it.id }) { expense ->
                            ModernExpenseRow(
                                expense = expense,
                                onClick = {
                                    selectedExpense = expense
                                    showDetailsSheet = true
                                }
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (!isInitialLoading && expenseList.isNotEmpty()) {
                ExpenseListScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }

    if (showFilterSheet) {
        ExpenseFilterSheet(
            categoryFilter = categoryFilter,
            onlyUncat = onlyUncat,
            sortMode = sortMode,
            excludedMode = excludedMode,
            amountRange = amountRange,
            onDismiss = { showFilterSheet = false },
            onCategorySelected = vm::updateCategoryFilter,
            onOnlyUncatChanged = vm::setOnlyUncategorized,
            onToggleSort = vm::toggleSort,
            onExcludedModeSelected = vm::setExcludedMode,
            onAmountRangeChanged = vm::setAmountRange,
            onClearAll = vm::clearFilters
        )
    }

    if (showDetailsSheet && selectedExpense != null) {
        ExpenseDetailsBottomSheet(
            expense = selectedExpense!!,
            onDismiss = { showDetailsSheet = false },
            onChangeCategory = { showCategoryPicker = true },
            onEdit = { showEditSheet = true },
            onDelete = {
                val deletedId = selectedExpense!!.id
                vm.softDeleteExpense(deletedId)
                showDetailsSheet = false

                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Expense excluded",
                        actionLabel = "Undo",
                        withDismissAction = true
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        vm.restoreExpense(deletedId)
                    }
                }
            }
        )
    }

    if (showCategoryPicker && selectedExpense != null) {
        CategoryPickerBottomSheet(
            onCategorySelected = { cat ->
                vm.updateExpenseCategory(selectedExpense!!.id, cat)
                showCategoryPicker = false
                showDetailsSheet = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showEditSheet && selectedExpense != null) {
        EditExpenseBottomSheet(
            expense = selectedExpense!!,
            onDismiss = { showEditSheet = false },
            onSave = { newMerchant, newAmount ->
                val e = selectedExpense!!
                vm.editExpense(
                    id = e.id,
                    merchant = newMerchant,
                    amount = newAmount,
                    timestamp = e.timestamp
                )
                showEditSheet = false
                showDetailsSheet = false
            }
        )
    }
}

@Composable
private fun ExpenseListLoadingState() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.loader)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Loading expenses...",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Preparing your transactions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveFilterChip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFilterSheet(
    categoryFilter: ExpenseCategory?,
    onlyUncat: Boolean,
    sortMode: ExpenseListViewModel.SortMode,
    excludedMode: ExpenseListViewModel.ExcludedMode,
    amountRange: ClosedFloatingPointRange<Float>,
    onDismiss: () -> Unit,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    onOnlyUncatChanged: (Boolean) -> Unit,
    onToggleSort: () -> Unit,
    onExcludedModeSelected: (ExpenseListViewModel.ExcludedMode) -> Unit,
    onAmountRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onClearAll: () -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClearAll) {
                    Text("Reset")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Category", style = MaterialTheme.typography.titleSmall)

                Box {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            categoryFilter?.name?.replace("_", " ")
                                ?.lowercase()
                                ?.replaceFirstChar { it.uppercase() }
                                ?: "All categories"
                        )
                    }

                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All categories") },
                            onClick = {
                                onCategorySelected(null)
                                categoryMenuExpanded = false
                            }
                        )
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        cat.name.replace("_", " ")
                                            .lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                },
                                onClick = {
                                    onCategorySelected(cat)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Amount range", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "₹${amountRange.start.toInt()} - ₹${amountRange.endInclusive.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RangeSlider(
                    value = amountRange,
                    onValueChange = onAmountRangeChanged,
                    valueRange = 0f..MAX_FILTER_AMOUNT
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Only uncategorized", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Show only transactions without a category",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = onlyUncat,
                    onCheckedChange = onOnlyUncatChanged
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sort", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        onClick = {
                            if (sortMode != ExpenseListViewModel.SortMode.NEWEST) onToggleSort()
                        },
                        selected = sortMode == ExpenseListViewModel.SortMode.NEWEST
                    ) {
                        Text("Newest")
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        onClick = {
                            if (sortMode != ExpenseListViewModel.SortMode.OLDEST) onToggleSort()
                        },
                        selected = sortMode == ExpenseListViewModel.SortMode.OLDEST
                    ) {
                        Text("Oldest")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Excluded expenses", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        onClick = {
                            onExcludedModeSelected(ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY)
                        },
                        selected = excludedMode == ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY
                    ) {
                        Text("Active")
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        onClick = {
                            onExcludedModeSelected(ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED)
                        },
                        selected = excludedMode == ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED
                    ) {
                        Text("Include")
                    }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        onClick = {
                            onExcludedModeSelected(ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY)
                        },
                        selected = excludedMode == ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY
                    ) {
                        Text("Excluded")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernExpenseRow(
    expense: ExpenseEntity,
    onClick: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isCredit = expense.type == ExpenseType.CREDIT
    val amountColor = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val typeLabel = if (isCredit) "Credit" else "Debit"
    val categoryLabel = (expense.category?.name ?: "Uncategorized").replace("_", " ")
    val srcLabel = expense.source?.toString()?.uppercase(Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = expense.merchant ?: "Unknown merchant",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (expense.editedAt != null) SmallBadge("Edited")
                        if (!srcLabel.isNullOrBlank()) SmallBadge(srcLabel)
                        SmallBadge(typeLabel)
                    }

                    Text(
                        text = timeFmt.format(Date(expense.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                Text(
                    text = "₹${String.format("%.0f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor
                )
            }
        )
    }
}

@Composable
private fun SmallBadge(text: String) {
    AssistChip(
        onClick = { },
        enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun ExpenseEmptyState(
    rawRes: Int,
    title: String,
    subtitle: String,
    action: @Composable (() -> Unit)? = null
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(220.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action?.let {
            Spacer(modifier = Modifier.height(10.dp))
            it()
        }
    }
}

@Composable
private fun ExpenseListScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItemsCount = layoutInfo.totalItemsCount

    if (totalItemsCount == 0 || visibleItems.isEmpty()) return

    val targetAlpha = if (listState.isScrollInProgress) 1f else 0f
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (listState.isScrollInProgress) 120 else 420
        ),
        label = "expenseScrollbarAlpha"
    )

    if (animatedAlpha <= 0.01f) return

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeightPx = (viewportEnd - viewportStart).coerceAtLeast(1)

    val totalVisibleItemsSize = visibleItems.sumOf { it.size }
    val avgItemSizePx = (totalVisibleItemsSize / visibleItems.size.toFloat()).coerceAtLeast(1f)

    val estimatedContentHeightPx = avgItemSizePx * totalItemsCount
    val visibleFraction = (viewportHeightPx / estimatedContentHeightPx).coerceIn(0.06f, 1f)

    val firstVisible = visibleItems.first()
    val rawScrollOffsetPx =
        (firstVisible.index * avgItemSizePx) - firstVisible.offset.toFloat()

    val maxScrollPx = (estimatedContentHeightPx - viewportHeightPx).coerceAtLeast(1f)
    val scrollFraction = (rawScrollOffsetPx / maxScrollPx).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(10.dp)
    ) {
        val trackTopPadding = 8.dp
        val trackBottomPadding = 8.dp
        val trackHeight = maxHeight - trackTopPadding - trackBottomPadding
        val thumbHeight = (trackHeight * visibleFraction).coerceIn(36.dp, 96.dp)
        val thumbTravel = (trackHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = thumbTravel * scrollFraction

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .padding(top = trackTopPadding, bottom = trackBottomPadding),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(100)
                    )
            )

            Box(
                modifier = Modifier
                    .padding(top = thumbOffset)
                    .width(4.dp)
                    .height(thumbHeight)
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        alpha = animatedAlpha
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(100)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailsBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onChangeCategory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val isCredit = expense.type == ExpenseType.CREDIT
    val typeLabel = if (isCredit) "Credit" else "Debit"

    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Exclude this expense?") },
            text = { Text("This will remove it from your lists and analytics. You can undo right after excluding.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text("Exclude") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense details", style = MaterialTheme.typography.titleMedium)
                if (expense.editedAt != null) {
                    Text(
                        "Edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            SheetRow("Merchant", expense.merchant ?: "Unknown")
            SheetRow("Amount", "₹${String.format("%.0f", expense.amount)}")
            SheetRow("Type", typeLabel)
            SheetRow("Source", expense.source?.toString().orEmpty())
            SheetRow("Date", fmt.format(Date(expense.timestamp)))
            SheetRow("Category", (expense.category?.name ?: "Uncategorized").replace("_", " "))

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Edit") }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onChangeCategory,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Change category") }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Exclude") }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (merchant: String?, amount: Double) -> Unit
) {
    var merchant by remember(expense.id) { mutableStateOf(expense.merchant.orEmpty()) }
    var amountText by remember(expense.id) { mutableStateOf(String.format("%.0f", expense.amount)) }
    var amountError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Edit expense", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountError = null
                },
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("₹") },
                isError = amountError != null,
                supportingText = {
                    amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        val amt = amountText.trim().toDoubleOrNull()
                        if (amt == null || amt < 0.0) {
                            amountError = "Enter a valid amount"
                            return@Button
                        }
                        onSave(merchant.trim().ifBlank { null }, amt)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}