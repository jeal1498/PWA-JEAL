package com.jeal.smartwidget.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.analytics.StatsCalculator
import com.jeal.smartwidget.core.Format
import com.jeal.smartwidget.core.UsageAccess
import com.jeal.smartwidget.features.ui.AppIcon
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.features.ui.Bars
import com.jeal.smartwidget.features.ui.InfoCard
import com.jeal.smartwidget.features.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<StatsCalculator.Stats?>(null) }
    LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { StatsCalculator.compute(ctx) } }

    Scaffold(topBar = { Bar("Estadísticas", onBack) }) { pad ->
        val s = stats
        if (s == null) {
            Column(Modifier.padding(pad).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
            return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            InfoCard {
                Text("${s.storedEvents} aperturas guardadas" + (s.oldestTs?.let {
                    " desde el ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}"
                } ?: ""))
                if (!UsageAccess.granted(ctx)) Text("Modo limitado: solo se cuentan las apps abiertas desde el widget.",
                    style = MaterialTheme.typography.bodySmall)
                s.peakHour?.let { Text("Hora de mayor actividad: $it:00–${it + 1}:00", style = MaterialTheme.typography.bodySmall) }
            }
            if (s.storedEvents == 0) {
                Text("Aún no hay datos. Concede el acceso a datos de uso o usa el widget unos días.", Modifier.padding(vertical = 16.dp))
                return@Column
            }

            SectionTitle("Más usadas (30 días)")
            s.top.take(12).forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg, 32.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.opens} aperturas · ${Format.duration(a.timeMs)} · " +
                            if (a.frequent) "frecuente" else "ocasional", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionTitle("Actividad por hora")
            Bars(s.hourly.toList(), (0..23).map { if (it % 6 == 0) "$it" else "" })

            SectionTitle("Evolución (14 días)")
            val zone = ZoneId.systemDefault()
            Bars(s.daily.map { it.second }, s.daily.mapIndexed { i, d ->
                if (i % 3 == 0 || i == s.daily.lastIndex) Instant.ofEpochMilli(d.first).atZone(zone).dayOfMonth.toString() else ""
            })

            SectionTitle("Entre semana vs fin de semana")
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Lun–Vie", style = MaterialTheme.typography.labelLarge)
                    s.weekdayTop.forEach { Text("${it.label} (${it.opens})", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Sáb–Dom", style = MaterialTheme.typography.labelLarge)
                    s.weekendTop.forEach { Text("${it.label} (${it.opens})", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
            }

            SectionTitle("Secuencias frecuentes")
            if (s.sequences.isEmpty()) Text("Todavía no hay secuencias claras.", style = MaterialTheme.typography.bodySmall)
            s.sequences.forEach { Text("${it.from} → ${it.to}  ·  ${it.count} veces", style = MaterialTheme.typography.bodyMedium) }

            SectionTitle("Perdiendo relevancia")
            if (s.declining.isEmpty()) Text("Ninguna.", style = MaterialTheme.typography.bodySmall)
            s.declining.take(10).forEach {
                Text("${it.label}: ${it.before} → ${it.after} aperturas (últimas 2 semanas)", style = MaterialTheme.typography.bodyMedium)
            }

            SectionTitle("Inactivas (30+ días sin uso)")
            Text("Solo informativo: no se ocultan ni desinstalan.", style = MaterialTheme.typography.bodySmall)
            s.inactive.take(30).forEach {
                Text("${it.label} · " + (it.days?.let { d -> "hace $d días" } ?: "sin uso registrado"),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text(" ", Modifier.padding(bottom = 24.dp))
        }
    }
}
