package com.jeal.smartwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.jeal.smartwidget.R
import com.jeal.smartwidget.data.WidgetEntity
import com.jeal.smartwidget.features.WidgetConfigActivity
import com.jeal.smartwidget.intelligence.GridFit

/**
 * RemoteViews del widget. Decisión técnica: GridView + RemoteViewsService (colección de widget)
 * porque es lo único que da scroll vertical real dentro de la pantalla de inicio en Android 8–15.
 * Jetpack Glance también traduce LazyVerticalGrid a esta misma colección, pero no deja controlar el
 * alto exacto de cada celda ni la cantidad de columnas por instancia sin cambiar de layout, y suma
 * una capa más: por eso se usa RemoteViews directo. Las columnas viven en layouts widget_c1..c10.
 */
object WidgetRenderer {
    const val MAX_COLS = 10
    const val MAX_ROWS = 12
    const val MAX_SUGGESTED = 60

    private val layouts = intArrayOf(
        R.layout.widget_c1, R.layout.widget_c2, R.layout.widget_c3, R.layout.widget_c4, R.layout.widget_c5,
        R.layout.widget_c6, R.layout.widget_c7, R.layout.widget_c8, R.layout.widget_c9, R.layout.widget_c10,
    )

    fun render(context: Context, w: WidgetEntity): RemoteViews {
        val id = w.appWidgetId
        val rv = RemoteViews(context.packageName, layouts[(w.cols - 1).coerceIn(0, MAX_COLS - 1)])

        val svc = Intent(context, GridRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("smartwidget://grid/$id")
        }
        @Suppress("DEPRECATION")
        rv.setRemoteAdapter(R.id.grid, svc)
        rv.setEmptyView(R.id.grid, R.id.empty)

        // Plantilla del toque: cada celda solo agrega el paquete (fill-in). Debe ser mutable.
        val template = PendingIntent.getActivity(
            context, id, Intent(context, LaunchActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        rv.setPendingIntentTemplate(R.id.grid, template)
        rv.setOnClickPendingIntent(R.id.empty, configIntent(context, id))

        rv.setViewVisibility(R.id.handle, if (w.showHandle && !w.editing) View.VISIBLE else View.GONE)
        rv.setOnClickPendingIntent(R.id.handle, edit(context, id, EditOp.TOGGLE))

        rv.setViewVisibility(R.id.edit_panel, if (w.editing) View.VISIBLE else View.GONE)
        if (w.editing) {
            rv.setTextViewText(R.id.cols_value, w.cols.toString())
            rv.setTextViewText(R.id.rows_value, w.rows.toString())
            rv.setTextViewText(R.id.sug_value, w.suggestedCount.toString())
            rv.setTextViewText(R.id.all_toggle, if (w.showAll) "Sí" else "No")
            rv.setTextViewText(R.id.mode_toggle, if (w.visualMode == 1) "Con info" else "Simple")
            rv.setOnClickPendingIntent(R.id.cols_minus, edit(context, id, EditOp.COLS_DEC))
            rv.setOnClickPendingIntent(R.id.cols_plus, edit(context, id, EditOp.COLS_INC))
            rv.setOnClickPendingIntent(R.id.rows_minus, edit(context, id, EditOp.ROWS_DEC))
            rv.setOnClickPendingIntent(R.id.rows_plus, edit(context, id, EditOp.ROWS_INC))
            rv.setOnClickPendingIntent(R.id.sug_minus, edit(context, id, EditOp.SUG_DEC))
            rv.setOnClickPendingIntent(R.id.sug_plus, edit(context, id, EditOp.SUG_INC))
            rv.setOnClickPendingIntent(R.id.all_toggle, edit(context, id, EditOp.ALL))
            rv.setOnClickPendingIntent(R.id.mode_toggle, edit(context, id, EditOp.MODE))
            rv.setOnClickPendingIntent(R.id.done, edit(context, id, EditOp.DONE))
            rv.setOnClickPendingIntent(R.id.more, configIntent(context, id))

            val fit = GridFit.compute(w.widthDp, w.heightDp, w.cols, w.rows, w.visualMode == 1)
            val s = fit.suggestion
            if (!fit.fits && s != null) {
                rv.setViewVisibility(R.id.fit_warning, View.VISIBLE)
                rv.setViewVisibility(R.id.fit_apply, View.VISIBLE)
                rv.setTextViewText(
                    R.id.fit_warning,
                    "${w.cols}×${w.rows} no cabe en este tamaño (${w.widthDp}×${w.heightDp} dp): " +
                        "caben hasta ${fit.maxCols}×${fit.maxRows} con íconos legibles. " +
                        "Así como está, las filas de más se ven con scroll. Agranda el widget o usa:",
                )
                rv.setTextViewText(R.id.fit_apply, "Usar ${s.first}×${s.second}")
                rv.setOnClickPendingIntent(R.id.fit_apply, edit(context, id, EditOp.FIT))
            } else {
                rv.setViewVisibility(R.id.fit_warning, View.GONE)
                rv.setViewVisibility(R.id.fit_apply, View.GONE)
            }
        }
        return rv
    }

    fun configIntent(context: Context, id: Int): PendingIntent {
        val i = Intent(context, WidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(WidgetConfigActivity.EXTRA_FROM_WIDGET, true)
            data = Uri.parse("smartwidget://config/$id")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(context, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun edit(context: Context, id: Int, op: EditOp): PendingIntent {
        val i = Intent(context, SmartWidgetProvider::class.java).apply {
            action = SmartWidgetProvider.ACTION_EDIT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(SmartWidgetProvider.EXTRA_OP, op.name)
            data = Uri.parse("smartwidget://edit/$id/${op.name}")
        }
        return PendingIntent.getBroadcast(context, id * 31 + op.ordinal, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

enum class EditOp { TOGGLE, DONE, COLS_INC, COLS_DEC, ROWS_INC, ROWS_DEC, SUG_INC, SUG_DEC, ALL, MODE, FIT }
