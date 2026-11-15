package com.jeal.smartwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.jeal.smartwidget.SmartApp
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.intelligence.GridFit
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch

class SmartWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) { RefreshWorker.cancelPeriodic(context) }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        RefreshWorker.schedulePeriodic(context)
        async { ids.forEach { WidgetUpdater.update(context, it) } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        // Redimensionado: recalcula el tamaño de celda sin reordenar.
        async { WidgetUpdater.update(context, id, recompute = false) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        async { ids.forEach { AppDatabase.get(context).widgets().delete(it) } }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PINNED) {
            // Fijado desde la app: Android no manda onUpdate si hay actividad de configuración.
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            RefreshWorker.schedulePeriodic(context)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) async { WidgetUpdater.update(context, id, force = true) }
            return
        }
        if (intent.action == ACTION_EDIT) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val op = runCatching { EditOp.valueOf(intent.getStringExtra(EXTRA_OP) ?: "") }.getOrNull() ?: return
            async { applyEdit(context, id, op) }
            return
        }
        super.onReceive(context, intent)
    }

    private suspend fun applyEdit(context: Context, id: Int, op: EditOp) {
        val dao = AppDatabase.get(context).widgets()
        val w = dao.get(id) ?: return
        val maxC = WidgetRenderer.MAX_COLS
        val maxR = WidgetRenderer.MAX_ROWS
        val next = when (op) {
            EditOp.TOGGLE -> w.copy(editing = !w.editing)
            EditOp.DONE -> w.copy(editing = false)
            EditOp.COLS_INC -> w.copy(cols = (w.cols + 1).coerceAtMost(maxC))
            EditOp.COLS_DEC -> w.copy(cols = (w.cols - 1).coerceAtLeast(1))
            EditOp.ROWS_INC -> w.copy(rows = (w.rows + 1).coerceAtMost(maxR))
            EditOp.ROWS_DEC -> w.copy(rows = (w.rows - 1).coerceAtLeast(1))
            EditOp.SUG_INC -> w.copy(suggestedCount = (w.suggestedCount + 1).coerceAtMost(WidgetRenderer.MAX_SUGGESTED))
            EditOp.SUG_DEC -> w.copy(suggestedCount = (w.suggestedCount - 1).coerceAtLeast(0))
            EditOp.ALL -> w.copy(showAll = !w.showAll)
            EditOp.MODE -> w.copy(visualMode = if (w.visualMode == 1) 0 else 1)
            EditOp.FIT -> {
                val s = GridFit.compute(w.widthDp, w.heightDp, w.cols, w.rows, w.visualMode == 1).suggestion
                if (s == null) w else w.copy(cols = s.first, rows = s.second)
            }
        }
        dao.upsert(next)
        val listOps = op == EditOp.SUG_INC || op == EditOp.SUG_DEC || op == EditOp.ALL
        WidgetUpdater.update(context, id, recompute = listOps, force = op != EditOp.TOGGLE && op != EditOp.DONE)
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        SmartApp.scope.launch {
            try { block() } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_EDIT = "com.jeal.smartwidget.EDIT"
        const val ACTION_PINNED = "com.jeal.smartwidget.PINNED"
        const val EXTRA_OP = "op"
    }
}
