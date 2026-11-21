package com.jeal.switchdriver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget de pantalla de inicio. Independiente de las burbujas. No abre ninguna app. */
class StopWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
    }

    companion object {
        const val ACT_ONE = "sd.widget.ONE"
        const val ACT_SELECTED = "sd.widget.SELECTED"
        const val EXTRA_PKG = "pkg"
        const val CONFIRM_MS = 5000L
        private val IDS = intArrayOf(R.id.w_app0, R.id.w_app1, R.id.w_app2, R.id.w_app3)

        fun refreshAll(ctx: Context) {
            val c = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, StopWidget::class.java))
            if (ids.isEmpty()) return
            Store.init(c)
            val rv = build(c)
            ids.forEach { mgr.updateAppWidget(it, rv) }
        }

        private fun pi(c: Context, code: Int, action: String, pkg: String?): PendingIntent {
            val i = Intent(c, WidgetActionReceiver::class.java).setAction(action)
            if (pkg != null) { i.putExtra(EXTRA_PKG, pkg); i.data = Uri.parse("sd://widget/$pkg") }
            return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun build(c: Context): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_stop)
            val st = ShizukuBridge.compute()
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val last = Store.widgetLast
            rv.setTextViewText(R.id.w_status, if (last.isNotEmpty()) last else "${if (st.ok) "● " else "○ "}${st.title} · $time")
            // Hasta 4 apps controlables sin protección (botón individual)
            val apps = Store.controlled.filter { Policy.blockReason(c, it) == null }
                .sortedBy { Catalog.labelOf(c, it).lowercase() }.take(IDS.size)
            IDS.forEachIndexed { i, id ->
                val p = apps.getOrNull(i)
                if (p == null) rv.setViewVisibility(id, android.view.View.GONE)
                else {
                    rv.setViewVisibility(id, android.view.View.VISIBLE)
                    Catalog.icon(c, p, 96)?.let { rv.setImageViewBitmap(id, it) }
                    rv.setContentDescription(id, "Detener ${Catalog.labelOf(c, p)}")
                    rv.setOnClickPendingIntent(id, pi(c, 100 + i, ACT_ONE, p))
                }
            }
            val n = Policy.plan(c, true).targets.size
            val armed = System.currentTimeMillis() - Store.widgetConfirmAt < CONFIRM_MS
            rv.setTextViewText(R.id.w_btn, when {
                n == 0 -> "Elige apps en Switch Driver"
                armed -> "Toca otra vez: detener $n"
                else -> "Detener seleccionadas ($n)"
            })
            rv.setOnClickPendingIntent(R.id.w_btn, pi(c, 200, ACT_SELECTED, null))
            return rv
        }
    }
}

/** Recibe los toques del widget. Solo no exportado: ninguna otra app puede dispararlo. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context); ShizukuBridge.init(context)
        val app = context.applicationContext
        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        fun toast(t: String) = main.post { Toast.makeText(app, t, Toast.LENGTH_SHORT).show() }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    StopWidget.ACT_ONE -> {
                        val pkg = intent.getStringExtra(StopWidget.EXTRA_PKG) ?: return@launch
                        val label = Catalog.labelOf(app, pkg)
                        val r = StopEngine.stop(app, pkg, "widget")
                        Store.widgetLast = StopEngine.short(label, r) + " · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        toast(Store.widgetLast)
                    }
                    StopWidget.ACT_SELECTED -> {
                        val plan = Policy.plan(app, true)
                        if (plan.targets.isEmpty()) { toast("No hay apps seleccionadas"); return@launch }
                        val now = System.currentTimeMillis()
                        if (now - Store.widgetConfirmAt >= StopWidget.CONFIRM_MS) {
                            // Primer toque: pide confirmación (acción masiva).
                            Store.widgetConfirmAt = now
                            Store.widgetLast = ""
                            StopWidget.refreshAll(app)
                            delay(StopWidget.CONFIRM_MS + 100)
                            return@launch
                        }
                        Store.widgetConfirmAt = 0
                        var ok = 0; var fail = 0
                        for (p in plan.targets) {
                            when (StopEngine.stop(app, p, "widget-lote")) {
                                StopResult.Stopped, StopResult.AlreadyStopped -> ok++
                                else -> fail++
                            }
                        }
                        Store.widgetLast = "$ok detenidas" + (if (fail > 0) " · $fail con error" else "") + " · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        toast(Store.widgetLast)
                    }
                }
            } finally {
                try { StopWidget.refreshAll(app) } catch (_: Throwable) {}
                pending.finish()
            }
        }
    }
}
