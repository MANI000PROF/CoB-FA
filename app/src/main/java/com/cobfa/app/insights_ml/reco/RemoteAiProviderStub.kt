package com.cobfa.app.insights_ml.reco

import com.cobfa.app.domain.model.ExpenseCategory

object RemoteAiProviderStub {

    /**
     * DEBUG/RESEARCH ONLY.
     * Returns a prompt string you could send to an LLM later.
     * No raw SMS, no merchant text.
     */
    fun buildAnonymizedPrompt(
        category: ExpenseCategory,
        riskPct: Int,
        cnt7: Int,
        daysSinceLast: Int,
        budgetUsagePct: Double?
    ): String {
        val budgetPct = budgetUsagePct?.let { (it * 100).toInt() }
        return buildString {
            append("Generate 3 short, actionable money-saving alternatives.\n")
            append("User risk context (anonymized):\n")
            append("- Category: ${category.name}\n")
            append("- Predicted repeat risk next week: ${riskPct}%\n")
            append("- Frequency last 7 days: ${cnt7}\n")
            append("- Days since last spend: ${daysSinceLast}\n")
            if (budgetPct != null) append("- Budget usage month-to-date: ${budgetPct}%\n")
            append("Constraints: India context, simple steps, no judgmental tone.\n")
        }
    }
}
