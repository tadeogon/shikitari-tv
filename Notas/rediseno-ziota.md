# Rediseño grande de Ziota: lo que nos gusta de Aura

Lista de ideas para armar, más adelante, el pedido a Impeccable. **Todavía no es el pedido.**
Aura TV: https://github.com/cookedzera/aura-tv (licencia MIT: se pueden tomar ideas y código si se lo menciona).

## Lo que te gusta de Aura

### 1. Música en pantalla
- Si suena música y no tocás el control por 10 segundos, el tema ocupa toda la pantalla, como hace Aura.
- Encaja con la isla de música de Ziota.
- En el Fire Stick se vería sin la tapa del disco, porque Spotify no la manda ahí.

### 2. Subir dos veces → pantalla con la hora grande
- Desde el inicio, apretando ↑ dos veces, se abre un panel de vidrio con la hora grande.
- En Aura ese panel es el "Centro de Control".

### 3. Notificaciones "como un celular"
- Dentro de ese panel están las últimas notificaciones de la tele, con mucha información.
- OK abre una; mantener OK la descarta.
- Ojo: para leer las notificaciones, Ziota necesita un permiso que se da una sola vez por ADB.

### 4. Visor de apps abiertas, estilo celular
- Aprietan Inicio dos veces y aparecen las apps abiertas como cartas: la última, grande en el medio, con la pantalla como la dejaste.
- ← → para moverse, OK abre, ↑ cierra esa app.
- "Cerrar todas" muestra cuánta memoria se liberó.

### 5. Prender el Wi-Fi y todo desde ahí
- El mismo panel tiene botones redondos estilo iPhone: Wi-Fi (con prueba de velocidad), Bluetooth, volumen, temporizador para dormir, entradas HDMI y pantalla negra con sonido.
- Ojo: en el Fire Stick algunas cosas solo las deja abrir Amazon (ya pasa con Ajustes y Bluetooth).

## Lo que ya tiene Ziota y hay que mantener
- Prender PC (Wake on LAN), arreglado en 1.36.
- La isla de música con Spotify.
- Los temas.
- Los botones del control para abrir apps.
- La conexión con la app del celular.

## Decisión
No se hace fork de Aura: se rediseña Ziota (el launcher propio), tomando ideas de Aura.

## Lo que no va
- El modo compacto (grilla Grande/Compacta/Densa).
- Ajustes que no aplican a Ziota: Celsius/Fahrenheit, el "Botón Inicio", "Configurar en esta TV", "Quitar Aura".

### 6. Ajustes que te explican cada cosa (te gusta mucho)
Capturas: `10-ajustes.png`, `11-ajustes-abajo.png`, `13-pantalla-de-inicio.png`.

- **Dos columnas.** A la izquierda, un panel fijo con un ícono grande de color, el nombre de la sección en letra muy grande y gruesa ("Ajustes"), una línea gris ("Los cambios se aplican al momento. Todo se puede deshacer igual.") y, debajo, en blanco, **la explicación de la opción que tenés elegida**. Cambia a medida que bajás con el control.
- **A la derecha, la lista estilo iPhone.** Cada fila lleva un iconito cuadrado de color, el nombre y, a la derecha, el valor actual en gris ("Negro puro", "Versión 7.8") o un interruptor verde. La fila elegida se pone blanca.
- **Secciones con título chico y espaciado:** TÚ, PANTALLA DE INICIO, ESTA TV, AURA.
- **Una tarjeta destacada arriba** para lo más importante ("Configurar en esta TV" con botón "Empezar").
- **Ejemplos de las explicaciones** (cortas, claras y que dicen para qué sirve):
  - Animaciones: "Lo rápido que se mueven todos los menús y apps de la TV. Rápida hace que toda la TV parezca más ágil."
  - Tu configuración: "Guarda el orden de las apps, carpetas y ajustes en tu móvil, y recupéralos en cualquier TV."
  - Informes de problemas: "Lo que falló últimamente, guardado en esta TV. Léelo en tu móvil y envíalo cuando informes de un problema."
- **Las subpáginas mantienen el mismo formato.** "Pantalla de inicio" agrupa ASPECTO (Estilo, Categorías, Casillas, Tamaño, Nombres de las apps, Iconos con paralaje) y QUÉ SE MUESTRA (Solo apps, Widgets, Top Shelf, Recomendación, Ver).

Ideas de ajustes de Aura que podrían servir en Ziota:
- **Animaciones** (Normal/Rápida): hace que toda la tele se sienta más ágil.
- **Tu configuración:** guardarla en el celular y pasarla a otra tele. Encaja con la app del instalador.
- **Informes de problemas:** lo que falló, para leerlo desde el celular.
- **Apps ocultas:** recuperar las que escondiste.
- **Actualizaciones:** muestra la versión y avisa cuando hay una nueva.
- **Fondo:** por ejemplo "Negro puro".

### 7. Recomendaciones
Captura: `12-recomendacion.png`.
- **"La recomendación de hoy":** una película o serie elegida cada día, que aparece en la pantalla de inicio.
- Se elige por géneros (ej. Terror), nota mínima (★ 7.0) y años (desde 2020).
- Usa TVmaze gratis; con una clave gratuita de TMDB suma películas y "solo lo que hay en tus apps".
- Botón "Buscar recomendaciones nuevas". Si nada encaja, dice "Nada encaja · prueba con menos límites".

## Cómo se arma el pedido
1. Claude saca capturas de Aura en la tele de cada cosa de esta lista y entiende el concepto (qué hace, cómo se abre, cómo se ve).
2. Recién ahí se le pasa todo a Impeccable para que lo adapte a Ziota, sin copiar Aura tal cual.

Las capturas están en `Notas/aura-capturas/`.

## Lo que muestran las capturas de Aura (Fire Stick, 19/11/2026)

Estilo general de Aura: fondo casi negro, una sola letra (Inter), blanco y grises, y un solo color de acento (azul) para lo que está encendido. Las formas son redondeadas y no lleva degradados.

- `01-inicio.png`: el estilo "tvOS".
  - Arriba, tarjetas grandes de "Nuevo episodio" con la foto de la serie.
  - Abajo, un dock de vidrio con las apps y la elegida en blanco.
  - Arriba dice "Desliza hacia arriba para pantalla completa".
- `02-arriba-1-tarjetas.png`: al subir una vez, el foco pasa a las tarjetas grandes.
- `03-arriba-2-barra-superior.png`: al subir otra vez, aparece arriba a la derecha una pastilla de vidrio con Wi-Fi, Bluetooth, clima y hora, más los botones redondos de Buscar, Ajustes y Centro de Control. Este último está elegido (en blanco).
- `04-centro-de-control.png`: el Centro de Control, abierto con OK sobre ese botón.
  - **Izquierda:** hora grande, fecha y "NOTIFICACIONES". En esta tele todavía no tiene permiso para leerlas.
  - **Derecha:** una grilla de módulos estilo iPhone:
    - un bloque con Wi-Fi, Bluetooth, Temporizador y Pantalla apagada;
    - la barra de volumen alta;
    - Entradas (ARC activa);
    - Imagen (4K · 60 Hz · HDR10+);
    - Sonido (HDMI TrueHD);
    - memoria libre en un anillo;
    - "hoy en apps";
    - dos píldoras anchas: Ajustes y Reposo.
- `05-apps-abiertas.png`: el visor de apps abiertas.
  - Una carta grande con la app (ícono y nombre arriba, "Ahora mismo"); las anteriores se apilan atrás a la izquierda.
  - A la derecha, un botón redondo "Cerrar todo" con la memoria usada ("1.2 de 1.6 GB" y una barrita).
  - Abajo, una ayuda: "OK abre · ▲ en una app la cierra · ► llega a Cerrar todo".
  - En Aura se abre con Inicio dos veces. Para la captura se abrió con `am start -n app.aura.home/.HomeActivity --es aura_switcher <app>`.
- **Música en pantalla:** sin captura. Cuando probé, Spotify ya no estaba sonando, y además Aura necesita el permiso de notificaciones, que en esta tele no tiene. Según su README: la tarjeta de la música toma el lugar de los widgets, y OK la abre a pantalla completa con los colores del disco.

## Para pensar antes del pedido
- ¿Qué pasa en el Fire Stick y qué en el Chromecast? Cada uno tiene límites distintos.
- La velocidad: el Fire Stick es lento, así que todo tiene que andar fluido ahí.
