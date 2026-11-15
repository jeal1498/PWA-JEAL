# Apps al momento — widget inteligente de apps (Android)

Widget de pantalla de inicio que se ve como íconos normales (fondo transparente, ícono y nombre originales), pone primero las apps que más usas y las de esa hora del día, y deja recorrer el resto en orden alfabético con **scroll vertical real** dentro del widget. No es un launcher.

APK lista: [`../apk/apps-al-momento-1.0.0.apk`](../apk/) (release firmada con llave de depuración, ~1.6 MB).

## Instalar
1. Pasa la APK al teléfono y ábrela (acepta «instalar apps de origen desconocido»).
2. Abre **Apps al momento** → Empezar → Siguiente → **Abrir Ajustes** → activa «Acceso a datos de uso» → regresa. Lee tu historial y muestra el orden.
3. Toca **Agregar widget a la pantalla de inicio** (o mantén presionado el inicio → Widgets → Apps al momento).
4. En el widget, el ícono ⚙ discreto (esquina superior derecha) abre el panel: columnas, filas, sugeridas, «todas», vista, **Más…** y **Listo**.

## Compilar
Requiere JDK 17+ y Android SDK 35.
```
echo "sdk.dir=/ruta/al/android-sdk" > local.properties
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # pruebas del motor
```
Los repos usan un espejo de Maven Central de Google (`settings.gradle.kts`) para evitar errores 429.

## Permisos
| Permiso | Para qué | Si no se da |
|---|---|---|
| `PACKAGE_USAGE_STATS` (especial, en Ajustes) | Leer aperturas, tiempos y horarios que Android ya guardó | Modo limitado: orden alfabético, favoritos, aprende solo de lo que abras desde el widget |
| `<queries>` LAUNCHER/HOME | Ver apps con ícono y saber cuál es el launcher | — (no se usa `QUERY_ALL_PACKAGES`) |

Sin permiso de internet: nada sale del teléfono.

## Arquitectura (`app/src/main/java/com/jeal/smartwidget/`)
| Paquete | Qué hace |
|---|---|
| `core/` | Permiso de uso (AppOps real), formatos |
| `data/` | Room: `apps`, `usage_events` (único por app+hora), `transitions` (secuencias), `profiles`, `profile_apps` (favorita/excluida/desactivada), `widgets` (config por instancia), `meta` |
| `analytics/` | `AppCatalog` (PackageManager), `UsageIngestor` (UsageStatsManager → sesiones, incremental, retención 180 días), `StatsCalculator` |
| `intelligence/` | `RecommendationEngine` (puro, probado), `CatalogBuilder` (favoritos → sugeridas → alfabético, sin duplicados, estable), `GridFit` (¿cabe la cuadrícula?) |
| `widget/` | `SmartWidgetProvider`, `WidgetRenderer` (RemoteViews), `GridRemoteViewsService` (colección), `LaunchActivity` (trampolín), `WidgetUpdater` |
| `workers/` | `RefreshWorker` (WorkManager cada 30 min + tras abrir apps) |
| `features/` | Compose: bienvenida, permisos, panel, apps, estadísticas, perfiles, privacidad, ajustes de widget |

### Decisión: RemoteViews + GridView (no Glance)
- Scroll vertical real en la pantalla de inicio solo existe con **colecciones de widget** (`GridView`/`ListView` + `RemoteViewsService`). Glance también las usa por debajo, pero no permite fijar el alto exacto de celda ni cambiar columnas por instancia sin otro layout; RemoteViews directo da control total y menos capas.
- Columnas: un layout por cantidad (`widget_c1..c10`), porque `setNumColumns` no es seguro vía RemoteViews.
- Filas: alto de celda = alto del widget ÷ filas (`setViewLayoutHeight` en Android 12+, padding calculado en 8–11).
- Transparencia: todo el árbol con fondo transparente, `listSelector` transparente y vista de carga transparente (la de Android dice «Cargando…»).
- `minSdk 26` (Android 8), `targetSdk 35`.

### Motor de recomendaciones
Puntaje = confianza × (35 % frecuencia + 30 % horario + 20 % reciente + 15 % semanal) + 10 % secuencia.
- Frecuencia: aperturas con vida media de 14 días + 25 % tiempo de uso (raíz): **no** decide solo por tiempo de pantalla.
- Horario: kernel gaussiano ±1 h sobre la hora actual, como "lift" sobre lo esperado al azar.
- Semanal: laboral vs fin de semana y mismo día.
- Secuencia: P(siguiente app | la que abriste hace ≤ 30 min).
- Confianza: min(1, aperturas/5); recién instaladas (< 3 días, < 3 aperturas) ×0.3.
- Estabilidad: una sugerida solo se reemplaza si la nueva la supera 15 %, y las que se quedan conservan su lugar.
- Pesos ajustables por perfil y botón **«Ajustar pesos con mis datos»**: mide el acierto (top 8) de varias combinaciones con tus últimos 7 días y propone la mejor.

### Reglas del catálogo
Favoritas (siempre, en tu orden) → sugeridas (hasta completar «Sugeridas», favoritas incluidas) → si «Todas» está activo, el resto alfabético. Excluida = nunca sugerida (sí en alfabético). Desactivada = no sale en el widget (no desinstala). Sin datos ni favoritas, nunca queda vacío: muestra el alfabético.

### Instancias
Cada widget nuevo recibe **su propio perfil** (favoritas, exclusiones, desactivadas, pesos) y su propia cuadrícula/sugeridas/vista. Compartir perfil es opcional desde los ajustes del widget.

## Pruebas hechas
- **Unitarias (20/20 pasan)**: orden favoritos→sugeridas→alfabético, sin duplicados, excluidas, desactivadas, «todas» apagado, no vacío, cantidad de sugeridas, estabilidad; motor: mañana/tarde/noche, secuencia, recién instaladas, no solo tiempo de pantalla, primer arranque con solo agregados, acierto medible; cabe/no cabe.
- **Emulador Android 11 con Pixel Launcher real** (sin aceleración, muy lento): ver `PRUEBAS.md`.

## Limitaciones conocidas
- Android guarda pocos días de **eventos detallados** (≈7–10 en muchos equipos) y agregados por día/semana/mes/año; se usa lo que haya. Desde la instalación, la app guarda su propia copia (180 días).
- El tamaño real lo decide el launcher (márgenes, celdas). Si filas×columnas no cabe, el panel lo dice y propone otra; si se deja, las filas de más se ven con scroll.
- El ⚙ queda encima de la celda superior derecha (35 % de opacidad); se puede ocultar y en Android 12+ usar «Reconfigurar» del launcher.
- `RemoteViewsService` está marcado obsoleto en Android 15 pero sigue funcionando; la alternativa (`RemoteCollectionItems`) manda todo de golpe y no conviene para cientos de íconos.
- Las actualizaciones son cada 30 min (y ~1 min después de abrir una app desde el widget): no es instantáneo, a propósito, por batería.
- No probado en teléfono físico ni en launchers de Samsung/Xiaomi.
