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
| **Real screenshots** | Coil 3.5.0 + `coil-network-ktor3` + Ktor 3.5.1 on wasmJs, streaming the live site's CDN. Falls back to the generated gradient while loading or on failure. |
| **Real typefaces** | Space Grotesk + DM Mono vendored through `compose.components.resources`. Skia never sees CSS fonts, so the bytes have to ship — `FontFamily.SansSerif` was a placeholder, not a choice. |
| **Real URLs + deep links** | `history.pushState` / `popstate` via `kotlinx-browser`. `/resume`, `/terminal`, `/project/<slug>` are shareable, refreshable, and Back works. |
| **Crawlable fallback** | Compose mounts into `#compose`, so a sibling `#seo` block of semantic HTML survives boot and serves crawlers, JS-off readers, and wasm-incapable browsers. |

### Degraded — ported, but not at parity

| Surface | What was lost, and why |
|---|---|
| Résumé | Layout ports fine under a light theme override; **`window.print()` has no wasm equivalent** — a canvas gives the print engine nothing to lay out. Links out to the React site's printable résumé rather than shipping a dead button. A hidden-iframe print path is proven and queued. |
| Mermaid diagrams | No Kotlin renderer. Shows raw diagram source in a collapsed mono card. Parsing the flowchart subset and laying it out on Canvas is tractable and queued. |
| Ambient background | One seeded `Canvas` starfield + radial blooms. The three.js depth and postprocessing are gone. GPU fragment shaders via Skiko `RuntimeEffect` are verified available and queued. |
| Footer | Static sitemap; the live Spotify / GitHub polling strip is now unblocked by Ktor but not yet wired. |
| Accessibility | Weaker than the DOM original, but **not** the "synthesised bridge" first assumed: CMP 1.12 emits a live `#cmp_a11y_root` DOM tree mirroring the layout with correct bounding boxes. It sits inside a shadow root, so assistive tech reaches it and crawlers generally do not. The committed axe suite still has no wasm equivalent. |
| Glass / glow | No `backdrop-filter`; every `box-shadow` glow becomes a hand-drawn radial gradient. |

### Dropped — deliberately, with reasons

| Surface | Why |
|---|---|
| Blueprint3D (tldraw + three.js) | **Impossible as composited UI.** Compose paints into one canvas inside a shadow root; there is no Compose-side DOM tree, so a DOM/WebGL widget can only be *overlaid* (owning all input in its rect), never laid out inside the Compose tree. Layering a second canvas *behind* it was tested and refuted — Compose clears its container and the app's own background occludes it. |
| Server-side rendering of Compose UI | **No library exists and none is coming.** Compose on web is Skia-on-canvas: there is no DOM-emitting renderer, no `renderToString`. The `#seo` shell is the answer, not SSR. |
| Typed WebGPU | **No library exists.** `kotlin-browser`'s `web.gpu` package ships exactly `GPUCanvasContext` and `GPUCanvasConfiguration` — no `GPUDevice`, so it is not a WebGPU path at all. WebGL2 bindings do exist. |
| AVIF images | **Platform limitation.** `strings` over the shipped skiko wasm finds jpeg, png, gif, ico, webp, wbmp — no AVIF decoder. Sidestepped rather than suffered: every one of the site's 166 avif assets has a webp sibling, so the gallery uses `.webp`. |
| Floating AI chat | Deferred, no longer blocked — Ktor, serialization and the SSE plugin are now on the classpath and the `import.meta` webpack trap is solved (see `index.html`). Needs the client, the SSE parser and the `[[directive]]` renderer. The Vercel endpoint is untouched and still live. |
| Compose Playground (`/compose`) | **Deferred, and the strongest argument for the whole exercise** — a real interpreter rendering real composables instead of styled `div`s. 470-line interpreter port plus a syntax-highlighting editor. Flagship v2 item. |
| Lab bench (9 experiments), `/map`, `/forge`, `/playground`, ⌘K palette | Deferred on scope only — 7 of 9 labs are pure Canvas + frame-loop work that ports cleanly. SignalLab's Leaflet map never ports; its engine does. |

## The honest cost

Measured, not estimated — `brotli -q 11` over the actual production distribution. Quote the brotli
column: Vercel and every other edge host compress `application/wasm` automatically, so the raw number
is not what anyone downloads.

| file | raw | gzip -9 | **brotli** |
|---|---:|---:|---:|
| skiko runtime | 8,640,316 | 3,324,704 | **2,618,182** |
| app wasm (the whole portfolio) | 3,640,914 | 1,195,648 | **907,765** |
| JS glue | 538,048 | 100,764 | **82,170** |
| **total** | 12,819,278 | 4,621,116 | **3,608,117** |

| | React site | this build |
|---|---|---|
| Over the wire, first paint | ~hundreds of KB | **3.6 MB brotli** |
| Crawlable | fully | `#seo` shell only — the Compose UI is a canvas |
| Routes | 13 | 4, with real URLs and deep links |

An empty Compose Multiplatform hello-world already costs **2.62 MB brotli** here; the entire
portfolio — every screen, all the content, real fonts, Coil and Ktor — adds **908 KB** on top. That
ratio is the finding worth keeping: **the framework floor dominates and the content
is nearly free**, which is exactly backwards from the web, and exactly why this belongs at a sub-path
as a demo rather than at the apex domain.
