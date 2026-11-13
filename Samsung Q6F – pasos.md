# Samsung Q6F (2025, Tizen 9) – optimización y privacidad

Fuente: guía de Samsung Tizen del repositorio [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
(`docs/devices/samsung-tizen.md`) y [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

En Samsung **no se pueden quitar apps del sistema** (no hay acceso tipo adb). Todo se hace con el control.
Los nombres pueden variar un poco según la traducción; entre paréntesis va el nombre en inglés.

**Antes de cambiar nada, sacale una foto a cada pantalla** para poder volver atrás.

## 1. Privacidad y publicidad

Configuración › Toda la configuración › General y privacidad › Términos y privacidad
(en algunos equipos: "Términos y políticas")

- [ ] Servicios de información de visualización (Viewing Information Services) → **Apagada** (ACR: reconoce lo que mirás)
- [ ] Anuncios basados en intereses (Interest-Based Advertisements) → **Apagada**
- [ ] Servicio de personalización (Customization Service) → **Apagada**
- [ ] Mejorar anuncios personalizados (Improve personalized ads) → **Apagada**
- [ ] Recibir noticias y ofertas (Get news and special offers) → **Apagada**
- [ ] No rastrear (Do not track) → **Prendida**
- [ ] Restablecer PSID (Reset PSID) → tocar una vez (nuevo ID de publicidad)

Si alguna no aparece en tu modelo, se saltea.

## 2. Micrófono de Bixby

- [ ] Configuración › Toda la configuración › Funciones avanzadas › Voz › Configuración de Bixby ›
      Activación por voz (Voice Wake-Up) → **Apagada**

La voz sigue funcionando apretando el botón del micrófono del control.

## 3. Arranque más rápido

Configuración › Toda la configuración › Funciones avanzadas › Opciones de pantalla de inicio

- [ ] Iniciar con Smart Hub Home → **Apagada**
- [ ] Ejecutar automáticamente la última app → **Prendida** (opcional: al encender vuelve a lo último que mirabas)

## 4. Fluidez

- [ ] Configuración › Toda la configuración › Soporte › Cuidado del dispositivo › **Iniciar cuidado del dispositivo**
- [ ] Cuidado del dispositivo › Administrar almacenamiento → en las apps pesadas, **Borrar caché**
      (no "Borrar datos": cierra la sesión)
- [ ] Apps › Configuración (engranaje) → desinstalar las apps que no uses

## Notas

- Pet Care (escuchar ladridos con la TV apagada) solo existe en Q8F o superior: no aplica al Q6F.
- Después de cada actualización de la TV, revisar la sección 1: a veces las opciones se vuelven a prender.
- Opcional y más avanzado: bloqueo por DNS en el router (NextDNS, Pi-hole o AdGuard Home).
  Bloquear: `acr-us-prd.samsungcloud.tv`, `acr-eu-prd.samsungcloud.tv`, `samsungacr.com`,
  `acr0.samsungcloudsolution.com`, `samsungads.com`, `samsungtvads.com`.
  **No bloquear**: `samsungcloudsolution.com` (corta la conexión), `time.samsungcloudsolution.com` (reloj),
  `otnprd8–11.samsungcloudsolution.net` y `samsungotn.net` (actualizaciones), `auth.samsungosp.com` (cuenta).
