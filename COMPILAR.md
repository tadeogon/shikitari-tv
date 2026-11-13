# Cómo compilar en otra PC

Los APK no se suben (pesan mucho), así que en una PC nueva hay que compilarlos:

1. Instalar Android Studio (trae el SDK y Java).
2. Compilar los launchers primero, porque el instalador los lleva adentro:
   - `MiLauncherTV`  →  `gradlew assembleRelease`
   - `PachiTV`       →  `gradlew assembleRelease` (genera Pachi TV y Solari)
3. Después el instalador del celular:
   - `ShikitariInstalador`  →  `gradlew assembleRelease`

Los APK quedan en `app/build/outputs/apk/...` de cada proyecto.
