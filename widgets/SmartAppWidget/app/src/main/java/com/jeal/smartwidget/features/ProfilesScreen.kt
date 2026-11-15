package com.jeal.smartwidget.features

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.data.ProfileEntity
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.features.ui.InfoCard
import com.jeal.smartwidget.features.ui.SectionTitle
import com.jeal.smartwidget.intelligence.RecommendationEngine
import com.jeal.smartwidget.intelligence.Recommender
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilesScreen(onBack: () -> Unit, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<ProfileEntity>>(emptyList()) }
    var usage by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var reload by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        profiles = repo.profiles()
        usage = profiles.associate { it.id to repo.db.widgets().countForProfile(it.id) }
    }

    Scaffold(topBar = { Bar("Perfiles", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Cada widget nuevo recibe su propio perfil (favoritos, exclusiones, apps desactivadas y pesos), así un cambio en uno no altera a los otros. Si quieres que dos widgets compartan perfil, elígelo en los ajustes del widget.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            profiles.forEach { p ->
                ProfileCard(p, usage[p.id] ?: 0,
                    onSave = { scope.launch { repo.db.profiles().upsert(it); reload++; RefreshWorker.refreshNow(ctx) } },
                    onApps = { open(Screen.Apps(p.id)) },
                    onDelete = { scope.launch {
                        if (!repo.deleteProfile(p.id)) Toast.makeText(ctx, "Está en uso por un widget", Toast.LENGTH_SHORT).show()
                        reload++
                    } },
                    onDuplicate = { scope.launch { repo.createProfile("${p.name} (copia)", p.id); reload++ } },
                )
            }
            SectionTitle("Nuevo perfil")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), placeholder = { Text("Nombre") }, singleLine = true)
                TextButton(onClick = {
                    if (newName.isNotBlank()) scope.launch { repo.createProfile(newName.trim()); newName = ""; reload++ }
                }) { Text("Crear") }
            }
            Text(" ", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun ProfileCard(
    p: ProfileEntity, widgets: Int, onSave: (ProfileEntity) -> Unit, onApps: () -> Unit,
    onDelete: () -> Unit, onDuplicate: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember(p) { mutableStateOf(p) }
    var tuning by remember { mutableStateOf<String?>(null) }
    InfoCard {
        OutlinedTextField(edit.name, { edit = edit.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
        Text("Usado por $widgets widget(s)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Weight("Frecuencia", edit.wFreq) { edit = edit.copy(wFreq = it) }
        Weight("Horario", edit.wHour) { edit = edit.copy(wHour = it) }
        Weight("Uso reciente", edit.wRecent) { edit = edit.copy(wRecent = it) }
        Weight("Día de la semana", edit.wWeek) { edit = edit.copy(wWeek = it) }
        Weight("Secuencias (bono)", edit.wSeq) { edit = edit.copy(wSeq = it) }
        Row {
            Button(onClick = { onSave(edit) }) { Text("Guardar") }
            TextButton(onClick = { edit = edit.copy(wFreq = .35f, wHour = .30f, wRecent = .20f, wWeek = .15f, wSeq = .10f) }) { Text("Pesos iniciales") }
        }
        OutlinedButton(onClick = {
            tuning = "Probando con tus datos…"
            scope.launch {
                tuning = withContext(Dispatchers.Default) { tune(ctx, edit) { best -> edit = best } }
            }
        }) { Text("Ajustar pesos con mis datos") }
        tuning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Row {
            TextButton(onClick = onApps) { Text("Apps del perfil") }
            TextButton(onClick = onDuplicate) { Text("Duplicar") }
            if (widgets == 0) TextButton(onClick = onDelete) { Text("Borrar") }
        }
    }
}

/** Mide el acierto (top 8) de varias combinaciones con los últimos 7 días y propone la mejor. */
private suspend fun tune(ctx: android.content.Context, p: ProfileEntity, apply: (ProfileEntity) -> Unit): String {
    val snap = Recommender.snapshot(ctx)
    val engine = RecommendationEngine()
    val now = System.currentTimeMillis()
    val recent = snap.events.count { it.ts >= now - 7 * 86_400_000L }
    if (recent < 20) return "Faltan datos: hay $recent aperturas en 7 días (se necesitan 20)."
    val current = Recommender.weights(p)
    val results = (listOf(current) + RecommendationEngine.CANDIDATE_WEIGHTS).distinct().map {
        it to engine.hitRate(snap.events, snap.priors, snap.transitions, it, 8, now)
    }
    val base = results.first().second
    val (best, rate) = results.maxBy { it.second }
    return if (rate > base + 0.01) {
        withContext(Dispatchers.Main) {
            apply(p.copy(wFreq = best.freq.toFloat(), wHour = best.hour.toFloat(), wRecent = best.recent.toFloat(),
                wWeek = best.week.toFloat(), wSeq = best.seq.toFloat()))
        }
        "Acierto actual ${(base * 100).toInt()} % → con estos pesos ${(rate * 100).toInt()} %. Toca Guardar para aplicarlos."
    } else "Tus pesos actuales ya son los mejores probados (acierto ${(base * 100).toInt()} % en el top 8)."
}

@Composable
private fun Weight(label: String, v: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${(v * 100).toInt()} %", Modifier.weight(0.45f), style = MaterialTheme.typography.bodySmall)
        Slider(value = v, onValueChange = { onChange((it * 20).toInt() / 20f) }, valueRange = 0f..0.6f, modifier = Modifier.weight(0.55f))
    }
}
