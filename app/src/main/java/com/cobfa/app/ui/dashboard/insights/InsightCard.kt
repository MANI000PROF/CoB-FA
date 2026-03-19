package com.cobfa.app.ui.dashboard.insights

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cobfa.app.domain.model.InsightSeverity
import com.cobfa.app.domain.model.PersonalizedInsight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InsightCard(
    insights: List<PersonalizedInsight>,
    modifier: Modifier = Modifier,
    onAction: (InsightAction) -> Unit
) {
    var sheetMode by rememberSaveable { mutableStateOf("none") } // none, all, detail
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val visibleInsights = remember(insights) {
        insights.filter { it.isActionableInsight() }
    }

    val hasInsights = visibleInsights.isNotEmpty()

    val selectedInsight = remember(visibleInsights, selectedKey) {
        visibleInsights.firstOrNull { it.key == selectedKey }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun openAllSheet() {
        if (!hasInsights) return
        selectedKey = null
        sheetMode = "all"
    }

    fun openDetailSheet(key: String) {
        val exists = visibleInsights.any { it.key == key }
        if (!exists) return
        selectedKey = key
        sheetMode = "detail"
    }

    fun closeSheet() {
        scope.launch {
            sheetState.hide()
            sheetMode = "none"
            selectedKey = null
        }
    }

    val showSheet = when (sheetMode) {
        "all" -> hasInsights
        "detail" -> selectedInsight != null
        else -> false
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { closeSheet() },
            sheetState = sheetState
        ) {
            when (sheetMode) {
                "all" -> {
                    AllInsightsSheet(
                        insights = visibleInsights,
                        onInsightClick = { ins -> openDetailSheet(ins.key) },
                        onClose = { closeSheet() }
                    )
                }

                "detail" -> {
                    selectedInsight?.let { insight ->
                        InsightDetailSheet(
                            insight = insight,
                            onAction = onAction,
                            onClose = { closeSheet() }
                        )
                    }
                }
            }
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderRow(
                hasInsights = hasInsights,
                onOpenAll = { openAllSheet() }
            )

            val primary = visibleInsights.firstOrNull()

            if (primary == null) {
                EmptyState()
            } else {
                PrimaryInsight(
                    ins = primary,
                    onDoThis = { openDetailSheet(primary.key) },
                    onNotUseful = { openDetailSheet(primary.key) }
                )

                val secondary = visibleInsights.drop(1).take(2)
                if (secondary.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        secondary.forEach { ins ->
                            SecondaryChip(
                                ins = ins,
                                onClick = { openDetailSheet(ins.key) }
                            )
                        }
                    }
                }

                val remainingCount = visibleInsights.size - 3
                if (remainingCount > 0) {
                    TextButton(onClick = { openAllSheet() }) {
                        Text("+ $remainingCount more insights")
                    }
                }
            }
        }
    }
}


private fun PersonalizedInsight.isActionableInsight(): Boolean {
    val normalizedTitle = title.trim().lowercase()
    val normalizedMessage = message.trim()
    val normalizedMessageLower = normalizedMessage.lowercase()

    if (normalizedTitle.isBlank() && normalizedMessage.isBlank()) return false

    val blockedWholeMessages = setOf(
        "",
        "no insights yet",
        "no insights yet.",
        "no personalized insights yet",
        "no personalized insights yet.",
        "no insights available",
        "no insights available.",
        "not enough data yet",
        "not enough data yet.",
        "add some expenses to get insights",
        "add some expenses to get insights.",
        "track a few expenses to get insights",
        "track a few expenses to get insights."
    )

    if (normalizedMessageLower in blockedWholeMessages) return false

    val (reasons, suggestionsRaw) = splitSuggestions(normalizedMessage)
    val cleanedReasons = reasons.trim()
    val cleanedSuggestions = suggestionsRaw.trim()

    val blockedReasonTexts = setOf(
        "",
        "no insights yet",
        "no insights yet.",
        "no personalized insights yet",
        "no personalized insights yet.",
        "no insights available",
        "no insights available.",
        "not enough data yet",
        "not enough data yet."
    )

    val hasUsefulReasons = cleanedReasons.isNotBlank() &&
            cleanedReasons.lowercase() !in blockedReasonTexts

    val suggestionItems = cleanedSuggestions
        .split("•")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val hasUsefulSuggestions = suggestionItems.isNotEmpty()

    return hasUsefulReasons || hasUsefulSuggestions
}

@Composable
private fun HeaderRow(
    hasInsights: Boolean,
    onOpenAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("AI insights", style = MaterialTheme.typography.titleMedium)
            Text(
                "Small actions that build discipline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasInsights) {
            IconButton(onClick = onOpenAll) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Open all insights"
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        "No insights yet",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    Text(
        "Confirm a few expenses and we’ll start coaching you.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PrimaryInsight(
    ins: PersonalizedInsight,
    onDoThis: () -> Unit,
    onNotUseful: () -> Unit
) {
    val colors = severityColors(ins.severity)
    var expanded by rememberSaveable(ins.key) { mutableStateOf(false) }

    val (reasons, suggestionsRaw) = splitSuggestions(ins.message)

    val hasReasons = reasons.isNotBlank()
    val hasSuggestions = suggestionsRaw.isNotBlank()
    val hasAnyInsightContent = hasReasons || hasSuggestions
    val hasActionableInsight = hasSuggestions

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(severityIcon(ins.severity), null, tint = colors.accent)
            Spacer(Modifier.width(8.dp))

            Text(
                text = ins.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = when (ins.severity) {
                    InsightSeverity.INFO -> "Tip"
                    InsightSeverity.WARN -> "Watch"
                    InsightSeverity.RISK -> "Risk"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent
            )
        }

        if (!hasAnyInsightContent) {
            Text(
                text = "No insights yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (hasSuggestions) {
            SuggestionHeroInline(
                suggestionsRaw = suggestionsRaw,
                onClick = onDoThis
            )
        }

        if (hasReasons) {
            Text(
                text = reasons,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) 50 else 3,
                overflow = TextOverflow.Ellipsis
            )

            val shouldShowExpandToggle = reasons.length > 140 || reasons.count { it == '\n' } > 1
            if (shouldShowExpandToggle) {
                Text(
                    text = if (expanded) "See less" else "See more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }

        if (hasActionableInsight) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = onDoThis,
                    label = { Text("Do this") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.container,
                        labelColor = colors.accent,
                        leadingIconContentColor = colors.accent
                    )
                )

                AssistChip(
                    onClick = onNotUseful,
                    label = { Text("Not useful") }
                )
            }
        }
    }
}

@Composable
private fun SuggestionHeroInline(
    suggestionsRaw: String,
    onClick: () -> Unit
) {
    val cleaned = suggestionsRaw.trim()
    if (cleaned.isBlank()) return

    val first = cleaned
        .split("•")
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: return

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Suggested swap",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = first,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Tap to see details",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SecondaryChip(ins: PersonalizedInsight, onClick: () -> Unit) {
    val colors = severityColors(ins.severity)
    AssistChip(
        onClick = onClick,
        label = { Text(ins.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(severityIcon(ins.severity), null, tint = colors.accent) }
    )
}

@Composable
private fun InsightDetailSheet(
    insight: PersonalizedInsight,
    onAction: (InsightAction) -> Unit,
    onClose: () -> Unit
) {
    val (reasons, suggestions) = splitSuggestions(insight.message)
    val resources = curatedResourcesFor(insight)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Text("Insight", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (reasons.isNotBlank()) {
                item {
                    Text(
                        reasons,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (suggestions.isNotBlank()) {
                item {
                    SuggestionsBlockFromText(suggestions)
                }
            }

            if (resources.isNotEmpty()) {
                item {
                    ResourcesBlock(
                        resources = resources,
                        onOpenUrl = { url -> onAction(InsightAction.OpenUrl(url)) }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    onAction(InsightAction.SetBudget(insight.key))
                    onClose()
                }
            ) {
                Text("Set budget")
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = onClose
            ) {
                Text("Close")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {
                    onAction(InsightAction.MarkDone(insight.key))
                    onClose()
                },
                label = { Text("Done") },
                leadingIcon = { Icon(Icons.Default.Done, null) }
            )

            AssistChip(
                onClick = {
                    onAction(InsightAction.NotUseful(insight.key))
                    onClose()
                },
                label = { Text("Not useful") }
            )
        }

        Spacer(Modifier.height(6.dp))
    }
}

private fun splitSuggestions(message: String): Pair<String, String> {
    val marker = "Suggestions:"
    val idx = message.indexOf(marker, ignoreCase = true)
    return if (idx < 0) {
        message.trim() to ""
    } else {
        val reasons = message.substring(0, idx).trim()
        val suggestions = message.substring(idx + marker.length).trim()
        reasons to suggestions
    }
}

@Composable
private fun SuggestionsBlockFromText(suggestionsText: String) {
    val items = suggestionsText
        .split("•")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (items.isEmpty()) return

    ElevatedCard(
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Suggested swap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            items.take(4).forEach { s ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", color = MaterialTheme.colorScheme.primary)
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

private fun curatedResourcesFor(ins: PersonalizedInsight): List<InsightResourceUi> {
    return when {
        ins.key.startsWith("ml_risk_", ignoreCase = true) -> listOf(
            InsightResourceUi(
                "Atomic Habits (habit loop ideas)",
                "https://jamesclear.com/atomic-habits",
                InsightResourceTypeUi.ARTICLE
            ),
            InsightResourceUi(
                "Tiny Habits (free method)",
                "https://tinyhabits.com/",
                InsightResourceTypeUi.TOOL
            )
        )

        ins.key == "top_category_share" -> listOf(
            InsightResourceUi(
                "Budgeting basics",
                "https://www.investopedia.com/terms/b/budget.asp",
                InsightResourceTypeUi.ARTICLE
            )
        )

        else -> emptyList()
    }
}

private data class InsightResourceUi(
    val title: String,
    val url: String,
    val type: InsightResourceTypeUi
)

private enum class InsightResourceTypeUi { VIDEO, ARTICLE, TOOL }

@Composable
private fun ResourcesBlock(
    resources: List<InsightResourceUi>,
    onOpenUrl: (String) -> Unit
) {
    Text(
        "Learn more",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )

    resources.take(6).forEachIndexed { index, r ->
        ListItem(
            headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(r.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Text(if (r.type == InsightResourceTypeUi.VIDEO) "▶" else "↗") },
            modifier = Modifier.clickable { onOpenUrl(r.url) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (index != minOf(resources.size, 6) - 1) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AllInsightsSheet(
    insights: List<PersonalizedInsight>,
    onInsightClick: (PersonalizedInsight) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "All insights",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Close") }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            items(
                count = insights.size,
                key = { idx -> insights[idx].key }
            ) { idx ->
                val ins = insights[idx]
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInsightClick(ins) }
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            ins.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            ins.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private data class SeverityUiColors(val accent: Color, val container: Color)

@Composable
private fun severityColors(severity: InsightSeverity): SeverityUiColors {
    val cs = MaterialTheme.colorScheme
    return when (severity) {
        InsightSeverity.INFO -> SeverityUiColors(cs.primary, cs.primary.copy(alpha = 0.12f))
        InsightSeverity.WARN -> SeverityUiColors(cs.tertiary, cs.tertiary.copy(alpha = 0.12f))
        InsightSeverity.RISK -> SeverityUiColors(cs.error, cs.error.copy(alpha = 0.12f))
    }
}

private fun severityIcon(severity: InsightSeverity) = when (severity) {
    InsightSeverity.INFO -> Icons.Default.Info
    InsightSeverity.WARN -> Icons.Default.WarningAmber
    InsightSeverity.RISK -> Icons.Default.Report
}
