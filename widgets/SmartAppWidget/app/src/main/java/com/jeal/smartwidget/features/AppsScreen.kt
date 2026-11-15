package com.jeal.smartwidget.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jeal.smartwidget.data.AppEntity
import com.jeal.smartwidget.data.ProfileAppEntity
import com.jeal.smartwidget.data.ProfileEntity
import com.jeal.smartwidget.data.Repository
import com.jeal.smartwidget.features.ui.AppIcon
import com.jeal.smartwidget.features.ui.Bar
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * Catálogo de un perfil: Todas / Favoritas / Excluidas / Desactivadas.
 * Desactivar = no sale en el widget (NO desinstala). Excluir = nunca se recomienda.
 */
@Composable
fun AppsScreen(initialProfile: Long?, initialTab: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<ProfileEntity>>(emptyList()) }
    var profileId by remember { mutableStateOf(initialProfile) }
    var apps by remember { mutableStateOf<List<AppEntity>?>(null) }
    var flags by remember { mutableStateOf<Map<String, ProfileAppEntity>>(emptyMap()) }
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload, profileId) {
        profiles = repo.profiles().ifEmpty { repo.newProfileForWidget(); repo.profiles() }
        if (profileId == null || profiles.none { it.id == profileId }) profileId = profiles.first().id
        apps = repo.db.apps().installed()
        flags = repo.flags(profileId!!)
    }

    fun change(pkg: String, f: (ProfileAppEntity) -> ProfileAppEntity) = scope.launch {
        repo.updateFlags(profileId!!, pkg, f)
        flags = repo.flags(profileId!!)
        RefreshWorker.refreshNow(ctx)
    }

    val q = norm(query)
    val shown = (apps ?: emptyList()).filter { a ->
        val f = flags[a.pkg]
        val inTab = when (tab) {
            1 -> f?.favorite == true
            2 -> f?.excluded == true
            3 -> f?.disabled == true
            else -> true
        }
        inTab && (q.isEmpty() || norm(a.label).contains(q))
    }.let { list -> if (tab == 1) list.sortedBy { flags[it.pkg]?.favOrder ?: 0 } else list }

    Scaffold(topBar = { Bar("Aplicaciones", onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (profiles.size > 1) {
                LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profiles) { p ->
                        FilterChip(selected = p.id == profileId, onClick = { profileId = p.id }, label = { Text(p.name) })
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                listOf("Todas", "Favoritas", "Excluidas", "Desactivadas").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Buscar app") }, singleLine = true)
            if (tab == 0) {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    TextButton(onClick = { scope.launch {
                        repo.setAllEnabled(profileId!!, apps.orEmpty().map { it.pkg }, true); reload++; RefreshWorker.refreshNow(ctx)
                    } }) { Text("Activar todas") }
                    TextButton(onClick = { scope.launch {
                        repo.setAllEnabled(profileId!!, apps.orEmpty().map { it.pkg }, false); reload++; RefreshWorker.refreshNow(ctx)
                    } }) { Text("Desactivar todas") }
                }
                Text("★ favorita (siempre primero) · ✕ no sugerir · interruptor: aparece en el widget",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (apps == null) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            } else if (shown.isEmpty()) {
                Text(when (tab) {
                    1 -> "Sin favoritas. Toca ★ en la pestaña Todas."
                    2 -> "Ninguna excluida."
                    3 -> "Ninguna desactivada."
                    else -> "Sin resultados."
                }, Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.pkg }) { a ->
                    val f = flags[a.pkg] ?: ProfileAppEntity(profileId ?: 0, a.pkg)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(a.pkg, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (f.disabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                            val tags = listOfNotNull(
                                "Favorita".takeIf { f.favorite }, "Excluida".takeIf { f.excluded },
                                "Desactivada".takeIf { f.disabled },
                            )
                            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        if (tab == 1) {
                            IconButton(onClick = { scope.launch { repo.moveFavorite(profileId!!, a.pkg, -1); reload++; RefreshWorker.refreshNow(ctx) } }) {
                                Icon(Icons.Default.KeyboardArrowUp, "Subir")
                            }
                            IconButton(onClick = { scope.launch { repo.moveFavorite(profileId!!, a.pkg, 1); reload++; RefreshWorker.refreshNow(ctx) } }) {
                                Icon(Icons.Default.KeyboardArrowDown, "Bajar")
                            }
                        }
                        IconButton(onClick = { change(a.pkg) { it.copy(favorite = !it.favorite) } }) {
                            Icon(Icons.Default.Star, if (f.favorite) "Quitar de favoritas" else "Hacer favorita",
                                tint = if (f.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)
                        }
                        IconButton(onClick = { change(a.pkg) { it.copy(excluded = !it.excluded) } }) {
                            Icon(Icons.Default.Close, if (f.excluded) "Volver a recomendar" else "Excluir de sugerencias",
                                tint = if (f.excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
                        }
                        Switch(checked = !f.disabled, onCheckedChange = { on -> change(a.pkg) { it.copy(disabled = !on) } })
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
