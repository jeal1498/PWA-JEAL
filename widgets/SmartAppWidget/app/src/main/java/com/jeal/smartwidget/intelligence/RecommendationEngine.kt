package com.jeal.smartwidget.intelligence

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/** Apertura de una app (entrada del motor; independiente de Room/Android). */
data class OpenEvent(val pkg: String, val ts: Long, val durationMs: Long)

/** Datos previos de UsageStatsManager (agregado histórico), útiles cuando hay pocos eventos. */
data class AppPrior(val pkg: String, val totalMs: Long, val lastUsed: Long, val firstInstall: Long)

data class Transition(val from: String, val to: String, val count: Int)

/**
 * Pesos. Propuesta inicial del encargo: frecuencia 35 %, horario 30 %, reciente 20 %, semanal 15 %.
 * La secuencia (A → B) se suma como bono (10 %) solo cuando hay una app abierta hace poco.
 */
data class Weights(
    val freq: Double = 0.35,
    val hour: Double = 0.30,
    val recent: Double = 0.20,
    val week: Double = 0.15,
    val seq: Double = 0.10,
)

data class Score(
    val pkg: String,
    val total: Double,
    val freq: Double,
    val hour: Double,
    val recent: Double,
    val week: Double,
    val seq: Double,
    val confidence: Double,
    val opens: Int,
)

/**
 * Motor local de recomendaciones: estadística con decaimiento, sin red ni IA generativa.
 *
 * - Frecuencia: aperturas con decaimiento exponencial (vida media 14 días) + 25 % tiempo de uso
 *   (raíz, para que el tiempo no domine: no se decide solo por tiempo de pantalla).
 * - Horario: densidad de aperturas alrededor de la hora actual (kernel gaussiano σ = 60 min, circular),
 *   suavizada con Laplace y expresada como "lift" sobre lo esperado si fuera al azar.
 * - Reciente: exp(−horas desde el último uso / 36).
 * - Semanal: lift de aperturas en el mismo tipo de día (laboral/fin de semana) y el mismo día de la semana.
 * - Secuencia: P(siguiente = app | última app abierta hace ≤ 30 min).
 * - Confianza: min(1, aperturas/5); las recién instaladas (< 3 días) con < 3 aperturas se penalizan.
 */
class RecommendationEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun score(
        events: List<OpenEvent>,
        priors: Map<String, AppPrior>,
        transitions: List<Transition>,
        now: Long,
        weights: Weights = Weights(),
        candidates: Set<String>? = null,
    ): Map<String, Score> {
        val horizon = now - WINDOW_MS
        val byPkg = events.asSequence()
            .filter { it.ts in horizon..now }
            .filter { candidates == null || it.pkg in candidates }
            .groupBy { it.pkg }

        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val nowMinute = nowZ.hour * 60 + nowZ.minute
        val nowDow = nowZ.dayOfWeek
        val nowWeekend = nowDow.isWeekend()

        // Secuencia: última apertura reciente
        val last = events.filter { it.ts <= now }.maxByOrNull { it.ts }
        val seqProb: Map<String, Double> = if (last != null && now - (last.ts + last.durationMs) <= SEQ_WINDOW_MS) {
            val from = transitions.filter { it.from == last.pkg && it.to != last.pkg }
            val total = from.sumOf { it.count }.toDouble()
            if (total >= 2) from.associate { it.to to it.count / total } else emptyMap()
        } else emptyMap()

        data class Raw(val opens: Int, val decOpens: Double, val decTime: Double, val hourLift: Double,
                       val weekLift: Double, val lastTs: Long)

        val raws = HashMap<String, Raw>()
        for ((pkg, list) in byPkg) {
            var decOpens = 0.0
            var decTime = 0.0
            var hourHits = 0.0
            var sameType = 0
            var sameDow = 0
            var lastTs = 0L
            for (e in list) {
                val ageDays = (now - e.ts) / DAY_MS.toDouble()
                val w = exp(-ageDays * LN2 / HALF_LIFE_DAYS)
                decOpens += w
                decTime += w * e.durationMs
                val z = Instant.ofEpochMilli(e.ts).atZone(zone)
                val m = z.hour * 60 + z.minute
                var d = abs(m - nowMinute).toDouble()
                if (d > 720) d = 1440 - d
                hourHits += exp(-(d / SIGMA_MIN) * (d / SIGMA_MIN) / 2)
                if (z.dayOfWeek.isWeekend() == nowWeekend) sameType++
                if (z.dayOfWeek == nowDow) sameDow++
                if (e.ts > lastTs) lastTs = e.ts
            }
            val n = list.size
            val smoothed = (hourHits + HOUR_PRIOR) / (n + 1.0)
            val hourLift = smoothed / HOUR_PRIOR
            val typeBase = if (nowWeekend) 2.0 / 7 else 5.0 / 7
            val typeLift = ((sameType + typeBase) / (n + 1.0)) / typeBase
            val dowLift = ((sameDow + 1.0 / 7) / (n + 1.0)) / (1.0 / 7)
            raws[pkg] = Raw(n, decOpens, decTime, hourLift, 0.6 * typeLift + 0.4 * dowLift, lastTs)
        }

        // Apps sin eventos propios pero con estadística histórica (primer arranque).
        val priorOnly = priors.values.filter { p ->
            p.pkg !in raws && (candidates == null || p.pkg in candidates) && p.totalMs > 0 && p.lastUsed > 0
        }

        val maxOpens = raws.values.maxOfOrNull { it.decOpens } ?: 0.0
        val maxTime = (raws.values.map { it.decTime } + priorOnly.map { it.totalMs.toDouble() / 30 })
            .maxOrNull() ?: 0.0
        val maxHour = raws.values.maxOfOrNull { it.hourLift } ?: 0.0
        val maxWeek = raws.values.maxOfOrNull { it.weekLift } ?: 0.0

        val out = HashMap<String, Score>()
        for ((pkg, r) in raws) {
            val freq = 0.75 * safeDiv(r.decOpens, maxOpens) + 0.25 * sqrt(safeDiv(r.decTime, maxTime))
            val hour = safeDiv(r.hourLift, maxHour)
            val week = safeDiv(r.weekLift, maxWeek)
            val lastUse = maxOf(r.lastTs, priors[pkg]?.lastUsed ?: 0L)
            val recent = recency(now, lastUse)
            var conf = min(1.0, r.opens / 5.0)
            val p = priors[pkg]
            if (p != null && r.opens < 3 && now - p.firstInstall < NEW_APP_MS) conf *= 0.3
            val seq = seqProb[pkg] ?: 0.0
            val total = conf * (weights.freq * freq + weights.hour * hour + weights.recent * recent +
                weights.week * week + weights.seq * seq)
            out[pkg] = Score(pkg, total, freq, hour, recent, week, seq, conf, r.opens)
        }
        for (p in priorOnly) {
            if (now - p.firstInstall < NEW_APP_MS) continue
            val freq = 0.25 * sqrt(safeDiv(p.totalMs.toDouble() / 30, maxTime))
            val recent = recency(now, p.lastUsed)
            val conf = 0.5
            val total = conf * (weights.freq * freq + weights.recent * recent)
            out[p.pkg] = Score(p.pkg, total, freq, 0.0, recent, 0.0, 0.0, conf, 0)
        }
        return out
    }

    /**
     * Mide qué tan bien predicen unos pesos: para cada apertura reciente, ¿estaba entre las k primeras
     * usando solo lo ocurrido antes? Sirve para ajustar los pesos con datos reales.
     */
    fun hitRate(
        events: List<OpenEvent>,
        priors: Map<String, AppPrior>,
        transitions: List<Transition>,
        weights: Weights,
        k: Int,
        now: Long,
        evalDays: Int = 7,
        maxSamples: Int = 150,
    ): Double {
        val sorted = events.sortedBy { it.ts }
        val evalFrom = now - evalDays * DAY_MS
        val samples = sorted.filter { it.ts >= evalFrom }.let { s ->
            if (s.size <= maxSamples) s else s.filterIndexed { i, _ -> i % (s.size / maxSamples + 1) == 0 }
        }
        if (samples.isEmpty()) return 0.0
        var hits = 0
        for (s in samples) {
            val before = sorted.filter { it.ts < s.ts }
            val scores = score(before, priors, transitions, s.ts - 1, weights)
            val top = scores.values.sortedByDescending { it.total }.take(k).map { it.pkg }
            if (s.pkg in top) hits++
        }
        return hits.toDouble() / samples.size
    }

    private fun recency(now: Long, last: Long): Double =
        if (last <= 0) 0.0 else exp(-((now - last).coerceAtLeast(0) / HOUR_MS.toDouble()) / 36.0)

    private fun safeDiv(a: Double, b: Double) = if (b <= 0) 0.0 else a / b

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        const val WINDOW_MS = 60 * DAY_MS
        const val NEW_APP_MS = 3 * DAY_MS
        const val SEQ_WINDOW_MS = 30 * 60_000L
        const val HALF_LIFE_DAYS = 14.0
        const val SIGMA_MIN = 60.0
        private const val LN2 = 0.6931471805599453
        /** Masa esperada del kernel si las aperturas fueran uniformes en el día: σ·√(2π)/1440. */
        val HOUR_PRIOR = SIGMA_MIN * sqrt(2 * Math.PI) / 1440.0

        /** Variantes que se prueban al "ajustar con mis datos". */
        val CANDIDATE_WEIGHTS = listOf(
            Weights(),
            Weights(freq = 0.45, hour = 0.25, recent = 0.20, week = 0.10),
            Weights(freq = 0.25, hour = 0.40, recent = 0.20, week = 0.15),
            Weights(freq = 0.30, hour = 0.25, recent = 0.35, week = 0.10),
            Weights(freq = 0.30, hour = 0.30, recent = 0.15, week = 0.25),
            Weights(freq = 0.35, hour = 0.30, recent = 0.20, week = 0.15, seq = 0.25),
        )
    }
}
