package com.cobfa.app.insights_ml.debug

import android.content.Context
import java.io.File

object MetricsCsvWriter {

    fun writeText(ctx: Context, filename: String, csv: String): File {
        val dir = File(ctx.filesDir, "ml_reports")
        if (!dir.exists()) dir.mkdirs()

        val f = File(dir, filename)
        f.writeText(csv)
        return f
    }
}
