package com.jeal.switchdriver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dibuja las burbujas. NO ejecuta nada por su cuenta: solo reacciona a un toque del usuario sobre
 * una burbuja y entonces llama a StopEngine con el paquete de ESA burbuja.
 */
class BubbleService : Service() {

    companion object {
        private const val CH = "bubbles"
        private const val NID = 41
        const val ACT_SYNC = "sd.SYNC"
        const val ACT_STOP = "sd.STOP"
        const val ACT_HIDE = "sd.HIDE"
        const val ACT_SHOW = "sd.SHOW"
        const val ACT_MINIMIZE = "sd.MINIMIZE"

        fun canDraw(c: Context) = Settings.canDrawOverlays(c)

        /** Arranca/actualiza/detiene el servicio según la configuración. Solo se llama por acción del usuario o al arrancar el teléfono. */
        fun sync(c: Context) {
            val want = Store.bubbleEnabled && canDraw(c) && Store.bubbles.isNotEmpty()
            if (want) {
                try { ContextCompat.startForegroundService(c, Intent(c, BubbleService::class.java).setAction(ACT_SYNC)) }
                catch (t: Throwable) { DiagLog.add("No se pudo iniciar burbujas: ${t.message}") }
            } else c.stopService(Intent(c, BubbleService::class.java))
        }

        fun command(c: Context, action: String) {
            if (action == ACT_HIDE) Store.bubbleHidden = true
            if (action == ACT_SHOW) Store.bubbleHidden = false
            if (!Store.bubbleEnabled) return
            try { ContextCompat.startForegroundService(c, Intent(c, BubbleService::class.java).setAction(action)) } catch (_: Throwable) {}
        }
    }

    private class Entry(val pkg: String, val view: BubbleView, val lp: WindowManager.LayoutParams) {
        var edge = "R"; var yFrac = 0.3f; var minimized = false; var attached = false
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val entries = LinkedHashMap<String, Entry>()
    private val idle = Runnable { if (Store.bubbleAutoMin) setAllMinimized(true) }
    private var rebuildPending = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("bubbles", "controlled", "protected", "bubbleSize", "bubbleAlpha", "bubbleEdge", "bubbleHidden", "bubbleEnabled", "bubbleAutoMin") && !rebuildPending) {
            rebuildPending = true
            handler.postDelayed({ rebuildPending = false; rebuild() }, 150)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Store.prefs().registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!canDraw(this) || !Store.bubbleEnabled) { DiagLog.add("Burbujas: sin permiso o desactivadas"); stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACT_STOP -> { Store.bubbleEnabled = false; stopSelf(); return START_NOT_STICKY }
            ACT_HIDE -> Store.bubbleHidden = true
            ACT_SHOW -> Store.bubbleHidden = false
            ACT_MINIMIZE -> { rebuild(); setAllMinimized(true); return START_STICKY }
        }
        rebuild()
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Burbujas", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, NID, notification(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun notification(): Notification {
        fun pi(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, BubbleService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val hidden = Store.bubbleHidden
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("Switch Driver")
            .setContentText(if (hidden) "Burbujas ocultas" else "${Store.bubbles.size} burbuja(s) activas · tócalas para detener esa app")
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, if (hidden) "Mostrar" else "Ocultar", pi(if (hidden) ACT_SHOW else ACT_HIDE, 1))
            .addAction(0, "Apagar burbujas", pi(ACT_STOP, 2))
            .build()
    }

    // --- Pantalla -----------------------------------------------------------------------------

    private fun screen(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else { @Suppress("DEPRECATION") val d = resources.displayMetrics; d.widthPixels to d.heightPixels }
    }

    private fun clearAll() {
        entries.values.forEach { if (it.attached) try { wm.removeView(it.view) } catch (_: Throwable) {} }
        entries.clear()
    }

    private fun rebuild() {
        handler.removeCallbacks(idle)
        clearAll()
        try { getSystemService(NotificationManager::class.java).notify(NID, notification()) } catch (_: Throwable) {}
        if (!Store.bubbleEnabled || !canDraw(this)) { stopSelf(); return }
        if (Store.bubbleHidden) return
        val pkgs = Store.bubbles.filter { it in Store.controlled && Policy.blockReason(this, it) == null && Catalog.isInstalled(this, it) }
            .sortedBy { Catalog.labelOf(this, it).lowercase() }
        val sizePx = (Store.bubbleSizeDp * resources.displayMetrics.density).toInt()
        val (sw, sh) = screen()
        pkgs.forEachIndexed { idx, pkg ->
            val label = Catalog.labelOf(this, pkg)
            val view = BubbleView(this, Catalog.icon(this, pkg, sizePx.coerceAtLeast(64)), label.take(1).uppercase(), sizePx)
            view.alpha = Store.bubbleAlphaPct / 100f
            val lp = WindowManager.LayoutParams(
                sizePx, sizePx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            val e = Entry(pkg, view, lp)
            val saved = Store.bubblePos(pkg)
            e.edge = saved?.first ?: Store.bubbleEdge
            e.yFrac = saved?.second ?: (0.25f + 0.12f * idx).coerceAtMost(0.8f)
            lp.x = if (e.edge == "L") 0 else sw - sizePx
            lp.y = (e.yFrac * sh).toInt().coerceIn(0, sh - sizePx)
            wire(e)
            try { wm.addView(view, lp); e.attached = true; entries[pkg] = e }
            catch (t: Throwable) { DiagLog.add("Burbuja $pkg: no se pudo mostrar (${t.javaClass.simpleName})") }
        }
        DiagLog.add("Burbujas mostradas: ${entries.size}")
        scheduleIdle()
    }

    private fun wire(e: Entry) {
        e.view.onTouchStart = { handler.removeCallbacks(idle) }
        e.view.onDrag = { dx, dy ->
            val (sw, sh) = screen()
            e.lp.x = (e.lp.x + dx.toInt()).coerceIn(-e.view.sizePx / 2, sw - e.view.sizePx / 2)
            e.lp.y = (e.lp.y + dy.toInt()).coerceIn(0, sh - e.view.sizePx)
            e.minimized = false
            try { wm.updateViewLayout(e.view, e.lp) } catch (_: Throwable) {}
        }
        e.view.onDragEnd = {
            val (sw, sh) = screen()
            e.edge = if (e.lp.x + e.view.sizePx / 2 < sw / 2) "L" else "R"
            e.yFrac = e.lp.y.toFloat() / sh
            e.lp.x = if (e.edge == "L") 0 else sw - e.view.sizePx
            try { wm.updateViewLayout(e.view, e.lp) } catch (_: Throwable) {}
            Store.saveBubblePos(e.pkg, e.edge, e.yFrac)
            scheduleIdle()
        }
        e.view.onTap = {
            // Un solo toque detiene la app, aunque la burbuja esté minimizada (también la despliega).
            if (e.minimized) setMinimized(e, false)
            onBubbleTap(e)
        }
    }

    private fun setMinimized(e: Entry, min: Boolean) {
        if (!e.attached) return
        val (sw, _) = screen()
        e.minimized = min
        val s = e.view.sizePx
        e.lp.x = if (e.edge == "L") (if (min) -s / 2 else 0) else (if (min) sw - s / 2 else sw - s)
        e.view.alpha = if (min) (Store.bubbleAlphaPct / 100f) * 0.55f else Store.bubbleAlphaPct / 100f
        try { wm.updateViewLayout(e.view, e.lp) } catch (_: Throwable) {}
    }

    private fun setAllMinimized(min: Boolean) { entries.values.forEach { setMinimized(it, min) } }
    private fun scheduleIdle() { handler.removeCallbacks(idle); if (Store.bubbleAutoMin) handler.postDelayed(idle, 4000) }

    private fun onBubbleTap(e: Entry) {
        if (e.view.mode == BubbleView.Mode.WORKING) return // evita toques repetidos
        e.view.mode = BubbleView.Mode.WORKING
        val label = Catalog.labelOf(this, e.pkg)
        scope.launch {
            val r = StopEngine.stop(applicationContext, e.pkg, "burbuja")
            e.view.mode = when (r) {
                is StopResult.Stopped, is StopResult.AlreadyStopped -> BubbleView.Mode.OK
                else -> BubbleView.Mode.FAIL
            }
            Toast.makeText(applicationContext, StopEngine.short(label, r), Toast.LENGTH_SHORT).show()
            delay(1400)
            e.view.mode = BubbleView.Mode.IDLE
            scheduleIdle()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (entries.isNotEmpty()) rebuild()
    }

    override fun onDestroy() {
        Store.prefs().unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacksAndMessages(null)
        clearAll()
        scope.cancel()
        super.onDestroy()
    }
}
