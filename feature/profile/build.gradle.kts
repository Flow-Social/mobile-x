plugins {
	alias(libs.plugins.androidLibrary)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeCompiler)
}

android {
	namespace = "me.floow.profile"
	compileSdk = 35

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
	buildFeatures { compose = true }
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	kotlinOptions {
		jvmTarget = "1.8"
	}
}

dependencies {
	implementation(project(":core:uikit"))
	implementation(project(":core:domain"))

	implementation(libs.appcompat)
	implementation(libs.activity.compose)
	implementation(libs.lifecycle.runtime.compose)
	implementation(libs.reorderable)
	implementation(libs.androidx.exifinterface)

	api(platform(libs.koin.bom))
	api(libs.koin.core)
	api(libs.koin.android)

	testImplementation(libs.junit)

	androidTestImplementation(libs.androidx.test.ext.junit)
	androidTestImplementation(libs.espresso.core)
}
