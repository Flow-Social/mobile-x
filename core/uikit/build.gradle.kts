@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.coil3.compose)
        }

        androidMain {
            kotlin.setSrcDirs(listOf("src/androidMain/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/compose/resourceGenerator/kotlin/androidMainResourceCollectors"))
        }

        androidMain.dependencies {
            api(project(":core:domain"))
            api(libs.core.ktx)
            api(libs.androidx.graphics.shapes)
            api(libs.material3)
            api(libs.ui.tooling.preview)
            api(libs.material.icons.extended)
            api(libs.activity.compose)
            api(libs.lifecycle.runtime.compose)
            api(libs.coil)
            api(libs.coil.compose)
            api(libs.coil.gif)
            api(libs.coil3.network.okhttp)
            api(libs.textflow.material3)
            api(libs.emoji2.emojipicker)
            api(libs.androidx.datastore.preferences)
            api(libs.appcompat)
        }

        wasmJsMain.dependencies {
            implementation(libs.coil3.network.ktor3)
        }
    }
}

android {
    namespace = "me.floow.uikit"
    compileSdk = 35

    sourceSets.getByName("main").apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.setSrcDirs(listOf("src/androidMain/res"))
        java.setSrcDirs(emptyList<String>())
    }

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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    testImplementation(libs.junit)
}
