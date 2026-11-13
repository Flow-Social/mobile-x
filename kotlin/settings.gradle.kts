pluginManagement {
    repositories {
        maven { url = uri("https://repo1.maven.org/maven2/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven { url = uri("https://repo1.maven.org/maven2/") }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Flow"

include(":app")

include(":core:api")
include(":core:data")
include(":core:uikit")
include(":core:domain")
include(":core:auth")

include(":feature:chatssearch")
include(":shared")
include(":feature:comments")
