package com.cobfa.app.domain.model

enum class InsightSeverity { INFO, WARN, RISK }

data class PersonalizedInsight(
    val key: String,
    val title: String,
    val message: String,
    val severity: InsightSeverity
)
