package com.jeal.switchdriver

/** Lógica pura (sin Android) para poder probarla con JUnit. */
object Validation {
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /** Un nombre de paquete válido: solo letras, números, '_' y '.', sin espacios ni símbolos de shell. */
    fun isPackageName(s: String?): Boolean = s != null && s.length <= 200 && PKG.matches(s)

    /** Lee `stopped=` de la línea "User <id>:" de `dumpsys package`. 1 / 0 / -1 (no se pudo saber). */
    fun parseStopped(dumpsys: String, userId: Int): Int {
        val rx = Regex("^\\s*User $userId:.*\\bstopped=(true|false)")
        for (line in dumpsys.lineSequence()) {
            val m = rx.find(line) ?: continue
            return if (m.groupValues[1] == "true") 1 else 0
        }
        return -1
    }
}

/** Resultado de armar una acción masiva. */
data class StopPlan(val targets: List<String>, val skipped: List<Pair<String, String>>)

object Planner {
    /**
     * @param base paquetes candidatos (seleccionadas o todas las controlables)
     * @param blockReason motivo por el que NO se puede detener (null = se puede). La protección va primero.
     */
    fun build(base: Collection<String>, blockReason: (String) -> String?): StopPlan {
        val targets = ArrayList<String>()
        val skipped = ArrayList<Pair<String, String>>()
        for (p in base.distinct()) {
            val r = blockReason(p)
            if (r == null) targets += p else skipped += p to r
        }
        return StopPlan(targets, skipped)
    }
}
