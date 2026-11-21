package com.jeal.switchdriver

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Ejecutor de comandos: lista de argumentos (sin shell) -> (código de salida, salida) o null si excedió el tiempo. */
typealias Exec = (List<String>, Long) -> Pair<Int, String>?

/** Operaciones privilegiadas, independientes de CÓMO se ejecuten (UserService o proceso directo de Shizuku). */
object PrivOps {
    fun isStopped(exec: Exec, pkg: String?, userId: Int): Int {
        if (!Validation.isPackageName(pkg) || userId < 0) return -1
        val r = exec(listOf("/system/bin/dumpsys", "package", pkg!!), 10_000) ?: return -1
        return Validation.parseStopped(r.second, userId)
    }

    /** "VERIFIED", "UNVERIFIED:<detalle>" o "FAIL:<detalle>". */
    fun forceStop(exec: Exec, selfPkg: String, pkg: String?, userId: Int): String {
        if (!Validation.isPackageName(pkg) || userId < 0) return "FAIL:paquete inválido"
        if (pkg == selfPkg) return "FAIL:no se detiene a sí misma"
        val r = exec(listOf("/system/bin/am", "force-stop", "--user", userId.toString(), pkg!!), 10_000)
            ?: return "FAIL:el comando no respondió"
        if (r.first != 0) return "FAIL:am salió con ${r.first} ${r.second.trim().take(160)}"
        repeat(6) {
            if (isStopped(exec, pkg, userId) == 1) return "VERIFIED"
            Thread.sleep(200)
        }
        return if (isStopped(exec, pkg, userId) == 0) "UNVERIFIED:el sistema sigue sin marcarla detenida (¿se reinició sola?)"
        else "UNVERIFIED:no se pudo leer el estado"
    }

    /** Lee la salida de un proceso con tiempo límite. */
    fun collect(p: Process, timeoutMs: Long): Pair<Int, String>? {
        val sb = StringBuilder()
        val reader = thread(isDaemon = true) {
            try { p.inputStream.bufferedReader().use { r ->
                val buf = CharArray(8192)
                while (true) { val n = r.read(buf); if (n < 0) break; if (sb.length < 400_000) sb.append(buf, 0, n) }
            } } catch (_: Throwable) {}
        }
        val end = System.currentTimeMillis() + timeoutMs
        var code: Int? = null
        while (System.currentTimeMillis() < end) {
            try { code = p.exitValue(); break } catch (_: IllegalThreadStateException) { Thread.sleep(40) }
        }
        if (code == null) { try { p.destroy() } catch (_: Throwable) {}; return null }
        reader.join(1500)
        return code to sb.toString()
    }

    /** Ejecución local (dentro del proceso privilegiado del UserService). */
    val local: Exec = { cmd, t ->
        try { collect(ProcessBuilder(cmd).redirectErrorStream(true).start(), t) } catch (_: Throwable) { null }
    }

    /** Ejecución directa con Shizuku.newProcess (método de respaldo, sin UserService). */
    val viaShizuku: Exec = { cmd, t ->
        try {
            val m = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, cmd.toTypedArray(), null, null) as Process
            collect(p, t)
        } catch (e: Throwable) { DiagLog.add("newProcess: ${e.javaClass.simpleName} ${e.cause?.message ?: e.message}"); null }
    }
}
