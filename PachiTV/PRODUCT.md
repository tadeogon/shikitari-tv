# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

One person (the owner), at home, from the couch with a TV remote (D-pad, OK, Back, Home, Menu). Uses it daily to open streaming apps, control Spotify, and wake the home PCs.

## Product Purpose

Ziota is a home-screen launcher for Android TV / Fire TV that replaces the stock home: apps in rows, quick controls, music, and home utilities in one calm place. Success: everything reachable in a few remote presses, fluid on a slow Fire Stick, and polished enough to feel made by a dedicated developer.

## Positioning

A personal launcher tied to the owner's own setup: Wake-on-LAN for the home PCs ("Prender PC"), a music island that keeps the last Spotify song, and a phone companion app (ShikitariInstalador) that installs and configures it.

## Operating Context

- Devices: Fire TV Stick (1920x1080 at 320 dpi, ~1.7 GB RAM, Fire OS, no system blur) and Chromecast with Google TV (Android 12+, has blur).
- Fire TV blocks some system screens (Settings, Bluetooth, audio); Ziota opens Amazon's own screens there.
- Permissions for reading notifications and closing apps are granted once over ADB.
- Two flavors share code: Ziota (personal) and Solari (work). This redesign applies to Ziota only; Solari stays as it is.

## Capabilities and Constraints

- Kotlin, Android Views built in code (no XML layouts, no Compose). minSdk 22.
- Existing: app rows by category, favorites/recents, hide/move apps, side menu, music island (MediaSession), weather (Open-Meteo), Prender PC (logic fixed in 1.36: do not touch), themes, remote buttons that open apps.
- Planned (Notas/pedido-impeccable.md): Control Center with notifications, open-apps switcher, full-screen music after 10 s idle, settings that explain each option, daily recommendation.
- Themes stay, but each theme becomes one accent color (no gradients).
- Must stay smooth on the Fire Stick: blur once, never animate what is still.

## Brand Commitments

- Name: Ziota. Logo: "TF" mark (drawable logo_chico), kept discreet.
- Voice: short, plain rioplatense Spanish ("vos"), saying what each thing is for.
- Reference for craft level (not for look): Aura TV (github.com/cookedzera/aura-tv, MIT). Not a fork; any borrowed code is credited in a comment.

## Product Principles

1. Remote first: every action reachable with the D-pad, focus always visible.
2. Calm by default: content and apps lead; chrome stays quiet.
3. Fast on the weakest device: the Fire Stick sets the performance budget.
4. Explain, don't assume: settings and controls say what they do.
