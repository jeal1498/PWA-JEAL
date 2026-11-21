package com.jeal.switchdriver

import kotlin.system.exitProcess

/**
 * Corre dentro del proceso privilegiado de Shizuku (uid shell/root).
 * Solo sabe hacer UNA cosa: `am force-stop` de un paquete validado (ver PrivOps).
 */
class ShellService() : IUserService.Stub() {
    override fun destroy() { exitProcess(0) }
    override fun whoami(): String = "uid=${android.os.Process.myUid()}"
    override fun isStopped(pkg: String?, userId: Int): Int = PrivOps.isStopped(PrivOps.local, pkg, userId)
    override fun forceStop(pkg: String?, userId: Int): String = PrivOps.forceStop(PrivOps.local, BuildConfig.APPLICATION_ID, pkg, userId)
}
