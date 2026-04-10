@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(project(":core:uikit"))
        }

        androidMain.dependencies {
            implementation(libs.appcompat)
        }

        androidUnitTest {
            kotlin.srcDir("src/test/java")
            dependencies {
                implementation(libs.junit)
            }
        }

        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.espresso.core)
        }
    }
}

android {
    namespace = "me.flowme.overview"
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
