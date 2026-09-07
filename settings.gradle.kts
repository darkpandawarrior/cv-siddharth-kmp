pluginManagement {
    // kmp-toolkit's own settings.gradle.kts does includeBuild("../kmp-build-logic") — a relative
    // path that only resolves if this repo also vendors kmp-build-logic as a sibling of
    // external/kmp-toolkit (see includeBuild("external/kmp-toolkit") below) and includes it here
    // too. Gradle then collapses both references into the same included build. Mirrors Kursi/Mileway.
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
// :llm-chat for FitCheckScreen.kt's JD analyzer ONLY — its HttpChatProvider always serializes a
// `mode` key (null when unset), and this app's production endpoint 400s on exactly that
// (validateRequest in chat-handler.ts rejects any mode that isn't undefined/"compose"/"jd",
// explicit null included). ChatClient.kt's ordinary chat traffic still bypasses HttpChatProvider
// for that reason — see its own comment — but the JD path always sends the literal string "jd",
// never null, so the same provider that 400s plain chat is exactly right here.
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
