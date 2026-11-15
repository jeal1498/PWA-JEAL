package com.jeal.smartwidget.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.jeal.smartwidget.SmartApp
import com.jeal.smartwidget.analytics.UsageIngestor
import com.jeal.smartwidget.workers.RefreshWorker
import kotlinx.coroutines.launch

/** Trampolín invisible: abre la app tocada y registra la apertura (aprendizaje sin permiso). */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG)
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (pkg != null && launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            launch.sourceBounds = intent.sourceBounds
            runCatching { startActivity(launch) }
            val ctx = applicationContext
            SmartApp.scope.launch { UsageIngestor.recordWidgetLaunch(ctx, pkg) }
            RefreshWorker.refreshSoon(ctx)
        } else {
            Toast.makeText(this, "Esa app ya no está instalada", Toast.LENGTH_SHORT).show()
            RefreshWorker.refreshNow(applicationContext)
        }
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    companion object { const val EXTRA_PKG = "pkg" }
}
