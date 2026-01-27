package com.cobfa.app.insights_ml.reco

import com.cobfa.app.domain.model.ExpenseCategory

object AlternativesCatalog {

    data class Suggestion(
        val title: String,
        val detail: String
    )

    fun suggestionsFor(
        category: ExpenseCategory,
        budgetUsagePct: Double?
    ): List<Suggestion> {
        val budgetHint = when {
            budgetUsagePct == null -> ""
            budgetUsagePct >= 1.0 -> " (you’re already over budget)"
            budgetUsagePct >= 0.8 -> " (near 80% budget)"
            else -> ""
        }

        return when (category) {
            ExpenseCategory.FOOD -> listOf(
                Suggestion("Set a mini-cap", "Limit FOOD to ₹150/order for the next 7 days$budgetHint."),
                Suggestion("Delay rule", "If you feel like ordering, wait 20 minutes and drink water first (if‑then plan)."),
                Suggestion("Swap option", "Choose a cheaper staple (idli/dosa/veg meals) instead of biryani/combos.")
            )

            ExpenseCategory.TRANSPORT -> listOf(
                Suggestion("Batch trips", "Combine errands into one trip to reduce repeated rides$budgetHint."),
                Suggestion("Cheaper mode", "Prefer bus/metro for short distances; save cabs for late-night only."),
                Suggestion("Set weekly cap", "Set TRANSPORT cap = last week avg × 0.8.")
            )

            ExpenseCategory.SHOPPING -> listOf(
                Suggestion("48h rule", "Add to wishlist, buy only after 48 hours (impulse breaker)$budgetHint."),
                Suggestion("One-in-one-out", "If you buy 1 item, sell/donate 1 item first."),
                Suggestion("Search alternatives", "Check local store/second-hand before online purchase.")
            )

            ExpenseCategory.ENTERTAINMENT -> listOf(
                Suggestion("Free alternative", "Replace 1 paid outing with a free activity this week$budgetHint."),
                Suggestion("Cap per week", "Limit ENTERTAINMENT to 1 paid activity/week."),
                Suggestion("If‑then", "If you open an app to spend, then close it and open Analytics instead.")
            )

            else -> listOf(
                Suggestion("Small cap", "Set a small cap for ${category.name} for next 7 days$budgetHint."),
                Suggestion("Delay", "Delay this spend by 20 minutes and re-check if it’s needed."),
                Suggestion("Track", "Log it immediately and review at night (reduces “auto-spend”).")
            )
        }
    }
}
