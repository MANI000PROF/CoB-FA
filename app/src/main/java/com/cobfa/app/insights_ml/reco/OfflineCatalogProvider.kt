package com.cobfa.app.insights_ml.reco

import com.cobfa.app.domain.model.ExpenseCategory

object OfflineCatalogProvider : SuggestionProvider {
    override fun suggestions(category: ExpenseCategory, budgetUsagePct: Double?) =
        AlternativesCatalog.suggestionsFor(category, budgetUsagePct)
}
