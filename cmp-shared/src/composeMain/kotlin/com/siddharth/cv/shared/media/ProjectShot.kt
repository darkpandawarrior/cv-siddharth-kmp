package com.siddharth.cv.shared.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.siddharth.cv.shared.theme.MediaPanel

/**
 * Installs the app-wide Coil loader. Call once, high in the tree — [com.siddharth.cv.shared.App]
 * does.
 *
 * `coil-network-ktor3` is not optional decoration: `coil-core` ships no `coil3.network` package at
 * all, so without the fetcher registered here an `https://` URL resolves to nothing and renders
 * silently blank — no exception, no log. That silence is the whole reason this function exists
 * rather than relying on Coil's defaults.
 */
@Composable
fun InstallCvImageLoader() {
    setSingletonImageLoaderFactory { context -> cvImageLoader(context) }
}

private fun cvImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory()) }
        .crossfade(true)
        .build()

/**
 * One streamed image from the live site, with a graceful floor.
 *
 * Named for its first caller and deliberately not renamed since: it is one loader for every bitmap
 * on the site, and it takes the URL rather than knowing how to build one. Three callers now build
 * their own — project shots from `CvGallery`, Excelsior scan pages from `pageUrl`, and the fiction
 * plates and portraits from `plateUrl` — and none of them needed a second pipeline.
 *
 * While loading, and if the fetch or decode fails, this falls back to the generated gradient
 * [MediaPanel] the port already used everywhere — so a dead CDN or an offline visitor degrades to
 * the previous look instead of a hole in the layout. `url == null` (a project with no synced media,
 * an anthology record whose art has not been drawn yet) takes the same path.
 *
 * @param url a `.webp` URL on the portfolio origin; never `.avif`, which skiko cannot decode.
 * @param label the `contentDescription`. These are content and not decoration, so it says what the
 *   picture IS, and it doubles as the [MediaPanel] label on the fallback.
 */
@Composable
fun ProjectShot(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (url == null) {
        MediaPanel(seed = label, label = label, modifier = modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = label,
        modifier = modifier,
        contentScale = contentScale,
    ) {
        // Coil 3 exposes `painter.state` as a StateFlow, not a plain value — reading it directly
        // compiles but every `is State.X` check is statically false, which fails silently as a
        // permanently-blank image rather than as an error.
        val state by painter.state.collectAsState()
        when (state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            // Loading and Error share the fallback deliberately: a shimmer that becomes a gradient
            // reads as two states, while the gradient simply resolving into a screenshot reads as one.
            else ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MediaPanel(seed = label, label = label, modifier = Modifier.fillMaxSize())
                }
        }
    }
}
