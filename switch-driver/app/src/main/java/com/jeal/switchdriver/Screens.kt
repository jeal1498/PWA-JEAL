package com.jeal.switchdriver

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun toast(c: Context, t: String) = Toast.makeText(c, t, Toast.LENGTH_SHORT).show()

private fun stateColor(s: ShizukuState) = if (s.ok) Green else if (s == ShizukuState.NO_PERMISSION) Amber else Red

// ---------------------------------------------------------------------------------------------
// Tarjeta de estado de Shizuku (se reutiliza en Inicio y Ajustes)
// ---------------------------------------------------------------------------------------------
@Composable
fun ShizukuCard() {
    val ctx = LocalContext.current
    val st by ShizukuBridge.state
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(stateColor(st)); Text(st.title, fontWeight = FontWeight.SemiBold)
        }
        Muted(st.help)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (st) {
                ShizukuState.NO_PERMISSION -> Button({ ShizukuBridge.requestPermission() }) { Text("Autorizar") }
                ShizukuState.NOT_INSTALLED -> Button({
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                }) { Text("Descargar Shizuku") }
                ShizukuState.NOT_RUNNING, ShizukuState.DENIED, ShizukuState.TOO_OLD -> Button({
                    if (!ShizukuBridge.launchShizuku(ctx)) toast(ctx, "No se pudo abrir Shizuku")
                }) { Text("Abrir Shizuku") }
                ShizukuState.READY -> {}
            }
            OutlinedButton({ ShizukuBridge.refresh(); Env.tick.intValue++ }) { Text("Reintentar") }
        }
    }
}

/** Banner cuando no se puede detener (Shizuku caído). */
@Composable
fun UnavailableBanner() {
    val st by ShizukuBridge.state
    if (!st.ok) CardBox { Text("Detener está desactivado", color = Red, fontWeight = FontWeight.SemiBold); Muted("${st.title}. ${st.help}") }
}

// ---------------------------------------------------------------------------------------------
// Detención masiva con resumen previo
// ---------------------------------------------------------------------------------------------
@Composable
fun MassStopDialog(selectedOnly: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val plan = remember { Policy.plan(ctx, selectedOnly) }
    val st by ShizukuBridge.state
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val results = remember { mutableStateMapOf<String, StopResult>() }
    val label = { p: String -> Catalog.labelOf(ctx, p) }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(if (selectedOnly) "Detener seleccionadas" else "Detener todas las controlables") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (plan.targets.isEmpty()) item { Text("No hay apps para detener.") }
                if (plan.targets.isNotEmpty()) {
                    item { Text(if (done) "Resultado" else "Se detendrán (${plan.targets.size}):", fontWeight = FontWeight.SemiBold) }
                    items(plan.targets) { p ->
                        val r = results[p]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon(p, 28)
                            Column {
                                Text(label(p))
                                if (r != null) Muted(StopEngine.describe(r))
                                else if (running) Muted("En espera…")
                            }
                        }
                    }
                }
                if (plan.skipped.isNotEmpty() && !done) {
                    item { Text("Se omitirán (${plan.skipped.size}):", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                    items(plan.skipped) { (p, why) -> Muted("• ${label(p)} — $why") }
                }
                if (!st.ok && !done) item { Text("Shizuku no está activo: ${st.title}.", color = Red) }
            }
        },
        confirmButton = {
            if (!done) Button(
                enabled = plan.targets.isNotEmpty() && st.ok && !running,
                onClick = {
                    running = true
                    scope.launch {
                        for (p in plan.targets) {
                            val r = StopEngine.stop(ctx, p, "lote")
                            results[p] = r
                            val bad = r !is StopResult.Stopped && r !is StopResult.AlreadyStopped
                            if (bad && !Store.batchContinue) break
                        }
                        running = false; done = true; Env.tick.intValue++
                    }
                },
            ) { Text(if (running) "Deteniendo…" else "Detener") }
            else Button(onDismiss) { Text("Cerrar") }
        },
        dismissButton = { if (!done) TextButton(onDismiss, enabled = !running) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------------------------------------
// 1. Inicio
// ---------------------------------------------------------------------------------------------
@Composable
fun HomeScreen(modifier: Modifier, go: (Int) -> Unit) {
    val ctx = LocalContext.current
    Store.version.intValue
    val overlay by Env.overlay
    var mass by remember { mutableStateOf<Boolean?>(null) }
    val controlled = Store.controlled.size
    val prot = Store.protectedApps.size
    val sel = Store.selected.size
    val bubblesOn = Store.bubbleEnabled && overlay

    Screen(modifier, "Switch Driver") {
        ShizukuCard()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardBox(Modifier.weight(1f).clickable { go(1) }) { Text("$controlled", fontSize = 28.sp, fontWeight = FontWeight.Bold); Muted("controlables") }
            CardBox(Modifier.weight(1f).clickable { go(2) }) { Text("$prot", fontSize = 28.sp, fontWeight = FontWeight.Bold); Muted("protegidas") }
        }
        UnavailableBanner()
        CardBox {
            Text("Acciones rápidas", fontWeight = FontWeight.SemiBold)
            Button({ mass = true }, Modifier.fillMaxWidth(), enabled = sel > 0) { Text("Detener seleccionadas ($sel)") }
            OutlinedButton({ mass = false }, Modifier.fillMaxWidth(), enabled = controlled > 0) { Text("Detener todas las controlables") }
            Muted("Siempre se muestra un resumen antes. Las protegidas nunca se detienen.")
            if (controlled == 0) Button({ go(1) }) { Text("Agregar apps") }
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(if (bubblesOn) Green else MaterialTheme.colorScheme.outline)
                Text(
                    when {
                        !Store.bubbleEnabled -> "Burbujas apagadas"
                        !overlay -> "Burbujas: falta permiso de overlay"
                        Store.bubbleHidden -> "Burbujas ocultas"
                        else -> "Burbujas activas (${Store.bubbles.size})"
                    }, fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton({ go(3) }) { Text("Configurar burbujas") }
        }
    }
    mass?.let { MassStopDialog(it) { mass = null } }
}

// ---------------------------------------------------------------------------------------------
// 2. Aplicaciones
// ---------------------------------------------------------------------------------------------
@Composable
fun AppsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Store.version.intValue
    val apps by Env.apps
    val st by ShizukuBridge.state
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var onlyMine by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var mass by remember { mutableStateOf<Boolean?>(null) }
    val busy = remember { mutableStateListOf<String>() }
    val controlled = Store.controlled
    val installedPk = apps.map { it.pkg }.toSet()
    // Apps de la lista que ya no aparecen (desinstaladas o sin ícono): se muestran para poder quitarlas.
    val ghosts = controlled.filter { it !in installedPk }.map { AppInfo(it, it, false) }
    val all = (apps + ghosts)
    val shown = all.filter { a ->
        (!onlyMine || a.pkg in controlled) &&
            (q.isBlank() || a.label.contains(q, true) || a.pkg.contains(q, true))
    }.sortedWith(compareByDescending<AppInfo> { it.pkg in controlled }.thenBy { it.label.lowercase() })

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)) {
        item { Text("Aplicaciones", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item { UnavailableBanner() }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar app o paquete") })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(onlyMine, { onlyMine = !onlyMine }, { Text("Solo mi lista (${controlled.size})") })
                if (Store.selected.isNotEmpty()) Button({ mass = true }, enabled = st.ok) { Text("Detener ${Store.selected.size} selec.") }
            }
        }
        if (shown.isEmpty()) item { Muted(if (all.isEmpty()) "Cargando apps…" else "Sin resultados.") }
        items(shown, key = { it.pkg }) { a ->
            val inList = a.pkg in controlled
            val prot = Store.isProtected(a.pkg)
            val block = Policy.blockReason(ctx, a.pkg)
            val open = expanded == a.pkg
            CardBox(Modifier.clickable { expanded = if (open) null else a.pkg }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(a.pkg)
                    Column(Modifier.weight(1f)) {
                        Text(a.label, maxLines = 1)
                        Muted(when {
                            prot -> "🛡 Protegida"
                            !Catalog.isInstalled(ctx, a.pkg) -> "No instalada"
                            block != null && inList -> "No se puede detener: $block"
                            inList && Catalog.looksStopped(ctx, a.pkg) -> "Detenida"
                            inList -> "En tu lista"
                            else -> if (a.system) "Del sistema" else "Instalada"
                        })
                    }
                    if (inList) Button(
                        { 
                            busy += a.pkg
                            scope.launch {
                                val r = StopEngine.stop(ctx, a.pkg, "botón")
                                toast(ctx, StopEngine.short(a.label, r))
                                busy -= a.pkg; Env.tick.intValue++
                            }
                        },
                        enabled = st.ok && block == null && a.pkg !in busy,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) { Text(if (a.pkg in busy) "…" else "Detener") }
                    else OutlinedButton({ Store.addControlled(a.pkg) }, contentPadding = PaddingValues(horizontal = 14.dp), enabled = !prot || true) { Text("Agregar") }
                }
                if (open) {
                    Muted(a.pkg)
                    SwitchRow("Protegida", "Nunca se detiene (ni en lotes ni con burbuja)", prot) { Store.setProtected(a.pkg, it) }
                    if (inList) {
                        SwitchRow("Seleccionada para lotes", null, a.pkg in Store.selected, enabled = !prot) { Store.setSelected(a.pkg, it) }
                        TextButton({ Store.removeControlled(a.pkg) }) { Text("Quitar de la lista de control", color = Red) }
                    }
                }
            }
        }
    }
    mass?.let { MassStopDialog(true) { mass = null } }
}

// ---------------------------------------------------------------------------------------------
// 3. Apps protegidas
// ---------------------------------------------------------------------------------------------
@Composable
fun ProtectedScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Store.version.intValue
    val apps by Env.apps
    var picking by remember { mutableStateOf(false) }
    val prot = Store.protectedApps.toList().sortedBy { Catalog.labelOf(ctx, it).lowercase() }

    Screen(modifier, "Apps protegidas") {
        CardBox {
            Muted("Estas apps se excluyen de TODAS las operaciones: detención individual, en lote, burbujas y widget. La protección tiene prioridad absoluta y se conserva al reiniciar.")
            Button({ picking = true }) { Text("Agregar app a la protección") }
        }
        if (prot.isEmpty()) Muted("No hay apps protegidas.")
        prot.forEach { p ->
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(p)
                    Column(Modifier.weight(1f)) {
                        Text(Catalog.labelOf(ctx, p), maxLines = 1)
                        Muted(if (Catalog.isInstalled(ctx, p)) p else "$p (no instalada)")
                    }
                    Switch(true, { if (!it) Store.setProtected(p, false) })
                }
            }
        }
    }
    if (picking) {
        var q by remember { mutableStateOf("") }
        val cand = apps.filter { it.pkg !in Store.protectedApps && (q.isBlank() || it.label.contains(q, true)) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Proteger app") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar") })
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(cand, key = { it.pkg }) { a ->
                            Row(Modifier.fillMaxWidth().clickable { Store.setProtected(a.pkg, true); picking = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppIcon(a.pkg, 32); Text(a.label, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ picking = false }) { Text("Cerrar") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Burbujas
// ---------------------------------------------------------------------------------------------
@Composable
fun BubblesScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Store.version.intValue
    val overlay by Env.overlay
    val controlled = Store.controlled.filter { Catalog.isInstalled(ctx, it) }.sortedBy { Catalog.labelOf(ctx, it).lowercase() }

    Screen(modifier, "Burbujas") {
        if (!overlay) CardBox {
            Text("Falta el permiso «Mostrar sobre otras apps»", color = Amber, fontWeight = FontWeight.SemiBold)
            Muted("Sin él, las burbujas no pueden mostrarse. No se finge que estén activas.")
            Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Abrir permiso") }
        }
        CardBox {
            SwitchRow("Burbujas activas", if (Store.bubbles.isEmpty()) "Elige abajo qué apps tendrán burbuja" else "${Store.bubbles.size} burbuja(s) elegidas", Store.bubbleEnabled && overlay, enabled = overlay) { on ->
                Store.bubbleEnabled = on
                if (on) (ctx as? MainActivity)?.askNotifications()
                BubbleService.sync(ctx)
            }
            Muted("Tocar una burbuja detiene SOLO esa app. No abre nada.")
        }
        CardBox {
            Text("Apps con burbuja", fontWeight = FontWeight.SemiBold)
            if (controlled.isEmpty()) Muted("Primero agrega apps en la pestaña Apps.")
            controlled.forEach { p ->
                val prot = Store.isProtected(p)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(p, 32)
                    Column(Modifier.weight(1f)) { Text(Catalog.labelOf(ctx, p), maxLines = 1); if (prot) Muted("Protegida: no puede tener burbuja") }
                    Switch(p in Store.bubbles, { Store.setBubble(p, it) }, enabled = !prot)
                }
            }
        }
        CardBox {
            Text("Apariencia", fontWeight = FontWeight.SemiBold)
            Muted("Tamaño: ${Store.bubbleSizeDp} dp")
            var size by remember { mutableFloatStateOf(Store.bubbleSizeDp.toFloat()) }
            Slider(size, { size = it }, valueRange = 32f..80f, onValueChangeFinished = { Store.bubbleSizeDp = size.toInt() })
            Muted("Transparencia: ${100 - Store.bubbleAlphaPct}%")
            var al by remember { mutableFloatStateOf(Store.bubbleAlphaPct.toFloat()) }
            Slider(al, { al = it }, valueRange = 30f..100f, onValueChangeFinished = { Store.bubbleAlphaPct = al.toInt() })
            Muted("Borde preferido (burbujas nuevas)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(Store.bubbleEdge == "L", { Store.bubbleEdge = "L" }, { Text("Izquierda") })
                FilterChip(Store.bubbleEdge == "R", { Store.bubbleEdge = "R" }, { Text("Derecha") })
            }
            OutlinedButton({ Store.resetBubblePositions(); BubbleService.command(ctx, BubbleService.ACT_SYNC) }) { Text("Restablecer posiciones") }
            Muted("Arrastra una burbuja para moverla; se pega al borde y recuerda su lugar.")
        }
        CardBox {
            SwitchRow("Minimizar al borde", "Se esconden a medias tras 4 s sin tocarlas; el primer toque solo las despliega", Store.bubbleAutoMin) { Store.bubbleAutoMin = it }
            SwitchRow("Mostrar tras reiniciar el teléfono", null, Store.bubbleBoot) { Store.bubbleBoot = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!Store.bubbleHidden) OutlinedButton({ BubbleService.command(ctx, BubbleService.ACT_HIDE) }, enabled = Store.bubbleEnabled) { Text("Ocultar") }
                else Button({ BubbleService.command(ctx, BubbleService.ACT_SHOW) }) { Text("Restaurar") }
                OutlinedButton({ BubbleService.command(ctx, BubbleService.ACT_MINIMIZE) }, enabled = Store.bubbleEnabled && !Store.bubbleHidden) { Text("Minimizar ahora") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Ajustes
// ---------------------------------------------------------------------------------------------
@Composable
fun SettingsScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Store.version.intValue
    val overlay by Env.overlay
    val st by ShizukuBridge.state
    val bound by ShizukuBridge.serviceBound
    var diagTick by remember { mutableIntStateOf(0) }

    Screen(modifier, "Ajustes") {
        Text("Shizuku", fontWeight = FontWeight.SemiBold)
        ShizukuCard()
        CardBox {
            Text("Permiso de overlay", fontWeight = FontWeight.SemiBold)
            Muted(if (overlay) "Concedido" else "No concedido")
            if (!overlay) Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Abrir permiso") }
        }
        CardBox {
            Text("Widget", fontWeight = FontWeight.SemiBold)
            Muted("Muestra el estado de Shizuku, botones por app y «Detener seleccionadas» (pide un segundo toque para confirmar). Se actualiza al cambiar Shizuku y cada ~30 min; la hora indica la última vez.")
            Button({
                val m = AppWidgetManager.getInstance(ctx)
                if (Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, StopWidget::class.java), null, null)
                else toast(ctx, "Tu launcher no permite agregarlo desde aquí: mantén pulsada la pantalla de inicio → Widgets → Switch Driver")
            }) { Text("Agregar widget") }
        }
        CardBox {
            SwitchRow("Seguir si una app falla", "En lotes: continuar con las demás. Apagado: se detiene el lote en el primer error.", Store.batchContinue) { Store.batchContinue = it }
        }
        CardBox {
            Text("Información", fontWeight = FontWeight.SemiBold)
            Muted("Switch Driver ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Muted("Solo detiene (Force Stop) las apps que eliges. No abre apps, no cierra sesiones, no borra datos ni caché.")
        }
        CardBox {
            Text("Diagnóstico", fontWeight = FontWeight.SemiBold)
            val txt = remember(diagTick, st, bound, overlay) {
                buildString {
                    appendLine("Shizuku: ${st.title} (${ShizukuBridge.versionInfo()})")
                    appendLine("Servicio privilegiado: ${if (bound) "conectado" else "sin conectar"}")
                    appendLine("Overlay: ${if (overlay) "sí" else "no"} · Burbujas: ${if (Store.bubbleEnabled) "activadas" else "apagadas"}")
                    appendLine("Controlables: ${Store.controlled.size} · Protegidas: ${Store.protectedApps.size} · Seleccionadas: ${Store.selected.size}")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("— Eventos —")
                    append(DiagLog.dump())
                }
            }
            Text(txt, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ diagTick++ }) { Text("Actualizar") }
                OutlinedButton({
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("diagnóstico", txt))
                    toast(ctx, "Copiado")
                }) { Text("Copiar") }
                OutlinedButton({ DiagLog.clear(); diagTick++ }) { Text("Limpiar") }
            }
        }
    }
}
