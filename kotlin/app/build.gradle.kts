import java.util.*

@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.googleGmsGoogleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
    namespace = "me.floow.app"
    compileSdk = 35

    val localProps = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

    val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE")
    val releaseStorePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")

    defaultConfig {
        applicationId = "me.floow.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

//        val secretsFile = project.rootProject.file("secrets.properties")
//        val secretsProperties = Properties()
//        secretsProperties.load(secretsFile.inputStream())

//        val googleClientId = secretsProperties.getProperty("googleClientId") ?: ""
        buildConfigField(
            type = "String",
            name = "GOOGLE_CLIENT_ID",
            value = "\"291755427997-hjaabnfaa435ikjlsocejeg5p9elraj8.apps.googleusercontent.com\""
        )
        buildConfigField(
            type = "boolean",
            name = "BUMP_ENABLED",
            value = "false"
        )
    }

    flavorDimensions += "data"

    productFlavors {
        create("production") {
            dimension = "data"

            buildConfigField(
				name = "USE_MOCK_DATA",
				value = "false",
                type = "boolean"
			)
            buildConfigField(
                type = "boolean",
                name = "BUMP_ENABLED",
                value = "true"
            )
        }

        create("mock") {
            dimension = "data"
			applicationIdSuffix = ".mock"
            buildConfigField(
                name = "USE_MOCK_DATA",
                value = "true",
                type = "boolean"
            )
            buildConfigField(
                type = "boolean",
                name = "BUMP_ENABLED",
                value = "false"
            )
        }
    }

    signingConfigs {
        create("release") {
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        create("fastDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!releaseStoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        checkDependencies = false
    }
}

androidComponents {
    beforeVariants(
        selector()
            .withBuildType("fastDebug")
            .withFlavor("data" to "production")
    ) { variantBuilder ->
        variantBuilder.hostTests.values.forEach { it.enable = false }
        variantBuilder.deviceTests.values.forEach { it.enable = false }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":core:uikit"))
    implementation(project(":core:domain"))
    implementation(project(":core:api"))
    implementation(project(":core:data"))
    implementation(project(":core:auth"))

    implementation(project(":feature:chatssearch"))
    implementation(project(":feature:comments"))
    implementation(project(":shared"))

    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose)
    // Decommissioned mock module: add("mockImplementation", project(":core:mock"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    add("fastDebugImplementation", libs.ui.tooling)
    add("fastDebugImplementation", libs.ui.test.manifest)
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
