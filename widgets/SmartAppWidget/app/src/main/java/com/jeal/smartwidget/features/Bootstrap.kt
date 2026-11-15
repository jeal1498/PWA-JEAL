package com.jeal.smartwidget.features

import android.content.Context
import com.jeal.smartwidget.analytics.AppCatalog
import com.jeal.smartwidget.analytics.UsageIngestor
import com.jeal.smartwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Primera lectura: apps instaladas + historial disponible + recalcula los widgets. */
object Bootstrap {
    suspend fun run(context: Context): String = withContext(Dispatchers.IO) {
        val apps = AppCatalog.sync(context)
        val s = UsageIngestor.ingest(context)
        WidgetUpdater.updateAll(context)
        when {
            s == null -> "Sin permiso: ${apps.size} apps en orden alfabético."
            s.newOpens == 0 -> "Se encontraron ${apps.size} apps. Android no tenía aperturas recientes guardadas; se usarán los totales históricos y empezará a aprender desde ahora."
            else -> "Listo: ${s.newOpens} aperturas leídas" +
                (s.oldestEventTs?.let { " desde el ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" } ?: "") +
                " en ${apps.size} apps. Ya puedes agregar el widget."
        }
    }
}
