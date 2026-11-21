# Pruebas en dispositivo real (pendientes)

Requisitos: Shizuku activo y autorizado; Uber y DiDi instaladas. Marca ✔ solo con evidencia.

| # | Criterio | Estado |
|---|---|---|
| 1 | Detener Uber no abre DiDi | ☐ |
| 2 | Detener DiDi no abre Uber | ☐ |
| 3 | Se pueden agregar otras apps | ☐ |
| 4 | Detención individual | ☐ |
| 5 | Detención en lote (seleccionadas) | ☐ |
| 6 | Protegidas nunca se detienen en lote | ☐ |
| 7 | Protegidas no se detienen por burbuja | ☐ |
| 8 | Configuración persiste tras reiniciar | ☐ |
| 9 | Burbujas sobre otras apps (con permiso) | ☐ |
| 10 | Burbujas se mueven | ☐ |
| 11 | Tocar burbuja solo afecta a su app | ☐ |
| 12 | Ocultar y restaurar burbujas | ☐ |
| 13 | Widget sin abrir la app | ☐ |
| 14 | Shizuku desconectado se detecta | ☐ |
| 15 | Errores sin fingir éxito | ☐ |
| 16 | No se abre ninguna app al detener | ☐ |
| 17 | No se detiene ninguna app no seleccionada | ☐ |
| 18 | Switch Driver no se detiene en lotes | ☐ |

Verificar «detenida»: `adb shell dumpsys package <paquete> | grep stopped=`.
