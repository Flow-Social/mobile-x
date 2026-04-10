import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeCompiler)
}

android {
	namespace = "me.flowme.chats"
	compileSdk = 35

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
		buildConfig = true
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_1_8)
	}
}

dependencies {
	implementation(project(":feature:shared"))
	implementation(project(":core:uikit"))
	implementation(project(":core:domain"))

	implementation(libs.appcompat)
	implementation(libs.activity.compose)

	api(platform(libs.koin.bom))
	api(libs.koin.core)
	api(libs.koin.android)
	implementation(libs.koin.androidx.compose)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)

	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.espresso.core)
}
