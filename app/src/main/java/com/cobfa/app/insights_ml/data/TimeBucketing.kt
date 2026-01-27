package com.cobfa.app.insights_ml.data

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

@RequiresApi(Build.VERSION_CODES.O)
object TimeBucketing {

    fun epochToLocalDate(epochMs: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()

    fun monthStartMillis(epochMs: Long, zoneId: ZoneId): Long {
        val d = epochToLocalDate(epochMs, zoneId)
        return d.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun isoWeekStartMillis(epochMs: Long, zoneId: ZoneId): Long {
        val d = epochToLocalDate(epochMs, zoneId)
        val wf = WeekFields.ISO // Monday-based ISO weeks [web:101]

        val dow = d.get(wf.dayOfWeek()) // 1..7
        val weekStart = d.minusDays((dow - 1).toLong()) // back to Monday

        return weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
