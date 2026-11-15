package com.jeal.smartwidget.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.analytics.UsageIngestor
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.features.ui.InfoCard
import com.jeal.smartwidget.features.ui.SectionTitle
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()
    var events by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { events = repo.db.usage().count() }

    Scaffold(topBar = { Bar("Privacidad y almacenamiento", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Qué se guarda")
            Text("Solo en este teléfono, en una base de datos local:\n• Lista de apps con ícono (nombre y fecha de instalación).\n• Cada apertura: app, hora y duración.\n• Secuencias (qué app abres después de cuál).\n• Tus favoritos, exclusiones, apps desactivadas, perfiles y la configuración de cada widget.")
            SectionTitle("Qué NO se hace")
            Text("Sin cuentas, sin servidores, sin internet (la app ni siquiera pide el permiso de internet). No se envía tu historial a ningún lado. No se desinstala ni se oculta ninguna app del teléfono.")
            SectionTitle("Límite de crecimiento")
            Text("Se conservan ${UsageIngestor.RETENTION_DAYS} días de aperturas; lo anterior se borra solo. El motor usa los últimos 60 días.")
            InfoCard { Text("$events aperturas guardadas ahora.") }
            Button(onClick = { confirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Borrar mi historial de uso")
            }
            Text("Conserva favoritos, perfiles y widgets. Si el permiso sigue activo, en la siguiente actualización se vuelve a leer lo que Android tenga guardado.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Borrar historial?") },
        text = { Text("Se borran las aperturas, duraciones y secuencias guardadas por la app.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            scope.launch { repo.clearUsageData(); reload++; RefreshWorker.refreshNow(ctx) }
        }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
    )
}
