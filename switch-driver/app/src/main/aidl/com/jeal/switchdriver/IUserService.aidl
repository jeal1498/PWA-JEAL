package com.jeal.switchdriver;

// Servicio que corre en el proceso privilegiado de Shizuku (shell/root).
interface IUserService {
    // Requerido por Shizuku (código reservado).
    void destroy() = 16777114;

    // Force Stop de UN paquete. Devuelve "VERIFIED", "UNVERIFIED:<detalle>" o "FAIL:<detalle>".
    String forceStop(String pkg, int userId) = 1;

    // 1 = detenida, 0 = no detenida, -1 = no se pudo saber.
    int isStopped(String pkg, int userId) = 2;

    // uid del proceso privilegiado (diagnóstico).
    String whoami() = 3;
}
