package com.cobfa.app.ui.analytics

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val label: String, // e.g., "Mon", "Tue" (optional for UI)
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

@Composable
fun AnalyticsScreen(
    ui: AnalyticsUiState,
    selectedRange: AnalyticsRange,
    onRangeChange: (AnalyticsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = ui.rangeLabel,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(16.dp))

        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        PieChartView(
            data = ui.categoryBreakdown,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text("Spending trend", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LineChartView(
            data = ui.trend,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text("Top categories", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.topCategories.isEmpty()) {
            Text("No data yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            ui.topCategories.forEachIndexed { idx, item ->
                Text(
                    text = "${idx + 1}. ${item.label}: ₹${item.amount.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("AI insights", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (ui.insights.isEmpty()) {
            Text("No insights yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            ui.insights.forEach { insight ->
                Text("• $insight", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RangeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun PieChartView(
    data: List<CategorySpend>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PieChart(ctx).apply {
                setUsePercentValues(false)
                description = Description().apply { text = "" }
                setEntryLabelColor(Color.DKGRAY)
                setEntryLabelTextSize(11f)
                legend.isEnabled = true
                setDrawHoleEnabled(true)
                holeRadius = 58f
                transparentCircleRadius = 62f
            }
        },
        update = { chart ->
            val entries = data
                .filter { it.amount > 0.0 }
                .map { PieEntry(it.amount.toFloat(), it.label) }

            val ds = PieDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueTextColor = Color.WHITE
                valueTextSize = 12f
                sliceSpace = 2f
            }

            chart.data = PieData(ds).apply {
                setValueFormatter(object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "₹${value.roundToInt()}"
                    }
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
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                description = Description().apply { text = "" }
                axisRight.isEnabled = false
                legend.isEnabled = true
                xAxis.granularity = 1f
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, p ->
                Entry(index.toFloat(), p.amount.toFloat())
            }

            val ds = LineDataSet(entries, "Spend").apply {
                color = ColorTemplate.MATERIAL_COLORS.first()
                valueTextColor = Color.DKGRAY
                lineWidth = 2f
                setDrawCircles(true)
                setDrawValues(false)
            }

            chart.data = LineData(ds)
            chart.invalidate()
        }
    )
}
