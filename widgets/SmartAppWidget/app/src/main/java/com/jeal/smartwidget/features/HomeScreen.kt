package com.jeal.smartwidget.features

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.core.UsageAccess
import com.jeal.smartwidget.data.AppDatabase
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.data.WidgetEntity
import com.jeal.smartwidget.features.ui.AppIcon
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.features.ui.InfoCard
import com.jeal.smartwidget.features.ui.SectionTitle
import com.jeal.smartwidget.intelligence.AppFlags
import com.jeal.smartwidget.intelligence.CatalogBuilder
import com.jeal.smartwidget.intelligence.CatalogInput
import com.jeal.smartwidget.intelligence.Recommender
import com.jeal.smartwidget.intelligence.Section
import com.jeal.smartwidget.widget.SmartWidgetProvider
import com.jeal.smartwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(tick: Int, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repository(ctx) }
    var granted by remember { mutableStateOf(UsageAccess.granted(ctx)) }
    var widgets by remember { mutableStateOf<List<Pair<WidgetEntity, String>>>(emptyList()) }
    var preview by remember { mutableStateOf<List<Pair<String, Section>>>(emptyList()) }
    var summary by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick, reload) {
        val nowGranted = UsageAccess.granted(ctx)
        if (nowGranted && !granted) { busy = true; summary = Bootstrap.run(ctx); busy = false }
        granted = nowGranted
        withContext(Dispatchers.IO) {
            val db = AppDatabase.get(ctx)
            if (db.apps().installed().isEmpty()) Bootstrap.run(ctx)
            val profiles = repo.profiles().associateBy { it.id }
            val live = WidgetUpdater.ids(ctx).toSet()
            widgets = db.widgets().all().filter { it.appWidgetId in live }
                .map { it to (profiles[it.profileId]?.name ?: "—") }

            // Vista previa del orden actual (perfil del primer widget o Principal).
            val profile = widgets.firstOrNull()?.first?.profileId?.let { profiles[it] } ?: profiles.values.firstOrNull()
            val installed = db.apps().installed().associate { it.pkg to it.label }
            val flags = profile?.let { p -> db.profiles().apps(p.id).associate { it.pkg to AppFlags(it.favorite, it.favOrder, it.excluded, it.disabled) } } ?: emptyMap()
            val scores = profile?.let { Recommender.scores(Recommender.snapshot(ctx), it).mapValues { e -> e.value.total } }
                ?: Recommender.scores(Recommender.snapshot(ctx), com.jeal.smartwidget.data.ProfileEntity(name = "")).mapValues { e -> e.value.total }
            val w = widgets.firstOrNull()?.first
            preview = CatalogBuilder.build(CatalogInput(installed, flags, scores, w?.suggestedCount ?: 8, true,
                w?.lastSuggestions?.split(',')?.filter { it.isNotBlank() } ?: emptyList()))
                .items.take(16).map { it.pkg to it.section }
            val n = db.usage().count()
            val oldest = db.usage().since(0).firstOrNull()?.ts
            summary = if (summary.isNotEmpty() && busy) summary else
                "${installed.size} apps · $n aperturas guardadas" +
                    (oldest?.let { " desde el ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}" } ?: "")
        }
    }

    Scaffold(topBar = { Bar("Apps al momento", null) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            InfoCard {
                if (granted) {
                    Text("✓ Acceso a datos de uso activo", color = MaterialTheme.colorScheme.primary)
                    Text("El widget aprende de tu historial y de lo que uses desde hoy.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Modo limitado: sin acceso a datos de uso", color = MaterialTheme.colorScheme.error)
                    Text("Funciona en orden alfabético, con favoritos, y aprende solo de lo que abras desde el widget.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        runCatching { ctx.startActivity(UsageAccess.settingsIntent(ctx)) }
                            .onFailure { ctx.startActivity(UsageAccess.fallbackIntent()) }
                    }, Modifier.padding(top = 8.dp)) { Text("Conceder permiso") }
                }
                Text(if (busy) "Leyendo estadísticas…" else summary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = {
                    scope.launch { busy = true; summary = Bootstrap.run(ctx); busy = false; reload++ }
                }) { Text("Actualizar ahora") }
            }

            SectionTitle("Tus widgets")
            if (widgets.isEmpty()) {
                Text("Aún no agregas el widget. Mantén presionado un espacio vacío de tu pantalla de inicio → Widgets → «Apps al momento», o usa el botón:",
                    style = MaterialTheme.typography.bodyMedium)
            }
            widgets.forEach { (w, profileName) ->
                Row(Modifier.fillMaxWidth().clickable {
                    ctx.startActivity(Intent(ctx, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, w.appWidgetId)
                        .putExtra(WidgetConfigActivity.EXTRA_FROM_WIDGET, true))
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Widget #${w.appWidgetId} · ${w.cols}×${w.rows}")
                        Text("Perfil: $profileName · ${w.suggestedCount} sugeridas · " +
                            (if (w.showAll) "todas las apps" else "solo sugeridas"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Ajustar ›", color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            OutlinedButton(onClick = {
                val mgr = AppWidgetManager.getInstance(ctx)
                if (mgr.isRequestPinAppWidgetSupported) {
                    val cb = android.app.PendingIntent.getBroadcast(ctx, 7,
                        Intent(ctx, SmartWidgetProvider::class.java).setAction(SmartWidgetProvider.ACTION_PINNED),
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
                    mgr.requestPinAppWidget(ComponentName(ctx, SmartWidgetProvider::class.java), null, cb)
                }
            }, Modifier.padding(top = 8.dp)) { Text("Agregar widget a la pantalla de inicio") }

            SectionTitle("Así se ordena ahora")
            Text("★ favorito · • sugerida · luego alfabético", style = MaterialTheme.typography.bodySmall)
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.forEach { (pkg, sec) ->
                    Column(Modifier.width(72.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(pkg, 44.dp)
                        Text((when (sec) { Section.FAVORITE -> "★ "; Section.SUGGESTED -> "• "; else -> "" }) +
                            (labelOf(ctx, pkg)), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            SectionTitle("Administrar")
            NavRow("Aplicaciones", "Favoritas, excluidas, activar o desactivar") { open(Screen.Apps()) }
            NavRow("Estadísticas", "Más usadas, horarios, secuencias, inactivas") { open(Screen.Stats) }
            NavRow("Perfiles", "Un perfil por widget o compartido; pesos del motor") { open(Screen.Profiles) }
            NavRow("Privacidad y almacenamiento", "Qué se guarda y borrar datos") { open(Screen.Privacy) }
            NavRow("Ver la bienvenida otra vez", "Cómo funciona y permisos") { open(Screen.Onboarding) }
        }
    }
}

private val labels = HashMap<String, String>()
private fun labelOf(ctx: android.content.Context, pkg: String) = labels.getOrPut(pkg) {
    runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)
}

@Composable
fun NavRow(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
