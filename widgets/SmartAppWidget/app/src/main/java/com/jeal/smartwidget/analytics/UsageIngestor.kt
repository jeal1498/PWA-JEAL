package com.jeal.smartwidget.analytics

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.jeal.smartwidget.core.UsageAccess
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.MetaEntity
import com.jeal.smartwidget.data.TransitionEntity
import com.jeal.smartwidget.data.UsageEventEntity

/**
 * Lee UsageStatsManager y guarda copia propia (Android no garantiza historial ilimitado).
 *
 * Primera vez: lee los eventos de los últimos 30 días (Android suele conservar solo ~7–10 días de
 * eventos detallados; se usa lo que haya) + el agregado histórico de hasta 1 año (tiempo total y
 * última vez usada). Después: solo lo nuevo desde la última lectura.
 */
object UsageIngestor {
    private const val KEY_LAST = "ingest_last_ts"
    private const val KEY_LAST_OPEN_PKG = "last_open_pkg"
    private const val KEY_LAST_OPEN_END = "last_open_end"
    private const val KEY_AGG = "agg_last_ts"
    const val KEY_BOOTSTRAP = "bootstrap_done"
    private const val DAY = 86_400_000L
    private const val SAME_SESSION_GAP = 15_000L
    const val SEQ_GAP = 10 * 60_000L
    const val RETENTION_DAYS = 180L

    data class Summary(val newOpens: Int, val apps: Int, val oldestEventTs: Long?, val bootstrap: Boolean)

    suspend fun ingest(context: Context): Summary? {
        if (!UsageAccess.granted(context)) return null
        val db = AppDatabase.get(context)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val bootstrap = db.meta().get(KEY_BOOTSTRAP) == null

        val launchable = db.apps().installed().map { it.pkg }.toSet()
        // Launchers se ignoran, pero no las apps con ícono que también declaran HOME (p. ej. Ajustes tiene FallbackHome).
        val ignored = (AppCatalog.homePackages(context) - launchable) + context.packageName + "com.android.systemui"

        // 1) Agregado histórico (una vez al día).
        val lastAgg = db.meta().get(KEY_AGG)?.toLongOrNull() ?: 0L
        if (now - lastAgg > DAY) {
            val agg = runCatching { usm.queryAndAggregateUsageStats(now - 365 * DAY, now) }.getOrDefault(emptyMap())
            for ((pkg, s) in agg) {
                if (pkg in launchable) db.apps().setStats(pkg, s.totalTimeInForeground, s.lastTimeUsed)
            }
            db.meta().put(MetaEntity(KEY_AGG, now.toString()))
        }

        // 2) Eventos → aperturas (sesiones en primer plano).
        val start = db.meta().get(KEY_LAST)?.toLongOrNull() ?: (now - 30 * DAY)
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        var cur: String? = null
        var curStart = 0L
        var lastPause: Long? = null
        var oldest: Long? = null
        var inserted = 0
        var lastOpenPkg = db.meta().get(KEY_LAST_OPEN_PKG)
        var lastOpenEnd = db.meta().get(KEY_LAST_OPEN_END)?.toLongOrNull() ?: 0L

        suspend fun emit(pkg: String, s: Long, end: Long) {
            val id = db.usage().insert(UsageEventEntity(pkg = pkg, ts = s, durationMs = (end - s).coerceIn(0, 6 * 3_600_000L), source = 0))
            if (id == -1L) return // ya registrada
            inserted++
            if (oldest == null) oldest = s
            val prev = lastOpenPkg
            if (prev != null && prev != pkg && s - lastOpenEnd in 0..SEQ_GAP) {
                val t = db.usage().transition(prev, pkg)
                db.usage().upsertTransition(TransitionEntity(prev, pkg, (t?.count ?: 0) + 1, s))
            }
            lastOpenPkg = pkg
            lastOpenEnd = end
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = e.eventType
            val pkg = e.packageName ?: continue
            when (type) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val lp = lastPause
                    if (pkg == cur && (lp == null || e.timeStamp - lp < SAME_SESSION_GAP)) {
                        lastPause = null
                        continue
                    }
                    cur?.let { emit(it, curStart, lp ?: e.timeStamp) }
                    if (pkg in launchable && pkg !in ignored) {
                        cur = pkg; curStart = e.timeStamp
                    } else {
                        cur = null
                    }
                    lastPause = null
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (pkg == cur) lastPause = e.timeStamp
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> {
                    cur?.let { emit(it, curStart, lastPause ?: e.timeStamp) }
                    cur = null; lastPause = null
                }
            }
        }
        // La sesión abierta se vuelve a leer la próxima vez (no se duplica: (pkg, ts) es único).
        val nextStart = if (cur != null) curStart - 1 else now
        db.meta().put(MetaEntity(KEY_LAST, nextStart.toString()))
        lastOpenPkg?.let { db.meta().put(MetaEntity(KEY_LAST_OPEN_PKG, it)) }
        db.meta().put(MetaEntity(KEY_LAST_OPEN_END, lastOpenEnd.toString()))
        if (bootstrap) db.meta().put(MetaEntity(KEY_BOOTSTRAP, now.toString()))

        // 3) Límite de crecimiento.
        db.usage().deleteBefore(now - RETENTION_DAYS * DAY)
        db.usage().deleteTransitionsBefore(now - RETENTION_DAYS * DAY)
        return Summary(inserted, launchable.size, oldest, bootstrap)
    }

    /** Modo sin permiso: aprende de los toques en el widget. */
    suspend fun recordWidgetLaunch(context: Context, pkg: String) {
        if (UsageAccess.granted(context)) return // UsageStats ya lo registra; evita duplicados
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val id = db.usage().insert(UsageEventEntity(pkg = pkg, ts = now, durationMs = 0, source = 1))
        if (id == -1L) return
        val prev = db.meta().get(KEY_LAST_OPEN_PKG)
        val prevEnd = db.meta().get(KEY_LAST_OPEN_END)?.toLongOrNull() ?: 0L
        if (prev != null && prev != pkg && now - prevEnd <= SEQ_GAP) {
            val t = db.usage().transition(prev, pkg)
            db.usage().upsertTransition(TransitionEntity(prev, pkg, (t?.count ?: 0) + 1, now))
        }
        db.meta().put(MetaEntity(KEY_LAST_OPEN_PKG, pkg))
        db.meta().put(MetaEntity(KEY_LAST_OPEN_END, now.toString()))
    }
}
