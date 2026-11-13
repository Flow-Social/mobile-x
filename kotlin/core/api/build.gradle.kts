@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.buildkonfig)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val serverUrl = localProps.getProperty("FLOW_API_BASE_URL")
    ?: localProps.getProperty("flow.api.base.url")
    ?: System.getenv("FLOW_API_BASE_URL")
    ?: "https://31.77.9.57.nip.io/api/v1"

buildkonfig {
    packageName = "me.floow.api.config"
    objectName = "AppConfig"
    exposeObjectWithName = "AppConfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "BASE_URL", serverUrl)
    }
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

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

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
    namespace = "me.floow.api"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets.getByName("main") { java.setSrcDirs(emptyList<String>()) }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
