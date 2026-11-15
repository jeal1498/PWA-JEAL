package com.jeal.smartwidget

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SmartApp : Application() {
    companion object {
        /** Ámbito de proceso para trabajos cortos (receptores, trampolín). */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
