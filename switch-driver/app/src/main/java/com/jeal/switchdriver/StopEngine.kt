package com.jeal.switchdriver

import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed class StopResult {
    /** Detenida y verificada por el sistema. */
    data object Stopped : StopResult()
    /** Ya estaba detenida: no se ejecutó nada. */
    data object AlreadyStopped : StopResult()
    /** No se ejecutó por seguridad o por no estar en la lista. */
    data class Blocked(val why: String) : StopResult()
    /** Shizuku no disponible. */
    data class Unavailable(val why: String) : StopResult()
    /** Se ejecutó pero el sistema no confirma que quedó detenida. */
    data class Unverified(val why: String) : StopResult()
    data class Failed(val why: String) : StopResult()
    data object Busy : StopResult()

    val success get() = this is Stopped
}

/**
 * ÚNICO punto por el que se detiene una app (botón, lote, burbuja o widget).
 * Nunca abre apps ni toca otra cosa que el paquete pedido.
 */
object StopEngine {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    suspend fun stop(ctx: Context, pkg: String, via: String): StopResult {
        val app = ctx.applicationContext
        val label = Catalog.labelOf(app, pkg)
        val t0 = System.currentTimeMillis()
        val res = run(app, pkg)
        DiagLog.add("[$via] $label ($pkg) → ${describe(res)} · ${System.currentTimeMillis() - t0} ms")
        return res
    }

    private suspend fun run(app: Context, pkg: String): StopResult {
        if (!Validation.isPackageName(pkg)) return StopResult.Blocked("Paquete inválido")
        // Protegida y reglas de seguridad primero (prioridad absoluta).
        Policy.blockReason(app, pkg)?.let { return StopResult.Blocked(it) }
        if (!Store.isControlled(pkg)) return StopResult.Blocked("No está en tu lista de control")
        if (!inFlight.add(pkg)) return StopResult.Busy
        try {
            val st = ShizukuBridge.ensureReady()
            if (!st.ok) return StopResult.Unavailable("${st.title}. ${st.help}")
            return withContext(Dispatchers.IO) {
                val user = Process.myUserHandle().hashCode() // id de usuario (0 en el perfil principal)
                val svc = ShizukuBridge.service()
                // Si el UserService no conecta (pasa en algunos equipos), se usa el proceso directo de Shizuku:
                // mismas reglas, mismo comando validado, misma verificación.
                if (svc == null) DiagLog.add("UserService sin conexión: usando método directo de Shizuku")
                try {
                    val out = svc?.forceStop(pkg, user) ?: PrivOps.forceStop(PrivOps.viaShizuku, app.packageName, pkg, user)
                    when {
                        out == "VERIFIED" -> StopResult.Stopped
                        out.startsWith("UNVERIFIED:") -> StopResult.Unverified(out.removePrefix("UNVERIFIED:"))
                        else -> StopResult.Failed(out.removePrefix("FAIL:"))
                    }
                } catch (t: Throwable) {
                    ShizukuBridge.refresh()
                    StopResult.Failed("Se perdió la conexión con Shizuku (${t.javaClass.simpleName})")
                }
            }
        } finally {
            inFlight.remove(pkg)
        }
    }

    fun describe(r: StopResult): String = when (r) {
        StopResult.Stopped -> "detenida y verificada"
        StopResult.AlreadyStopped -> "ya estaba detenida (no se ejecutó nada)"
        is StopResult.Blocked -> "bloqueada: ${r.why}"
        is StopResult.Unavailable -> "no disponible: ${r.why}"
        is StopResult.Unverified -> "sin verificar: ${r.why}"
        is StopResult.Failed -> "falló: ${r.why}"
        StopResult.Busy -> "ya hay una detención en curso"
    }

    /** Texto breve para toasts / filas. */
    fun short(label: String, r: StopResult): String = when (r) {
        StopResult.Stopped -> "$label detenida ✓"
        StopResult.AlreadyStopped -> "$label ya estaba detenida"
        is StopResult.Blocked -> "$label: ${r.why}"
        is StopResult.Unavailable -> "Shizuku no disponible"
        is StopResult.Unverified -> "$label: no se pudo confirmar"
        is StopResult.Failed -> "$label: error"
        StopResult.Busy -> "$label: espera…"
    }
}
