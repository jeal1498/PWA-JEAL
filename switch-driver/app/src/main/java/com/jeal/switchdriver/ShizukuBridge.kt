package com.jeal.switchdriver

import android.content.ComponentName
import android.content.Context
import android.os.IBinder
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuState(val title: String, val help: String) {
    NOT_INSTALLED("Shizuku no instalado", "Instala Shizuku desde Google Play o GitHub (RikkaApps/Shizuku)."),
    NOT_RUNNING("Shizuku detenido", "Abre Shizuku y toca «Iniciar» (depuración inalámbrica o ADB). Tras reiniciar el teléfono hay que volver a iniciarlo."),
    TOO_OLD("Shizuku desactualizado", "Actualiza Shizuku a la versión más reciente."),
    NO_PERMISSION("Falta autorización", "Toca «Autorizar» y acepta el aviso de Shizuku."),
    DENIED("Autorización negada", "Abre Shizuku → Apps autorizadas y permite a Switch Driver."),
    READY("Shizuku activo", "Todo listo para detener apps.");

    val ok get() = this == READY
}

object ShizukuBridge {
    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQ = 4821

    val state = mutableStateOf(ShizukuState.NOT_RUNNING)
    val serviceBound = mutableStateOf(false)
    private lateinit var app: Context
    @Volatile private var svc: IUserService? = null
    @Volatile private var pending: CompletableDeferred<IUserService?>? = null
    @Volatile private var binderWaiter: CompletableDeferred<Unit>? = null
    private val mutex = Mutex()
    private var started = false

    fun init(c: Context) {
        if (started) return
        started = true
        app = c.applicationContext
        Shizuku.addBinderReceivedListenerSticky {
            DiagLog.add("Shizuku: binder recibido")
            binderWaiter?.complete(Unit)
            refresh()
        }
        Shizuku.addBinderDeadListener {
            DiagLog.add("Shizuku: binder perdido")
            svc = null; serviceBound.value = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, result ->
            DiagLog.add("Shizuku: permiso ${if (result == PackageManager.PERMISSION_GRANTED) "concedido" else "negado"}")
            refresh()
        }
        refresh()
    }

    private fun installed(): Boolean = try { app.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true } catch (_: Throwable) { false }

    fun compute(): ShizukuState {
        val alive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        if (!alive) return if (installed()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        return try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) ShizukuState.TOO_OLD
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuState.READY
            else if (Shizuku.shouldShowRequestPermissionRationale()) ShizukuState.DENIED
            else ShizukuState.NO_PERMISSION
        } catch (_: Throwable) { ShizukuState.NOT_RUNNING }
    }

    fun refresh(): ShizukuState {
        val s = compute()
        if (s != state.value) DiagLog.add("Estado Shizuku: ${s.title}")
        state.value = s
        try { StopWidget.refreshAll(app) } catch (_: Throwable) {}
        return s
    }

    fun requestPermission() {
        try { if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQ) } catch (t: Throwable) { DiagLog.add("requestPermission: ${t.message}") }
    }

    fun versionInfo(): String = try {
        if (Shizuku.pingBinder()) "v${Shizuku.getVersion()} · uid ${Shizuku.getUid()}" else "no disponible"
    } catch (_: Throwable) { "no disponible" }

    fun launchShizuku(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG) ?: return false
        ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = if (binder != null && binder.pingBinder()) IUserService.Stub.asInterface(binder) else null
            svc = s; serviceBound.value = s != null
            DiagLog.add("Servicio privilegiado ${if (s != null) "conectado" else "no respondió"}")
            pending?.complete(s)
        }
        override fun onNullBinding(name: ComponentName?) { DiagLog.add("UserService: binding nulo"); pending?.complete(null) }
        override fun onBindingDied(name: ComponentName?) { DiagLog.add("UserService: murió el binding"); svc = null; serviceBound.value = false; pending?.complete(null) }
        override fun onServiceDisconnected(name: ComponentName?) {
            DiagLog.add("Servicio privilegiado desconectado")
            svc = null; serviceBound.value = false
        }
    }

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    }

    /** Si el proceso se acaba de crear (widget tras reinicio), espera un poco al binder de Shizuku. */
    suspend fun ensureReady(): ShizukuState {
        var s = compute()
        if (s == ShizukuState.NOT_RUNNING && installed()) {
            val w = CompletableDeferred<Unit>(); binderWaiter = w
            withTimeoutOrNull(2500) { w.await() }
            s = compute()
        }
        state.value = s
        return s
    }

    suspend fun service(): IUserService? = mutex.withLock {
        svc?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (compute() != ShizukuState.READY) return null
        val d = CompletableDeferred<IUserService?>(); pending = d
        try { Shizuku.bindUserService(args, conn) } catch (t: Throwable) { DiagLog.add("bindUserService: ${t.message}"); return null }
        val got = withTimeoutOrNull(8000) { d.await() }
        if (got == null) {
            DiagLog.add("UserService: sin respuesta en 8 s (Android ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.MODEL})")
            try { Shizuku.unbindUserService(args, conn, true) } catch (_: Throwable) {} // limpia para reintentar
        }
        got
    }
}
