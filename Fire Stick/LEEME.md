# Fire TV Stick (AFTT, Fire OS 5.2.9.5, Android 5.1)

Cambios hechos:
- Animaciones a 0.5 (se deshace con restaurar_firestick.bat).
- Desinstaladas: Magis TV (APK en apk_respaldo) y Spotify. APK de Stremio y Kodi movidos a apk_respaldo.
- Instalado Shikitari TV 1.4 (Shikitari-TV-1.4-FireStick.apk).

Bloqueado por Fire OS (sin root no se puede): desactivar o quitar apps de Amazon, limpiar la caché de todas las apps,
reemplazar la pantalla de inicio, servicio de accesibilidad, aviso del botón Home.

Botón Inicio: un servicio de Shikitari mira qué app está en primer plano y, si es la pantalla de Amazon, se abre encima.
Tarda unos 5 segundos. Se apaga en Ajustes de Shikitari > "Abrir Shikitari TV con Inicio".

Para instalarlo de nuevo (Depuración ADB prendida):
  adb connect IP:5555
  adb -s IP:5555 install -r Shikitari-TV-1.4-FireStick.apk
  adb -s IP:5555 shell pm grant com.milauncher.tv android.permission.WRITE_SECURE_SETTINGS
  adb -s IP:5555 shell appops set com.milauncher.tv GET_USAGE_STATS allow

Para quitarlo y volver a Amazon:
  adb -s IP:5555 uninstall com.milauncher.tv

Estado actual: Shikitari TV se desinstaló del Fire Stick para priorizar la fluidez; queda la pantalla de Amazon.
