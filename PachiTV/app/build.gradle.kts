plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pachi.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pachi.tv"
        minSdk = 22
        targetSdk = 35
        versionCode = 27
        versionName = "1.26"
    }

    // Dos versiones del mismo launcher: la tuya y la del trabajo.
    // Comparten todo el código; solo cambian el nombre y el logo (carpeta src/trabajo/res).
    flavorDimensions += "marca"
    productFlavors {
        create("pachi") {
            dimension = "marca"
            applicationId = "com.pachi.tv"
        }
        create("trabajo") {
            dimension = "marca"
            applicationId = "com.pachi.tv.trabajo"
        }
    }

    buildTypes {
        release {
            // Achica el APK; se firma con la clave de debug para poder instalarlo con adb.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

dependencies {
    // Para los códigos QR de la vitrina de Solari
    implementation("com.google.zxing:core:3.5.3")
}
