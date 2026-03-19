package com.cobfa.app.ui.dashboard.insights

sealed class InsightAction {
    data class SetBudget(val insightKey: String) : InsightAction()
    data class MarkDone(val insightKey: String) : InsightAction()
    data class NotUseful(val insightKey: String) : InsightAction()
    data class OpenUrl(val url: String) : InsightAction() // NEW
}

