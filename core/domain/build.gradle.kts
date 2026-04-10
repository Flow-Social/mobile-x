@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()
    androidTarget()
    wasmJs {
        browser()
    }

    jvmToolchain(17)

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/java")
            dependencies {
                api(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

android {
    namespace = "me.floow.domain"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets["main"].java.setSrcDirs(emptyList<String>())

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
