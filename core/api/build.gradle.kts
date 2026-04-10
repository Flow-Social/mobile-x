@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
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
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.ktor.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(project(":core:domain"))
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.cio)
            }
        }

        wasmJsMain {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.1.2")
            }
        }
    }
}

android {
    namespace = "me.floow.api"
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
