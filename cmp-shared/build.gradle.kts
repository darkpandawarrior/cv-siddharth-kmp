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
        // CMP-4906: without a declared executable the Compose plugin's Skiko-runtime check fails
        // `build` outright, because Compose UI cannot load its renderer from a bare klib. This gate
        // was permanently red before 2026-08-27 - it never told anyone anything, it just failed.
        binaries.executable()
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
        // Skiko-backed targets. `org.jetbrains.skia.*` (RuntimeEffect / RuntimeShaderBuilder —
        // GPU fragment shaders) exists on every Compose target EXCEPT Android, which renders
        // through the platform's own pipeline and would need AGSL instead. Android therefore
        // hangs off composeMain directly and supplies its own actual; everyone else shares one.
        val skikoMain by creating { dependsOn(composeMain) }

        androidMain.get().dependsOn(composeMain)
        jvmMain.get().dependsOn(skikoMain)
        getByName("wasmJsMain").apply {
            dependsOn(skikoMain)
            dependencies {
                // The wasmJs Ktor engine (declares SSECapability, streams every
                // ReadableStream chunk). Pulls npm ws — run kotlinWasmUpgradeYarnLock once.
                implementation(libs.ktor.client.js)
            }
        }

        // iosArm64/iosSimulatorArm64 only: the ComposeUIViewController entry point (UIKit API,
        // not available on watchOS/other Apple targets).
        val composeIosMain by creating {
            dependsOn(skikoMain)
        }
        getByName("iosArm64Main").dependsOn(composeIosMain)
        getByName("iosSimulatorArm64Main").dependsOn(composeIosMain)
    }
}

// ---------------------------------------------------------------------------------------------
// prerenderSite — the static crawlable layer, generated from the same Kotlin data the app renders.
//
// It writes INTO the wasm distribution on purpose. Every generated page carries the same
// `#compose` mount point and `<script type="module" src="/cmpWeb.js">` as the hand-written
// template, so the app boots over a prerendered page exactly as it boots over `index.html` — the
// generated root page is a strict superset of the template it replaces. Emitting somewhere else
// would mean a second copy step before deploy, which is one more thing to forget.
//
// Hence `dependsOn` rather than `mustRunAfter`: the distribution task writes `index.html` into the
// same directory, so running it afterwards would silently undo this. Making the ordering a hard
// dependency means `./gradlew prerenderSite` alone produces the complete deployable directory.
//
// Classpath is `jvmJar` + the `jvmRuntimeClasspath` configuration rather than the KotlinCompilation
// object: both are stable public Gradle handles, and `files(TaskProvider)` carries the build
// dependency, so no explicit dependsOn on the compile is needed.
// ponytail: the origin defaults to the Vercel URL baked into Prerender.kt. Pass -Pprerender.origin
// (or CV_SITE_ORIGIN) when deploying anywhere else — a wrong <link rel="canonical"> is worse than
// none, so make this a required property the day this is deployed from CI.
val prerenderSite by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Generates static per-route HTML + sitemap.xml + robots.txt into the wasm distribution."
    dependsOn(":cmp-web:wasmJsBrowserDistribution")

    mainClass.set("com.siddharth.cv.shared.prerender.Prerender")
    classpath = files(tasks.named("jvmJar"), configurations.named("jvmRuntimeClasspath"))

    val outDir = providers.gradleProperty("prerender.out")
        .getOrElse("${rootProject.projectDir}/cmp-web/build/dist/wasmJs/productionExecutable")
    val origin = providers.gradleProperty("prerender.origin").orNull
    argumentProviders.add(CommandLineArgumentProvider { listOfNotNull(outDir, origin) })
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
