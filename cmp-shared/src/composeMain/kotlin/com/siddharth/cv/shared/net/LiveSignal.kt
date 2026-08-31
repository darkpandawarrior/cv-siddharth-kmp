package com.siddharth.cv.shared.net

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The two read-only signals the site's footer strip shows: what he last listened to, and what he
 * last pushed. The Kotlin counterpart of `cv-siddharth/src/lib/useLiveSignal.ts`, against the same
 * two endpoints on the same deployment `/api/chat` already talks to.
 *
 * WHY THIS IS NOT `useLiveSignal`: the React hook fetches once and then every 20 seconds, forever,
 * on every page that mounts it. This fetches once, on load. A footer decoration that re-hits an
 * origin three times a minute for the whole time a tab is open is a cost the visitor pays and gets
 * nothing for — the answer changes a few times a day. A poll would be `while (true) { …; delay() }`
 * around [fetchSignal] if the strip ever needs to be live rather than fresh.
 *
 * WHY THIS CAN LEGITIMATELY RETURN NULL FOREVER IN A BROWSER, which is the honest part: unlike
 * `/api/chat`, neither of these endpoints emits an `access-control-allow-origin` header
 * (`github-activity-handler.ts` sets only `content-type` and `cache-control`; the Spotify handler
 * does the same). Same-origin — the React site itself — that costs nothing, and it is why the web
 * original never needed one. This build is served from a different origin, so the browser rejects
 * the response before any status reaches Ktor. Every caller here degrades to showing nothing, and
 * the real fix is one header on the two handlers in the React repo, not more client code. Verified
 * 2026-08-31: both endpoints answer 200 to a direct request and carry no CORS header at all.
 */

/** Same deployment as [com.siddharth.cv.shared.chat.CHAT_ENDPOINT], and hardcoded for that reason. */
private const val SIGNAL_ORIGIN = "https://cv-siddharth.vercel.app"

const val SPOTIFY_ENDPOINT: String = "$SIGNAL_ORIGIN/api/spotify"
const val GITHUB_ACTIVITY_ENDPOINT: String = "$SIGNAL_ORIGIN/api/github-activity"

/**
 * `ignoreUnknownKeys` is doing real work rather than being defensive boilerplate: the DTOs below
 * deliberately carry only the fields the strip renders, so `album`, `playedAt`, `message`, `at` and
 * `type` all arrive on the wire and are dropped here on purpose.
 */
@PublishedApi
internal val signalJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * One client for every live signal, built the same way and for the same reason
 * `chat/ChatClient.kt` builds its own: an [HttpClient] owns a connection pool, and `by lazy`
 * because `HttpClient()` resolves its engine off the classpath and only `wasmJs` has one wired
 * (`ktor-client-js`). On jvm/android/ios that constructor throws, and it must throw where
 * [fetchSignal] can catch it rather than at class-init time.
 *
 * ponytail: this is the second such client in the module. Merging them is one line in ChatClient.kt
 * (`private val chatClient` becomes this), which is not this pass's file to edit.
 */
@PublishedApi
internal val signalClient: HttpClient by lazy { HttpClient() }

/**
 * Fetch and parse one signal, or null.
 *
 * Null rather than a sealed failure type on purpose: there is exactly one caller and it renders the
 * same thing (nothing) for every ending — no engine, no CORS, a 500, malformed JSON. A visitor
 * cannot act on which of those it was, and a footer that explains its own network stack to a
 * recruiter is worse than a footer that quietly isn't there.
 */
suspend inline fun <reified T> fetchSignal(url: String): T? =
    try {
        val response = signalClient.get(url)
        if (!response.status.isSuccess()) null else signalJson.decodeFromString<T>(response.bodyAsText())
    } catch (cancel: CancellationException) {
        // The composable left the composition. Not a failure — never swallow it into "no data".
        throw cancel
    } catch (ignored: Throwable) {
        // Named for detekt's allowlist, and genuinely ignored: no engine on this target, a browser
        // CORS rejection, a 500 and malformed JSON all render the same nothing, and there is no
        // logger in commonMain to tell apart what nobody can act on. The visible consequence is
        // documented on [LiveSignalStrip] instead of guessed at here.
        null
    }

// ---------------------------------------------------------------------------------------------
// Wire DTOs — the fields the strip renders, and no others
// ---------------------------------------------------------------------------------------------

/** `SpotifyTrack` in `api/_lib/spotify-handler.ts`. */
@Serializable
data class SpotifyTrack(
    val track: String = "",
    val artist: String = "",
    val url: String? = null,
)

/**
 * `SpotifyNow`. [connected] is false until the owner finishes the one-time Spotify OAuth setup,
 * which is what the live endpoint returns today.
 *
 * The React footer fills that gap with a dashed placeholder track marked "(preview)" so the
 * widget's shape is visible before real data exists. This build shows nothing instead. A portfolio
 * that a staff engineer is reading for evidence should not render an invented song title, however
 * carefully it is labelled, and the strip has a second half that is real.
 */
@Serializable
data class SpotifyNow(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val track: String? = null,
    val artist: String? = null,
    val url: String? = null,
    val recent: List<SpotifyTrack> = emptyList(),
)

/**
 * One item of `GithubActivity`. [upstream] is the field worth keeping: a commit to someone else's
 * project is a different claim from a commit to your own, and the strip marks it.
 *
 * `type`, `message` and `at` are on the wire and dropped. The web renders them only into an
 * `title=` tooltip, and this strip has no hover surface to put one on.
 */
@Serializable
data class GithubActivityItem(
    val repo: String = "",
    val url: String = "",
    val upstream: Boolean = false,
)

@Serializable
data class GithubActivity(
    val connected: Boolean = false,
    val items: List<GithubActivityItem> = emptyList(),
)

/** How many repositories the strip names. The web slices to the same five. */
const val ACTIVITY_REPOS_SHOWN: Int = 5

/**
 * The feed collapsed to one row per repository, most recent first.
 *
 * The same reduction `NowChip` does, and for the same reason: five pushes to one repo in a morning
 * is one fact, not five. The endpoint returns items newest-first, so the first sighting of a repo
 * is its most recent event and `LinkedHashMap` insertion order carries that through.
 */
fun GithubActivity.byRepo(): List<GithubActivityItem> {
    if (!connected) return emptyList()
    val seen = LinkedHashMap<String, GithubActivityItem>()
    items.forEach { seen.getOrPut(it.repo) { it } }
    return seen.values.toList().take(ACTIVITY_REPOS_SHOWN)
}

/**
 * What the strip's music half shows, or null.
 *
 * Playing wins; otherwise the most recent play. Not connected means null, never a placeholder.
 */
fun SpotifyNow.nowOrLast(): SpotifyTrack? {
    if (!connected) return null
    if (isPlaying && !track.isNullOrBlank()) {
        return SpotifyTrack(track = track, artist = artist.orEmpty(), url = url)
    }
    return recent.firstOrNull { it.track.isNotBlank() }
}

// ponytail: one runnable check rather than a test module — the only logic here is the two
// reductions, and both have a way of going wrong that a compiler cannot see (a repo listed twice,
// a placeholder track surfacing while Spotify is disconnected). Call from any target's main().
internal fun liveSignalSelfCheck() {
    val feed = GithubActivity(
        connected = true,
        items = listOf(
            GithubActivityItem("a/one", "u1"),
            GithubActivityItem("a/one", "u2"),
            GithubActivityItem("b/two", "u3", upstream = true),
            GithubActivityItem("c/three", "u4"),
            GithubActivityItem("d/four", "u5"),
            GithubActivityItem("e/five", "u6"),
            GithubActivityItem("f/six", "u7"),
        ),
    )
    val repos = feed.byRepo()
    check(repos.map { it.repo }.distinct().size == repos.size) { "one row per repository" }
    check(repos.size == ACTIVITY_REPOS_SHOWN) { "capped at the same five the web shows" }
    check(repos.first().url == "u1") { "the first sighting of a repo is its most recent event" }
    check(repos[1].upstream) { "an upstream contribution keeps its mark through the collapse" }
    check(GithubActivity(connected = false, items = feed.items).byRepo().isEmpty()) {
        "connected:false is no feed, not a feed of stale rows"
    }

    check(SpotifyNow().nowOrLast() == null) { "a disconnected endpoint shows nothing, never a preview" }
    check(
        SpotifyNow(connected = true, isPlaying = true, track = "T", artist = "A").nowOrLast()?.track == "T",
    ) { "playing wins" }
    check(
        SpotifyNow(connected = true, recent = listOf(SpotifyTrack("R", "A"))).nowOrLast()?.track == "R",
    ) { "not playing falls back to the most recent play" }
    check(SpotifyNow(connected = true).nowOrLast() == null) { "connected with nothing to report is still nothing" }

    // The DTOs against the bytes the endpoints actually returned on 2026-08-31, captured verbatim
    // and trimmed only in the number of items. This is the half that can be silently wrong: a
    // renamed or mistyped field decodes to a default, the strip shows nothing, and "nothing" is
    // also exactly what a browser CORS rejection looks like. Nobody would ever find it by looking.
    val liveSpotify = signalJson.decodeFromString<SpotifyNow>(
        """{"connected":false,"isPlaying":false,"recent":[]}""",
    )
    check(!liveSpotify.connected && liveSpotify.nowOrLast() == null) {
        "the live Spotify endpoint is not connected yet, and that is a strip with no music half"
    }

    val liveGithub = signalJson.decodeFromString<GithubActivity>(
        """{"connected":true,"items":[""" +
            """{"repo":"darkpandawarrior/cv-siddharth-kmp","type":"create","message":"created branch",""" +
            """"url":"https://github.com/darkpandawarrior/cv-siddharth-kmp","at":"2026-08-31T10:49:04Z",""" +
            """"upstream":false},""" +
            """{"repo":"darkpandawarrior/cv-siddharth","type":"pr","message":"opened a PR",""" +
            """"url":"https://github.com/darkpandawarrior/cv-siddharth","at":"2026-08-31T10:49:14Z",""" +
            """"upstream":false}]}""",
    )
    check(liveGithub.byRepo().map { it.repo.substringAfterLast('/') } == listOf("cv-siddharth-kmp", "cv-siddharth")) {
        "the real feed decodes into the two repo words the strip renders, in the order it returned them"
    }
    check(liveGithub.items.none { it.url.isBlank() }) { "every row keeps somewhere to go" }
}
