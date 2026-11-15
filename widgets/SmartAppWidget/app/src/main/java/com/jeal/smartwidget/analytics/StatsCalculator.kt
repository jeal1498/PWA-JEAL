package com.jeal.smartwidget.analytics

import android.content.Context
import com.jeal.smartwidget.data.AppDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Estadísticas para la pantalla de Estadísticas (todo local). */
object StatsCalculator {
    private const val DAY = 86_400_000L

    data class AppStat(
        val pkg: String, val label: String, val opens: Int, val timeMs: Long, val lastUse: Long, val frequent: Boolean,
    )
    data class Decline(val pkg: String, val label: String, val before: Int, val after: Int)
    data class Inactive(val pkg: String, val label: String, val days: Int?)
    data class Seq(val from: String, val to: String, val count: Int)

    data class Stats(
        val top: List<AppStat>,
        val hourly: IntArray,
        val daily: List<Pair<Long, Int>>,
        val weekdayTop: List<AppStat>,
        val weekendTop: List<AppStat>,
        val declining: List<Decline>,
        val inactive: List<Inactive>,
        val sequences: List<Seq>,
        val storedEvents: Int,
        val oldestTs: Long?,
        val peakHour: Int?,
    )

    suspend fun compute(context: Context, zone: ZoneId = ZoneId.systemDefault()): Stats {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val apps = db.apps().installed()
        val labels = apps.associate { it.pkg to it.label }
        val all = db.usage().since(0).filter { it.pkg in labels }
        val last30 = all.filter { it.ts >= now - 30 * DAY }

        fun statsOf(list: List<com.jeal.smartwidget.data.UsageEventEntity>, weeks: Double) =
            list.groupBy { it.pkg }.map { (pkg, l) ->
                AppStat(pkg, labels[pkg] ?: pkg, l.size, l.sumOf { it.durationMs }, l.maxOf { it.ts }, l.size / weeks >= 3)
            }.sortedByDescending { it.opens }

        val top = statsOf(last30, 30 / 7.0)

        val hourly = IntArray(24)
        last30.forEach { hourly[Instant.ofEpochMilli(it.ts).atZone(zone).hour]++ }

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daily = (13 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
            start to all.count { it.ts >= start && it.ts < start + DAY }
        }

        fun weekend(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).dayOfWeek.let {
            it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY
        }
        val weekdayTop = statsOf(last30.filter { !weekend(it.ts) }, 30 / 7.0).take(5)
        val weekendTop = statsOf(last30.filter { weekend(it.ts) }, 30 / 7.0).take(5)

        val recent = all.filter { it.ts >= now - 14 * DAY }.groupingBy { it.pkg }.eachCount()
        val prev = all.filter { it.ts in (now - 28 * DAY) until (now - 14 * DAY) }.groupingBy { it.pkg }.eachCount()
        val declining = prev.filter { (pkg, b) -> b >= 4 && (recent[pkg] ?: 0) <= b / 2 }
            .map { (pkg, b) -> Decline(pkg, labels[pkg] ?: pkg, b, recent[pkg] ?: 0) }
            .sortedByDescending { it.before - it.after }

        val lastEvent = all.groupBy { it.pkg }.mapValues { e -> e.value.maxOf { it.ts } }
        val inactive = apps.mapNotNull { a ->
            val last = maxOf(lastEvent[a.pkg] ?: 0L, a.statsLastUsed)
            when {
                last == 0L && a.firstInstallTime < now - 30 * DAY -> Inactive(a.pkg, a.label, null)
                last in 1 until now - 30 * DAY -> Inactive(a.pkg, a.label, ((now - last) / DAY).toInt())
                else -> null
            }
        }.sortedByDescending { it.days ?: Int.MAX_VALUE }

        val sequences = db.usage().topTransitions(10)
            .filter { it.fromPkg in labels && it.toPkg in labels }
            .map { Seq(labels.getValue(it.fromPkg), labels.getValue(it.toPkg), it.count) }

        val peak = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
        return Stats(top, hourly, daily, weekdayTop, weekendTop, declining, inactive, sequences,
            all.size, all.minOfOrNull { it.ts }, peak)
    }
}
