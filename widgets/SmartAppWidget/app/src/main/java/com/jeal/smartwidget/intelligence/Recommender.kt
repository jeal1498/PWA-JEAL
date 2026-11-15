package com.jeal.smartwidget.intelligence

import android.content.Context
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.ProfileEntity

/** Une la base de datos con el motor puro. */
object Recommender {
    private const val DAY = 86_400_000L

    data class Snapshot(
        val events: List<OpenEvent>,
        val priors: Map<String, AppPrior>,
        val transitions: List<Transition>,
    )

    suspend fun snapshot(context: Context): Snapshot {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val events = db.usage().since(now - RecommendationEngine.WINDOW_MS).map { OpenEvent(it.pkg, it.ts, it.durationMs) }
        val priors = db.apps().all().filter { it.installed }
            .associate { it.pkg to AppPrior(it.pkg, it.statsTotalMs, it.statsLastUsed, it.firstInstallTime) }
        val transitions = db.usage().transitions().map { Transition(it.fromPkg, it.toPkg, it.count) }
        return Snapshot(events, priors, transitions)
    }

    fun weights(p: ProfileEntity) = Weights(
        p.wFreq.toDouble(), p.wHour.toDouble(), p.wRecent.toDouble(), p.wWeek.toDouble(), p.wSeq.toDouble(),
    )

    fun scores(snap: Snapshot, profile: ProfileEntity, now: Long = System.currentTimeMillis()): Map<String, Score> =
        RecommendationEngine().score(snap.events, snap.priors, snap.transitions, now, weights(profile),
            candidates = snap.priors.keys)
}
