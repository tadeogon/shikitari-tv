package com.shikitari.instalador.ui

/*
 * La única costura entre la interfaz y lo que de verdad toca la TV.
 * La pantalla nunca escribe en la TV por su cuenta: todo pasa por ProveedorPlan.
 * El plan se arma solo leyendo; recién «Dejarla limpia» ejecuta.
 */

data class EntradaUi(val nombre: String, val paquete: String, val motivo: String? = null)

/** recomendado = aparece con el filtro «Recomendado»; con «Todo» se ven todos los grupos. */
data class GrupoUi(val titulo: String, val entradas: List<EntradaUi>, val recomendado: Boolean)

data class PlanUi(
    val modelo: String,              // "AFTKRT · Fire OS 8"
    val totalApps: Int?,             // cuántas apps tiene la TV, si se sabe
    val grupos: List<GrupoUi>,       // lo que se puede apagar
    val seQuedan: List<EntradaUi>,   // con motivo
    val perfilConocido: Boolean,
    val esEjemplo: Boolean,          // true = datos de muestra, no cambia nada en la TV
    val yaApagadas: List<EntradaUi> = emptyList(), // de las listas, las que ya estaban apagadas (motivo = grupo)
) {
    fun todos(): List<EntradaUi> = grupos.flatMap { it.entradas }
}

/** Lo que se elige en la hoja «Personalizar» y no es un paquete. */
data class OpcionesUi(val animacionesRapidas: Boolean = false)

/** avisos = líneas con ✓ que se muestran al terminar (las arma el proveedor, no la pantalla). */
data class Resumen(val apagadas: Int, val protegidas: Int, val seQuedan: Int, val avisos: List<String> = emptyList())

sealed interface Resultado<out T> {
    data class Ok<out T>(val valor: T) : Resultado<T>
    data class Error(val mensaje: String) : Resultado<Nothing>
}

interface ProveedorPlan {
    suspend fun analizar(): Resultado<PlanUi>

    /** apagar = paquetes que la persona dejó seleccionados. Si se cancela la corrutina, para donde esté. */
    suspend fun ejecutar(
        apagar: Set<String>,
        opciones: OpcionesUi,
        alPaso: (hechos: Int, total: Int, narracion: String) -> Unit,
    ): Resultado<Resumen>

    suspend fun deshacerTodo(): Resultado<Unit>
    suspend fun prender(paquete: String): Resultado<Unit>
}

/** Alexa es la única que se puede apagar solo desde el interruptor de «Personalizar». */
const val PAQUETE_VOZ_ALEXA = "com.amazon.vizzini"
