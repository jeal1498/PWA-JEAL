package com.jeal.switchdriver

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Estado compartido de la pantalla: catálogo de apps y permiso de overlay. */
object Env {
    val tick = mutableIntStateOf(0)
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    val overlay = mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SDTheme { Root() } }
    }

    override fun onResume() {
        super.onResume()
        ShizukuBridge.refresh()
        Env.overlay.value = BubbleService.canDraw(this)
        Env.tick.intValue++
        BubbleService.sync(this)
    }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val TABS = listOf("Inicio" to "⌂", "Apps" to "▦", "Protegidas" to "⛨", "Burbujas" to "◉", "Ajustes" to "⚙")

@Composable
fun Root() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tick by Env.tick
    LaunchedEffect(tick) {
        Env.apps.value = withContext(Dispatchers.IO) { Catalog.load(ctx) }
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (name, glyph) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(glyph) }, label = { Text(name, maxLines = 1) })
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> HomeScreen(m) { tab = it }
            1 -> AppsScreen(m)
            2 -> ProtectedScreen(m)
            3 -> BubblesScreen(m)
            else -> SettingsScreen(m)
        }
    }
}
