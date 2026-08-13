// Serve-path for the built bundle.
//
// This build is embedded on the React site at /portfolio-app/, not at an origin root. Compose
// resolves composeResources/ (fonts, images) relative to the document, so without a publicPath the
// five bundled fonts 404 and the app renders with fallbacks.
//
// Do NOT "fix" this with <base href> in index.html instead — that was tried, and it repoints script
// resolution too, so the bundle itself never loads and the page renders blank. The failure is
// silent in the worst way: a 404 checker reports ZERO failures, because nothing is requested at all.
//
// Kept overridable so a local `wasmJsBrowserRun` at the origin root still works:
//   ./gradlew :cmp-web:wasmJsBrowserDistribution -Pcv.publicPath=/
config.output = config.output || {};
config.output.publicPath = process.env.CV_PUBLIC_PATH || "/portfolio-app/";
