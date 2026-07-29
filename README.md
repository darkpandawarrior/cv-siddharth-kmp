<div align="center">

### cv-siddharth-kmp

**A [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) port of
[cv-siddharth.vercel.app](https://cv-siddharth.vercel.app/)** — one Kotlin `commonMain` rendering the
same portfolio to web (Kotlin/Wasm), desktop, Android and iOS.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20--Beta1-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12.0--beta02-4285F4?logo=jetpackcompose&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.4.0--alpha04-3DDC84?logo=android&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-Web%20%7C%20Desktop%20%7C%20Android%20%7C%20iOS-3DDC84)
![Gradle](https://img.shields.io/badge/Gradle-9.7--milestone--2-02303A?logo=gradle&logoColor=white)

**[Toolchain](#toolchain)** · **[Run it](#run-it)** · **[What ported](#what-ported)** · **[The honest cost](#the-honest-cost)**

</div>

---

> **This is an experiment, not a replacement.** The React site remains the canonical, indexed,
> recruiter-facing portfolio. This build measures how much of it Compose Multiplatform can reproduce
> and — just as usefully — exactly where it cannot. Read [What ported](#what-ported) before
> drawing conclusions from it.

Forked from [`kmp-app-template`](https://github.com/darkpandawarrior/kmp-app-template).
UI code is ~5.7k LOC in `:cmp-shared`.

## Toolchain

Deliberately bleeding edge — every version is the newest published, pre-release included.

| | |
|---|---|
| Kotlin | `2.4.20-Beta1` |
| Compose Multiplatform | `1.12.0-beta02` |
| Android Gradle Plugin | `9.4.0-alpha04` |
| Gradle | `9.7.0-milestone-2` |
| compileSdk / minSdk | `37` / `26` |

Dependencies beyond `compose.{runtime,foundation,material3,ui}` and `kotlinx-coroutines-core`: **none.**
No image loader, no nav library, no DI, no serialization, no HTTP client — everything here is built
on Compose primitives.

## Run it

```bash
./gradlew :cmp-web:wasmJsBrowserDevelopmentRun   # web — serves on localhost:8080
./gradlew :cmp-desktop:run                       # desktop JVM window
./gradlew :cmp-android:installDebug              # Android
./gradlew :cmp-web:wasmJsBrowserDistribution     # production web bundle
open cmp-ios/iosApp.xcodeproj                    # iOS (arm64 + simulator-arm64 only)
```

## What ported

Four routes ship: **home**, **résumé**, **project detail**, **terminal**. Roughly 33 of the source
site's 51 surfaces port natively; the rest degrade or were dropped for the reasons below.

### Native — no meaningful loss

| Surface | How |
|---|---|
| Nav shell + scroll-spy | Sticky `Row`; active section derived from a hoisted `LazyListState` in `derivedStateOf` |
| Hero + shimmer headline | `TextStyle(brush = Brush.linearGradient)` on an infinite float — cleaner than the CSS `background-clip:text` original |
| Tilt phone | `graphicsLayer` rotationX/Y driven by `pointerInput` |
| Animated metrics band | `AnimatedCounter`, `drawArc` gauge, `PathMeasure` sparkline reveal (an exact analogue of `stroke-dashoffset`) |
| Case studies + expanders | `AnimatedVisibility` |
| Experience timeline | Gradient spine + glow dots |
| Skills chips | `FlowRow` |
| Contact + copy-email | Clipboard write + 2 s `AnimatedContent` confirmation |
| Project detail | Full port including per-project theming — `CompositionLocal` shadowing is the precise analogue of the CSS custom-property cascade |
| **Terminal** | Full port: ~25 commands over the compiled-in data, history, Tab completion, CRT chrome. Best-value surface in the port. |

### Degraded — ported, but not at parity

| Surface | What was lost, and why |
|---|---|
| Projects grid | Generated gradient panels instead of authored hero art — 196 gallery rasters on top of a bundle already carrying ~3 MB of skiko isn't defensible. First item for v2. |
| Résumé | Layout ports fine under a light theme override; **`window.print()` has no wasm equivalent** — a canvas gives the print engine nothing to lay out. Links out to the React site's printable résumé rather than shipping a dead button. |
| Mermaid diagrams | No Kotlin renderer. Shows raw diagram source in a collapsed mono card. |
| Ambient background | One seeded `Canvas` starfield + radial blooms. The three.js depth and postprocessing are gone. |
| Footer | Static sitemap; the live Spotify / GitHub polling strip needs an HTTP client. |
| Accessibility | CMP-web a11y is a synthesised bridge, materially weaker than DOM semantics, and the committed axe suite has no wasm equivalent. Mitigated in-port: real focus indicators, `semantics {}` on icon-only controls, reduced-motion honoured at every animation source. |
| Glass / glow | No `backdrop-filter`; every `box-shadow` glow becomes a hand-drawn radial gradient. |

### Dropped — deliberately, with reasons

| Surface | Why |
|---|---|
| Blueprint3D (tldraw + three.js) | **Impossible.** No scene graph, no OrbitControls, no HTML-in-3D, no postprocessing, no tldraw equivalent at any level. Substituting honestly beats faking it badly. |
| SEO / SSR / JSON-LD / sitemap / link previews | **Impossible.** A wasmJs app is one `<canvas>` — nothing crawlable, no per-route `<head>`, no find-in-page, multi-MB before first paint. Not papered over: `index.html` carries `noindex` plus a canonical link back to the React site. |
| Floating AI chat | Costs a Ktor wasm engine, serialization, an SSE parser, the `[[directive]]` card renderer and a `kotlinx-io` webpack workaround — five moving parts for a low-priority feature. The Vercel endpoint is untouched and still live. |
| Compose Playground (`/compose`) | **Deferred, and the strongest argument for the whole exercise** — a real interpreter rendering real composables instead of styled `div`s. 470-line interpreter port plus a syntax-highlighting editor. Flagship v2 item. |
| Lab bench (9 experiments), `/map`, `/forge`, `/playground`, ⌘K palette | Deferred on scope only — 7 of 9 labs are pure Canvas + frame-loop work that ports cleanly. SignalLab's Leaflet map never ports; its engine does. |

## The honest cost

| | React site | this build |
|---|---|---|
| Payload before first paint | ~hundreds of KB | **12.3 MB** — 8.6 MB skiko + 3.2 MB app wasm + 529 KB JS |
| Crawlable | yes | **no** — one canvas |
| Routes | 13 | 4 |

An empty Compose Multiplatform hello-world already costs **10.1 MB** here; the entire portfolio added
only 1.2 MB on top. That ratio is the real finding — **the framework floor dominates and the content
is nearly free**, which is exactly backwards from the web, and exactly why this belongs at a sub-path
as a demo rather than at the apex domain.
