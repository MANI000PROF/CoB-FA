package com.cobfa.app.ui.analytics

import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cobfa.app.R
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlin.math.roundToInt

@Immutable
data class CategorySpend(
    val label: String,
    val amount: Double
)

@Immutable
data class TrendPoint(
    val label: String,
    val amount: Double
)

@Immutable
data class AnalyticsUiState(
    val rangeLabel: String = "This week",
    val categoryBreakdown: List<CategorySpend> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val topCategories: List<CategorySpend> = emptyList(),
    val insights: List<String> = emptyList()
)

enum class AnalyticsRange { WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    ui: AnalyticsUiState,
    selectedRange: AnalyticsRange,
    onRangeChange: (AnalyticsRange) -> Unit,
    modifier: Modifier = Modifier,
    selectedMonthLabel: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNextMonth: Boolean
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Analytics") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeChip(
                    text = "Week",
                    selected = selectedRange == AnalyticsRange.WEEK,
                    onClick = { onRangeChange(AnalyticsRange.WEEK) }
                )
                RangeChip(
                    text = "Month",
                    selected = selectedRange == AnalyticsRange.MONTH,
                    onClick = { onRangeChange(AnalyticsRange.MONTH) }
                )
            }

            AnimatedContent(
                targetState = selectedRange == AnalyticsRange.MONTH,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(140))
                },
                label = "monthSwitcher"
            ) { showMonthRow ->
                if (showMonthRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevMonth) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                        }
                        Text(
                            text = selectedMonthLabel,
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = onNextMonth, enabled = canGoNextMonth) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }

            AnalyticsCardEntrance(index = 0) {
                AnalyticsHeroCard(
                    rangeLabel = ui.rangeLabel,
                    selectedMonthLabel = selectedMonthLabel,
                    selectedRange = selectedRange
                )
            }

            AnalyticsCardEntrance(index = 1) {
                AnalyticsSectionCard(
                    title = "Spending by category",
                    icon = Icons.Default.Insights
                ) {
                    key(selectedRange, selectedMonthLabel, ui.categoryBreakdown.hashCode()) {
                        PieChartView(
                            data = ui.categoryBreakdown,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                    }
                }
            }

            AnalyticsCardEntrance(index = 2) {
                AnalyticsSectionCard(
                    title = "Spending trend",
                    icon = Icons.Default.TrendingUp
                ) {
                    key(selectedRange, selectedMonthLabel, ui.trend.hashCode()) {
                        LineChartView(
                            data = ui.trend,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                    }
                }
            }

            AnalyticsCardEntrance(index = 3) {
                AnalyticsSectionCard(
                    title = "Top categories",
                    icon = Icons.Default.AutoAwesome
                ) {
                    TopCategoriesSection(ui.topCategories)
                }
            }

            AnalyticsCardEntrance(index = 4) {
                AnalyticsInsightsCard(ui.insights)
            }
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}

@Composable
private fun AnalyticsCardEntrance(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = index * 80, easing = FastOutSlowInEasing),
        label = "analyticsCardAlpha$index"
    )

    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(durationMillis = 420, delayMillis = index * 80, easing = FastOutSlowInEasing),
        label = "analyticsCardTranslation$index"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
    ) {
        content()
    }
}

@Composable
private fun AnalyticsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = accent.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            content()
        }
    }
}

@Composable
private fun TopCategoriesSection(items: List<CategorySpend>) {
    if (items.isEmpty()) {
        Text(
            "No data yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(5).forEachIndexed { idx, item ->
            val accent = when (idx) {
                0 -> MaterialTheme.colorScheme.primary
                1 -> MaterialTheme.colorScheme.secondary
                2 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Strong contributor to this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "₹${item.amount.roundToInt()}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsInsightsCard(insights: List<String>) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.08f),
                            tertiary.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        "AI insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Patterns worth your attention",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (insights.isEmpty()) {
                Text(
                    "No insights yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    insights.take(4).forEachIndexed { idx, insight ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = insight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeroCard(
    rangeLabel: String,
    selectedMonthLabel: String,
    selectedRange: AnalyticsRange
) {
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(R.raw.analytics_anim)
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
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            com.airbnb.lottie.compose.LottieAnimation(
                composition = composition,
                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                modifier = Modifier.size(84.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Spending intelligence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = if (selectedRange == AnalyticsRange.MONTH) {
                        "Insights and patterns for $selectedMonthLabel"
                    } else {
                        "Insights and patterns for $rangeLabel"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PieChartView(
    data: List<CategorySpend>,
    modifier: Modifier = Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PieChart(ctx).apply {
                setUsePercentValues(false)
                description = Description().apply { text = "" }

                legend.isEnabled = true
                legend.textColor = onSurface
                legend.textSize = 12f

                setEntryLabelColor(onSurfaceVariant)
                setEntryLabelTextSize(11f)

                setDrawHoleEnabled(true)
                holeRadius = 58f
                transparentCircleRadius = 62f
                isRotationEnabled = false
                setHighlightPerTapEnabled(true)
                setNoDataText("No data yet")
                setNoDataTextColor(onSurfaceVariant)
            }
        },
        update = { chart ->
            val entries = data
                .filter { it.amount > 0.0 }
                .map { PieEntry(it.amount.toFloat(), it.label) }

            if (entries.isEmpty()) {
                chart.clear()
                chart.invalidate()
                return@AndroidView
            }

            val ds = PieDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextColor = Color.WHITE
                valueTextSize = 12f
                sliceSpace = 2f
                selectionShift = 6f
            }

            chart.legend.textColor = onSurface
            chart.setEntryLabelColor(onSurfaceVariant)
            chart.highlightValues(null)

            chart.data = PieData(ds).apply {
                setValueFormatter(object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "₹${value.roundToInt()}"
                    }
                })
            }

            chart.notifyDataSetChanged()
            chart.animateY(650, Easing.EaseOutCubic)
        }
    )
}

@Composable
private fun LineChartView(
    data: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val primarySoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f).toArgb()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description = Description().apply { text = "" }
                axisRight.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setNoDataText("No data yet")
                setNoDataTextColor(onSurfaceVariant)

                xAxis.apply {
                    granularity = 1f
                    setDrawGridLines(false)
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    textColor = onSurfaceVariant
                    setDrawAxisLine(false)
                    yOffset = 8f
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = android.graphics.Color.argb(30, 120, 120, 120)
                    textColor = onSurfaceVariant
                    setDrawAxisLine(false)
                    xOffset = 10f
                    setLabelCount(5, false)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "₹${value.roundToInt()}"
                        }
                    }
                }

                setDrawGridBackground(false)
                setExtraOffsets(14f, 8f, 12f, 8f)
                setViewPortOffsets(72f, 20f, 24f, 40f)
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, p ->
                Entry(index.toFloat(), p.amount.toFloat())
            }

            if (entries.isEmpty()) {
                chart.clear()
                chart.invalidate()
                return@AndroidView
            }

            val ds = LineDataSet(entries, "Spend").apply {
                color = primary
                lineWidth = 2.5f
                setDrawCircles(true)
                circleRadius = 3.8f
                setCircleColor(primary)
                setCircleHoleColor(primary)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = primarySoft
                highLightColor = primary
                setDrawHorizontalHighlightIndicator(false)
            }

            chart.data = LineData(ds)

            val labels = data.map { it.label }
            chart.xAxis.apply {
                labelCount = minOf(labels.size, 7)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val i = value.toInt()
                        return labels.getOrNull(i).orEmpty()
                    }
                }
                textColor = onSurfaceVariant
            }

            chart.axisLeft.textColor = onSurfaceVariant
            chart.highlightValues(null)

            chart.data?.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.animateX(700, com.github.mikephil.charting.animation.Easing.EaseOutCubic)
        }
    )
}
