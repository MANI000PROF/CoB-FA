package com.cobfa.app.insights_ml.baselines

object EwmaBaseline {
    fun ewma(values: List<Double>, alpha: Double): Double {
        if (values.isEmpty()) return 0.0
        var e = values.first()
        for (i in 1 until values.size) e = alpha * values[i] + (1 - alpha) * e
        return e
    }
}
