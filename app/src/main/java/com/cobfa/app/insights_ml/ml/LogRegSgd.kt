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

    fun fitWithHistory(
        xs: List<DoubleArray>,
        ys: List<Int>,
        epochs: Int = 50
    ): List<Double> {
        if (xs.isEmpty()) return emptyList()

        val losses = ArrayList<Double>(epochs)

        for (e in 0 until epochs) {
            var lossSum = 0.0

            for (i in xs.indices) {
                val x = xs[i]
                val y = ys[i].toDouble()
                val p = predictProb(x)

                // binary log loss
                val pp = p.coerceIn(1e-9, 1.0 - 1e-9)
                lossSum += -(y * kotlin.math.ln(pp) + (1.0 - y) * kotlin.math.ln(1.0 - pp))

                // SGD update
                val err = (p - y)
                for (j in 0 until dim) {
                    val grad = err * x[j] + l2 * w[j]
                    w[j] -= lr * grad
                }
            }

            losses.add(lossSum / xs.size.toDouble())
        }

        return losses
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
