@file:Suppress("DEPRECATION")

import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
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
    packageName = "me.floow.shared.config"
    objectName = "AppConfig"
    exposeObjectWithName = "AppConfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "BASE_URL", serverUrl)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(project(":feature:chatssearch"))
            implementation(project(":feature:comments"))
            implementation(project(":core:domain"))
            implementation(project(":core:api"))
            implementation(project(":core:data"))
            implementation(project(":core:uikit"))
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.reorderable)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.qrose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(project(":core:uikit"))
            implementation(project(":core:domain"))
            implementation(libs.appcompat)
            implementation(libs.androidx.exifinterface)
            implementation(libs.coil3.network.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.datastore.preferences)

            val cameraxVersion = "1.4.1"
            implementation("androidx.camera:camera-core:$cameraxVersion")
            implementation("androidx.camera:camera-camera2:$cameraxVersion")
            implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
            implementation("androidx.camera:camera-view:$cameraxVersion")
            implementation("androidx.camera:camera-video:$cameraxVersion")

            val media3Version = "1.5.1"
            implementation("androidx.media3:media3-exoplayer:$media3Version")
            implementation("androidx.media3:media3-session:$media3Version")
            implementation("androidx.media3:media3-ui:$media3Version")
            implementation("androidx.media3:media3-datasource:$media3Version")
            implementation("androidx.media3:media3-database:$media3Version")
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.espresso.core)
        }
    }
}

android {
    namespace = "me.floow.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
