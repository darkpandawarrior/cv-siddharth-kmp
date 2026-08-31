// The file is named for what it does; detekt would rather it were named for the one type it
// happens to declare, which would be a worse name for a parser.
@file:Suppress("MatchingDeclarationName")

package com.siddharth.cv.shared.read

import com.siddharth.cv.shared.data.generated.printedPieces

/**
 * The markdown renderer /read needs, which is two constructs.
 *
 * THE README CLASSIFIED THIS ROUTE AS BLOCKED ON "a markdown renderer, a subsystem, not an
 * afternoon". That was measured wrong. The React route mounts `react-markdown` + `remark-gfm`
 * because it also serves the anthology, whose entries carry real GFM tables, thematic rules and
 * apparatus lists. The nine PRINTED pieces this build's archive corpus actually holds carry two
 * things and nothing else: a `> ` blockquote (nine of them, one per piece, and it is always the
 * blurb set as an epigraph) and `*italic*` (two, both in Prophecy #201112003, both whole lines
 * marking a time skip). No headings, no bold, no lists, no links, no code, no rules, no tables.
 * So the renderer is this file, and [unsupportedMarkdown] is what keeps that true: the moment a
 * tenth piece arrives carrying a construct nothing here draws, [readParseSelfCheck] fails at
 * build time rather than the page shipping a literal `**` to a reader.
 *
 * Deliberately NOT a markdown library and deliberately not CommonMark. It implements the subset
 * the corpus uses, it states its own ceiling, and it refuses to guess past it.
 *
 * TWO CHARACTERS OF PLUMBING THAT ARE NOT MARKDOWN AT ALL.
 *  - U+2028 LINE SEPARATOR. Four of the nine pieces were pasted out of a word processor and use it
 *    as their internal line break: 78 of them in Deadline, 52 in Pointer Games, which by itself is
 *    the difference between a readable page and three unbroken walls of text. CSS Text treats it as
 *    a forced break, so the browser has been drawing those breaks all along without markdown ever
 *    being involved. [parsePiece] turns it into a real newline inside the block, where Compose
 *    breaks on it the same way. Thirty-seven of them are doubled, which is a blank line, which is
 *    the paragraph gap the writer meant.
 *  - A single newline between two lines of one paragraph is a SOFT break in markdown and collapses
 *    to a space, which is what a browser does with it too. The corpus has none today (every
 *    paragraph is one source line) and it is handled anyway, because getting it wrong would only
 *    ever show up as a mangled page rather than an error.
 */

/**
 * One rendered block: a paragraph, or a blockquote.
 *
 * Colour-free on purpose. [italics] are character ranges into [text], INCLUSIVE at both ends the
 * way `String.substring(IntRange)` reads them, so the parse can be asserted by
 * [readParseSelfCheck] without a composition, a theme or a font in the room. ReadScreen.kt is the
 * only thing that knows what italic looks like.
 */
internal data class ProseBlock(
    val text: String,
    val italics: List<IntRange>,
    val quote: Boolean,
)

/** U+2028, the word processor's line break. See this file's header. */
private const val LINE_SEPARATOR = '\u2028'

/**
 * Split [body] into the blocks a reader scrolls through.
 *
 * A run of `>` lines is one blockquote; a run of non-blank, non-quote lines is one paragraph; a
 * blank line, or the switch between those two kinds, ends whatever was open. That last clause is
 * the one worth stating: a quote that is not followed by a blank line still ends at the first line
 * that is not part of it.
 */
internal fun parsePiece(body: String): List<ProseBlock> {
    val lines = body.split('\n')
    val blocks = mutableListOf<ProseBlock>()
    var i = 0
    while (i < lines.size) {
        if (lines[i].isBlank()) {
            i++
            continue
        }
        val quote = isQuote(lines[i])
        val run = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() && isQuote(lines[i]) == quote) {
            run += if (quote) lines[i].trimStart().removePrefix(">").trim() else lines[i].trim()
            i++
        }
        // Soft breaks fold to a space; U+2028 survives as a real break. Trimming last, so a
        // paragraph that opens or closes on a separator does not start with a blank line.
        val text = run.joinToString(" ").replace(LINE_SEPARATOR, '\n').trim()
        if (text.isNotEmpty()) {
            val (plain, italics) = emphasise(text)
            blocks += ProseBlock(plain, italics, quote)
        }
    }
    return blocks
}

private fun isQuote(line: String): Boolean = line.trimStart().startsWith(">")

/**
 * Strip `*` pairs out of [raw] and report where the emphasis landed.
 *
 * AN UNCLOSED MARKER IS A LITERAL ASTERISK. That is the failure mode worth designing against: a
 * naive "open at the first star, close at the end" swallows the entire rest of the piece into one
 * italic run, silently, on a page nobody re-reads after it ships. An opener with no closer, or with
 * nothing but whitespace between the two, is just a character here, and the scan carries on past
 * it. Same reading CommonMark arrives at, without CommonMark's delimiter stack: the corpus has no
 * nesting to resolve.
 */
private fun emphasise(raw: String): Pair<String, List<IntRange>> {
    if ('*' !in raw) return raw to emptyList()
    val out = StringBuilder(raw.length)
    val italics = mutableListOf<IntRange>()
    var i = 0
    while (i < raw.length) {
        val close = if (raw[i] == '*') closingStar(raw, i) else -1
        if (close > i + 1 && raw.substring(i + 1, close).isNotBlank()) {
            val start = out.length
            out.append(raw, i + 1, close)
            italics += start until out.length
            i = close + 1
        } else {
            out.append(raw[i])
            i++
        }
    }
    return out.toString() to italics
}

/**
 * The `*` that closes the one at [open], or -1.
 *
 * Emphasis may not cross a line break here. CommonMark would let it, over a soft break, but by the
 * time this runs every break in the string is a HARD one that the writer put there, and every pair
 * in the corpus sits between two of them. Stopping at the break is what keeps one stray asterisk
 * from italicising the paragraph under it.
 */
private fun closingStar(raw: String, open: Int): Int {
    val at = raw.indexOf('*', open + 1)
    if (at < 0) return -1
    val brk = raw.indexOf('\n', open + 1)
    return if (brk in 0 until at) -1 else at
}

/**
 * The first markdown construct in [body] that this file does not render, or `null` when the piece
 * is inside the subset.
 *
 * This is the guard that makes "the renderer is two constructs" a fact rather than a claim from a
 * measurement taken once. It reads the SOURCE, before any parse, so a construct that the parser
 * would quietly pass through as plain text is still caught.
 */
internal fun unsupportedMarkdown(body: String): String? {
    for (raw in body.split('\n')) {
        val line = raw.trimStart().removePrefix(">").trim()
        if (line.isEmpty()) continue
        val kind = when {
            "**" in line -> "bold"
            line.startsWith("#") && line.trimStart('#').startsWith(" ") -> "heading"
            ListItem.containsMatchIn(line) -> "list item"
            ThematicBreak.matches(line) -> "thematic break"
            "](" in line -> "link"
            '`' in line -> "code"
            line.startsWith("|") -> "table row"
            else -> null
        }
        if (kind != null) return "$kind in: ${line.take(unsupportedExcerpt)}"
    }
    return null
}

private const val unsupportedExcerpt = 60
private val ListItem = Regex("""^([-+*]|\d+\.)\s""")
private val ThematicBreak = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

// ---------------------------------------------------------------------------------------------
// Self-check
// ---------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, the same shape `navSelfCheck` and
 * `mermaidParseSelfCheck` already have. Must be called from `selfCheck()` in jvmMain's
 * Prerender.kt: nothing here runs it, and that file belongs to the spine.
 *
 * A parser is the one thing on this page that can corrupt it in silence. Every case below is a way
 * that has actually happened to somebody: an off-by-one that italicises the space beside the word,
 * an unclosed marker that eats the rest of the piece, a quote that never closes and swallows the
 * prose under it, and a corpus that grows a construct the renderer was never told about.
 */
internal fun readParseSelfCheck() {
    // Emphasis lands on the exact characters, and the markers are gone from the text.
    val mid = parsePiece("a *b c* d").single()
    check(mid.text == "a b c d") { "markers are consumed: ${mid.text}" }
    check(mid.italics == listOf(2..4)) { "italic range: ${mid.italics}" }
    check(mid.text.substring(mid.italics.single()) == "b c") { "the span covers the emphasised words" }

    // The corpus's real shape: a whole line, marker to marker.
    val whole = parsePiece("*1 Day Later*").single()
    check(whole.text == "1 Day Later" && whole.italics == listOf(0..10)) { "whole-line italic" }

    // An unclosed marker is a character, not a mode. The block keeps its asterisk, carries no span,
    // and the paragraph AFTER it still parses: this is the swallow-the-rest failure.
    val unclosed = parsePiece("a *b c\n\nnext para")
    check(unclosed.size == 2) { "an unclosed marker must not consume the following block" }
    check(unclosed[0].text == "a *b c" && unclosed[0].italics.isEmpty()) { "rendered literally" }
    check(unclosed[1].text == "next para") { "the next block survives" }
    check(parsePiece("* *").single().let { it.text == "* *" && it.italics.isEmpty() }) {
        "a pair with only whitespace between it is not emphasis"
    }
    val crossing = parsePiece("a *b\u2028c* d").single()
    check(crossing.text == "a *b\nc* d" && crossing.italics.isEmpty()) {
        "emphasis may not cross a line break: ${crossing.text}"
    }

    // A blockquote ends at a blank line AND at the first line that is not part of it.
    val quotes = parsePiece("> quoted\n\nprose\n\n> two")
    check(quotes.map { it.quote } == listOf(true, false, true)) { "quote, prose, quote" }
    check(quotes.map { it.text } == listOf("quoted", "prose", "two")) { "markers stripped: $quotes" }
    val tight = parsePiece("> q\nprose")
    check(tight.size == 2 && tight[0].quote && !tight[1].quote) { "a quote ends without a blank line" }
    check(parsePiece("> a\n> b").single().text == "a b") { "consecutive quote lines are one quote" }

    // U+2028 is a line break inside one block, never a new block. The doubled form is the blank
    // line four of the nine pieces use for a paragraph gap.
    check(parsePiece("one\u2028two").single().text == "one\ntwo") { "U+2028 breaks the line" }
    check(parsePiece("one\u2028\u2028two").single().text == "one\n\ntwo") { "doubled U+2028 is a gap" }
    check(parsePiece("\u2028one\u2028").single().text == "one") { "a leading or trailing break is trimmed" }
    check(parsePiece("one\ntwo").single().text == "one two") { "a soft break folds to a space" }

    // The corpus stays inside the subset this file renders, and every piece opens the way /read
    // assumes it does.
    check(printedPieces.isNotEmpty()) { "the printed archive is empty" }
    printedPieces.forEach { p ->
        val unsupported = unsupportedMarkdown(p.body)
        check(unsupported == null) {
            "${p.slug} carries markdown this reader does not draw: $unsupported. " +
                "Extend InlineMarkdown.kt rather than letting the page ship the raw marks."
        }
        val blocks = parsePiece(p.body)
        check(blocks.first().quote && blocks.first().text == p.blurb) {
            "${p.slug} must open on its blurb, set as the epigraph"
        }
        check(blocks.none { '*' in it.text }) { "${p.slug} shipped a literal asterisk into the prose" }
        check(blocks.count { it.quote } == 1) { "${p.slug} has one epigraph and no other quote" }
    }
}
