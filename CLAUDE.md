# Shikitari TV

Apps propias para las teles de la casa: **Ziota** (launcher, `PachiTV/`), **Solari** (mismo código, sabor `trabajo`)
y la **app del celular** (`ShikitariInstalador/`). Repositorio privado en GitHub (`tadeogon/shikitari-tv`).

Equipos, direcciones adb, versiones y lo aprendido: `Notas/contexto.md` (leerlo solo si hace falta).
Cómo compilar: `COMPILAR.md`. Cambios hechos en cada tele: `Registros/firestick-cambios.txt`.

## Cómo hablarle al usuario
- Es principiante: español rioplatense, simple, sin palabras técnicas. Respuestas cortas.
- Cuida los tokens: pocas capturas de pantalla (solo si hace falta ver algo), no leer archivos enteros sin necesidad,
  no repetir pruebas que ya salieron bien.

## Reglas
- Probar cada cambio en **una sola tele** (el Fire Stick si está conectado). Las otras teles, el instalador del
  celular, `APKs/` y GitHub se actualizan solo cuando el usuario dice **"actualizá todo"**.
- Anotar en `Registros/firestick-cambios.txt` todo lo que se cambie en una tele.
- Apps del sistema de la tele: solo `pm disable-user --user 0` o `pm uninstall -k --user 0`, nunca partes críticas.
- No modificar `AccionesTv.kt` (se puede leer y usar).
- Antes de manejar una tele o el celular, mirar qué hay en pantalla: no interrumpir si el usuario lo está usando.
- Nunca aceptar por el usuario carteles de permiso en la tele (por ejemplo "¿Permitir depuración?").
- No cambiar ajustes de accesibilidad, seguridad ni del sistema del celular sin preguntar.
- Antes de descargar algo, pedir permiso diciendo qué, de dónde y cuánto pesa.
- No poner el correo del usuario en nada público.
