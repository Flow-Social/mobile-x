import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.googleGmsGoogleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
}
true // Needed to make the Suppress annotation work for the plugins block

allprojects {
    plugins.withType<NodeJsPlugin> {
        extensions.configure(NodeJsEnvSpec::class.java) {
            download.set(false)
            command.set("/opt/homebrew/bin/node")
        }
    }

    plugins.withType<WasmNodeJsPlugin> {
        extensions.configure(WasmNodeJsEnvSpec::class.java) {
            download.set(false)
            command.set("/opt/homebrew/bin/node")
        }
    }
}

rootProject.plugins.withType<YarnPlugin> {
    rootProject.extensions.configure(YarnRootEnvSpec::class.java) {
        download.set(false)
        command.set("/opt/homebrew/bin/yarn")
    }
}

rootProject.plugins.withType<WasmNodeJsRootPlugin> {
    rootProject.extensions.configure(WasmNodeJsEnvSpec::class.java) {
        download.set(false)
        command.set("/opt/homebrew/bin/node")
    }
}

rootProject.plugins.withType<WasmYarnPlugin> {
    rootProject.extensions.configure(WasmYarnRootEnvSpec::class.java) {
        download.set(false)
        command.set("/opt/homebrew/bin/yarn")
    }
}
