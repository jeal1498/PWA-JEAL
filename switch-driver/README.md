# Switch Driver

APK Android nativa (Kotlin + Compose, sin root) que **detiene (Force Stop) solo las apps que eliges** usando [Shizuku](https://shizuku.rikka.app/): pensada para conductores que usan Uber, DiDi u otras a la vez. `minSdk 26` · `targetSdk 35` · v1.0.1 · APK: `apk/switch-driver-1.0.1.apk` (firmada con llave de depuración).

## Reglas que cumple el código
- Solo `am force-stop` de **un** paquete validado (`Validation.isPackageName`), con argumentos en lista (sin shell, sin comandos libres). No abre apps, no cierra sesiones, no borra datos/caché.
- **Un solo punto de detención**: `StopEngine.stop` (botón, lote, burbuja y widget pasan por él). Orden: paquete válido → **protegida/crítica/sistema/launcher/teclado/teléfono/Switch Driver** → está en tu lista → Shizuku listo → ejecutar → **verificar**.
- **Verificación real**: tras el force-stop se lee `stopped=` de `dumpsys package` (usuario actual). Resultados: `Stopped` (verificado), `AlreadyStopped` (no se ejecutó nada), `Unverified`, `Failed`, `Blocked`, `Unavailable`. Solo `Stopped` se anuncia como «detenida ✓».
- Toques repetidos: bloqueo por paquete (`inFlight`) y la burbuja ignora toques mientras trabaja.
- Protegidas: prioridad absoluta (se quitan de selección y burbujas al protegerlas; el motor las rechaza aunque otra capa falle). Primera vez se sugiere proteger Maps, Spotify, WhatsApp, Waze y teléfono si están instaladas.

## Arquitectura (`app/src/main/java/com/jeal/switchdriver`)
| Archivo | Qué hace |
|---|---|
| `ShellService.kt` + `IUserService.aidl` | UserService de Shizuku (proceso shell/root): `forceStop`, `isStopped`. Revalida el paquete |
| `ShizukuBridge.kt` | Estado (no instalado / detenido / sin autorizar / negado / activo), permiso, bind del servicio, binder muerto |
| `StopEngine.kt`, `Apps.kt` (`Policy`, `Catalog`) | Reglas, catálogo (solo apps con ícono de lanzador, sin `QUERY_ALL_PACKAGES`) |
| `BubbleService.kt`, `BubbleView.kt` | Burbujas overlay (servicio en primer plano `specialUse`) |
| `StopWidget.kt` | Widget + receptor no exportado |
| `Store.kt` | SharedPreferences (listas, burbujas, posiciones, preferencias) |
| `MainActivity.kt`, `Screens.kt`, `Theme.kt` | 5 pantallas: Inicio, Apps, Protegidas, Burbujas, Ajustes (oscuro) |

## Burbujas
Una por app elegida; arrastrables (se pegan al borde, recuerdan posición), tamaño 32–80 dp, transparencia, borde preferido, minimizar al borde tras 4 s (el primer toque en una minimizada **solo la despliega**), ocultar/restaurar (también desde la notificación). El toque llama a `StopEngine` con el paquete de **esa** burbuja; aro verde/ámbar/rojo + toast breve. Sin permiso «Mostrar sobre otras apps» no se finge nada: aviso + botón. El servicio no hace nada por sí solo.

## Widget
Estado de Shizuku con hora de última actualización, hasta 4 botones por app y «Detener seleccionadas (N)», que pide **segundo toque en 5 s** como confirmación del lote. Receptor no exportado; sin abrir la app.

## Compilar
JDK 17+, Android SDK 35 (`local.properties` con `sdk.dir`).
```
./gradlew assembleRelease testDebugUnitTest
```

## Límites conocidos (sin simular)
- **Tras reiniciar el teléfono Shizuku debe iniciarse otra vez** (depuración inalámbrica/ADB); la app lo dice como estado, no como fallo. Las burbujas reaparecen solas pero detener exige Shizuku activo.
- Si una app se reinicia sola al instante (servicio/alarma), se reporta «sin verificar», no éxito.
- Solo se listan apps con ícono de lanzador. Apps del sistema (no actualizadas) no se pueden detener.
- Fabricantes con ahorro de batería agresivo (Xiaomi, Huawei, Samsung…) pueden matar el servicio de burbujas: desactiva la optimización de batería para Switch Driver.
- La overlay no se dibuja sobre pantallas protegidas ni sobre el teclado/ajustes del sistema en algunos fabricantes.

## Pruebas
`ValidationTest` (5, pasan): validación de paquetes e inyección, lectura de `stopped` por usuario, plan de lote que nunca incluye protegidas.
**Pendiente (requiere teléfono con Shizuku): los 18 criterios de aceptación** — ver `PRUEBAS.md`.
