# Shikitari TV

Proyecto para optimizar un **Chromecast con Google TV** ("sabrina", Android 14) y darle un launcher propio
con estética japonesa y los gatos de la casa (un tabby y una calico).

## Carpetas

| Carpeta | Qué hay |
|---|---|
| `APKs/` | Las apps listas para instalar: `Shikitari-TV.apk` (launcher de la TV) y `Shikitari-Instalador.apk` (app del celular). |
| `MiLauncherTV/` | Código del launcher **Shikitari TV** (Kotlin, sin librerías). |
| `ShikitariInstalador/` | Código de la app de celular **Shikitari Instalador** (Kotlin). |
| `platform-tools-latest-windows/platform-tools/` | `adb.exe`, `restaurar.bat`, los registros de lo que se quitó y el respaldo del APK de Stremio. |

## Lo que se hizo en la TV

- **Fase 1 – limpieza segura:** se quitaron 13 apps de Google que no hacen falta (con `pm uninstall -k --user 0`, reversible).
- **Fase 2 – apps propias:** se desinstalaron TV Bro, Flow, Stremio, Claro video, Downloader y después Projectivy.
  El APK de Stremio quedó en `platform-tools/apk_respaldo/`.
- **Fase 4 – limpieza profunda:** se quitaron videos, tvrecommendations, backdrop y katniss (Asistente / voz).
  **dreamx se restauró** porque contiene el panel de Google Home (luces y termotanque).
- **Animaciones** en 0.5 y **protector de pantalla** propio (los gatos o la foto elegida) a los 10 minutos.
- Shikitari TV tiene el **permiso de ajustes** (`WRITE_SECURE_SETTINGS` + `WRITE_SETTINGS`) y su servicio de accesibilidad
  hace que el botón **Inicio** del control abra Shikitari TV.

- **Otro perfil de Google TV (perfil 10):** se le aplicó la lista segura de [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
  ("debloat-keep-cast" para Chromecast): se quitaron feedbackconsent y videos. Tu perfil ya la tenía completa.

Registros: `platform-tools/quitados_por_claude.txt` (apps del sistema), `quitados_perfil10_por_claude.txt` (otro perfil), `apps_desinstaladas.txt` (apps propias),
`desactivados_por_claude.txt` y los respaldos `*_original.txt`.

## Deshacer

Doble clic en `platform-tools/restaurar.bat` → escribir el puerto actual de la TV. Reinstala las apps del sistema quitadas,
reactiva lo desactivado y restaura animaciones y protector. Las apps propias se reinstalan desde la Play Store.

## Shikitari TV (launcher)

- Barra con el logo, menú **Inicio · Streaming · Pelis · Juegos · Apps · ⚙** y reloj.
- Portada (ilustración de los gatos o una foto con el logo encima), Favoritas y filas por categoría.
- **Mantener OK** sobre una app: abrir, favoritas, mover, cambiar categoría, ocultar, información, desinstalar.
- **Ajustes:** modo claro/oscuro, 6 colores, letra (Pincel / Redonda / Clásica), portada, logo sobre la foto,
  optimizar (memoria, almacenamiento, animaciones), protector de pantalla, botón Inicio, apps ocultas.

## Shikitari Instalador (celular)

Vincula y conecta con una TV por **depuración inalámbrica**, y desde ahí: instalar Shikitari TV + permisos + botón Inicio
y protector ("Todo en uno"), **cambiar la portada** (con editor de encuadre), **optimizar / deshacer** y **ayuda con Claude**
(necesita una clave de API de Anthropic; los cambios siempre piden confirmación).

## Para instalar en otra TV

1. En la TV: activar **Opciones para desarrolladores** y **Depuración inalámbrica**.
2. En el celular, Shikitari Instalador: **Vincular** (código + puerto de vinculación) → **Conectar** → **Todo en uno**.
3. Opcional: **Optimizar** y **Cambiar portada**.

(El botón Inicio funciona en Google TV; en otras marcas Shikitari TV se abre como una app más.)

## Recompilar

Requisitos ya instalados en esta PC: JDK 17 (Eclipse Adoptium), SDK de Android en `C:\Android\sdk`, Gradle.

```
cd MiLauncherTV
gradlew.bat assembleRelease
```

```
cd ShikitariInstalador
gradlew.bat assembleRelease
```

El instalador copia solo el APK del launcher adentro (compilar primero `MiLauncherTV`).
Las firmas usan la llave de `C:\Users\Administrador\.android\debug.keystore`: **guardar una copia**, sin ella no se puede
actualizar la app sin desinstalarla.

## Créditos

Fuentes: Kaushan Script y Varela Round (Google Fonts, licencia OFL). Conexión ADB del celular: libadb-android.
