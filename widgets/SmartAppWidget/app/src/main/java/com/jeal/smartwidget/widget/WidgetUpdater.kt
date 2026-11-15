package com.jeal.smartwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.data.WidgetEntity
import com.jeal.smartwidget.intelligence.AppFlags
import com.jeal.smartwidget.intelligence.CatalogBuilder
import com.jeal.smartwidget.intelligence.CatalogInput
import com.jeal.smartwidget.intelligence.GridFit
import com.jeal.smartwidget.intelligence.Recommender
import com.jeal.smartwidget.intelligence.Section
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** Calcula la lista y el tamaño de celda de cada instancia y le manda sus RemoteViews. */
object WidgetUpdater {
    private val mutex = Mutex()

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, SmartWidgetProvider::class.java))

    suspend fun updateAll(context: Context) {
        val ids = ids(context)
        val db = AppDatabase.get(context)
        // Limpia instancias que el launcher ya no tiene.
        db.widgets().all().filter { it.appWidgetId !in ids }.forEach { db.widgets().delete(it.appWidgetId) }
        val snap = Recommender.snapshot(context)
        ids.forEach { update(context, it, snap) }
    }

    suspend fun update(context: Context, id: Int, snap: Recommender.Snapshot? = null, recompute: Boolean = true, force: Boolean = false) =
        mutex.withLock {
            val db = AppDatabase.get(context)
            val old = db.widgets().get(id)
            var w = old ?: WidgetEntity(id, Repository(context).newProfileForWidget())
            val mgr = AppWidgetManager.getInstance(context)
            w = withMetrics(context, mgr, w)
            if (recompute || w.currentList.isEmpty()) {
                w = withCatalog(context, w, snap ?: Recommender.snapshot(context))
            }
            val changed = force || old == null || old.currentList != w.currentList || old.cellHeightPx != w.cellHeightPx ||
                old.iconPx != w.iconPx || old.labelSp != w.labelSp || old.visualMode != w.visualMode ||
                old.cols != w.cols || old.favCount != w.favCount
            w = w.copy(version = w.version + 1)
            db.widgets().upsert(w)
            mgr.updateAppWidget(id, WidgetRenderer.render(context, w))
            // Solo recarga la colección si algo cambió (ahorra batería y evita parpadeos).
            if (changed) mgr.notifyAppWidgetViewDataChanged(id, com.jeal.smartwidget.R.id.grid)
        }

    fun sizeDp(context: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return w to h
    }

    private fun withMetrics(context: Context, mgr: AppWidgetManager, w: WidgetEntity): WidgetEntity {
        val (wd, hd) = sizeDp(context, mgr, w.appWidgetId)
        val info = w.visualMode == 1
        val fit = GridFit.compute(wd, hd, w.cols, w.rows, info)
        val density = context.resources.displayMetrics.density
        val cellH = if (hd > 0) (fit.cellHeightDp * density).toInt() else 0
        val labelSp = when {
            fit.iconDp < 36 -> 10f
            fit.iconDp < 44 -> 11f
            else -> 12f
        }
        return w.copy(
            widthDp = wd, heightDp = hd, cellHeightPx = cellH,
            iconPx = (fit.iconDp * density).roundToInt(), labelSp = labelSp,
        )
    }

    private suspend fun withCatalog(context: Context, w: WidgetEntity, snap: Recommender.Snapshot): WidgetEntity {
        val db = AppDatabase.get(context)
        val profile = db.profiles().get(w.profileId) ?: run {
            val id = Repository(context).newProfileForWidget()
            db.profiles().get(id)!!
        }
        val installed = db.apps().installed().associate { it.pkg to it.label }
        val flags = db.profiles().apps(profile.id).associate {
            it.pkg to AppFlags(it.favorite, it.favOrder, it.excluded, it.disabled)
        }
        val scores = Recommender.scores(snap, profile).mapValues { it.value.total }
        val result = CatalogBuilder.build(
            CatalogInput(
                installed = installed,
                flags = flags,
                scores = scores,
                suggestedCount = w.suggestedCount,
                showAll = w.showAll,
                previousSuggestions = w.lastSuggestions.split(',').filter { it.isNotBlank() },
            ),
        )
        return w.copy(
            profileId = profile.id,
            currentList = result.items.joinToString(",") { it.pkg },
            favCount = result.items.count { it.section == Section.FAVORITE },
            sugCount = result.items.count { it.section == Section.SUGGESTED },
            lastSuggestions = result.suggestions.joinToString(","),
        )
    }
}
