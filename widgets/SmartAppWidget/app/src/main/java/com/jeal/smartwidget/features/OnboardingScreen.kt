package com.jeal.smartwidget.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.core.UsageAccess

@Composable
fun OnboardingScreen(tick: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(UsageAccess.granted(ctx)) }
    var result by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(tick) { granted = UsageAccess.granted(ctx) }
    LaunchedEffect(granted, page) {
        if (granted && page == 2 && result == null) {
            loading = true
            result = Bootstrap.run(ctx)
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            when (page) {
                0 -> {
                    Title("Tus apps, al momento")
                    Body("Un widget que se ve como tus íconos normales en la pantalla de inicio, con fondo transparente.")
                    Body("Arriba pone las apps que más usas (y las de esta hora del día). Abajo, si quieres, todas las demás en orden alfabético: desliza hacia arriba dentro del widget para recorrerlas.")
                    Body("No es un launcher: tu pantalla de inicio sigue igual.")
                    Button(onClick = { page = 1 }, Modifier.fillMaxWidth()) { Text("Empezar") }
                }
                1 -> {
                    Title("Cómo aprende")
                    Body("• Cuenta qué apps abres, cuánto tiempo, a qué hora y qué día.")
                    Body("• Nota qué apps abres una tras otra (p. ej. Uber Driver → Maps → Spotify).")
                    Body("• Con eso calcula un puntaje: frecuencia 35 %, coincidencia de horario 30 %, uso reciente 20 % y patrón semanal 15 %.")
                    Body("• Tus favoritos siempre van primero. Lo que excluyas nunca se recomienda.")
                    Body("Todo se queda en tu teléfono: sin cuentas, sin internet, sin enviar nada.")
                    Button(onClick = { page = 2 }, Modifier.fillMaxWidth()) { Text("Siguiente") }
                }
                else -> {
                    Title("Permiso de uso")
                    Body("Para ordenar tus apps desde hoy, la app necesita el permiso especial «Acceso a datos de uso». Con él lee las estadísticas que Android ya guardó (normalmente unos días de detalle y hasta un año de totales) y no tienes que esperar semanas.")
                    Body("Se concede en Ajustes: busca «${ctx.applicationInfo.loadLabel(ctx.packageManager)}» y activa el permiso. Puedes quitarlo cuando quieras.")
                    if (granted) {
                        Text("✓ Permiso concedido", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (loading) Row { CircularProgressIndicator(); Text("  Leyendo tus estadísticas…") }
                        result?.let { Body(it) }
                        Button(onClick = onDone, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
                    } else {
                        Button(onClick = {
                            runCatching { ctx.startActivity(UsageAccess.settingsIntent(ctx)) }
                                .onFailure { ctx.startActivity(UsageAccess.fallbackIntent()) }
                        }, Modifier.fillMaxWidth()) { Text("Abrir Ajustes") }
                        TextButton(onClick = {
                            onDone()
                        }, Modifier.fillMaxWidth()) { Text("Seguir sin permiso (modo limitado)") }
                        Body("Sin permiso: el widget muestra tus apps en orden alfabético, puedes marcar favoritas y aprende solo de lo que abras desde el widget.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(t: String) = Text(t, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Body(t: String) = Text(t, style = MaterialTheme.typography.bodyLarge)
