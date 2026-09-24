// external/kmp-toolkit and external/kmp-build-logic are git submodules. A checkout that clones
// this repo without initialising submodules (e.g. actions/checkout without `submodules:
// recursive` — refresh-twin.yml in the sibling cv-siddharth repo does exactly this) leaves both
// directories present but empty. includeBuild then treats each as a build with zero projects
// instead of failing at checkout time, so the first symptom is a confusing
// "Project with path ':network' not found in build ':kmp-toolkit'" deep in dependency
// resolution. Self-heal here, once, rather than rely on every caller remembering the flag.
if (!File(rootDir, "external/kmp-toolkit/settings.gradle.kts").exists() ||
    !File(rootDir, "external/kmp-build-logic/settings.gradle.kts").exists()
) {
    ProcessBuilder("git", "submodule", "update", "--init", "--recursive")
        .directory(rootDir)
        .inheritIO()
        .start()
        .waitFor()
}

pluginManagement {
    // kmp-toolkit's own settings.gradle.kts does includeBuild("../kmp-build-logic") — a relative
    // path that only resolves if this repo also vendors kmp-build-logic as a sibling of
    // external/kmp-toolkit (see includeBuild("external/kmp-toolkit") below) and includes it here
    // too. Gradle then collapses both references into the same included build. Mirrors Gaddi/Doori.
    includeBuild("external/kmp-build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// fork.project.name lets customizer.sh rename the whole project in one place.
rootProject.name = providers.gradleProperty("fork.project.name").getOrElse("cv-siddharth-kmp")

// kmp-toolkit monorepo — vendored as one submodule, wired via composite build so its coordinates
// resolve to the local checkout instead of remote repos (nothing under com.siddharth.kmp is
// published anywhere). Three modules are substituted:
// :network for a real per-platform HttpClientEngine (this app had none outside wasmJs before),
// :result for the shared AiResult<T>/AiFailure vocabulary.
// :llm-chat for FitCheckScreen.kt's JD analyzer AND ChatClient.kt's ordinary chat, both via
// HttpChatProvider. Ordinary chat only became safe once llm-chat#52 shipped `explicitNulls =
// false` on HttpChatProvider's request Json — before that, an unset `mode` serialized as an
// explicit `"mode": null`, which this app's production endpoint 400s on (validateRequest in
// chat-handler.ts rejects any mode that isn't undefined/"compose"/"jd", explicit null included).
// See ChatClient.kt's file doc for what the swap still can't carry (route, per-status message
// fidelity, mid-stream-cutoff detection) — each a HttpChatConfig/AiChunk gap in the toolkit, not
// a wire-shape mismatch left here.
includeBuild("external/kmp-toolkit") {
    dependencySubstitution {
        substitute(module("com.siddharth.kmp:network")).using(project(":network"))
        substitute(module("com.siddharth.kmp:result")).using(project(":result"))
        substitute(module("com.siddharth.kmp:llm-chat")).using(project(":llm-chat"))
    }
}

include(":cmp-shared")
include(":cmp-android")
include(":cmp-desktop")
include(":cmp-web")
// iOS umbrella framework. cmp-ios/iosApp.xcodeproj links ComposeApp.framework produced
// here; without this include the xcodeproj referenced a module the build did not know.
include(":cmp-ios")
