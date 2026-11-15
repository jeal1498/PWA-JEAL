package com.jeal.smartwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.jeal.smartwidget.R
import com.jeal.smartwidget.analytics.AppCatalog
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.WidgetEntity
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil

/** Colección desplazable del widget: una celda por app. */
class GridRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        GridFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

class GridFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var w: WidgetEntity? = null
    private var items: List<String> = emptyList()
    private var labels: Map<String, String> = emptyMap()
    private var info: Map<String, String> = emptyMap()
    private var iconPx = 0
    private val icons = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun onCreate() {}

    override fun onDataSetChanged() = runBlocking {
        val db = AppDatabase.get(context)
        val cfg = db.widgets().get(widgetId)
        w = cfg
        val installed = db.apps().installed().associate { it.pkg to it.label }
        labels = installed
        items = cfg?.currentList?.split(',')?.filter { it.isNotBlank() && it in installed } ?: emptyList()
        if (cfg != null && cfg.iconPx != iconPx) {
            icons.evictAll(); iconPx = cfg.iconPx
        }
        info = if (cfg?.visualMode == 1) {
            val since = System.currentTimeMillis() - 7 * 86_400_000L
            db.usage().countsSince(since).associate {
                it.pkg to "${it.c}× · ${com.jeal.smartwidget.core.Format.shortDuration(it.d)}"
            }
        } else emptyMap()
    }

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_item)
        val cfg = w ?: return rv
        val pkg = items.getOrNull(position) ?: return rv
        val size = if (cfg.iconPx > 0) cfg.iconPx else (48 * context.resources.displayMetrics.density).toInt()
        val icon = icons.get(pkg) ?: AppCatalog.icon(context, pkg, size)?.also { icons.put(pkg, it) }
        if (icon != null) rv.setImageViewBitmap(R.id.icon, icon)
        rv.setTextViewText(R.id.label, labels[pkg] ?: pkg)
        rv.setTextViewTextSize(R.id.label, TypedValue.COMPLEX_UNIT_SP, cfg.labelSp)

        val showInfo = cfg.visualMode == 1
        if (showInfo) {
            val star = if (position < cfg.favCount) "★ " else ""
            rv.setViewVisibility(R.id.info, View.VISIBLE)
            rv.setTextViewText(R.id.info, star + (info[pkg] ?: "—"))
        } else {
            rv.setViewVisibility(R.id.info, View.GONE)
        }

        // Alto exacto de celda = alto del widget / filas.
        if (cfg.cellHeightPx > 0) {
            if (Build.VERSION.SDK_INT >= 31) {
                rv.setViewLayoutHeight(R.id.cell, cfg.cellHeightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            } else {
                val content = size + textHeight(cfg.labelSp, 4f) + if (showInfo) textHeight(9f, 1f) else 0
                val free = (cfg.cellHeightPx - content).coerceAtLeast(0)
                rv.setViewPadding(R.id.cell, 0, free / 2, 0, free - free / 2)
            }
        }
        rv.setOnClickFillInIntent(R.id.cell, Intent().putExtra(LaunchActivity.EXTRA_PKG, pkg))
        return rv
    }

    private fun textHeight(sp: Float, padDp: Float): Int {
        val dm = context.resources.displayMetrics
        val paint = Paint().apply { textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm) }
        val fm = paint.fontMetricsInt
        return (fm.descent - fm.ascent) + ceil(padDp * dm.density).toInt()
    }

    /** Vista de carga transparente (la predeterminada dice "Cargando…" y rompe la ilusión). */
    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.widget_item)

    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = items.getOrNull(position)?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds() = true
    override fun onDestroy() = icons.evictAll()
}
