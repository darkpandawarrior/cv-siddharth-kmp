import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Pins the generated Res class package so imports are deterministic instead of derived from
// the module name. Accessors only generate once files exist under
// src/composeMain/composeResources/{font,drawable,files}/.
compose.resources {
    generateResClass = org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration.Always
    publicResClass = true
    packageOfResClass = "com.siddharth.cv.shared.resources"
}

kotlin {
    jvm()

    // Library target — :cmp-web owns the executable() + index.html; browser() here just lets
    // the Kotlin/Wasm tooling (npm install, distribution tasks) see this as a JS-consuming module.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "com.siddharth.cv.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    // iosArm64/iosSimulatorArm64 get the Compose UI (below); iosX64 is kept as a bare
    // Kotlin/Native target only — Compose Multiplatform 1.12.0-beta02 publishes no iosX64
    // artifacts (org.jetbrains.compose.{runtime,foundation,ui}), so App() can't run there.
    // ponytail: scaffold-only until Compose ships iosX64, or drop it if Intel sim support
    // isn't actually needed.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // Compile targets only, same reason as iosX64 above — Compose Multiplatform publishes no
    // watchOS artifacts at all. Ready for shared non-UI logic (commonMain); no UI shell.
    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // Pure-Kotlin (no Compose) network + serialization stack — usable from every
            // target, including the UI-less watchOS/iosX64 ones. SSE lives inside
            // ktor-client-core (io.ktor.client.plugins.sse); there is no ktor-client-sse.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        // Compose UI lives here, not in commonMain, so it's only on the classpath of targets
        // Compose Multiplatform actually supports (android, jvm, iosArm64, iosSimulatorArm64).
        val composeMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                // Res.font/Res.drawable — Compose draws through Skia and can't see CSS fonts.
                implementation(compose.components.resources)
                implementation(libs.coil.compose)
                // Mandatory companion: coil-core ships no coil3.network package, so
                // coil-compose alone silently renders nothing for an https:// URL.
                implementation(libs.coil.network.ktor3)
            }
        }
        androidMain.get().dependsOn(composeMain)
        jvmMain.get().dependsOn(composeMain)
        getByName("wasmJsMain").apply {
            dependsOn(composeMain)
            dependencies {
                // The wasmJs Ktor engine (declares SSECapability, streams every
                // ReadableStream chunk). Pulls npm ws — run kotlinWasmUpgradeYarnLock once.
                implementation(libs.ktor.client.js)
            }
        }

        // iosArm64/iosSimulatorArm64 only: the ComposeUIViewController entry point (UIKit API,
        // not available on watchOS/other Apple targets).
        val composeIosMain by creating {
            dependsOn(composeMain)
        }
        getByName("iosArm64Main").dependsOn(composeIosMain)
        getByName("iosSimulatorArm64Main").dependsOn(composeIosMain)
    }
}

// Compose Resources hardcodes commonMain as the home of the generated Res class, but
// components-resources publishes no watchOS/iosX64 artifacts — leaving Res.kt in commonMain
// fails those targets with "Unresolved reference 'org'". Re-point the generated sources at
// composeMain (which owns the resources dependency) and strip the orphaned per-target actuals
// from the non-Compose targets.
// ponytail: this exists only to keep the UI-less scaffold targets alive. Delete the block and
// the targets together if watchOS/iosX64 are ever dropped.
afterEvaluate {
    val nonComposeTargets = listOf(
        "iosX64Main", "watchosArm64Main", "watchosSimulatorArm64Main", "watchosX64Main",
    )
    kotlin.sourceSets.configureEach {
        if (name == "commonMain" || name in nonComposeTargets) {
            kotlin.setSrcDirs(kotlin.srcDirs.filterNot { "compose/resourceGenerator" in it.path })
        }
    }
    kotlin.sourceSets.getByName("composeMain").kotlin.srcDir(tasks.named("generateComposeResClass"))
    kotlin.sourceSets.getByName("composeMain").kotlin
        .srcDir(tasks.named("generateExpectResourceCollectorsForCommonMain"))
}
