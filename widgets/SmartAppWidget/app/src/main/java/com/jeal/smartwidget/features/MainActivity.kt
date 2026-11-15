package com.jeal.smartwidget.features

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.jeal.smartwidget.features.ui.SmartTheme
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Apps(val profileId: Long? = null, val tab: Int = 0) : Screen
    data object Stats : Screen
    data object Profiles : Screen
    data object Privacy : Screen
}

class MainActivity : ComponentActivity() {
    /** Sube cada vez que la app vuelve al frente (p. ej. al regresar de Ajustes con el permiso). */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshWorker.schedulePeriodic(this)
        val prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        val startProfile = intent.getLongExtra(EXTRA_PROFILE, -1L).takeIf { it >= 0 }
        setContent {
            SmartTheme {
                val stack = remember {
                    mutableStateListOf<Screen>().apply {
                        add(if (prefs.getBoolean("onboarded", false)) Screen.Home else Screen.Onboarding)
                        if (startProfile != null) add(Screen.Apps(startProfile))
                    }
                }
                fun push(s: Screen) { stack.add(s) }
                fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
                BackHandler(stack.size > 1) { pop() }
                val tick = resumeTick.intValue
                when (val s = stack.last()) {
                    Screen.Onboarding -> OnboardingScreen(tick) {
                        prefs.edit().putBoolean("onboarded", true).apply()
                        stack.clear(); stack.add(Screen.Home)
                    }
                    Screen.Home -> HomeScreen(tick, ::push)
                    is Screen.Apps -> AppsScreen(s.profileId, s.tab, onBack = ::pop)
                    Screen.Stats -> StatsScreen(onBack = ::pop)
                    Screen.Profiles -> ProfilesScreen(onBack = ::pop, open = ::push)
                    Screen.Privacy -> PrivacyScreen(onBack = ::pop)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        // Dibuja cualquier instancia que el launcher haya creado sin avisar (p. ej. al fijarla).
        val ctx = applicationContext
        com.jeal.smartwidget.SmartApp.scope.launch { com.jeal.smartwidget.widget.WidgetUpdater.updateAll(ctx) }
    }

    companion object { const val EXTRA_PROFILE = "profile" }
}
