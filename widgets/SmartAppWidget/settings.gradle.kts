pluginManagement {
    repositories {
        google()
        // Espejo de Maven Central (Google): evita el límite de peticiones (429) del repo oficial.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Espejo de Maven Central (Google): evita el límite de peticiones (429) del repo oficial.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "SmartAppWidget"
include(":app")
