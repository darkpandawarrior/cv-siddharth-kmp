@file:OptIn(ExperimentalLayoutApi::class)

package com.siddharth.cv.shared.shipped

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.siddharth.cv.shared.LocalNav
import com.siddharth.cv.shared.CvNavState
import com.siddharth.cv.shared.Route
import com.siddharth.cv.shared.data.generated.LiveClient
import com.siddharth.cv.shared.data.generated.PastClient
import com.siddharth.cv.shared.data.generated.fleetStats
import com.siddharth.cv.shared.data.generated.lastShipped
import com.siddharth.cv.shared.data.generated.liveClients
import com.siddharth.cv.shared.data.generated.pastClients
import com.siddharth.cv.shared.data.generated.storeApps
import com.siddharth.cv.shared.data.generated.storeGeneratedAt
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import kotlin.math.round

/**
 * Port of cv-siddharth/src/Shipped.tsx and the three components it delegates to (ShippedClient,
 * ShippedTile, ShippedTimeline).
 *
 * EVERY NUMBER ON THIS PAGE IS DERIVED, none is typed. `reached`, the set-up-by-him count, the
 * company counts and the install floor are all computed from `CvStoreData.kt`, which is generated
 * from store.ts, which is generated from the store crawl. A hand-typed total here would be a fourth
 * copy that no generator refreshes, which is exactly the failure this port exists not to repeat.
 *
 * Three deliberate degradations, because the alternative would be drawing a lie:
 *  - NO APP ICONS. `client.icon` is a path to a .webp on the React site's origin, and every bitmap
 *    on wasmJs is a network round trip this port does not make. Each client gets the monogram tile
 *    that ShippedTile.tsx itself falls back to, painted in the client's real brand colour, so the
 *    wall still reads as a wall of different companies, which is the claim the surface makes.
 *  - The timeline's pulled segment is a hairline outline rather than a dashed one. Compose has no
 *    dashed border modifier and a PathEffect for a legend swatch is not worth the code.
 *  - Ratings read "4.5/5", not "4.5" beside a star. U+2605 is in neither vendored font cut (Space
 *    Grotesk, DM Mono), and Skia paints a missing glyph as a tofu box rather than falling back to a
 *    system font, so typing the star would put a rectangle next to every number on the page.
 */
@Composable
fun ShippedScreen(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    val live = remember { liveClients.map { it.toShelf() } }
    val past = remember { pastClients.map { it.toShelf() } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The `sm:grid-cols-2 lg:grid-cols-3` breakpoints, resolved once and used to chunk the
        // corpus into rows. Chunking rather than a LazyVerticalGrid keeps ONE scroll container for
        // a page a hundred-odd client cards long: the grid would have to nest inside this column
        // and either lose its laziness to an infinite height constraint or fight it for the scroll.
        val columns = when {
            maxWidth >= WideBreakpoint -> 3
            maxWidth >= MediumBreakpoint -> 2
            else -> 1
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ShelfHeader(nav) }
            item { TheThree(uri) }
            item { WhyTheseAndNotMore() }

            item {
                Section(eyebrow = "// live", title = "On the store now: ${live.size} companies") {
                    BasicText(
                        text =
                            "Grouped by the company that ships them, because nearly every client " +
                                "put out a pair: one app for riders, one for drivers. That is " +
                                "${fleetStats.live} listings in all, and every one is a link you " +
                                "can open. The rating, install count and update date come from the " +
                                "listing itself; the coloured edge is the colour that client's own " +
                                "app was themed in.",
                        style = cvType.bodySmall,
                    )
                }
            }
            // Stable key per row, so a resize or a scroll back does not recompose the whole wall.
            items(live.chunked(columns), key = { it.first().key }) { row ->
                ClientRow(row, columns, past = false, uri = uri)
            }

            item {
                Section(eyebrow = "// pulled", title = "Pulled since: ${past.size} companies") {
                    BasicText(
                        text =
                            "Every one of these was on Google Play and is not any more: clients " +
                                "that closed, moved on, or were bought. Google keeps no record of " +
                                "an app once it comes down, so the only reason these can be named " +
                                "is that the Internet Archive saved the page while it was up. Each " +
                                "link opens the copy it saved.",
                        style = cvType.bodySmall,
                    )
                }
            }
            items(past.chunked(columns), key = { "past-${it.first().key}" }) { row ->
                ClientRow(row, columns, past = true, uri = uri)
            }

            item { HowIKnow() }
            item { ShelfFootnote() }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Measure + shared section frame, matching ProjectDetailScreen
// -------------------------------------------------------------------------------------------

private val WideBreakpoint: Dp = 900.dp
private val MediumBreakpoint: Dp = 620.dp

/**
 * The web page is `max-w-6xl`; this port holds every surface to [CvContentMaxWidth] so a reader
 * moving between screens never sees the measure jump.
 */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
private fun Section(eyebrow: String, title: String, content: @Composable () -> Unit) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = CvSectionGap)) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(10.dp))
            SectionHeading(title)
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

// -------------------------------------------------------------------------------------------
// Derived totals. Read these, never a literal.
// -------------------------------------------------------------------------------------------

/** Everything that ever reached the store: what is still on it plus what was taken down. */
private val reached: Int = fleetStats.live + fleetStats.delisted

/**
 * `fleetStats.setUpByHim` counts the LIVE listings only (gen-store.mjs derives it from `liveKept`),
 * so the pulled ones are counted here rather than assumed into it.
 */
private val setUpByHimTotal: Int =
    fleetStats.setUpByHim + pastClients.sumOf { c -> c.apps.count { it.setUpByHim } }

// -------------------------------------------------------------------------------------------
// 1. Header and the four totals
// -------------------------------------------------------------------------------------------

@Composable
private fun ShelfHeader(nav: CvNavState) {
    Reveal {
        Column(Modifier.pageMeasure().padding(top = 32.dp)) {
            GhostButton(text = "Back to portfolio", onClick = { nav.go(Route.Home) })
            Spacer(Modifier.height(28.dp))
            SectionEyebrow("// the shelf")
            Spacer(Modifier.height(10.dp))
            BasicText(text = "Everything that shipped", style = cvType.hero)
            Spacer(Modifier.height(18.dp))
            BasicText(
                text =
                    "${storeApps.size} of these I worked on directly, at Dice and at Jugnoo. The " +
                        "other ${reached - storeApps.size} are clients of the white-label platform " +
                        "I worked on at Jugnoo, each one a separate build of the same two " +
                        "codebases, shipped under its own company on Google Play.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.body,
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                text =
                    "I never had a list of them, and nobody there did. This page is that list, put " +
                        "back together and then checked one store listing at a time, including " +
                        "${fleetStats.delisted} that have since been taken down and can only be " +
                        "shown at all because the Internet Archive kept a copy of the page.",
                modifier = Modifier.widthIn(max = 680.dp),
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(32.dp))
            FleetTotals()
        }
    }
}

@Composable
private fun FleetTotals() {
    val colors = cvColors
    val totals =
        listOf(
            reached.toString() to "apps reached the store",
            fleetStats.live.toString() to "still on it today",
            fleetStats.developers.toString() to "companies published them",
            "≥ ${compact(fleetStats.installFloor)}" to "installs, on Play's own counts",
        )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        totals.forEach { (value, label) ->
            Column(Modifier.widthIn(min = 150.dp, max = 240.dp)) {
                BasicText(text = value, style = cvType.metric.copy(color = colors.accent))
                Spacer(Modifier.height(6.dp))
                BasicText(text = label, style = cvType.metaMono)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 2. The three he worked on directly
// -------------------------------------------------------------------------------------------

@Composable
private fun TheThree(uri: UriHandler) {
    val colors = cvColors
    Section(eyebrow = "// direct", title = "The ones I worked on directly") {
        Column {
            BasicText(
                text =
                    "Not mine: Dice's and Jugnoo's. These are the products themselves rather than " +
                        "a client's build of one, and they are the two teams the rest of this page " +
                        "comes out of.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                storeApps.forEach { app ->
                    CvCard(
                        modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
                        onClick = { uri.openUri(app.url) },
                    ) {
                        Monogram(app.name, colorHex = null, size = 48.dp)
                        Spacer(Modifier.height(16.dp))
                        MonoMeta(app.employer)
                        Spacer(Modifier.height(4.dp))
                        BasicText(text = app.name, style = cvType.cardTitle)
                        Spacer(Modifier.height(6.dp))
                        BasicText(text = app.role, style = cvType.bodySmall)
                        Spacer(Modifier.height(14.dp))
                        BasicText(
                            text = "${oneDecimal(app.rating)}/5 · ${app.installs} installs",
                            style = cvType.metaMono.copy(color = colors.accent),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// 3. The tenure rule, and the chart that draws it
// -------------------------------------------------------------------------------------------

@Composable
private fun WhyTheseAndNotMore() {
    Section(eyebrow = "// the rule", title = "Why these $reached and not more") {
        Column {
            BasicText(
                text =
                    "The platform is older than my time on it, so most of what it ever shipped has " +
                        "nothing to do with me. I was at Jugnoo from January 2021 to May 2023, " +
                        "which means an app only belongs here if the store shows it still shipping " +
                        "builds in that window or after it: either it went out while I was there, " +
                        "or it went out later from a codebase my work is in. Every app below " +
                        "clears that line; ${fleetStats.predatingHim} others were on the store and " +
                        "do not, so they are not counted.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            LastShippedChart()
        }
    }
}

private val BarTrackHeight: Dp = 120.dp

/**
 * ShippedTimeline.tsx, as one stacked column per year. Solid accent is what you can still install,
 * a hairline outline is what was pulled. There is nothing to the left of the join year because
 * nothing to the left of it was counted, which is the entire point of the figure.
 */
@Composable
private fun LastShippedChart() {
    val colors = cvColors
    val peak = lastShipped.maxOf { it.live + it.gone }.coerceAtLeast(1)
    val joinYear = fleetStats.joined.take(4)
    val summary =
        "Last build shipped by year. " +
            lastShipped.joinToString("; ") { "${it.year}: ${it.live} still installable, ${it.gone} pulled" }

    CvCard(modifier = Modifier.fillMaxWidth(), glowOnHover = false) {
        BasicText(
            text = "Last build shipped, by year",
            style = cvType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            text = "Nothing before $joinYear, because nothing before $joinYear is counted.",
            style = cvType.metaMono,
        )
        Spacer(Modifier.height(24.dp))

        // The bars carry no text a screen reader can walk, so the whole strip is announced as one
        // sentence rather than as a row of unlabelled boxes.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = summary },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            lastShipped.forEach { year ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(text = "${year.live + year.gone}", style = cvType.metaMono)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().height(BarTrackHeight),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(BarTrackHeight * year.gone / peak)
                                .border(1.dp, colors.line, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(BarTrackHeight * year.live / peak)
                                .background(colors.accent.copy(alpha = 0.8f)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BasicText(text = "${year.year}", style = cvType.metaMono)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(colors.accent.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            MonoMeta("still installable")
            Spacer(Modifier.width(20.dp))
            Box(Modifier.size(8.dp).border(1.dp, colors.line, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            MonoMeta("pulled since")
        }
    }
}

// -------------------------------------------------------------------------------------------
// 4. The wall
// -------------------------------------------------------------------------------------------

/**
 * One client, with everything it shipped, collapsed from LiveClient / PastClient.
 *
 * ShippedClient.tsx takes a single union-typed `Client` for exactly this reason: ninety listings
 * collapse to forty-odd companies, and the page then reads as what it is, a list of businesses
 * rather than a list of binaries. Kotlin has no union type, so the two generated shapes map onto
 * this one at composition time; `meta` is the preformatted line the web version joins inline.
 */
private data class ShelfClient(
    val key: String,
    val name: String,
    val developer: String?,
    val colorHex: String?,
    val setUpByHim: Boolean,
    val gone: String?,
    val apps: List<ShelfApp>,
)

private data class ShelfApp(
    val id: String,
    val name: String,
    val url: String,
    val side: String,
    val meta: String,
    val setUpByHim: Boolean,
)

private fun LiveClient.toShelf(): ShelfClient =
    ShelfClient(
        key = key,
        name = name,
        developer = developer,
        colorHex = color,
        setUpByHim = setUpByHim,
        gone = null,
        apps =
            apps.map { a ->
                ShelfApp(
                    id = a.id,
                    name = a.name,
                    url = a.url,
                    side = a.side,
                    meta =
                        listOfNotNull(
                            a.installs.ifBlank { null },
                            a.rating?.let { "${oneDecimal(it)}/5" },
                            shortDate(a.updated),
                        ).joinToString(" · ").ifEmpty { "open listing" },
                    setUpByHim = a.setUpByHim,
                )
            },
    )

private fun PastClient.toShelf(): ShelfClient =
    ShelfClient(
        key = key,
        name = name,
        developer = null,
        colorHex = color,
        setUpByHim = setUpByHim,
        gone = archiveMonth(lastSeen),
        apps =
            apps.map { a ->
                ShelfApp(
                    id = a.id,
                    name = a.name,
                    url = a.url,
                    side = a.side,
                    meta =
                        listOfNotNull(
                            a.rating?.let { "${oneDecimal(it)}/5" },
                            archiveMonth(a.lastSeen),
                        ).joinToString(" · ").ifEmpty { "open the archived listing" },
                    setUpByHim = a.setUpByHim,
                )
            },
    )

@Composable
private fun ClientRow(row: List<ShelfClient>, columns: Int, past: Boolean, uri: UriHandler) {
    Row(
        modifier = Modifier.pageMeasure().padding(top = 12.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { client ->
            ClientCard(
                client = client,
                past = past,
                uri = uri,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        // A short last row must not stretch its cards across the full measure, or the wall ends
        // with two tiles twice the width of the ninety above them.
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** "rider" -> "Rider". The label a person would use for it; anything else is just "App". */
private fun sideLabel(side: String): String = when (side) {
    "rider" -> "Rider"
    "driver" -> "Driver"
    "merchant" -> "Merchant"
    else -> "App"
}

@Composable
private fun ClientCard(
    client: ShelfClient,
    past: Boolean,
    uri: UriHandler,
    modifier: Modifier = Modifier,
) {
    val colors = cvColors
    CvCard(modifier = modifier, glowOnHover = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Monogram(client.name, client.colorHex, size = if (past) 34.dp else 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = client.name,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            cvType.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (past) colors.muted else colors.onBackground,
                            ),
                    )
                    if (client.setUpByHim) {
                        Spacer(Modifier.width(6.dp))
                        SetUpDot()
                    }
                }
                val developer = client.developer
                if (developer != null && developer != client.name) {
                    BasicText(
                        text = developer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = cvType.metaMono,
                    )
                }
                val gone = client.gone
                if (gone != null) {
                    BasicText(text = "on the store until $gone", style = cvType.metaMono)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Each app keeps its own destination, so grouping by company costs nobody a link.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            client.apps.forEach { app ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { uri.openUri(app.url) }
                            .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = sideLabel(app.side),
                        modifier = Modifier.width(56.dp),
                        style = cvType.metaMono.copy(color = colors.accent),
                    )
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            text = app.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = cvType.bodySmall,
                        )
                        BasicText(
                            text = app.meta,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = cvType.metaMono,
                        )
                    }
                    if (app.setUpByHim && !client.setUpByHim) {
                        Spacer(Modifier.width(8.dp))
                        SetUpDot()
                    }
                }
            }
        }
    }
}

/**
 * The 1.5px accent dot that marks a listing he set up. Its meaning is stated once, in the footnote,
 * so here it is announced per occurrence rather than left as an unlabelled decoration.
 */
@Composable
private fun SetUpDot() {
    val colors = cvColors
    Box(
        Modifier
            .size(6.dp)
            .clearAndSetSemantics { contentDescription = "set up by Siddharth" }
            .background(colors.accent, CircleShape),
    )
}

/**
 * ShippedTile.tsx's own icon fallback: the first letter on the client's brand colour.
 *
 * Decorative on purpose. The React version marks the identical element `aria-hidden` because the
 * app's name is the very next thing in the reading order, and announcing a bare "S" ahead of
 * "SmartBike" is noise, not information.
 */
@Composable
private fun Monogram(name: String, colorHex: String?, size: Dp) {
    val colors = cvColors
    val brand = colorHex?.let { cvColor(it) } ?: colors.card
    Box(
        modifier =
            Modifier
                .size(size)
                .clearAndSetSemantics { }
                .background(brand, RoundedCornerShape(size * 22 / 100))
                .border(1.dp, colors.line, RoundedCornerShape(size * 22 / 100)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = name.take(1).uppercase(),
            style = cvType.cardTitle.copy(color = readableOn(brand)),
        )
    }
}

/**
 * Brand colours here run from #000000 to #ffd404, so a fixed letter colour is illegible on one end
 * or the other. Rec. 601 luma, the same rule a contrast checker applies.
 */
private fun readableOn(background: Color): Color {
    val luma = RED_WEIGHT * background.red + GREEN_WEIGHT * background.green + BLUE_WEIGHT * background.blue
    return if (luma > LUMA_MIDPOINT) Color.Black else Color.White
}

private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f
private const val LUMA_MIDPOINT = 0.55f

// -------------------------------------------------------------------------------------------
// 5. Method
// -------------------------------------------------------------------------------------------

@Composable
private fun HowIKnow() {
    val steps =
        listOf(
            "Every client shipped separately" to
                "That is what made it a platform rather than a product: one app, rebuilt and " +
                    "rebranded for each client, each with its own name and its own listing. So the " +
                    "work is not one entry on a CV, it is spread across a lot of store pages, " +
                    "under a lot of company names, and none of them are mine.",
            "Then Google told me which survived" to
                "Most never made it out of pilot. ${fleetStats.live} are still on the Play Store, " +
                    "and each one hands over its real name, icon, rating, install count and the " +
                    "company that publishes it. Anything that no longer answers is taken off this " +
                    "page rather than left as a broken link.",
            "And the Archive remembered the rest" to
                "An app that was taken down and an app that never existed look identical on Play: " +
                    "both are a dead link. The Internet Archive can tell them apart, because it " +
                    "kept a copy of the listing while it was up. That is the only reason " +
                    "${fleetStats.delisted} of these can be named at all.",
            "What I left out" to
                "${fleetStats.predatingHim} apps that were genuinely published, but whose last " +
                    "build went out before I joined, so I cannot have written a line in them. One " +
                    "with a million installs that came off the same platform two years before I " +
                    "arrived. Anything that was a demo or a template rather than a real client. " +
                    "And anything a former employer would reasonably consider theirs.",
        )
    Section(eyebrow = "// method", title = "How I know") {
        Column {
            BasicText(
                text =
                    "None of this is from memory. Nobody remembers $reached apps, and a list " +
                        "written from recollection is a list you should not trust, so every line " +
                        "of it was rebuilt from things that can be checked.",
                style = cvType.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                steps.forEachIndexed { i, (heading, body) ->
                    CvCard(
                        modifier = Modifier.widthIn(min = 260.dp, max = 420.dp),
                        glowOnHover = false,
                    ) {
                        MonoMeta("0${i + 1}")
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            text = heading,
                            style = cvType.cardTitle.copy(fontSize = cvType.body.fontSize),
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicText(text = body, style = cvType.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfFootnote() {
    Column(Modifier.pageMeasure().padding(top = 40.dp, bottom = 120.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            // ponytail: the marker is the drawn dot, not a typed bullet. Same missing-glyph reason
            // as the star above, and the same reason ExpanderSection draws its own chevron.
            Box(Modifier.padding(top = 5.dp)) { SetUpDot() }
            Spacer(Modifier.width(10.dp))
            BasicText(
                text =
                    "marks the $setUpByHimTotal I set up myself. " +
                        "${fleetStats.carryingHisCommits} of the ${fleetStats.live} live ones are " +
                        "builds of an app I worked on. Listings read $storeGeneratedAt.",
                modifier = Modifier.widthIn(max = 640.dp),
                style = cvType.metaMono,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Formatting: the Kotlin half of src/shippedFormat.ts
// -------------------------------------------------------------------------------------------

private const val MILLION = 1_000_000
private const val THOUSAND = 1_000
private val Months =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 2920170 -> "2.9M". Install counts are Play's buckets, so any total is a floor, never a count. */
private fun compact(n: Int): String = when {
    n >= MILLION -> "${oneDecimal(n.toDouble() / MILLION).removeSuffix(".0")}M"
    n >= THOUSAND -> "${(n + THOUSAND / 2) / THOUSAND}K"
    else -> n.toString()
}

/** "20211215" -> "Dec 2021". Archive timestamps, trimmed to what is meaningful. */
private fun archiveMonth(ts: String): String {
    val month = ts.drop(4).take(2).toIntOrNull() ?: return ts
    val name = Months.getOrNull(month - 1) ?: return ts
    return "$name ${ts.take(4)}"
}

/** "Jul 22, 2025" -> "Jul 2025". Play's own update date, without the day. */
private val PlayDate = Regex("""^([A-Za-z]{3})\s+\d{1,2},\s+(\d{4})$""")

private fun shortDate(d: String): String? {
    val raw = d.trim()
    if (raw.isEmpty()) return null
    val m = PlayDate.find(raw) ?: return raw
    return "${m.groupValues[1]} ${m.groupValues[2]}"
}

/**
 * 3.14 -> "3.1", locale-free. `toString()` would print "3.0999999" for some doubles and there is no
 * KMP-common printf; ratings are 0.0-5.0 so no sign or overflow handling is needed.
 */
private fun oneDecimal(v: Double): String {
    val scaled = round(v * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

// ponytail: one runnable check instead of a test module. Every one of these formatters has a branch
// or a parse in it, and all three are the kind of thing that looks right and prints wrong.
@Suppress("MagicNumber")
internal fun shippedFormatSelfCheck() {
    check(compact(2_922_170) == "2.9M") { "compact millions: ${compact(2_922_170)}" }
    check(compact(2_000_000) == "2M") { "compact trims a trailing .0: ${compact(2_000_000)}" }
    check(compact(45_400) == "45K") { "compact thousands: ${compact(45_400)}" }
    check(compact(950) == "950") { "compact leaves small counts alone" }

    check(archiveMonth("20211215") == "Dec 2021") { "archiveMonth: ${archiveMonth("20211215")}" }
    check(archiveMonth("20250404") == "Apr 2025") { "archiveMonth: ${archiveMonth("20250404")}" }
    check(archiveMonth("nonsense") == "nonsense") { "archiveMonth passes junk through unchanged" }

    check(shortDate("Jul 22, 2025") == "Jul 2025") { "shortDate: ${shortDate("Jul 22, 2025")}" }
    check(shortDate("Aug 5, 2026") == "Aug 2026") { "shortDate single-digit day" }
    check(shortDate("whenever") == "whenever") { "shortDate passes an unparsed date through" }
    check(shortDate("") == null) { "shortDate drops an empty string rather than joining it" }

    check(oneDecimal(3.14) == "3.1") { "oneDecimal rounds down" }
    check(oneDecimal(2.95) == "3.0") { "oneDecimal rounds up and keeps the tenth: ${oneDecimal(2.95)}" }
    check(oneDecimal(4.0) == "4.0") { "oneDecimal keeps a whole rating's tenth" }

    // The two totals this page asserts are derived, not typed. If the generated corpus ever stops
    // agreeing with fleetStats, that is a store.ts problem and it should fail loudly here.
    check(reached == fleetStats.live + fleetStats.delisted) { "reached is live + delisted" }
    check(liveClients.sumOf { it.apps.size } == fleetStats.live) {
        "grouped live apps must total fleetStats.live: ${liveClients.sumOf { it.apps.size }} vs ${fleetStats.live}"
    }
    check(pastClients.sumOf { it.apps.size } == fleetStats.delisted) {
        "grouped pulled apps must total fleetStats.delisted: ${pastClients.sumOf { it.apps.size }} vs ${fleetStats.delisted}"
    }
    check(setUpByHimTotal >= fleetStats.setUpByHim) { "the pulled ones only add to the live count" }
}
