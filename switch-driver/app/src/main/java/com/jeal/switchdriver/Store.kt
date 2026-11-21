package com.jeal.switchdriver

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

/** Persistencia local (SharedPreferences): sobrevive a cerrar la app, reiniciar el teléfono o Shizuku. */
object Store {
    private lateinit var sp: SharedPreferences
    private lateinit var pos: SharedPreferences

    /** Sube con cada cambio para que Compose se refresque. */
    val version = mutableIntStateOf(0)

    fun init(c: Context) {
        if (::sp.isInitialized) return
        val app = c.applicationContext
        sp = app.getSharedPreferences("sd", Context.MODE_PRIVATE)
        pos = app.getSharedPreferences("sd_pos", Context.MODE_PRIVATE)
    }

    fun prefs(): SharedPreferences = sp

    private fun bump() { version.intValue++ }
    private fun getSet(k: String): Set<String> = sp.getStringSet(k, emptySet())!!.toSet()
    private fun putSet(k: String, v: Set<String>) { sp.edit().putStringSet(k, v).apply(); bump() }

    val controlled: Set<String> get() = getSet("controlled")
    val protectedApps: Set<String> get() = getSet("protected")
    val selected: Set<String> get() = getSet("selected")
    val bubbles: Set<String> get() = getSet("bubbles")

    fun isControlled(p: String) = p in controlled
    fun isProtected(p: String) = p in protectedApps

    fun addControlled(p: String) { if (Validation.isPackageName(p)) putSet("controlled", controlled + p) }
    fun removeControlled(p: String) {
        putSet("controlled", controlled - p)
        putSet("selected", selected - p)
        putSet("bubbles", bubbles - p)
        pos.edit().remove(p).apply()
    }

    fun setProtected(p: String, on: Boolean) {
        if (!Validation.isPackageName(p)) return
        putSet("protected", if (on) protectedApps + p else protectedApps - p)
        if (on) { // la protección tiene prioridad: ni seleccionada ni con burbuja
            putSet("selected", selected - p)
            putSet("bubbles", bubbles - p)
        }
    }

    fun setSelected(p: String, on: Boolean) = putSet("selected", if (on) selected + p else selected - p)
    fun setBubble(p: String, on: Boolean) = putSet("bubbles", if (on) bubbles + p else bubbles - p)

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }

    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun setBool(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); bump() }
    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun setInt(k: String, v: Int) { sp.edit().putInt(k, v).apply(); bump() }

    var bubbleEnabled: Boolean get() = bool("bubbleEnabled", false); set(v) = setBool("bubbleEnabled", v)
    var bubbleHidden: Boolean get() = bool("bubbleHidden", false); set(v) = setBool("bubbleHidden", v)
    var bubbleAutoMin: Boolean get() = bool("bubbleAutoMin", true); set(v) = setBool("bubbleAutoMin", v)
    var bubbleBoot: Boolean get() = bool("bubbleBoot", true); set(v) = setBool("bubbleBoot", v)
    var bubbleSizeDp: Int get() = int("bubbleSize", 52); set(v) = setInt("bubbleSize", v.coerceIn(32, 80))
    var bubbleAlphaPct: Int get() = int("bubbleAlpha", 90); set(v) = setInt("bubbleAlpha", v.coerceIn(30, 100))
    /** "L" o "R": borde preferido para las burbujas nuevas. */
    var bubbleEdge: String get() = sp.getString("bubbleEdge", "R") ?: "R"; set(v) { sp.edit().putString("bubbleEdge", v).apply(); bump() }
    var batchContinue: Boolean get() = bool("batchContinue", true); set(v) = setBool("batchContinue", v)

    var widgetConfirmAt: Long
        get() = sp.getLong("widgetConfirmAt", 0L)
        set(v) { sp.edit().putLong("widgetConfirmAt", v).apply() }
    var widgetLast: String
        get() = sp.getString("widgetLast", "") ?: ""
        set(v) { sp.edit().putString("widgetLast", v).apply() }

    /** Posición de una burbuja: borde ("L"/"R") y fracción vertical 0..1. Sin listener: no dispara reconstrucciones. */
    fun bubblePos(p: String): Pair<String, Float>? {
        val s = pos.getString(p, null) ?: return null
        val parts = s.split(",")
        if (parts.size != 2) return null
        return parts[0] to (parts[1].toFloatOrNull() ?: return null)
    }
    fun saveBubblePos(p: String, edge: String, yFrac: Float) { pos.edit().putString(p, "$edge,$yFrac").apply() }
    fun resetBubblePositions() { pos.edit().clear().apply(); bump() }
}
