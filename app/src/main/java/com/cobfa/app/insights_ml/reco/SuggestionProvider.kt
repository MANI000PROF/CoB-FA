package com.cobfa.app.insights_ml.reco

import com.cobfa.app.domain.model.ExpenseCategory

interface SuggestionProvider {
    fun suggestions(category: ExpenseCategory, budgetUsagePct: Double?): List<AlternativesCatalog.Suggestion>
}
