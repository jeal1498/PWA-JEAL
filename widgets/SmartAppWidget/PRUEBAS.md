# Resultados de pruebas (14–15 nov 2026)

Entorno: emulador Android 11 (API 30, Google APIs) con **Pixel Launcher real**, sin aceleración de hardware (muy lento: a veces «System UI no responde», se tocó «Esperar»). Capturas en `docs/capturas/`.

## Unitarias — 20/20 ✅
`./gradlew testDebugUnitTest` (catálogo, motor, cuadrícula). Ver README.

## En emulador
| # | Prueba | Resultado |
|---|---|---|
| 1 | Instalar APK (debug y release con R8) | ✅ |
| 2 | Bienvenida → cómo aprende → permiso | ✅ (capturas 01–03) |
| 3 | «Abrir Ajustes» abre la pantalla del permiso de esta app | ✅ |
| 4 | Permiso concedido* → lee historial previo a la instalación | ✅ 12 aperturas reales de 4 apps, abiertas antes de instalar (04) |
| 5 | Orden inicial: más usadas primero, resto alfabético, sin duplicados | ✅ (05, 06) |
| 6 | Agregar widget con el diálogo del launcher | ✅ 4×4 por defecto |
| 7 | Fondo transparente, íconos y nombres originales | ✅ (06) |
| 8 | Scroll vertical real dentro del widget hasta el final | ✅ (07) |
| 9 | Abrir app con un toque (Teléfono) | ✅ |
| 10 | Aprender de la apertura: Teléfono pasa a sugerida | ✅ |
| 11 | Panel ⚙ en el widget: columnas 4→5, «Listo» lo oculta | ✅ (08, 09) |
| 12 | Favorita (Drive) va primero; desactivada (Camera) desaparece | ✅ (10, 11) |
| 13 | Dos instancias con perfiles y cuadrículas distintas | ✅ (#2 4×5 Principal, #3 5×5 Widget 2) |
| 14 | Reinicio del dispositivo: widget y configuración persisten | ✅ (columnas, favorita y desactivada intactas) |
| 15 | Release (R8) abre y lee la base sin errores | ✅ |

\* El interruptor del permiso se activó con `adb shell appops set … GET_USAGE_STATS allow` (mismo efecto que tocarlo); la pantalla de Ajustes sí se abrió desde la app.

## Errores encontrados y corregidos durante las pruebas
- Ajustes no contaba como uso (declara HOME de respaldo y se ignoraba como launcher) → solo se ignoran launchers sin ícono.
- Al fijar el widget desde la app, Android no manda `onUpdate` si hay actividad de configuración → callback al fijar + refresco al abrir la app + `configuration_optional` (Android 12+).
- Tamaño inicial 2×2 → 4×4.
- Panel de edición cortado en widgets chicos → compacto y a lo ancho, con Listo/Más arriba.
- Pantalla Apps decía «Sin resultados» mientras cargaba → barra de carga.

## No probado (pendiente en teléfono real)
- Redimensionar con las manijas del launcher (los gestos largos no funcionaron en el emulador lento; el cálculo de celdas sí se probó en unitarias y al cambiar filas/columnas).
- Pantallas Estadísticas, Perfiles («Ajustar pesos con mis datos»), Privacidad y la vista «Con información» (compilan; no se abrieron en el emulador).
- Android 12–15, launchers de Samsung/Xiaomi/Motorola, consumo de batería medido, modo sin permiso de punta a punta.
