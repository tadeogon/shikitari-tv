# Xiaomi Stick (MiTV-AESP0, aquaman, Android 10)

Base: lista de cleanyourtv para este modelo (examples/xiaomi-mitv-aesp0), adaptada: sin su launcher, sin setupwraith ni mitv.service,
sin providers.

- Desactivadas: 35 apps (ver desactivados_xiaomi.txt), incluidos voz (katniss), Mi TV+, TalkBack, protector de pantalla,
  actualizaciones de Xiaomi (updateservice), Miracast (wfdsinkhelperservice) y voz de texto (tts).
- Desinstaladas: Magis TV (APK en apk_respaldo), Kodi, Spotify y Fylmo TV (APK en ..\APKs\Fylmo TV).
- Animaciones en 0. Búsqueda de Wi-Fi en segundo plano apagada (estaba en 1).
- Límite de apps en memoria (max_cached_processes): no se conserva al reiniciar, no tiene efecto duradero.
- Antes: 596 MB libres, 245 MB de memoria. Primera optimización: 3,2 GB libres, 399 MB de memoria.

Importante: al desactivar, esperar antes de reiniciar. Reiniciar enseguida perdió los cambios una vez.

Deshacer: restaurar_xiaomi.bat (reactiva las apps y las animaciones).

Pachi TV 1.0 instalado para probar (com.pachi.tv), con WRITE_SECURE_SETTINGS otorgado. Botón Inicio sin activar.
