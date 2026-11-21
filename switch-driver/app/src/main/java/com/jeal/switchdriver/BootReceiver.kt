package com.jeal.switchdriver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tras reiniciar: solo vuelve a mostrar las burbujas (si el usuario las dejó activas). No detiene nada. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        DiagLog.add("Arranque: ${intent.action}")
        try { StopWidget.refreshAll(context) } catch (_: Throwable) {}
        if (Store.bubbleEnabled && Store.bubbleBoot) BubbleService.sync(context)
    }
}
