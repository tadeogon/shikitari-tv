pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android y sun-security-android se publican en JitPack
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "ShikitariInstalador"
include(":app")
