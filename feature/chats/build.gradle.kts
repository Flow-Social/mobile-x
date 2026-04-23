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

	val cameraxVersion = "1.4.1"
	implementation("androidx.camera:camera-core:$cameraxVersion")
	implementation("androidx.camera:camera-camera2:$cameraxVersion")
	implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
	implementation("androidx.camera:camera-view:$cameraxVersion")
	implementation("androidx.camera:camera-video:$cameraxVersion")
	implementation("com.google.guava:guava:33.0.0-android")

	val media3Version = "1.5.1"
	implementation("androidx.media3:media3-exoplayer:$media3Version")
	implementation("androidx.media3:media3-ui:$media3Version")
	implementation("androidx.media3:media3-datasource:$media3Version")
	implementation("androidx.media3:media3-database:$media3Version")
	implementation("androidx.media3:media3-transformer:$media3Version")
	implementation("androidx.media3:media3-effect:$media3Version")

	api(platform(libs.koin.bom))
	api(libs.koin.core)
	api(libs.koin.android)
	implementation(libs.koin.androidx.compose)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)

	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.espresso.core)
}
