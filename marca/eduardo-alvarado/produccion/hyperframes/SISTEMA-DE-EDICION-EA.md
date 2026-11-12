# Sistema de edición EA — HyperFrames

**Versión:** 1.0  
**Estado:** especificación inicial aprobada para implementación  
**Motor:** HyperFrames  
**Agente de producción:** Claude Code  
**Formato prioritario:** TikTok / Reels / Shorts vertical 9:16

## 1. Objetivo

Crear un lenguaje audiovisual reconocible para Eduardo Alvarado y un flujo reutilizable para producir videos con HyperFrames y Claude. El sistema debe reducir decisiones repetitivas sin hacer que todos los videos parezcan copias.

La marca vende edición y producción audiovisual. No debe presentarse como agencia de marketing ni prometer resultados comerciales que no controla.

## 2. Identidad invariable

| Token | Valor | Uso |
|---|---|---|
| EA Black | `#171717` | fondos y texto principal |
| EA Orange | `#F57C00` | acentos, indicadores y palabras clave |
| EA Ivory | `#F4F1EA` | fondos claros y texto sobre oscuro |
| EA Gray | `#77736D` | texto secundario |

- Títulos: Montserrat, preferentemente Bold/ExtraBold.
- Texto secundario: Roboto, Regular/Medium.
- Estética: editorial, minimalista, profesional y creativa.
- Firma: **EDUARDO ALVARADO** como nombre principal.
- **EA.** es un recurso gráfico secundario.
- Lema: “Transformo tus grabaciones en contenido que destaca.”

No inventar variaciones del logotipo. Usar los SVG aprobados del directorio `identidad/`. No reconstruir el monograma con texto ni formas aproximadas.

## 3. Formato técnico de salida

- Lienzo: 1080 × 1920 (9:16).
- FPS: 30 como valor inicial.
- Entrega: MP4 H.264, audio AAC cuando corresponda.
- Duración: la que exija el guion; evitar alargar escenas para rellenar.
- Mantener textos y elementos críticos dentro de una zona segura central. Evitar los extremos superior e inferior por la interfaz de TikTok.
- No usar 4K/60 fps por defecto: solo cuando el material y el destino lo justifiquen.

## 4. Gramática de movimiento

### Ritmo
- Priorizar cortes motivados por la voz, la acción o el cambio de idea.
- Alternar demostración visual, rostro a cámara, detalle y texto.
- Evitar animar todos los elementos simultáneamente.
- Las animaciones deben reforzar jerarquía y comprensión, no competir con el mensaje.

### Movimientos de marca
- Entrada: aparición breve y limpia; desplazamiento corto o fade.
- Palabra clave: acento naranja, escala o subrayado discreto.
- Transiciones: cortes limpios, wipes sencillos o movimientos motivados por contenido.
- Cierre: firma EA y CTA pertinente, sin una pantalla final larga.

### No usar
- Transiciones llamativas sin función narrativa.
- Texto excesivo o subtítulos que cubran el rostro.
- Brillos, sombras pesadas, degradados decorativos o colores fuera de paleta sin justificación.
- Animaciones elásticas caricaturescas como estilo predeterminado.
- Música o efectos que compitan con la voz.

## 5. Sistema de componentes

Construir y mantener componentes editables para:

1. **Intro breve:** firma EA / tema del video. Usarla solo cuando aporte reconocimiento; no obligatoria en cada pieza.
2. **Hook:** frase inicial de alto contraste, legible en móvil.
3. **Subtítulos:** Roboto; máximo dos líneas; bloques cortos sincronizados con el habla. Resaltar solo palabras clave en naranja.
4. **Keyword:** palabra o frase de impacto en Montserrat Bold.
5. **Lower third:** nombre, especialidad o contexto; entrada y salida discretas.
6. **Before / After:** comparación clara con etiquetas consistentes.
7. **Callout:** señalización de un detalle en pantalla.
8. **Cierre:** firma EA, propuesta de valor o llamada a la acción.
9. **Marca de agua:** SVG aprobado, tamaño discreto y contraste suficiente.

Antes de crear un componente desde cero, revisar el catálogo/registry de HyperFrames y adaptar un bloque existente si cumple los criterios.

## 6. Estructura sugerida para cada video

1. **Hook (0–3 s):** problema, promesa concreta o resultado visual.
2. **Contexto (3–8 s):** qué se va a mostrar.
3. **Demostración:** proceso, comparación o explicación.
4. **Resultado:** mostrar el cambio o aprendizaje.
5. **CTA:** pedir una acción específica y coherente (comentar, guardar, enviar material o solicitar cotización).

Los tiempos son orientativos, no una plantilla rígida. El contenido manda.

## 7. Flujo de producción con Claude

1. Leer este documento y `PROMPT-MAESTRO-CLAUDE.md`.
2. Activar la skill oficial de HyperFrames (`/hyperframes`) antes de crear o modificar una composición.
3. Revisar assets disponibles y sus rutas reales; nunca inventar rutas.
4. Proponer estructura de escenas y ritmo antes de implementar si el guion no está definido.
5. Consultar el registry oficial para componentes existentes.
6. Construir composición HTML/CSS/JS siguiendo el contrato actual de HyperFrames.
7. Ejecutar lint y validate; resolver errores y advertencias relevantes.
8. Abrir preview y revisar fotogramas clave, legibilidad móvil, sincronía y safe areas.
9. Renderizar MP4 y revisar el archivo final antes de marcarlo como terminado.

Comandos habituales (confirmar opciones con la versión instalada):
- `npx hyperframes preview`
- `npx hyperframes lint`
- `npx hyperframes validate`
- `npx hyperframes render --output output.mp4`

## 8. Estructura de proyecto recomendada

```text
produccion/hyperframes/
├── SISTEMA-DE-EDICION-EA.md
├── PROMPT-MAESTRO-CLAUDE.md
├── design-tokens.css
├── assets/
│   ├── logos/
│   ├── fonts/
│   ├── audio/
│   └── footage/
├── components/
│   ├── captions/
│   ├── titles/
│   ├── lower-thirds/
│   ├── transitions/
│   └── brand/
└── videos/
    └── video-001/
        ├── index.html
        ├── assets/
        └── output/
```

No mover ni duplicar los archivos maestros de identidad sin necesidad. Los proyectos de video pueden referenciarlos o copiar una versión de trabajo.

## 9. Criterios de aceptación

Un video cumple el sistema EA cuando:

- Respeta paleta, tipografías y jerarquía de marca.
- Usa los recursos oficiales, sin redibujar el logo.
- Subtítulos sincronizados, legibles y sin errores ortográficos.
- Textos dentro de zonas seguras y legibles en pantalla móvil.
- Animaciones deterministas y reproducibles.
- Audio inteligible; voz prioritaria sobre música y efectos.
- Lint y validate sin errores.
- MP4 final reproducido y revisado visualmente.

## 10. Evolución

Esta versión define dirección, componentes y proceso. Los componentes animados definitivos se validarán mediante una composición piloto real antes de declararlos presets de producción.
