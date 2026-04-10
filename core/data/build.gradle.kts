@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/java")
            dependencies {
                implementation(project(":core:domain"))
                implementation(libs.kotlinx.coroutines)
            }
        }
    }
}

android {
    namespace = "me.floow.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets["main"].java.setSrcDirs(emptyList<String>())

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
