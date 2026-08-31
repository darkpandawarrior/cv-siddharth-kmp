package com.siddharth.cv.shared.anthology

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddharth.cv.shared.data.generated.AnthologyEntry
import com.siddharth.cv.shared.data.generated.AnthologySeason
import com.siddharth.cv.shared.data.generated.AnthologyWitness
import com.siddharth.cv.shared.data.generated.SiblingSeries
import com.siddharth.cv.shared.data.generated.StarWorld
import com.siddharth.cv.shared.data.generated.anthology
import com.siddharth.cv.shared.data.generated.anthologyEntries
import com.siddharth.cv.shared.data.generated.namedThirteen
import com.siddharth.cv.shared.data.generated.seasonCanon
import com.siddharth.cv.shared.data.generated.siblingSeries
import com.siddharth.cv.shared.data.generated.unfiledPieces
import com.siddharth.cv.shared.data.profile
import com.siddharth.cv.shared.media.ProjectShot
import com.siddharth.cv.shared.theme.CvCard
import com.siddharth.cv.shared.theme.CvColors
import com.siddharth.cv.shared.theme.CvContentMaxWidth
import com.siddharth.cv.shared.theme.CvDarkColors
import com.siddharth.cv.shared.theme.CvGutter
import com.siddharth.cv.shared.theme.CvSectionGap
import com.siddharth.cv.shared.theme.CvTheme
import com.siddharth.cv.shared.theme.GhostButton
import com.siddharth.cv.shared.theme.LocalReducedMotion
import com.siddharth.cv.shared.theme.MonoMeta
import com.siddharth.cv.shared.theme.Reveal
import com.siddharth.cv.shared.theme.SectionEyebrow
import com.siddharth.cv.shared.theme.SectionHeading
import com.siddharth.cv.shared.theme.TagChip
import com.siddharth.cv.shared.theme.cvColor
import com.siddharth.cv.shared.theme.cvColors
import com.siddharth.cv.shared.theme.cvType
import com.siddharth.cv.shared.theme.tiltOnHover
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// The ink world's palette
// =============================================================================================

/**
 * `.ink-world` in cv-siddharth/src/index.css:1457. Lamplight on old stock, the room the fiction
 * lives in. It is a scoped token override on the web and it is a nested [CvTheme] here, which is
 * the same mechanism: everything below keeps reading `cvColors.accent` and comes out ochre.
 *
 * `accent2Dim` is deliberately not in this copy because `.ink-world` does not override
 * `--color-accent2-dim` either; the site's cyan stays underneath on both builds.
 *
 * Lost with a straight face: `--font-display: "Rozha One"`. The ink world swaps to a display serif
 * and only Space Grotesk and DM Mono are vendored under `composeResources/font/`, so the room is
 * the right colour in the wrong face. Vendoring one more TTF is the whole fix.
 */
internal val InkColors: CvColors =
    CvDarkColors.copy(
        accent = cvColor("#d9a441"), // ochre, 8.42:1 on the ink ground
        accentDim = cvColor("#b8842c"),
        accent2 = cvColor("#cf8f63"), // terracotta, replacing the telemetry cyan
        ink = cvColor("#14100c"),
        surface = cvColor("#1b1611"),
        card = cvColor("#221b15"),
        line = cvColor("#3a2f24"),
        onBackground = cvColor("#efe7d8"),
        // `--color-text-dim` and `--color-muted` are the same #a4978a in this world (6.65:1 on ink),
        // so CvColors' single muted slot loses nothing.
        muted = cvColor("#a4978a"),
    )

/** `--color-danger`. Border-only by rule: 4.49:1 fails AA for text by a hundredth. */
internal val InkDanger: Color = cvColor("#c25a4a")

/** `--color-warn`: season three's ember, swapped in for the accent on a burned page. */
private val Ember: Color = cvColor("#d97a3d")

/** `--color-coverage`: season four's cyan, the one cold token in a warm room. */
private val Coverage: Color = cvColor("#5ec8dc")

/**
 * The one page he keeps: the whole palette goes to paper under a nested [CvTheme], exactly as
 * `season-kept`'s token block does. Ratios are the measured ones from lib/seasonTheme.ts.
 */
private val KeptPaperColors: CvColors =
    InkColors.copy(
        card = cvColor("#e9dfc9"),
        onBackground = cvColor("#1f1a12"), // 13.06:1
        muted = cvColor("#5b5142"), //  5.87:1
        line = cvColor("#8a7a5c"), //  3.16:1, the non-text UI floor
        accent = cvColor("#9e3b2e"), //  5.09:1
        accentDim = cvColor("#7d2e23"),
    )

// =============================================================================================
// The five media, and the vocabulary for them
// =============================================================================================

/**
 * The layers, ported from the `LAYERS` table in cv-siddharth/src/routes/anthology.tsx.
 *
 * The media are the navigation: a reader picked up a form, a case, a fire, a wall, a map or the
 * roll of tellers, so that is what the switch says. Season numbers stay out of the vocabulary
 * because a number is a filing detail and renumbering one would retarget every link ever pasted.
 *
 * One table doing three jobs, same as upstream: the switch row in its order, which season a layer
 * opens, and the `?layer=` key the React route's `validateSearch` accepts, so when the spine wires
 * a `Route.Anthology(layer)` it has the vocabulary already and cannot invent a second one.
 */
enum class AnthologyLayer(val key: String, val label: String, val season: Int?) {
    Form("form", "The Form", 1),
    Case("case", "The Case", 2),
    Fire("fire", "The Fire", 3),

    /** After the fire in publication order because that is the order it happened in. */
    Wall("wall", "The Wall", 4),
    Map("map", "The Map", null),
    Tellers("tellers", "The Tellers", null),

    /** Work in this universe with no season, no series and no designation. Not a fifth season. */
    Unfiled("unfiled", "Unfiled", null),

    /** A separate work in the same universe. It carries its own name, not a "The …" label. */
    Dark("dark", "The Dark Directory", null),
}

// =============================================================================================
// The screen
// =============================================================================================

/**
 * Port of cv-siddharth/src/routes/anthology.tsx: the season and entry index for The Morkinstar
 * Journals, one room deeper than /ink.
 *
 * [onOpenCanon] is the pill at the end of the switch row. It is a callback rather than a route
 * because this file owns no navigation: the canon outgrew a tab on the web and became an address,
 * and it stays a separate destination here.
 *
 * The layer is local state, seeded by [initialLayer]. Upstream it is a URL search param, which is
 * the better answer and the one the spine can restore for free by passing [AnthologyLayer.key]
 * through `Route`; nothing below assumes it is local.
 *
 * DELIBERATELY LOST, and each one is a capability rather than a decision:
 *  - Every plate and portrait. 600x780 and 1100x600 JPEGs, and this port ships no bitmaps.
 *  - The starmap's orbit. `/anthology?layer=map` is a three.js scene on the web; see [Starfield].
 *  - The deliberate blanks on the tellers roll. See [SeasonRoll]; that is a data problem, not a
 *    rendering one, and copying nine paragraphs of argument out of a .tsx route into Kotlin is the
 *    exact second-copy-nobody-refreshes mistake this port's data pipeline exists to prevent.
 */
@Composable
fun AnthologyScreen(
    onOpenCanon: () -> Unit,
    modifier: Modifier = Modifier,
    initialLayer: AnthologyLayer = AnthologyLayer.Form,
) {
    CvTheme(colors = InkColors) {
        AnthologyBody(initialLayer, onOpenCanon, modifier)
    }
}

/** `max-w-5xl mx-auto px-6`: the measure every block on this page shares. */
private fun Modifier.pageMeasure(): Modifier =
    this.widthIn(max = CvContentMaxWidth).fillMaxWidth().padding(horizontal = CvGutter)

@Composable
private fun AnthologyBody(
    initialLayer: AnthologyLayer,
    onOpenCanon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = cvColors
    val uri = LocalUriHandler.current
    var layer by remember(initialLayer) { mutableStateOf(initialLayer) }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.ink)) {
        // Read once, outside the list: a per-item BoxWithConstraints would measure 48 times to
        // answer the same question, and the answer is a property of the window.
        val columns =
            when {
                maxWidth >= 920.dp -> 3
                maxWidth >= 620.dp -> 2
                else -> 1
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
        ) {
            item("masthead") { Masthead(uri) }
            item("switch") { LayerSwitch(layer, onSelect = { layer = it }, onOpenCanon = onOpenCanon) }

            val season = layer.season
            if (season != null) {
                seasonLayer(season, columns, uri)
            } else {
                when (layer) {
                    AnthologyLayer.Map -> item("map") { StarmapLayer() }
                    AnthologyLayer.Tellers -> tellersLayer(columns, uri)
                    AnthologyLayer.Unfiled -> unfiledLayer(columns, uri)
                    AnthologyLayer.Dark -> siblingLayer(columns, uri)
                    // Unreachable: every layer with a null season is named above. Saying so is
                    // cheaper than an `else -> {}` that would silently swallow a ninth member.
                    else -> item("unwired") { MonoMeta("// ${layer.key}: no panel wired") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 0. The door
// ---------------------------------------------------------------------------------------------

/** 220wpm, the same figure the reading page uses. */
private const val wordsPerMinute = 220

/**
 * Every number on the masthead is derived, and that is the repair rather than a flourish: this page
 * shipped "twenty entries, two seasons" against a corpus of thirty-four across three, and the fix is
 * not a better number, it is not having one to keep.
 */
private val totalWords: Int = anthologyEntries.sumOf { it.words }
private val readingHours: Int = (totalWords.toFloat() / wordsPerMinute / 60f).roundToInt()

/** The first piece in publication order, and the shortest way into the season that needs no prior context. */
private val firstEntry: AnthologyEntry? =
    anthologyEntries.filter { it.season == 1 }.minByOrNull { it.idx }
private val shortestEntry: AnthologyEntry? =
    anthologyEntries.filter { it.season == 1 }.minByOrNull { it.words }

@Composable
private fun Masthead(uri: UriHandler) {
    val colors = cvColors
    Reveal {
        Column(Modifier.pageMeasure()) {
            SectionEyebrow(
                "// ${anthologyEntries.size} pieces, ${anthology.seasons.size} seasons, " +
                    "${totalWords.grouped()} words",
            )
            Spacer(Modifier.height(14.dp))
            BasicText(text = anthology.title, style = cvType.hero)
            Spacer(Modifier.height(18.dp))
            BasicText(
                text = anthology.tagline,
                style = cvType.body.copy(fontSize = cvType.body.fontSize * 1.1f),
                modifier = Modifier.widthIn(max = 680.dp),
            )
            Spacer(Modifier.height(18.dp))
            BasicText(
                modifier = Modifier.widthIn(max = 680.dp),
                text =
                    "A correspondent visits worlds that cannot yet leave them, and writes down the " +
                        "story each one tells about its own weather. Every world he has ever surveyed " +
                        "independently reports fourteen gods and fourteen monsters, the same count, " +
                        "worlds apart, with no contact between them. Nobody, on any of them, can name " +
                        "the fourteenth.",
                style = cvType.body.copy(color = colors.muted),
            )
            Spacer(Modifier.height(16.dp))
            // Said here, once, and nowhere else. No explanation follows it and none is allowed to:
            // an order that has to be described is an order.
            BasicText(
                text = "Works separately, together, or in any order.",
                style = cvType.body.copy(color = colors.muted),
            )

            Spacer(Modifier.height(24.dp))
            TheDoor(uri)
        }
    }
}

/**
 * Three facts a stranger needs and could not otherwise get: how long this is, what the four seasons
 * physically are, and where to start. It does NOT explain the line above it.
 *
 * The two doors are buttons rather than links inside the sentence: an inline clickable span needs an
 * AnnotatedString with a link annotation and a second text style, and two of the site's own ghost
 * buttons say the same thing in a quarter of the code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TheDoor(uri: UriHandler) {
    val colors = cvColors
    val rule = colors.accent.copy(alpha = 0.4f)
    val first = firstEntry
    val shortest = shortestEntry
    // `border-l-2 border-accent/40 pl-4`. The rule is painted rather than a sibling Box, which
    // would need the row's height before the column that sets it has been measured.
    Column(
        Modifier
            .widthIn(max = 680.dp)
            .drawBehind { drawRect(color = rule, size = Size(2.dp.toPx(), size.height)) }
            .padding(start = 16.dp),
    ) {
        BasicText(
            text =
                "About $readingHours hours of reading, in four objects: a Directory survey form, " +
                    "a page in a wooden case, a fire, and a public wall he pastes notices onto.",
            style = cvType.body.copy(color = colors.muted),
        )
        if (first != null && shortest != null) {
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = "If you would rather be pointed somewhere:",
                style = cvType.body.copy(color = colors.muted),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GhostButton(
                    text = "Start here: ${first.title}",
                    onClick = { openRead(first.slug, uri) },
                )
                GhostButton(
                    text = "Shortest way in: ${shortest.title}, ${shortest.words.grouped()} words",
                    onClick = { openRead(shortest.slug, uri) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 1. The switch row
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerSwitch(
    current: AnthologyLayer,
    onSelect: (AnthologyLayer) -> Unit,
    onOpenCanon: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.pageMeasure().padding(top = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnthologyLayer.entries.forEach { l ->
            LayerPill(label = l.label, selected = l == current, onSelect = { onSelect(l) })
        }
        // The canon pill navigates, so it is the site's navigation affordance rather than a
        // switch that never takes the pressed state. Same row, different job, and that is the
        // distinction the React version spells out in a comment.
        GhostButton(text = "The Canon", onClick = onOpenCanon)
    }
}

/**
 * Eight mutually exclusive layers are a radio group to a screen reader, and "selected" is the state
 * that matters, the same call [com.siddharth.cv.shared.labs.LabScreen]'s tabs make.
 */
@Composable
private fun LayerPill(label: String, selected: Boolean, onSelect: () -> Unit) {
    val colors = cvColors
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent, shape)
                .border(1.dp, if (selected) colors.accent else colors.line, shape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    // indication = null sitewide: the site draws its own hover and focus treatment.
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicText(
            text = label,
            style =
                cvType.body.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.accent else colors.muted,
                ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 2. A season: blurb, anchor object, grid
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.seasonLayer(
    season: Int,
    columns: Int,
    uri: UriHandler,
) {
    val meta: AnthologySeason? = anthology.seasons.firstOrNull { it.n == season }
    val entries = anthologyEntries.filter { it.season == season }

    item("season-$season-head") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = 28.dp)) {
                if (meta != null) {
                    BasicText(
                        text = meta.blurb,
                        modifier = Modifier.widthIn(max = 680.dp),
                        style = cvType.bodySmall,
                    )
                }
                SeasonHeroFigure(season)
            }
        }
    }

    gridItems("season-$season", entries, columns, key = { it.slug }) { entry, index ->
        EntryCard(entry, index, uri)
    }
}

/**
 * The anchor object above a season's grid, ported from `seasonHero()`.
 *
 * Season four returns nothing, and that is canon rather than a gap: it is the season in which he
 * holds nothing, keeps no copies, and cannot read his own work again. The grid IS the wall.
 */
@Composable
private fun SeasonHeroFigure(season: Int) {
    when (season) {
        1 -> TheFourteenFigure()
        2 -> TheCase(burned = false)
        3 -> TheCase(burned = true)
        else -> Unit
    }
}

/**
 * Season one's anchor, and the same figure `/canon` opens The Count with.
 *
 * On the web this is a 1200x1560 painted plate of thirteen sigils in a ring and one empty slot;
 * there are no bitmaps in this port, so it is the same census drawn as a grid of fourteen cells:
 * thirteen filled, one dashed and empty.
 *
 * Not a reproduction of the painting and not pretending to be one. The count is read off
 * [namedThirteen] rather than typed, so the figure cannot outlive the corpus that fills it.
 */
@Composable
internal fun TheFourteenFigure() {
    val colors = cvColors
    val filled = namedThirteen.size
    Figure(
        caption =
            "Thirteen sigils and one empty slot. Every world he has surveyed reports the same " +
                "fourteen, and nobody, on any of them, can name the fourteenth.",
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(7f / 2.6f)
                .padding(20.dp)
                .semantics {
                    contentDescription =
                        "$filled filled slots and one empty slot outlined in a broken line, " +
                            "where a fourteenth should be."
                },
        ) {
            val cols = 7
            val rows = 2
            val cellW = size.width / cols
            val cellH = size.height / rows
            val pad = min(cellW, cellH) * 0.16f
            repeat(filled + 1) { i ->
                val left = (i % cols) * cellW + pad
                val top = (i / cols) * cellH + pad
                val box = Size(cellW - pad * 2, cellH - pad * 2)
                val corner = CornerRadius(3.dp.toPx())
                if (i < filled) {
                    drawRoundRect(
                        color = colors.accent.copy(alpha = 0.55f),
                        topLeft = Offset(left, top),
                        size = box,
                        cornerRadius = corner,
                    )
                } else {
                    drawRoundRect(
                        color = InkDanger,
                        topLeft = Offset(left, top),
                        size = box,
                        cornerRadius = corner,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashes()),
                    )
                }
            }
        }
    }
}

/** Canon, not a count: season two is titled The Ninety-One Pages, and thirteen by seven is ninety-one. */
private const val caseSlots = 91
private const val caseColumns = 13

/**
 * The case, and the season's whole plot in one figure. Derived from the entries themselves rather
 * than drawn, so it cannot drift from the corpus: the ten slots the reader has been handed, and, in
 * the burned state, the thirteen the fire has taken. The page he keeps carries page 0 and drops out
 * of both sets, which is right, because it goes back into the case blank.
 *
 * The grid emits no text nodes at all, so it is out of the accessibility tree the way the web's
 * `aria-hidden` puts it; the caption below carries the meaning in prose. Colour is never the only
 * channel either: a filled slot is solid, a read slot is solid and ringed, an emptied slot is an
 * outline with nothing inside it.
 */
@Composable
private fun TheCase(burned: Boolean) {
    val colors = cvColors
    val read = remember { anthologyEntries.filter { it.season == 2 }.map { it.page }.toSet() }
    val gone =
        remember(burned) {
            if (burned) {
                anthologyEntries.filter { it.season == 3 }.map { it.page }.filter { it != 0 }.toSet()
            } else {
                emptySet()
            }
        }

    Figure(
        caption =
            if (burned) {
                "The same case, thirteen nights later. Every outline is a page he has taken out and " +
                    "burned, in the order he took it, and page ninety-one is the last one to go. The " +
                    "slot he fills again holds a blank sheet."
            } else {
                "Ninety-one slots, and he filled every one of them. The marked ten are the pages you " +
                    "have been handed. He wrote page ninety-one first, at the very back, before he " +
                    "had any right to reach it, and that is what finished the case."
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(13f / 7.4f).padding(20.dp)) {
            val cellW = size.width / caseColumns
            val cellH = size.height / (caseSlots / caseColumns)
            val r = min(cellW, cellH) * 0.30f
            for (page in 1..caseSlots) {
                val i = page - 1
                val cx = (i % caseColumns) * cellW + cellW / 2f
                val cy = (i / caseColumns) * cellH + cellH / 2f
                when {
                    gone.contains(page) ->
                        drawCircle(
                            color = colors.accent.copy(alpha = 0.75f),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.2.dp.toPx()),
                        )

                    read.contains(page) -> {
                        drawCircle(colors.accent, radius = r, center = Offset(cx, cy))
                        drawCircle(
                            color = colors.accent.copy(alpha = 0.45f),
                            radius = r * 1.85f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }

                    else -> drawCircle(colors.line, radius = r, center = Offset(cx, cy))
                }
            }
        }
    }
}

/** `<figure>` + `<figcaption>`: a framed panel with the meaning in prose underneath it. */
@Composable
private fun Figure(caption: String, content: @Composable () -> Unit) {
    val colors = cvColors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(top = 28.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .background(colors.deepVoid.copy(alpha = 0.4f), shape)
            .border(1.dp, colors.line, shape),
    ) {
        content()
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        BasicText(
            text = caption,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = cvType.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 3. The entry card
// ---------------------------------------------------------------------------------------------

/** Season three's kindling ordinal: 1-13 withdrawn, 14 the one page kept. */
private const val kindlingFinale = 14

/** Loose warm paper on a desk sits very slightly askew. Season one and the kept page are neither. */
private const val paperTiltDeg = 0.65f

/**
 * Per-entry identity, ported from lib/seasonTheme.ts.
 *
 * The descriptor is a function of the ENTRY, not of the season number. That is the decision
 * everything else falls out of: the one page he keeps is an exception inside season three's own row
 * rather than a special case leaking into the card.
 *
 * Lost against the web: the corner radius, which is season three's whole tell ("a burned page is
 * identified by its edge"). [CvCard] fixes its own 16dp shape and the house rule is to use a
 * primitive as it stands rather than fork it, so the seasons are told apart here by colour, label
 * and tilt, which is three channels rather than four. `CvCard(shape = …)` is the one-line fix.
 */
private data class EntryLook(
    val label: String,
    val colors: CvColors,
    val kickerAccent: Boolean,
    val tiltDeg: Float,
    val hoverTilt: Boolean,
    /**
     * What the plate IS, for a reader who cannot see it. The web card ships `alt=""` because the
     * title sits two lines below the picture, which is the correct call for decoration and the
     * wrong one here: these are painted artefacts, one per entry, and the title alone does not say
     * that a picture is even present. Every phrase below is a claim the season already makes about
     * its own stock elsewhere in this file, never a description of a painting nothing here can see.
     */
    val plateIs: String,
)

private fun entryLook(e: AnthologyEntry): EntryLook =
    when {
        // The Directory's survey form. Broadcast, filed, numbered. A case file does not tilt.
        e.season == 1 ->
            EntryLook(
                "ENTRY #${e.entry}",
                InkColors,
                false,
                0f,
                false,
                "a Directory survey plate, filed",
            )
        // His own page. Loose warm paper on a desk, so it sits very slightly askew.
        e.season == 2 ->
            EntryLook(
                "PAGE ${e.page} OF 91",
                InkColors,
                true,
                paperTiltDeg,
                true,
                "a page out of his case, on warm paper",
            )
        // The exception, and the only undamaged object in the season.
        e.season == 3 && e.kindling == kindlingFinale ->
            EntryLook(
                "THE PAGE HE KEEPS",
                KeptPaperColors,
                true,
                0f,
                false,
                "the one page he did not burn, undamaged",
            )
        // The fire. Same paper, marked by it, so the accent goes to ember and nothing rotates:
        // a burned page is the shape of its own edge and a tilt would fight the bite.
        e.season == 3 ->
            EntryLook(
                "PAGE ${e.page} WITHDRAWN",
                InkColors.copy(accent = Ember, accentDim = Ember.copy(alpha = 0.8f)),
                true,
                0f,
                false,
                "a withdrawn page, identified by its burned edge",
            )
        // The notice board. Posted rather than handled, so it is flat, and it carries the same
        // coverage cyan its plates do.
        e.season == 4 ->
            EntryLook(
                "NOTICE ${e.idx} OF 14",
                InkColors.copy(accent = Coverage, accentDim = Coverage.copy(alpha = 0.8f)),
                true,
                0f,
                false,
                "a notice pasted on a public wall",
            )
        // A season with no row of its own. Positional, always true, and it never asserts a
        // counting scheme it does not have.
        else ->
            EntryLook(
                "№ ${e.idx}",
                InkColors,
                true,
                paperTiltDeg,
                true,
                "a plate on the season's own stock",
            )
    }

/**
 * One entry, plate and all. The 600x780 plate is streamed from the live origin by [plateUrl] rather
 * than bundled, so the season's identity comes from the picture again and not only from the shell
 * around it.
 *
 * Two honest differences from the web card, neither faked. The plate is inset inside [CvCard]'s own
 * 24dp padding instead of bleeding to the card edge, because [CvCard] fixes its padding and the
 * house rule is to use a primitive as it stands rather than fork it. And season one's plates lose
 * the `grayscale-[35%] sepia-[10%]` filter that is what makes ten of them read as one filed set: a
 * `ColorMatrix` through [com.siddharth.cv.shared.media.ProjectShot] is the one-line fix and it is
 * not worth a parameter on a shared loader for one season.
 *
 * The nested [CvTheme] is the whole per-season mechanism and the exact analogue of `t.vars`: every
 * `cvColors` read below re-resolves against the shadowed local, so nothing here knows that season
 * three burned or that one page in it is on paper.
 */
@Composable
private fun EntryCard(e: AnthologyEntry, index: Int, uri: UriHandler) {
    val look = entryLook(e)
    val reduced = LocalReducedMotion.current
    CvTheme(colors = look.colors) {
        val colors = cvColors
        // Alternating the sign is what reads as handled paper rather than as a consistent skew
        // applied by a stylesheet.
        val rot = if (index % 2 == 0) -look.tiltDeg else look.tiltDeg
        CvCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = if (reduced) 0f else rot }
                    .then(
                        if (look.hoverTilt) {
                            Modifier.tiltOnHover(4f, spotlight = colors.accent)
                        } else {
                            Modifier
                        },
                    ),
            onClick = { openRead(e.slug, uri) },
        ) {
            val plate = plateUrl(e.plate)
            if (plate != null) {
                ProjectShot(
                    url = plate,
                    label = "Field plate for ${e.title}: ${look.plateIs}.",
                    modifier = Modifier.fillMaxWidth().aspectRatio(plateAspect).clip(PlateShape),
                )
                Spacer(Modifier.height(16.dp))
            }
            BasicText(
                text = look.label,
                style = cvType.metaMono.copy(color = if (look.kickerAccent) colors.accent else colors.muted),
            )
            Spacer(Modifier.height(10.dp))
            // The colour is stated, not inherited, and that is load-bearing on the kept page: its
            // paper ground would otherwise sit under the ink world's cream at 1.08:1.
            BasicText(
                text = e.title,
                style = cvType.cardTitle.copy(color = colors.onBackground),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = if (e.system.isBlank()) e.planet else "${e.planet} · ${e.system}",
                style = cvType.metaMono,
            )
            Spacer(Modifier.height(12.dp))
            BasicText(text = e.blurb, style = cvType.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4. The Tellers
// ---------------------------------------------------------------------------------------------

private fun seasonOfKey(k: String): Int = k.substringAfter('s').substringBefore('-').toIntOrNull() ?: 0

private fun idxOfKey(k: String): Int = k.substringAfter('-').toIntOrNull() ?: 0

private fun LazyListScope.tellersLayer(columns: Int, uri: UriHandler) {
    item("tellers-intro") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = 28.dp)) {
                BasicText(
                    modifier = Modifier.widthIn(max = 680.dp),
                    text =
                        "A god or a monster reaches us as a mark, the shape its name makes. A person " +
                            "reaches us as a face, because the people are the reason any of it survived " +
                            "to be written down.",
                    style = cvType.body.copy(color = cvColors.muted),
                )
                Spacer(Modifier.height(12.dp))
                // Stated on the page, not only in a comment, because the roll is visibly shorter
                // than the web's and a reader is owed the reason.
                MonoMeta(
                    "// the deliberate blanks are not on this roll yet: they live in the React route " +
                        "file rather than in the corpus, and a Kotlin transcript of them would be a " +
                        "second copy no generator refreshes",
                )
            }
        }
    }

    anthology.seasons.forEach { s -> seasonRoll(s, columns, uri) }
}

/**
 * A teller files under the season they first tell in, once, however many pages they carry, so the
 * grouping is derived from the witness's own entry key ("s1-04" style). A season with no records
 * renders nothing rather than an empty heading, which is how a fourth season groups itself.
 *
 * The web interleaves nine argued absences and two unplaceable names into this roll by entry index,
 * because a slot left empty on purpose is a finding and omitting it reads as an oversight. Those
 * eleven records are hand-written constants inside anthology.tsx, because src/data/anthology.ts is
 * generated and does not carry them, so they are absent here rather than transcribed. The fix is
 * upstream: when the generator grows an `absences` field, this function reads it and interleaves.
 */
private fun LazyListScope.seasonRoll(
    season: AnthologySeason,
    columns: Int,
    uri: UriHandler,
) {
    val tellers =
        anthology.witnesses
            .filter { w -> w.entries.firstOrNull()?.let { seasonOfKey(it) } == season.n }
            .sortedBy { w -> w.entries.firstOrNull()?.let { idxOfKey(it) } ?: 0 }
    if (tellers.isEmpty()) return

    item("roll-${season.n}-head") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = CvSectionGap / 2)) {
                SectionHeading(season.title)
                Spacer(Modifier.height(8.dp))
                MonoMeta("${tellers.size} tellers")
            }
        }
    }

    gridItems("roll-${season.n}", tellers, columns, key = { it.id }) { witness, _ ->
        TellerCard(witness, uri)
    }
}

@Composable
private fun TellerCard(w: AnthologyWitness, uri: UriHandler) {
    val colors = cvColors
    // Resolved from the record's own entry keys rather than by scanning the corpus for an entry
    // that kept a copy of this witness: that scan is outright wrong the moment one teller carries
    // several pages. The first key is the home page, the count is the rest.
    val home = w.entries.firstNotNullOfOrNull { key -> anthologyEntries.firstOrNull { it.entryKey() == key } }
    CvCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = home?.let { entry -> { openRead(entry.slug, uri) } },
    ) {
        // The 1100x600 rendering, streamed. The null branch is the web's other state and not a
        // failure: a record whose art has not been drawn yet ships with `art: ""`, and every teller
        // on the roll today has one, so it degrades to the type-only card rather than carrying an
        // "Awaiting rendering" box that nothing can currently reach.
        val art = plateUrl(w.art)
        if (art != null) {
            ProjectShot(
                url = art,
                label = "Rendered portrait of ${w.name}. ${w.did}",
                modifier = Modifier.fillMaxWidth().aspectRatio(portraitAspect).clip(PlateShape),
            )
            Spacer(Modifier.height(16.dp))
        }
        BasicText(text = w.name, style = cvType.cardTitle.copy(color = colors.onBackground))
        val of = w.of
        if (of != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(text = of, style = cvType.metaMono)
        }
        Spacer(Modifier.height(12.dp))
        BasicText(text = w.did, style = cvType.bodySmall)
        if (w.entries.size > 1) {
            Spacer(Modifier.height(12.dp))
            MonoMeta("TELLS IN ${w.entries.size} ENTRIES")
        }
    }
}

/** `s1-04`: the key a witness record files itself under. */
private fun AnthologyEntry.entryKey(): String = "s$season-${idx.toString().padStart(2, '0')}"

// ---------------------------------------------------------------------------------------------
// 5. Unfiled, and the sibling series
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.unfiledLayer(columns: Int, uri: UriHandler) {
    item("unfiled-intro") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = 28.dp)) {
                BasicText(
                    modifier = Modifier.widthIn(max = 680.dp),
                    text =
                        "Filed under no season and no series. The Directory has a form for work in this " +
                            "position and the form has a field for the designation, and the field is " +
                            "filled in the way that field is always filled in when nobody has decided yet.",
                    style = cvType.body.copy(color = cvColors.muted),
                )
                if (unfiledPieces.isEmpty()) {
                    // Not decoration: the lane ships before anything is guaranteed to be in it, and
                    // an empty grid with no words in it reads as a broken page.
                    Spacer(Modifier.height(24.dp))
                    MonoMeta("Nothing here yet.")
                }
            }
        }
    }

    gridItems("unfiled", unfiledPieces, columns, key = { it.slug }) { p, _ ->
        CvCard(modifier = Modifier.fillMaxWidth(), onClick = { openRead(p.slug, uri) }) {
            // The designation, printed exactly as the frontmatter carries it. The corpus uses
            // square brackets for a value a form requires and nobody has filled in, so
            // "[unassigned]" IS the answer rather than a missing one.
            MonoMeta(p.series)
            Spacer(Modifier.height(10.dp))
            BasicText(text = p.title, style = cvType.cardTitle.copy(color = cvColors.onBackground))
            Spacer(Modifier.height(12.dp))
            BasicText(text = p.blurb, style = cvType.bodySmall)
            Spacer(Modifier.height(14.dp))
            MonoMeta("${p.words.grouped()} words")
        }
    }
}

private fun LazyListScope.siblingLayer(columns: Int, uri: UriHandler) {
    siblingSeries.forEach { series -> siblingSeriesBlock(series, columns, uri) }
}

/**
 * A separate work in the same universe, not a fifth season, and the data shape is the argument:
 * four seasons and forty-eight entries are printed on four pages and asserted on both sides of the
 * registry hop, so `siblingSeries` is its own array. It carries its own medium line, because the
 * medium is the whole point of the distinction. The parent's four are broadcast, never sent,
 * destroyed and executed; this one is retrieval, the only one where somebody asked.
 */
private fun LazyListScope.siblingSeriesBlock(
    series: SiblingSeries,
    columns: Int,
    uri: UriHandler,
) {
    item("sibling-${series.slug}") {
        Reveal {
            Column(Modifier.pageMeasure().padding(top = 28.dp)) {
                MonoMeta(series.medium.uppercase())
                Spacer(Modifier.height(10.dp))
                SectionHeading(series.title)
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = series.tagline,
                    modifier = Modifier.widthIn(max = 680.dp),
                    style = cvType.body.copy(color = cvColors.muted),
                )
            }
        }
    }

    gridItems("sibling-${series.slug}-entries", series.entries, columns, key = { it.slug }) { e, _ ->
        CvCard(modifier = Modifier.fillMaxWidth(), onClick = { openRead(e.slug, uri) }) {
            val plate = plateUrl(e.plate)
            if (plate != null) {
                ProjectShot(
                    url = plate,
                    // The medium IS the distinction this series exists to make, so the description
                    // says which one it is rather than repeating the word "plate" ten times.
                    label = "Retrieval plate for ${e.title}: a ${series.medium} file from ${series.title}.",
                    modifier = Modifier.fillMaxWidth().aspectRatio(plateAspect).clip(PlateShape),
                )
                Spacer(Modifier.height(16.dp))
            }
            MonoMeta("REQUEST ${e.idx.toString().padStart(2, '0')}")
            Spacer(Modifier.height(10.dp))
            BasicText(text = e.title, style = cvType.cardTitle.copy(color = cvColors.onBackground))
            Spacer(Modifier.height(12.dp))
            BasicText(text = e.blurb, style = cvType.bodySmall)
            Spacer(Modifier.height(14.dp))
            MonoMeta("${e.words.grouped()} words")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 6. The Map
// ---------------------------------------------------------------------------------------------

/** The Directory's own count of Concluded worlds: 611 on the day of the case's first page, 671 at the end. */
private const val concludedStart = 611
private const val concludedEnd = 671

private const val fieldSeed = 20260815
private const val fieldRadiusMin = 240f
private const val fieldRadiusMax = 800f
private const val fieldYSquash = 0.6f

/** The states a world can be in, and their colours. Same values as `STATE_COLOR` in Starmap.tsx. */
private val StateColor: Map<String, Color> =
    mapOf(
        "lit" to cvColor("#8FD3FF"),
        "open" to cvColor("#7EE787"),
        "concluded" to cvColor("#39424E"),
        "ruin" to cvColor("#8A6A2F"),
        "self" to cvColor("#D9A441"),
        "withdrawn" to cvColor("#A85A38"),
    )

private val FieldLitColor: Color = cvColor("#c9d6e3")

private val StateLegendRows: List<Pair<String, String>> =
    listOf(
        "Lit" to "Filed. The entry it explains is on that season's grid.",
        "Open" to "The one open file in the sky, still unresolved.",
        "Concluded" to "Closed once the count reaches its number.",
        "Ruin" to "Concluded, and the record itself did not survive.",
        "Self" to "The Directory. Him.",
        "Withdrawn" to
            "The page burned. The world is still there. The count never reaches it, because the " +
                "Directory never had it to file.",
    )

/**
 * The map layer.
 *
 * WHAT IS LOST, precisely: on the web this is a lazy-loaded three.js scene the reader orbits with a
 * drag, with a label floating beside every named world and a range input running the Concluded
 * count. Compose draws on a 2D canvas, so the orbit is gone and this is one fixed orthographic
 * projection of exactly the same coordinates. Nothing is invented and nothing is faked into three
 * dimensions. The labels come off the canvas and become the register below it, which is the only
 * version of them a screen reader could ever have read.
 *
 * The range input becomes its two ends, and that is an accessibility answer rather than a shortcut:
 * a drag handle painted on a canvas is operable by mouse and by nothing else. The count animates
 * between them, so the sixty worlds still go out one after another instead of blinking.
 */
@Composable
private fun StarmapLayer() {
    val colors = cvColors
    var target by remember { mutableStateOf(concludedStart) }
    val reduced = LocalReducedMotion.current
    val concluded by
        animateIntAsState(
            targetValue = target,
            animationSpec = tween(if (reduced) 0 else 1600),
            label = "concluded",
        )

    Column(Modifier.pageMeasure().padding(top = 28.dp)) {
        BasicText(
            modifier = Modifier.widthIn(max = 680.dp),
            text =
                "The Directory's count of Concluded worlds runs from six hundred and eleven to six " +
                    "hundred and seventy-one, and the sky goes out behind him.",
            style = cvType.body.copy(color = colors.muted),
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoMeta("CONCLUDED COUNT")
            Spacer(Modifier.width(16.dp))
            LayerPill(
                label = concludedStart.grouped(),
                selected = target == concludedStart,
                onSelect = { target = concludedStart },
            )
            Spacer(Modifier.width(8.dp))
            LayerPill(
                label = concludedEnd.grouped(),
                selected = target == concludedEnd,
                onSelect = { target = concludedEnd },
            )
        }

        Spacer(Modifier.height(20.dp))
        Starfield(concluded)

        Spacer(Modifier.height(28.dp))
        StateLegend()

        Spacer(Modifier.height(28.dp))
        WorldRegister(concluded)
    }
}

/**
 * A tiny deterministic LCG (Numerical Recipes' constants), so the sky is the same sky on every
 * render and on every platform. Kotlin's Int multiply wraps exactly as `Math.imul` does, which is
 * why the two builds produce the same 671 points rather than merely similar ones.
 */
private const val lcgMultiplier = 1664525
private const val lcgIncrement = 1013904223
private const val uint32Mask = 0xFFFFFFFFL
private const val uint32Span = 4294967296f

private class Lcg(seed: Int) {
    private var state: Int = seed

    fun next(): Float {
        state = state * lcgMultiplier + lcgIncrement
        return (state.toLong() and uint32Mask).toFloat() / uint32Span
    }
}

/** Uniform points on a sphere shell, flattened on y so the field reads as a wide sky. */
private val fieldPoints: List<Offset> =
    run {
        val rand = Lcg(fieldSeed)
        List(concludedEnd) {
            val theta = rand.next() * 2f * PI.toFloat()
            val phi = acos(2f * rand.next() - 1f)
            val radius = fieldRadiusMin + rand.next() * (fieldRadiusMax - fieldRadiusMin)
            Offset(radius * sin(phi) * cos(theta), radius * cos(phi) * fieldYSquash)
        }
    }

/** World-space position of a named world: its system's centre plus its own offset. */
private fun StarWorld.position(): Offset? {
    val sys = anthology.starmap.systems[system] ?: return null
    val off = offset ?: return null
    if (sys.size < 2 || off.size < 2) return null
    return Offset((sys[0] + off[0]).toFloat(), (sys[1] + off[1]).toFloat())
}

/**
 * A world with a `darkAt` stays lit until the count reaches it and only then falls to its own
 * state. Ported verbatim from `effectiveState()`: the count on screen and the number of dark
 * points are the same number by construction, not by coincidence.
 */
private fun StarWorld.effectiveState(concluded: Int): String {
    val at = darkAt ?: return state
    return if (concluded < at) "lit" else state
}

private const val mapScale = 460f

@Composable
private fun Starfield(concluded: Int) {
    val colors = cvColors
    val shape = RoundedCornerShape(16.dp)
    val worlds = anthology.starmap.worlds
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .background(colors.deepVoid.copy(alpha = 0.6f), shape)
            .border(1.dp, colors.line, shape)
            .semantics {
                contentDescription =
                    "A flat projection of the Directory's sky. ${fieldPoints.size} anonymous worlds " +
                        "behind ${worlds.size} named ones, with $concluded of the anonymous field " +
                        "gone dark. Every named world is listed below."
            },
    ) {
        val half = min(size.width, size.height) / 2f
        val k = half / mapScale
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun project(p: Offset) = Offset(cx + p.x * k, cy - p.y * k)

        // The field. Anything projecting outside the frame simply is not drawn, which reads as a
        // sky wider than the window because that is what it is.
        fieldPoints.forEachIndexed { i, p ->
            val at = project(p)
            val dark = i < concluded
            drawCircle(
                color = if (dark) StateColor.getValue("concluded") else FieldLitColor.copy(alpha = 0.55f),
                radius = if (dark) 1.1f else 1.5f,
                center = at,
            )
        }

        // The pairings. A fence ties two worlds that a later season took apart.
        anthology.starmap.fences.forEach { pair ->
            if (pair.size < 2) return@forEach
            val a = worlds.firstOrNull { it.name == pair[0] }?.position()?.let(::project)
            val b = worlds.firstOrNull { it.name == pair[1] }?.position()?.let(::project)
            if (a != null && b != null) {
                drawLine(
                    color = colors.accent2.copy(alpha = 0.35f),
                    start = a,
                    end = b,
                    strokeWidth = 1f,
                    pathEffect = dashes(),
                )
            }
        }

        // The named worlds, over everything, with a halo so a near-black `concluded` world is still
        // findable against the field.
        worlds.forEach { w ->
            val at = w.position()?.let(::project) ?: return@forEach
            val color = StateColor[w.effectiveState(concluded)] ?: colors.muted
            drawCircle(color = colors.ink, radius = 6.5f, center = at)
            drawCircle(color = color, radius = 4f, center = at)
            drawCircle(
                color = color.copy(alpha = 0.35f),
                radius = 8f,
                center = at,
                style = Stroke(width = 1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StateLegend() {
    val colors = cvColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateLegendRows.forEach { (term, desc) ->
            Row(Modifier.widthIn(min = 240.dp, max = 440.dp), verticalAlignment = Alignment.Top) {
                // Decorative: the term beside it is the channel that carries the state, so the
                // swatch is never the only one.
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(9.dp)
                        .background(StateColor[term.lowercase()] ?: colors.muted, CircleShape)
                        .border(1.dp, colors.line, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    BasicText(
                        text = term,
                        style = cvType.bodySmall.copy(
                            color = colors.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    BasicText(text = desc, style = cvType.bodySmall)
                }
            }
        }
    }
}

/**
 * The register. On the web every named world carries a floating label in the 3D scene and reveals
 * its `detail` on hover; a flat canvas cannot place twenty-four labels without collisions, and a
 * hover reveal is unreachable by keyboard anyway, so both become rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldRegister(concluded: Int) {
    val colors = cvColors
    Column {
        MonoMeta("THE NAMED WORLDS")
        Spacer(Modifier.height(14.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            anthology.starmap.worlds.forEach { w ->
                val state = w.effectiveState(concluded)
                Column(Modifier.widthIn(min = 240.dp, max = 320.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = w.name,
                            style = cvType.bodySmall.copy(
                                color = colors.onBackground,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        TagChip(text = state)
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText(text = "${w.system} · ${w.detail}", style = cvType.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared parts
// ---------------------------------------------------------------------------------------------

/**
 * A responsive grid inside a [LazyColumn]: one lazy row per grid row, keyed on the first cell, so
 * forty-eight entry cards cost forty-eight compositions at most and sixteen in practice.
 *
 * Chunking rather than LazyVerticalGrid: a grid nested in a column has to be given a height, and
 * every honest answer to "how tall" is either a second measurement pass or a hardcoded number.
 */
private fun <T> LazyListScope.gridItems(
    idPrefix: String,
    items: List<T>,
    columns: Int,
    key: (T) -> String,
    cell: @Composable (T, Int) -> Unit,
) {
    val rows = items.chunked(columns)
    rows.forEachIndexed { rowIndex, row ->
        item("$idPrefix-${key(row.first())}") {
            Row(
                modifier = Modifier.pageMeasure().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                row.forEachIndexed { colIndex, entry ->
                    // The weight is on the outside: Modifier.weight is a RowScope extension and
                    // Reveal takes a plain content lambda, so applying it inside would not compile.
                    Box(Modifier.weight(1f)) {
                        Reveal(delayMillis = colIndex * 80) { cell(entry, rowIndex * columns + colIndex) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun dashes(): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

/**
 * `Number.prototype.toLocaleString()`, minus the locale. `kotlin.text` has no grouping formatter on
 * every target, and the site prints these figures with commas.
 *
 * ponytail: no negatives in this corpus, so no sign handling.
 */
internal fun Int.grouped(): String = toString().reversed().chunked(3).joinToString(",").reversed()

/**
 * `/read/$slug` is not a route in this build. Every hash and path on the React site that this port
 * does not serve resolves against the live site instead of silently doing nothing, which is the same
 * contract `ProjectDetailScreen.openLink` already holds.
 */
internal fun openRead(slug: String, uri: UriHandler) {
    uri.openUri(profile.portfolio.trimEnd('/') + "/read/" + slug)
}

/** `600x780` for a field plate, the size every entry and sibling plate is painted at. */
internal const val plateAspect = 600f / 780f

/** `1100x600` for a teller. Landscape, because a rendering is a scene and not a headshot. */
internal const val portraitAspect = 1100f / 600f

/** Inside [CvCard]'s own 24dp padding, so the plate needs its own corner rather than the card's. */
internal val PlateShape: RoundedCornerShape = RoundedCornerShape(8.dp)

/**
 * The live URL for a plate or a portrait, from the site-relative `.jpg` path the corpus records.
 *
 * Streamed, not shipped. [com.siddharth.cv.shared.media.ProjectShot] already does exactly this for
 * project shots and for the Excelsior scans, so this is a URL rule and not a second image pipeline:
 * ninety-three plates and portraits would be megabytes on a wasm bundle a visitor pays for before
 * they have scrolled to one, and the React repo's own daily media job keeps the origin current.
 *
 * `.webp` and never `.avif`, the same constraint `CvGallery` and `/excelsior` are built around:
 * skiko ships no AVIF decoder and an avif URL renders blank with nothing in the log. Every path the
 * corpus carries has a `.webp` sibling on the origin; [anthologySelfCheck] asserts the extension
 * swap rather than the file count, because nothing in this build can see the other repo.
 *
 * Blank is a real state and not an error: `gen-anthology.mjs` records `""` when a fetch failed
 * rather than reusing a stale image, so blank returns null and the caller draws type only.
 */
internal fun plateUrl(path: String): String? =
    if (path.isBlank()) {
        null
    } else {
        profile.portfolio.trimEnd('/') + path.substringBeforeLast('.') + ".webp"
    }

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, the same shape `navSelfCheck` and
 * `labScreenSelfCheck` already have. Must be called from `selfCheck()` in jvmMain's Prerender.kt:
 * nothing here runs it, and that file belongs to the spine.
 *
 * It guards the four derivations these two screens are built out of, and every one of them fails
 * silently rather than loudly if it breaks: a witness key that resolves to nothing drops a teller
 * off the roll with no gap where it was, a `darkAt` read the wrong way round makes the sky go out
 * one world early, and a spoiler partition keyed off a season number instead of `spoils` leaks an
 * ending onto an open page.
 */
internal fun anthologySelfCheck() {
    // The corpus the masthead counts.
    check(anthologyEntries.isNotEmpty()) { "no entries" }
    check(firstEntry != null && shortestEntry != null) { "season one has no door" }
    check(totalWords > 0 && readingHours > 0) { "the door prints a zero" }

    // Grouping. `1804` is printed as `1,804` on both builds or the two pages disagree.
    check(1804.grouped() == "1,804") { "grouped(): ${1804.grouped()}" }
    check(671.grouped() == "671") { "grouped() padded a three-digit number" }
    check(1000000.grouped() == "1,000,000") { "grouped(): ${1000000.grouped()}" }

    // Witness keys. A key that resolves to no entry is a teller with no home page, and the roll
    // renders it as an unclickable card rather than saying anything is wrong.
    anthology.witnesses.forEach { w ->
        val key = w.entries.firstOrNull()
        check(key != null) { "${w.id}: no entry key, so it files under no season and never renders" }
        check(seasonOfKey(key) in 1..anthology.seasons.size) { "${w.id}: key '$key' has no season" }
        check(anthologyEntries.any { it.entryKey() == key }) { "${w.id}: key '$key' resolves to no entry" }
    }

    // The plates. An `.avif` URL is the one failure here that is completely silent: skiko decodes
    // nothing, logs nothing, and the card degrades to the gradient floor forever. So assert the
    // extension swap on the two shapes the corpus actually carries, and assert that no entry,
    // sibling or teller ends up pointed at a path this rule cannot rewrite.
    check(plateUrl("/p/anthology/plates/s1-01-x.jpg") == "${profile.portfolio}/p/anthology/plates/s1-01-x.webp") {
        "plateUrl() did not swap a plate to webp: ${plateUrl("/p/anthology/plates/s1-01-x.jpg")}"
    }
    check(plateUrl("/p/anthology/witnesses/ossul.jpg")?.endsWith(".webp") == true) {
        "plateUrl() did not swap a portrait to webp"
    }
    check(plateUrl("") == null) { "a lost plate resolved to a URL instead of to nothing" }
    val plateSources =
        anthologyEntries.map { it.plate } +
            siblingSeries.flatMap { s -> s.entries.map { it.plate } } +
            anthology.witnesses.map { it.art }
    plateSources.forEach { path ->
        val url = plateUrl(path) ?: return@forEach
        check(url.endsWith(".webp")) { "'$path' resolves to $url, which skiko cannot decode" }
        check(url.startsWith(profile.portfolio)) { "'$path' resolves off the portfolio origin" }
    }

    // The kept page is an exception inside season three's own row, not a season of its own.
    val kept = anthologyEntries.filter { it.season == 3 && it.kindling == kindlingFinale }
    check(kept.size == 1) { "season three has ${kept.size} kept pages" }
    check(entryLook(kept.first()).colors == KeptPaperColors) { "the kept page is not on paper" }
    check(
        anthologyEntries.filter { it.season == 3 }.none {
            it != kept.first() && entryLook(it).colors == KeptPaperColors
        },
    ) { "paper leaked onto a burned page" }

    // The sky. A world with a darkAt is lit until the count reaches it, and only then falls.
    anthology.starmap.worlds.forEach { w ->
        val at = w.darkAt ?: return@forEach
        check(w.effectiveState(at - 1) == "lit") { "${w.name} went dark before the count reached it" }
        check(w.effectiveState(at) == w.state) { "${w.name} never falls to its own state" }
    }
    check(fieldPoints.size == concludedEnd) { "the field is not the slider's ceiling" }

    // The spoiler partition, which is the one thing on /canon that must never be keyed off a
    // season number: a gated season's laws stay inside its own block.
    val gatedLaws = seasonCanon.values.filter { it.spoils != null }.flatMap { it.laws }
    val openLaws = seasonCanon.values.filter { it.spoils == null }.flatMap { it.laws }
    check(openLaws.isNotEmpty()) { "no law is safe to show" }
    check(gatedLaws.none { law -> openLaws.any { it.n == law.n } }) { "a gated law leaked above the line" }
}
