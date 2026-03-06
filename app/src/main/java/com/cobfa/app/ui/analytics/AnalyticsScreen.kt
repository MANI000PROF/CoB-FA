package com.cobfa.app.ui.analytics

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            if (selectedRange == AnalyticsRange.MONTH) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(selectedMonthLabel, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onNextMonth, enabled = canGoNextMonth) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                    }
                }
            }

            AnalyticsSectionCard {
                Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                PieChartView(
                    data = ui.categoryBreakdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }

            AnalyticsSectionCard {
                Text("Spending trend", style = MaterialTheme.typography.titleMedium)
                LineChartView(
                    data = ui.trend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }

            AnalyticsSectionCard {
                Text("Top categories", style = MaterialTheme.typography.titleMedium)

                if (ui.topCategories.isEmpty()) {
                    Text(
                        "No data yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ui.topCategories.forEachIndexed { idx, item ->
                        ListItem(
                            leadingContent = {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = { Text(item.label) },
                            trailingContent = {
                                Text(
                                    "₹${item.amount.roundToInt()}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )

                        if (idx != ui.topCategories.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            AnalyticsSectionCard {
                Text("AI insights", style = MaterialTheme.typography.titleMedium)

                if (ui.insights.isEmpty()) {
                    Text("No insights yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.insights.forEach { insight ->
                            Text("• $insight", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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
private fun AnalyticsSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
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

                setEntryLabelColor(onSurfaceVariant)
                setEntryLabelTextSize(11f)

                setDrawHoleEnabled(true)
                holeRadius = 58f
                transparentCircleRadius = 62f
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
            }

            chart.legend.textColor = onSurface
            chart.setEntryLabelColor(onSurfaceVariant)

            chart.data = PieData(ds).apply {
                setValueFormatter(object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "₹${value.roundToInt()}"
                })
            }

            chart.invalidate()
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

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description = Description().apply { text = "" }
                axisRight.isEnabled = false
                legend.isEnabled = false

                xAxis.apply {
                    granularity = 1f
                    setDrawGridLines(false)
                    position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                    textColor = onSurfaceVariant
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    textColor = onSurfaceVariant
                }
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
                lineWidth = 2f
                setDrawCircles(true)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setCircleColor(primary)
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
            chart.legend.textColor = onSurfaceVariant

            chart.data?.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.invalidate()
        }
    )
}
