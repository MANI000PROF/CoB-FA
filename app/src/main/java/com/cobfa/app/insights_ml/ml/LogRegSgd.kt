package com.cobfa.app.insights_ml.ml

import kotlin.math.exp

class LogRegSgd(
    private val dim: Int,
    private val lr: Double = 0.05,
    private val l2: Double = 0.0005
) {
    private val w = DoubleArray(dim) { 0.0 }

    fun weights(): DoubleArray = w.copyOf()

    fun predictProb(x: DoubleArray): Double {
        var z = 0.0
        for (i in 0 until dim) z += w[i] * x[i]
        return sigmoid(z)
    }

    fun fit(
        xs: List<DoubleArray>,
        ys: List<Int>,
        epochs: Int = 50
    ) {
        if (xs.isEmpty()) return
        for (e in 0 until epochs) {
            for (i in xs.indices) {
                val x = xs[i]
                val y = ys[i].toDouble()
                val p = predictProb(x)
                val posWeight = 2.0 // simple constant; later: derive from class ratio
                val wgt = if (y == 1.0) posWeight else 1.0
                val err = (p - y) * wgt

                for (j in 0 until dim) {
                    val grad = err * x[j] + l2 * w[j]
                    w[j] -= lr * grad
                }
            }
        }
    }

    private fun sigmoid(z: Double): Double {
        // stable-ish sigmoid
        return if (z >= 0) {
            1.0 / (1.0 + exp(-z))
        } else {
            val ez = exp(z)
            ez / (1.0 + ez)
        }
    }
}
