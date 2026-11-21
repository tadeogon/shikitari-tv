# Pedido para Impeccable: rediseño grande de Ziota

Usá la skill `/impeccable` para rediseñar **Ziota**, el launcher de la carpeta `PachiTV`. La meta es que se vea y se use como una app hecha por un programador dedicado: tranquila, prolija y coherente, al nivel de Aura TV, pero con identidad propia.

## Antes de empezar, leé
- `Notas/rediseno-ziota.md`: la lista completa de lo que le gusta al usuario, lo que no va y la descripción de cada captura.
- Las capturas de `Notas/aura-capturas/` (01 a 05 y 10 a 13), sacadas de Aura en el Fire Stick.
- Aura en GitHub (https://github.com/cookedzera/aura-tv, licencia MIT), solo como referencia de concepto. **No es un fork y no se copia tal cual**: se adapta a Ziota. Si se toma código, se cita a Aura en un comentario.

## Lenguaje visual
Que se note que es Ziota y no Aura:
- Fondo casi negro.
- Una sola familia de letra.
- Blanco y grises.
- Un solo color de acento, usado solo para lo encendido o enfocado.
- Formas redondeadas y vidrio esmerilado sutil.
- Sin degradados ni decoración que compita con el contenido. Nada de soles ni cuadrículas: ver el pulido de 1.35 y 1.37.
- Mismo espaciado en todo (múltiplos de 8).
- El brillo solo en lo enfocado.
- Logo Ziota (TF) discreto.

## Qué hay que construir (por orden de importancia para el usuario)
1. **Centro de Control.** Desde el inicio, subiendo dos veces (↑ ↑) o con un botón arriba, se abre un panel de vidrio (captura `04`).
   - **Izquierda:** hora grande, fecha y **notificaciones de la tele "como en el celular"**, con mucha información. OK abre una; mantener OK la descarta.
   - **Derecha:** grilla de módulos estilo iPhone:
     - Wi-Fi y Bluetooth;
     - temporizador para dormir;
     - pantalla apagada con sonido;
     - volumen alto;
     - entradas;
     - memoria libre;
     - **Prender PC** (lo que ya existe en Ziota);
     - Ajustes y Reposo.
2. **Visor de apps abiertas, estilo celular** (captura `05`).
   - Cartas: la última app grande en el medio y las anteriores apiladas.
   - ← → se mueve, OK abre, ↑ cierra esa app.
   - "Cerrar todo" muestra la memoria liberada.
   - Ojo: al cerrar el visor con Atrás no debe abrirse otra app sin querer, que en Aura pasó.
3. **Música en pantalla.** Si suena música y no se toca el control por 10 segundos, el tema ocupa toda la pantalla. Tiene que salir de la isla de música actual de Ziota. En el Fire Stick, Spotify no manda la tapa del disco, así que tiene que verse bien sin ella.
4. **Ajustes que explican cada cosa** (capturas `10`, `11` y `13`).
   - Dos columnas. A la izquierda, ícono grande, título grande y la explicación de la opción enfocada, que cambia al moverse. A la derecha, una lista con iconito de color, nombre, valor actual o interruptor.
   - Secciones con título chico. Una tarjeta destacada arriba para lo importante.
   - Textos cortos que digan para qué sirve cada cosa, en español rioplatense.
   - Ajustes a incluir:
     - los temas de Ziota;
     - animaciones Normal/Rápida;
     - apps ocultas;
     - guardar la configuración en el celular y pasarla a otra tele (encaja con la app instaladora);
     - informes de problemas;
     - versión y actualizaciones.
5. **Recomendaciones** (captura `12`). "La recomendación de hoy": una película o serie por día en el inicio, elegida por género, nota mínima y años.

## Lo que NO va
- Modo compacto o grilla Grande/Compacta/Densa.
- Celsius/Fahrenheit.
- Ajuste de "Botón Inicio".
- "Configurar en esta TV".
- "Quitar Aura".

## Lo que Ziota ya tiene y se mantiene
- Prender PC (Wake on LAN, arreglado en 1.36: no tocar esa lógica).
- La isla de música con Spotify.
- Los temas.
- Los botones del control para abrir apps.
- Hora, fecha y clima.
- La conexión con la app del celular (ShikitariInstalador).

## Límites técnicos
- **Tiene que andar fluido en el Fire Stick**, que es lento y tiene poca memoria. Seguí la idea de Aura: desenfocar el fondo una vez y no en cada cuadro, y no animar lo que está quieto.
- **Fire Stick vs. Chromecast:** en el Fire Stick, Amazon no deja abrir algunas pantallas propias (Ajustes, Bluetooth, audio). Ahí el módulo abre la pantalla de Amazon. Ya hay código para eso en `MainActivity.kt` (`esFireTv`).
- **Permisos por ADB:** leer notificaciones y cerrar apps necesitan permisos que se dan una sola vez. Dejá preparado el comando y anotalo; la app del celular lo puede dar después.

## Cómo trabajar
1. **Primero, una maqueta** de las pantallas nuevas (inicio, Centro de Control, apps abiertas, música, Ajustes). Mostrala y **esperá el sí del usuario** antes de tocar código.
2. Hacelo **por partes**, en el orden de la lista. Mostrá cada parte terminada.
3. **Probá solo en una TV**: el Fire Stick (192.168.100.68:5555); si no está, el Chromecast.
4. **No instales en las otras TVs, no actualices el APK del instalador y no hagas commit ni push** hasta que el usuario diga "actualizá todo".
5. Anotá cada instalación en `Registros/firestick-cambios.txt`.
6. Hablale al usuario en español simple. Es principiante.
