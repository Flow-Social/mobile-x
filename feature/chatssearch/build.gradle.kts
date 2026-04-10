@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
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
	}

	sourceSets {
		commonMain.dependencies {
			implementation(compose.runtime)
			implementation(compose.foundation)
			implementation(compose.ui)
			implementation(compose.material3)
			implementation(project(":core:uikit"))
			implementation(project(":core:domain"))
			implementation(libs.kotlinx.coroutines)
		}

		androidMain.dependencies {
			implementation(libs.appcompat)
			implementation(libs.activity.compose)
			implementation(libs.lifecycle.runtime.compose)

			api(libs.koin.core)
			api(libs.koin.android)
			implementation(libs.koin.androidx.compose)
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
	namespace = "me.floow.chatssearch"
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
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	buildFeatures {
		compose = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
}
