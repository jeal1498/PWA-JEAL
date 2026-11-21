package com.jeal.switchdriver

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bitácora corta en memoria para el diagnóstico (no sale del teléfono). */
object DiagLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        Log.i("SwitchDriver", msg)
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > 60) lines.removeFirst()
    }

    @Synchronized fun dump(): String = if (lines.isEmpty()) "(sin eventos)" else lines.reversed().joinToString("\n")
    @Synchronized fun clear() { lines.clear() }
}
