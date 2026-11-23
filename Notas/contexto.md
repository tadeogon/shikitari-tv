# Contexto rápido (leer cuando haga falta, no entero cada vez)

Actualizado: 2026-11-23

## Equipos y versiones
| Equipo | Dirección adb | Versión |
|---|---|---|
| Fire Stick | 192.168.100.68:5555 | Ziota 1.51 |
| Chromecast (Android 14) | adb-23161HFDD5CD6T-GRh2rO._adb-tls-connect._tcp (192.168.100.2) | Ziota 1.51 |
| Celular Samsung | adb-R5CY5520NLJ-WguLmn._adb-tls-connect._tcp (192.168.100.65) | App instalador 4.2 |
| Tele Samsung QN65Q6F | 192.168.100.67 (puertos 8001/8002) | volumen por Wi-Fi |

adb: `C:\Android\sdk\platform-tools\adb.exe` (en bash: `export MSYS_NO_PATHCONV=1`).

## Proyectos
- `PachiTV/` = Ziota (`com.pachi.tv`) y Solari (sabor `trabajo`, `com.pachi.tv.trabajo`).
- `ShikitariInstalador/` = app del celular (`com.shikitari.instalador.nuevo`). Lleva Ziota/Solari adentro en `app/src/main/assets/`.
- Compilar: `./gradlew.bat assembleRelease --console=plain -q` en cada proyecto. Ver `COMPILAR.md`.
- Cada cambio en una TV se anota en `Registros/firestick-cambios.txt`.

## Reglas
- Probar en una sola TV; actualizar todo, APKs/ y GitHub solo cuando el usuario dice "actualizá todo".
- No tocar AccionesTv. Apps del sistema: solo `pm disable-user` / `pm uninstall -k --user 0`.
- No manejar la TV o el celular si el usuario los está usando (mirar qué hay en pantalla antes).

## Cosas aprendidas
- Fire Stick por HDMI ignora el volumen ("fixed volume device"): la app manda el volumen directo a la tele Samsung (la primera vez hay que elegir Permitir en la tele).
- Android 14 (Chromecast) y Fire OS no dejan que una app cierre otra: Apps abiertas lleva a "Forzar detención" en la información de la app.
- El celular Samsung corta la red de la app 5 s después de salir de ella; la app reconecta rápido al volver.
- Cuando el Fire Stick se despierta puede quedar en la pantalla de Amazon; Ziota aparece al mover el control.

## Falta probar
- Volumen de la tele Samsung por Wi-Fi.
- Guardar Apple TV (.apks) y reinstalar una copia guardada.
