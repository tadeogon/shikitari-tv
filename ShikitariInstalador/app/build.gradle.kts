plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shikitari.instalador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shikitari.instalador.nuevo"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.3"
    }

    buildTypes {
        release {
            // Sin achicar: la librerÃ­a de Claude usa reflexiÃ³n (Jackson) y se romperÃ­a.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

// Copia el APK de Shikitari TV (el launcher) adentro de esta app, para instalarlo en otras TVs.
val copiarLauncher by tasks.registering(Copy::class) {
    from("../../MiLauncherTV/app/build/outputs/apk/release/app-release.apk")
    into("src/main/assets")
    rename { "shikitari-tv.apk" }
}
// Lo mismo con Pachi TV, el segundo launcher.
val copiarPachi by tasks.registering(Copy::class) {
    from("../../PachiTV/app/build/outputs/apk/pachi/release/app-pachi-release.apk")
    into("src/main/assets")
    rename { "pachi-tv.apk" }
}
// Y Solari: el mismo launcher con la marca del trabajo.
val copiarSolari by tasks.registering(Copy::class) {
    from("../../PachiTV/app/build/outputs/apk/trabajo/release/app-trabajo-release.apk")
    into("src/main/assets")
    rename { "solari-tv.apk" }
}
tasks.named("preBuild") { dependsOn(copiarLauncher, copiarPachi, copiarSolari) }

dependencies {
    // ConexiÃ³n ADB inalÃ¡mbrica con la TV (emparejar con cÃ³digo, conectar, ejecutar comandos)
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Certificado propio para emparejar con la TV
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    // SDK oficial de Claude (Anthropic)
    implementation("com.anthropic:anthropic-java:2.34.0")
}
