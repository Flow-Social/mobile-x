plugins {
    id("java-library")
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.serialization)
}

kotlin {
    // Android/Gradle test runtime is Java 17; compiling this JVM module with 21
    // produces classfiles (v65) that JUnit on 17 can't load.
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
