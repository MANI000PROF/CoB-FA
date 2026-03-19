package com.cobfa.app.navigation

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.ui.analytics.AnalyticsScreen
import com.cobfa.app.ui.analytics.AnalyticsViewModel
import com.cobfa.app.ui.budget.BudgetScreen
import com.cobfa.app.ui.budget.BudgetViewModel
import com.cobfa.app.ui.dashboard.DashboardScreen
import com.cobfa.app.ui.expense.list.ExpenseListScreen
import com.cobfa.app.ui.expense.list.ExpenseListViewModel
import com.cobfa.app.ui.expense.list.ExpenseListViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private data class MainNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScaffold(
    rootNavController: NavHostController
) {
    UpdateSystemBars()

    val mainNavController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val items = listOf(
        MainNavItem("dashboard", "Home", Icons.Default.Home),
        MainNavItem("budgets", "Budgets", Icons.Default.AccountBalanceWallet),
        MainNavItem("analytics", "Analytics", Icons.Default.BarChart),
        MainNavItem("expenses?merchant={merchant}", "Expenses", Icons.Default.ReceiptLong)
    )

    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun isExpensesRoute(route: String?) =
        route == "expenses?merchant={merchant}" || route?.startsWith("expenses?merchant=") == true

    val haptic = LocalHapticFeedback.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var drawerUser by remember { mutableStateOf(DrawerUserUi()) }

    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) return@LaunchedEffect

        val snap = FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .get()
            .await()

        drawerUser = DrawerUserUi(
            name = snap.child("name").getValue(String::class.java).orEmpty(),
            phone = snap.child("phone").getValue(String::class.java).orEmpty(),
            username = snap.child("username").getValue(String::class.java).orEmpty(),
            photoUrl = snap.child("photoUrl").getValue(String::class.java).orEmpty()
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProfileDrawerContent(
                user = drawerUser,
                onOpenProfile = {
                    scope.launch { drawerState.close() }
                    rootNavController.navigate("profile_view")
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    rootNavController.navigate("settings")
                },
                onOpenDetails = {
                    scope.launch { drawerState.close() }
                    rootNavController.navigate("account_insights")
                }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PremiumBottomBar(
                    items = items,
                    currentRoute = currentRoute,
                    isExpensesRoute = ::isExpensesRoute,
                    onItemClick = { item, selected ->
                        if (!selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            mainNavController.navigate(item.route) {
                                popUpTo(mainNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
        NavHost(
                navController = mainNavController,
                startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
                composable("dashboard") {
                    DashboardScreen(
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenBudgets = { mainNavController.navigate("budgets") },
                        onOpenAnalytics = { mainNavController.navigate("analytics") },
                        onOpenExpenses = { merchant ->
                            if (merchant.isNullOrBlank()) {
                                mainNavController.navigate("expenses?merchant={merchant}") {
                                    launchSingleTop = true
                                }
                            } else {
                                val encoded = URLEncoder.encode(merchant, "UTF-8")
                                mainNavController.navigate("expenses?merchant=$encoded") {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onOpenAchievements = { rootNavController.navigate("achievements") },
                        onOpenLeaderboard = { rootNavController.navigate("leaderboard") },
                        onRequestSmsPermission = { rootNavController.navigate("sms_permission") }
                    )
                }

                composable("budgets") {
                    val context = LocalContext.current
                    val db = remember { ExpenseDatabase.getInstance(context) }

                    val vm: BudgetViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                val repo = BudgetRepository(db.budgetDao())
                                val syncManager = SyncManager(db, FirestoreService())
                                return BudgetViewModel(db, repo, syncManager) as T
                            }
                        }
                    )

                    BudgetScreen(vm)
                }

                composable("analytics") {
                    val context = LocalContext.current
                    val db = remember { ExpenseDatabase.getInstance(context) }

                    val vm: AnalyticsViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AnalyticsViewModel(db) as T
                            }
                        }
                    )

                    val ui by vm.uiState.collectAsState()
                    val range by vm.range.collectAsState()
                    val selectedMonth: YearMonth by vm.selectedMonth.collectAsState()

                    val monthLabel = remember(selectedMonth) {
                        selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                    }

                    val canGoNextMonth = remember(selectedMonth) {
                        selectedMonth.isBefore(YearMonth.now())
                    }

                    AnalyticsScreen(
                        ui = ui,
                        selectedRange = range,
                        onRangeChange = { vm.setRange(it) },
                        selectedMonthLabel = monthLabel,
                        onPrevMonth = { vm.prevMonth() },
                        onNextMonth = { vm.nextMonth() },
                        canGoNextMonth = canGoNextMonth
                    )
                }

                composable(
                    "expenses?merchant={merchant}",
                    arguments = listOf(
                        navArgument("merchant") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val context = LocalContext.current
                    val merchant = backStackEntry.arguments?.getString("merchant")

                    val vm: ExpenseListViewModel = viewModel(
                        factory = ExpenseListViewModelFactory(context)
                    )

                    LaunchedEffect(merchant) {
                        if (!merchant.isNullOrBlank()) {
                            vm.updateMerchantFilter(merchant)
                        } else {
                            vm.updateMerchantFilter(null)
                        }
                    }

                    ExpenseListScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun PremiumBottomBar(
    items: List<MainNavItem>,
    currentRoute: String?,
    isExpensesRoute: (String?) -> Boolean,
    onItemClick: (MainNavItem, Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = when (item.route) {
                    "expenses?merchant={merchant}" -> isExpensesRoute(currentRoute)
                    else -> currentRoute == item.route
                }

                ModernBottomBarItem(
                    item = item,
                    selected = selected,
                    onClick = { onItemClick(item, selected) }
                )
            }
        }
    }
}

@Composable
private fun ModernBottomBarItem(
    item: MainNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val itemWidth by animateFloatAsState(
        targetValue = if (selected) 116f else 74f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "navWidth_${item.label}"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navIconScale_${item.label}"
    )

    val selectedAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "navBgAlpha_${item.label}"
    )

    Box(
        modifier = Modifier
            .width(itemWidth.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * selectedAlpha),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f * selectedAlpha)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            )

            AnimatedVisibility(visible = selected) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun UpdateSystemBars() {
    val view = LocalView.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val navigationColor = MaterialTheme.colorScheme.surface
    val useDarkIcons = true

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = navigationColor.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }
}
