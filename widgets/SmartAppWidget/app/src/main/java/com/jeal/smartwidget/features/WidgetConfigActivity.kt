package com.jeal.smartwidget.features

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.data.ProfileEntity
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.data.WidgetEntity
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.features.ui.InfoCard
import com.jeal.smartwidget.features.ui.SectionTitle
import com.jeal.smartwidget.features.ui.SmartTheme
import com.jeal.smartwidget.features.ui.Stepper
import com.jeal.smartwidget.intelligence.GridFit
import com.jeal.smartwidget.widget.WidgetRenderer
import com.jeal.smartwidget.widget.WidgetUpdater
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch

/**
 * Ajustes de UNA instancia: cuadrícula, sugeridas, mostrar todas, vista, perfil.
 * Se abre al agregar el widget, desde «Más ajustes» del panel del widget, y (Android 12+)
 * con «Reconfigurar» al mantener presionado el widget.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        RefreshWorker.schedulePeriodic(this)
        setContent { SmartTheme { ConfigScreen(widgetId, isNew = !fromWidget, onDone = ::done) } }
    }

    private fun done() {
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    companion object { const val EXTRA_FROM_WIDGET = "from_widget" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigScreen(id: Int, isNew: Boolean, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()
    var w by remember { mutableStateOf<WidgetEntity?>(null) }
    var profiles by remember { mutableStateOf<List<ProfileEntity>>(emptyList()) }
    val (wd, hd) = remember { WidgetUpdater.sizeDp(ctx, AppWidgetManager.getInstance(ctx), id) }

    LaunchedEffect(Unit) {
        if (repo.db.apps().installed().isEmpty()) Bootstrap.run(ctx)
        val existing = repo.db.widgets().get(id)
        w = existing ?: WidgetEntity(id, repo.newProfileForWidget())
        if (existing == null) repo.db.widgets().upsert(w!!)
        profiles = repo.profiles()
    }
    val cfg = w ?: return

    Scaffold(topBar = { Bar(if (isNew) "Nuevo widget" else "Ajustes del widget", null) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Cuadrícula")
            Stepper("Columnas", cfg.cols, 1, WidgetRenderer.MAX_COLS, { w = cfg.copy(cols = it) })
            Stepper("Filas visibles", cfg.rows, 1, WidgetRenderer.MAX_ROWS, { w = cfg.copy(rows = it) },
                help = "Lo que no quepa se recorre deslizando dentro del widget")
            val fit = GridFit.compute(wd, hd, cfg.cols, cfg.rows, cfg.visualMode == 1)
            InfoCard {
                if (wd <= 0 || hd <= 0) {
                    Text("El tamaño real se conoce al colocar el widget. Si no cabe, el panel ⚙ del widget te lo dirá y te propondrá otra distribución.",
                        style = MaterialTheme.typography.bodySmall)
                } else if (fit.fits) {
                    Text("Tamaño del widget: $wd×$hd dp → celdas de ${fit.cellWidthDp.toInt()}×${fit.cellHeightDp.toInt()} dp, íconos de ${fit.iconDp} dp.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${cfg.cols}×${cfg.rows} no cabe en $wd×$hd dp: caben hasta ${fit.maxCols} columnas y ${fit.maxRows} filas con íconos legibles. Si lo dejas así, las filas de más se ven con scroll.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    fit.suggestion?.let { s ->
                        OutlinedButton(onClick = { w = cfg.copy(cols = s.first, rows = s.second) }) { Text("Usar ${s.first}×${s.second}") }
                    }
                    Text("O agranda el widget en tu pantalla de inicio.", style = MaterialTheme.typography.bodySmall)
                }
            }

            SectionTitle("Apps sugeridas")
            Stepper("Cuántas", cfg.suggestedCount, 0, WidgetRenderer.MAX_SUGGESTED, { w = cfg.copy(suggestedCount = it) },
                help = "Tus favoritas ocupan los primeros lugares")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar todas las aplicaciones")
                    Text("Después de las sugeridas, el resto en orden alfabético (desliza para verlas)",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(cfg.showAll, { w = cfg.copy(showAll = it) })
            }

            SectionTitle("Vista")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cfg.visualMode == 0, { w = cfg.copy(visualMode = 0) }, { Text("Íconos simples") })
                FilterChip(cfg.visualMode == 1, { w = cfg.copy(visualMode = 1) }, { Text("Con información") })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Botón de ajustes en el widget")
                    Text("Ícono discreto en la esquina. Si lo ocultas, entra desde esta app" +
                        (if (android.os.Build.VERSION.SDK_INT >= 31) " o manteniendo presionado el widget → Reconfigurar" else ""),
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(cfg.showHandle, { w = cfg.copy(showHandle = it) })
            }

            SectionTitle("Perfil de este widget")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { p ->
                    FilterChip(p.id == cfg.profileId, { w = cfg.copy(profileId = p.id) }, { Text(p.name) })
                }
                FilterChip(false, {
                    scope.launch {
                        val pid = repo.createProfile("Widget ${profiles.size + 1}")
                        profiles = repo.profiles(); w = cfg.copy(profileId = pid)
                    }
                }, { Text("+ Nuevo") })
            }
            Text("Favoritas, excluidas y apps desactivadas son del perfil. Dos widgets con el mismo perfil comparten eso.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    repo.db.widgets().upsert(cfg)
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_PROFILE, cfg.profileId))
                }
            }, Modifier.padding(top = 8.dp)) { Text("Favoritas, excluidas y activar/desactivar apps") }

            Button(onClick = {
                scope.launch {
                    repo.db.widgets().upsert(cfg.copy(editing = false))
                    WidgetUpdater.update(ctx, id, force = true)
                    onDone()
                }
            }, Modifier.fillMaxWidth().padding(vertical = 20.dp)) { Text("Guardar") }
        }
    }
}
