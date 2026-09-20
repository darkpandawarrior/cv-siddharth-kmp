plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * iOS umbrella module: produces the single `ComposeApp.framework` that cmp-ios/iosApp.xcodeproj
 * links against.
 *
 * This module sits ABOVE :cmp-shared and must never be depended upon by anything else (that would
 * introduce a cycle). It mirrors Kursi's :cmp-ios exactly — the only repo in the family whose iOS
 * app builds green end to end. Before this module existed, `include(":cmp-ios")` was absent from
 * settings.gradle.kts and the Xcode project reached directly into :cmp-shared's own framework
 * binary, so `iosApp.xcodeproj` was not wired to any Gradle module the build actually knew about.
 *
 * Only iosArm64 + iosSimulatorArm64 are declared here. :cmp-shared additionally carries iosX64 and
 * three watchOS targets, but Compose Multiplatform publishes no artifacts for those, so they can
 * never host `App()` and therefore never belong in a UI framework.
 *
 * Exported API surfaced to Swift:
 *  - :cmp-shared → App(), reached through the viewController() entry point defined in iosMain.
 */
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // Matches `import ComposeApp` in cmp-ios/iosApp/ContentView.swift.
            baseName = "ComposeApp"
            isStatic = true
            // export() requires api() in the source set below to surface the module's public API.
            export(project(":cmp-shared"))
        }
    }

    // gradle.properties sets kotlin.mpp.applyDefaultHierarchyTemplate=false, because :cmp-shared
    // hand-wires composeMain/skikoMain/composeIosMain around targets Compose Multiplatform does
    // not publish for. That property is project-wide, so without asking for the template back here
    // there is no iosMain at all and the two ios*Main source sets would each need their own copy
    // of these dependencies. This module has only the two Compose-capable iOS targets, so the
    // stock template fits it exactly.
    applyDefaultHierarchyTemplate()

    sourceSets.getByName("iosMain").dependencies {
        // api(...) is required for export(...) above to surface :cmp-shared's public API.
        api(project(":cmp-shared"))
        // compose.ui exposes ComposeUIViewController on iOS targets.
        implementation(compose.ui)
    }
}
