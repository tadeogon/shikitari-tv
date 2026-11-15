package com.shikitari.instalador

/**
 * La pantalla de Control ahora vive en ui/PantallaControl.kt (interfaz nueva).
 * Este archivo queda solo con las listas de apps que usan la app y AccionesTv
 * (se mantiene el nombre para no tocar AccionesTv).
 */
class ControlActivity private constructor() {

    companion object {
        const val COLUMNAS = 3
        const val CLAVE_FAVORITAS = "control_favoritas"

        val FAVORITAS_INICIALES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.firetv.youtube",
            "com.amazon.avod",
            "com.disney.disneyplus",
            "com.hbo.hbonow",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
        )

        /** Apps de fábrica que conviene mostrar aunque no sean "-3", con su nombre real. */
        val NOMBRES = mapOf(
            "com.netflix.ninja" to "Netflix",
            "com.google.android.youtube.tv" to "YouTube",
            "com.amazon.firetv.youtube" to "YouTube",
            "com.amazon.avod" to "Prime Video",
            "com.amazon.venezia" to "Appstore",
            "com.amazon.tv.settings.v2" to "Ajustes",
            "tv.twitch.android.viewer" to "Twitch",
            "org.clipbox.player" to "ClipBox",
            "dev.dworks.apps.anexplorer" to "AnExplorer",
            "com.estrongs.android.pop" to "ES Explorador",
            "com.precisecontrol.filemanager.free" to "Administrador de archivos",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.disney.disneyplus" to "Disney+",
            "com.hbo.hbonow" to "HBO Max",
            "com.wbd.stream" to "Max",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "com.spotify.tv.android" to "Spotify",
            "com.cbs.ott" to "Paramount+",
            "tv.twitch.android.app" to "Twitch",
            "com.kick.mobile" to "Kick",
            "com.plexapp.android" to "Plex",
            "org.videolan.vlc" to "VLC",
            "com.teamsmart.videomanager.tv" to "SmartTube",
            "com.liskovsoft.smarttubetv.beta" to "SmartTube Beta",
            "com.google.android.play.games" to "Play Juegos",
            "com.android.vending" to "Play Store",
            "com.google.android.tv" to "Google TV",
            "com.android.tv.settings" to "Ajustes",
            "com.milauncher.tv" to "Shikitari TV",
            "com.pachi.tv" to "Ziota",
            "com.pachi.tv.trabajo" to "Solari",
            "com.limelight" to "Moonlight",
            "com.mxlmovies.app" to "MXL Movies",
            "com.fylmo.tv" to "Fylmo TV",
            "org.smarttube.stable" to "SmartTube",
            "com.stremio.one" to "Stremio",
            "com.esaba.downloader" to "Downloader",
            "ar.wolpuente" to "Wolpuente",
            "com.yablio.sendfilestotv" to "Send Files",
            "tv.mxlmovies.app" to "MXL Movies",
            "com.cbs.ca" to "Paramount+",
            "com.apgsolutionsllc.APGSOLUTIONSLLC0007" to "APG",
        )

        /** Paquetes de "-3" que no son apps para abrir (servicios, actualizadores). */
        /** No se ofrecen para desinstalar: sin ellas la TV queda sin tienda, sin ajustes o sin inicio. */
        val PROTEGIDAS = setOf("com.android.vending", "com.google.android.tv", "com.android.tv.settings", "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
            "com.amazon.tv.launcher", "com.amazon.tv.settings.v2", "com.amazon.venezia")

        val OCULTAR = setOf("com.shikitari.instalador", "com.shikitari.instalador.nuevo", "com.android.mgstxNF")
    }
}
