package com.jeal.switchdriver

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Corre dentro del proceso privilegiado de Shizuku (uid shell/root).
 * Solo sabe hacer UNA cosa: `am force-stop` de un paquete validado. Los argumentos van en una lista
 * (sin shell), así que no hay forma de inyectar comandos.
 */
class ShellService() : IUserService.Stub() {

    override fun destroy() { exitProcess(0) }

    override fun whoami(): String = "uid=${android.os.Process.myUid()}"

    override fun isStopped(pkg: String?, userId: Int): Int {
        if (!Validation.isPackageName(pkg) || userId < 0) return -1
        val r = exec(listOf("/system/bin/dumpsys", "package", pkg!!), 10_000) ?: return -1
        return Validation.parseStopped(r.second, userId)
    }

    override fun forceStop(pkg: String?, userId: Int): String {
        if (!Validation.isPackageName(pkg) || userId < 0) return "FAIL:paquete inválido"
        if (pkg == BuildConfig.APPLICATION_ID) return "FAIL:no se detiene a sí misma"
        val r = exec(listOf("/system/bin/am", "force-stop", "--user", userId.toString(), pkg!!), 10_000)
            ?: return "FAIL:el comando no respondió"
        if (r.first != 0) return "FAIL:am salió con ${r.first} ${r.second.trim().take(160)}"
        // Verificación real: la bandera "stopped" del sistema (con reintentos cortos).
        repeat(6) {
            if (isStopped(pkg, userId) == 1) return "VERIFIED"
            Thread.sleep(200)
        }
        val last = isStopped(pkg, userId)
        return if (last == 0) "UNVERIFIED:el sistema sigue sin marcarla detenida (¿se reinició sola?)"
        else "UNVERIFIED:no se pudo leer el estado"
    }

    /** (código de salida, salida) o null si excedió el tiempo. */
    private fun exec(cmd: List<String>, timeoutMs: Long): Pair<Int, String>? {
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val sb = StringBuilder()
            val reader = thread(isDaemon = true) {
                try { p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) { val n = r.read(buf); if (n < 0) break; if (sb.length < 400_000) sb.append(buf, 0, n) }
                } } catch (_: Throwable) {}
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return null }
            reader.join(1500)
            p.exitValue() to sb.toString()
        } catch (_: Throwable) { null }
    }
}
