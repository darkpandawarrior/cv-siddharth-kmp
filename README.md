# cv-siddharth-kmp

My portfolio, written a second time in Kotlin. One `commonMain` of Compose Multiplatform code draws the same site as [siddharth-pandalai.vercel.app](https://siddharth-pandalai.vercel.app/) to the web (Kotlin/Wasm), the desktop, Android and iOS.

The React site is still the real one. This repo exists to find out how much of it Compose can reproduce and, more usefully, where it cannot. Every gap is written down instead of hidden, so if you are weighing Compose for the web, the useful part is [docs/port-notes.md](docs/port-notes.md).

It started as a fork of [kmp-app-template](https://github.com/darkpandawarrior/kmp-app-template) and pulls in [kmp-toolkit](https://github.com/darkpandawarrior/kmp-toolkit) and [kmp-build-logic](https://github.com/darkpandawarrior/kmp-build-logic) as git submodules under `external/`.

## Where the port stands

Measured against the React repo (`cv-siddharth`) on 2026-11-06.

| | Count | How I counted |
|---|---|---|
| Route files on the React site | 26 | `ls src/routes/*.tsx`, minus `__root` and the `$` catch-all |
| Route kinds here | 20 | the sealed `Route` interface in `Nav.kt` |
| Fixed-path routes | 18 | `staticRoutes` in `Nav.kt` |
| Not ported | 6 | `/blueprint`, `/globe`, `/lanes`, `/playground`, `/pulse`, `/time-machine` |

Two of the six are walls rather than backlog. `/blueprint` is tldraw plus three.js, and Compose paints into a single canvas, so a DOM or WebGL widget can only be laid over it, never inside it. `/pulse` reads a shared counter from `@playhtml/react`, which has no Kotlin client. I have not written up the other four here yet.

Android and iOS only compile. I have not launched either one on a device or simulator, so anything that depends on them (the shader fallback on Android, `PrintManager`, iOS printing) is reasoned from source, not observed. The home page, project pages, terminal and the first routes were checked in a real browser in the earlier passes, and so was the desktop build, which found a stale window title and a stale colour palette that no green build had caught. The thirteen routes added later (/hire, /shipped, /weeb, /ops, /loopdown, /ink, /anthology, /canon, /making, /read, /excelsior, /chess, /map) compile and prerender but, per the port notes, had not been opened in a browser.

## Run it

Needs JDK 25 (CI uses Corretto 25) and, for iOS, Xcode. Clone with submodules or the build cannot find the toolkit:

```bash
git clone --recurse-submodules https://github.com/darkpandawarrior/cv-siddharth-kmp
cd cv-siddharth-kmp
./gradlew :cmp-web:wasmJsBrowserDevelopmentRun   # web, localhost:8080
./gradlew :cmp-desktop:run                       # desktop window
./gradlew :cmp-android:installDebug              # Android, with a device attached
./gradlew :cmp-web:wasmJsBrowserDistribution     # production web bundle
./gradlew :cmp-shared:prerenderSite              # bundle plus one static HTML page per route
open cmp-ios/iosApp.xcodeproj                    # iOS, arm64 and simulator-arm64 only
```

`prerenderSite` is the task you deploy. It runs the distribution, then writes an `index.html` for each route, plus `sitemap.xml` and `robots.txt`, from the same Kotlin data the app draws. The crawlable pages and the canvas cannot disagree without someone editing `data/`. Pass `-Pprerender.origin=https://...` (or set `CV_SITE_ORIGIN`) when deploying anywhere but the default origin, because the canonical links and sitemap depend on it.

CI runs `./gradlew assemble check` on a `macos-14` runner, since the iOS targets need Xcode. The last completed green run on `main` was 2026-10-28. The run for the 2026-11-06 push was cancelled by the workflow's concurrency rule, so it says nothing either way.

## Toolchain

It tracks the newest published versions, pre-releases included. Do not trust a number typed into a README for this; read the file:

```bash
grep -E '^(kotlin|compose-multiplatform|agp|ktor|coil) =' gradle/libs.versions.toml
grep -o 'gradle-[0-9.a-z-]*-bin' gradle/wrapper/gradle-wrapper.properties
```

Today that prints Kotlin 2.5.0-Beta2, Compose Multiplatform 1.13.0-beta02, AGP 9.6.0-alpha03, Ktor 3.6.0, Coil 3.6.3 and Gradle 9.9.0-rc-1, with `compileSdk` 37 and `minSdk` 26.

Dependencies beyond Compose itself are coroutines, kotlinx-serialization, Ktor's client and Coil. There is no navigation library, no DI framework, no diagram library and no markdown parser. Routing, the Mermaid layout, the SkSL background shader, the SSE reader behind the chat and the chat's markdown are written on Compose and Ktor primitives.

## Layout

```
cmp-shared/    the app: screens, theme, routing, the prerenderer (44,894 lines of Kotlin)
  data/generated/   9,261 of those lines, emitted from the React repo, not written by hand
cmp-web/       Wasm entry point and webpack config
cmp-desktop/   JVM window
cmp-android/   Android app
cmp-ios/       Xcode project
external/      kmp-toolkit and kmp-build-logic (submodules)
docs/          port-notes.md and DECISIONS.md
```

The design system is `CvTheme` and `CvComponents.kt` in `cmp-shared`. It deliberately does not use `MaterialTheme.colorScheme`; [DESIGN.md](DESIGN.md) says why and is the file to read before changing any UI.

## Where to read more

- [docs/port-notes.md](docs/port-notes.md): what ported with no loss, what degraded, what was dropped on purpose and why, two cost estimates I got wrong, and the bundle-size numbers. It is dated, and its header lists what has gone stale.
- [docs/DECISIONS.md](docs/DECISIONS.md): settled choices, one line each.
