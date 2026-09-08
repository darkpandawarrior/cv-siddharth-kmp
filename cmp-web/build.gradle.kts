import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "cmpWeb"
        browser {
            commonWebpackConfig {
                outputFileName = "cmpWeb.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":cmp-shared"))
            implementation(compose.ui)
            // configureWebResources() in Main.kt — cmp-shared depends on this as `implementation`,
            // so it isn't visible here transitively.
            implementation(compose.components.resources)
        }
    }
}
