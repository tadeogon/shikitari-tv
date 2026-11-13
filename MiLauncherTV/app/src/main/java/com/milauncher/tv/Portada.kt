package com.milauncher.tv

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

/**
 * Imagen de portada (y del protector). Si hay una foto elegida se usa esa; si no, la ilustración de los gatos.
 * La foto vive en la carpeta externa de la app para que también se pueda mandar desde el celular por ADB:
 * /sdcard/Android/data/com.milauncher.tv/files/portada.jpg
 */
object Portada {

    fun archivo(contexto: Context): File = File(contexto.getExternalFilesDir(null), "portada.jpg")

    fun esPersonalizada(contexto: Context) = archivo(contexto).let { it.exists() && it.length() > 0 }

    fun cargar(contexto: Context): Drawable {
        val f = archivo(contexto)
        if (f.exists() && f.length() > 0) {
            // Lee la foto achicada si es muy grande, para no gastar memoria de más.
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, medidas)
            var muestra = 1
            while (medidas.outWidth / (muestra * 2) >= 1920) muestra *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = muestra })?.let {
                return BitmapDrawable(contexto.resources, it)
            }
        }
        return contexto.getDrawable(R.drawable.arte)!!
    }

    /** Copia una foto elegida en la TV como nueva portada. */
    fun guardarDesde(contexto: Context, uri: Uri): Boolean {
        val destino = archivo(contexto)
        contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            destino.outputStream().use { entrada.copyTo(it) }
            return true
        }
        return false
    }

    fun restaurar(contexto: Context) {
        archivo(contexto).delete()
    }
}
