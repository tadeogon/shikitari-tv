# Índice del repo (dónde está cada cosa)

Usar este índice para ir directo al archivo. Para encontrar una función: `grep -n "fun nombre" archivo`.
Los archivos grandes se leen por partes (`offset`/`limit`), nunca enteros.

## Ziota / Solari — launcher de la tele (`PachiTV/`)
Código: `PachiTV/app/src/main/java/com/pachi/tv/`. Versión en `PachiTV/app/build.gradle.kts` (`versionName`).
Sabores: `pachi` = Ziota (`com.pachi.tv`), `trabajo` = Solari (`com.pachi.tv.trabajo`, logos y textos en `app/src/trabajo/`).

| Archivo | Qué hace |
|---|---|
| `MainActivity.kt` (~2600 líneas) | Pantalla principal. Está dividida en secciones con `// ---------- Nombre ----------`: Centro de Control, Música en pantalla, Apps abiertas, Movimiento (animaciones), Armado de la pantalla, Menú lateral, Datos (lista de apps, favoritas, ocultas), Contenido (filas, tarjetas, búsqueda, recomendación), Optimizar, Prender PC, Ajustes, Menú al mantener OK, Acciones. Teclas del control: `dispatchKeyEvent`. Buscar sección: `grep -n "// ----------" MainActivity.kt` |
| `Tema.kt` | Temas (Prisma, Seda negra, Vino líquido; "Clásico" de Solari) y colores |
| `AjustesPantalla.kt` | Pantalla de Ajustes con secciones y explicaciones |
| `CentroDeControl.kt` | Centro de Control (subiendo desde la primera fila) |
| `AppsAbiertas.kt` | Apps abiertas (multitarea) |
| `IslaMusica.kt` | Qué música suena + notificaciones de la tele |
| `MusicaEnPantalla.kt` | Música a pantalla completa tras 10 s sin tocar |
| `Recomendacion.kt` | Serie recomendada del día (TVmaze) |
| `Clima.kt` | Pronóstico (ipwho.is + Open-Meteo) |
| `Vitrina.kt` | Vitrina de Solari: propiedades de solaribienesraices.com con QR |
| `Protector.kt` | Protector de pantalla (logo rebotando) |
| `FondoConsola.kt` | Fondo dibujado / fondo AMOLED |
| `Botones.kt` | Botones del control remapeados a apps |
| `Pcs.kt` | Prender PCs (Wake on LAN) |
| `InicioService.kt` | Botón Inicio del control abre Ziota |
| `ReceptorActualizacion.kt` | Rearma el botón Inicio después de actualizar |

Otros: textos `app/src/main/res/values/strings.xml`, fondos y logos de apps `app/src/main/res/drawable-nodpi/`,
diseño `PachiTV/PRODUCT.md` y `Notas/rediseno-ziota.md`.

## App del celular — Shikitari Instalador (`ShikitariInstalador/`)
Código: `ShikitariInstalador/app/src/main/java/com/shikitari/instalador/`. Versión en `app/build.gradle.kts`.
Lleva los APK de Ziota/Solari adentro en `app/src/main/assets/`.

| Archivo | Qué hace |
|---|---|
| `AccionesTv.kt` | Lo que la app hace en la tele (listas de limpieza). **No modificar.** |
| `ConexionTv.kt` | Conexión adb inalámbrica con la tele |
| `MisTvs.kt` | Teles guardadas |
| `SamsungTv.kt` | Volumen de la tele Samsung por Wi-Fi |
| `AyudaClaude.kt` | Ayuda con Claude (consulta la tele por adb) |
| `RecorteActivity.kt` | Recortar foto de portada |
| `Bitacora.kt` | Registro de cambios hechos en las teles |
| `ControlActivity.kt` | Solo listas de apps (la pantalla está en `ui/PantallaControl.kt`) |
| `ui/App.kt` | Pestañas y navegación |
| `ui/EstadoApp.kt` | Lógica general (tele conectada, cartel, control, Claude) |
| `ui/PantallaTv.kt` | Pestaña TV |
| `ui/PantallaControl.kt` | Control remoto + pantalla casi en vivo |
| `ui/VideoTv.kt` | Pantalla de la tele como video en vivo |
| `ui/PantallaOptimizar.kt`, `ui/AdaptadorPlan.kt`, `ui/PlanUi.kt` | Optimizar: pantalla, motor y plan |
| `ui/PantallaArchivos.kt` | Archivos de la tele |
| `ui/PantallaMas.kt` | Pestaña Más |
| `ui/Respaldo.kt` | Copia de seguridad de apps (APK) |
| `ui/Memoria.kt` | Memoria de la tele |
| `ui/Tema.kt`, `ui/Componentes.kt`, `ui/Comun.kt`, `ui/Iconos.kt`, `ui/Vidrio.kt`, `ui/Fondo.kt` | Colores, botones, piezas comunes, íconos |

## Shikitari TV — launcher viejo (`MiLauncherTV/`, v1.4)
Código: `MiLauncherTV/app/src/main/java/com/milauncher/tv/` (`MainActivity.kt`, `Protector.kt`, `Portada.kt`,
`InicioService.kt`, `BotonInicioService.kt`, `Tema.kt`). Casi no se toca.

## Teles y documentación
| Dónde | Qué |
|---|---|
| `Notas/contexto.md` | Equipos, direcciones adb, versiones, lo aprendido |
| `Registros/firestick-cambios.txt` | Cambios hechos en cada tele (anotar acá) |
| `Registros/firestick-limpieza.txt` | Limpieza del Fire Stick |
| `Fire Stick/`, `Xiaomi Stick/` | LEEME, lista original de apps y `.bat` para restaurar |
| `Samsung Q6F – pasos.md` | Pasos de la tele Samsung |
| `LEEME.md` | Historia del proyecto (Chromecast, Shikitari TV) |
| `COMPILAR.md` | Cómo compilar |

## No abrir salvo que se pida
Imágenes (`Notas/aura-capturas/`, `Notas/sleek-app/`, `Notas/fondos/`, `drawable-nodpi/`, `*.png`, `*.webp`),
fuentes (`assets/fuentes/`), HTML de pruebas en `Notas/`, `gradle/`, `build/`, `.gradle/`, `.impeccable/`.
