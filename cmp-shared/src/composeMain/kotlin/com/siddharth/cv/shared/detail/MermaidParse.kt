package com.siddharth.cv.shared.detail

import com.siddharth.cv.shared.data.projects

/**
 * A hand-rolled parser for the *exact* Mermaid subset the shipped diagrams in
 * [com.siddharth.cv.shared.data.projects] actually use. Not a Mermaid implementation.
 *
 * The inventory (grepped from cv-siddharth/src/data/profile.ts and mirrored in CvProjectData.kt):
 *
 * - headers: `graph TD` and `graph LR`, nothing else
 * - one node shape: `id["quoted label"]` — every single node, every diagram
 * - two connectors: `-->` and `-.->`, both with an optional `|"quoted label"|`
 * - chains on one line: `gps --> jit --> spk --> fus --> ...` (Mileway's location pipeline)
 * - `&` groups on both sides: `app --> t & s & tr & ap & pa & ag` (Mileway's module graph)
 * - `<br/>` inside labels for a forced line break
 * - one genuine cycle: Kursi's `s --> r --> s2` plus `s2 -.-> s`
 *
 * We support a little more than that — the other bracket shapes and the `---`/`==>` connectors —
 * because the scanner gets them almost free and a future diagram shouldn't fall back to raw source
 * over a pair of parentheses. We deliberately do *not* support `subgraph`: laying out clusters is
 * real work, nothing needs it, and [parseMermaidFlow] returning `null` gives the caller an honest
 * raw-source fallback rather than a picture that quietly drops a box.
 *
 * Failure is all-or-nothing on purpose. A half-parsed diagram renders a half-true architecture,
 * which is worse than showing the source — so any line the scanner can't account for kills the
 * whole parse. See [mermaidParseSelfCheck] for the runnable contract.
 */

/** `graph TD` vs `graph LR`. `TB`/`BT` fold into [TopDown], `RL` into [LeftRight]. */
enum class FlowDirection { TopDown, LeftRight }

/** Only [Box] appears in the real data; the rest exist so a new diagram doesn't hit the fallback. */
enum class NodeShape { Box, Round, Stadium, Diamond, Subroutine }

data class FlowNode(val id: String, val label: String, val shape: NodeShape)

data class FlowEdge(
    val from: String,
    val to: String,
    val label: String? = null,
    val dashed: Boolean = false,
    val thick: Boolean = false,
    /** `---` and `===` are undirected in Mermaid, so they draw without an arrowhead. */
    val arrow: Boolean = true,
)

data class FlowGraph(
    val direction: FlowDirection,
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
) {
    /** Not a constructor property: it's derived, and it must stay out of `equals`. */
    val byId: Map<String, FlowNode> = nodes.associateBy { it.id }
}

/**
 * Parse [source] or return `null`. `null` means "render the raw source" — never "render nothing".
 */
fun parseMermaidFlow(source: String): FlowGraph? {
    var direction: FlowDirection? = null
    val nodes = LinkedHashMap<String, FlowNode>()
    val edges = mutableListOf<FlowEdge>()

    for (rawLine in source.lineSequence()) {
        val line = rawLine.substringBefore("%%").trim().trimEnd(';').trim()
        if (line.isEmpty()) continue

        if (direction == null) {
            direction = parseHeader(line) ?: return null
            continue
        }

        // Cluster syntax would change the layout contract, so bail rather than mis-draw it.
        if (line.startsWith("subgraph") || line == "end") return null
        // Styling directives don't affect the shape of the graph — drop them and keep going.
        if (IGNORED_PREFIXES.any { line.startsWith(it) }) continue

        if (!parseStatement(line, nodes, edges)) return null
    }

    if (direction == null || nodes.isEmpty()) return null
    return FlowGraph(direction, nodes.values.toList(), edges)
}

/**
 * Longest-path layering — the "assign a rank" half of Sugiyama. Nodes come back grouped by rank in
 * declaration order, which is also the order they're drawn in within a rank.
 *
 * Cycles are the interesting case: Kursi's replay diagram loops `s2` back to `s`, so an in-degree-0
 * root doesn't exist and naive layering would spin. A DFS marks every edge that points at a node
 * still on the recursion stack as a back edge and drops it from the ranking only — the edge is
 * still drawn, it just draws *backwards*, which is exactly what "byte-for-byte replay" should look
 * like.
 *
 * Within a rank the order is then handed to [barycenterOrder] — Sugiyama's phase three. Declaration
 * order is the seed, not the answer: the shipped diagrams happen to declare siblings in a
 * good order, but a hand-authored one doesn't have to, and the sweep can only ever return an
 * arrangement with fewer [crossings] than the one it started from.
 */
fun FlowGraph.ranks(): List<List<FlowNode>> {
    val index = nodes.withIndex().associate { (i, n) -> n.id to i }
    val out = Array(nodes.size) { mutableListOf<Int>() }
    for (e in edges) {
        val a = index[e.from] ?: continue
        val b = index[e.to] ?: continue
        if (a != b) out[a] += b // a self-loop can't contribute a rank
    }

    // 0 = unvisited, 1 = on the stack, 2 = done. Iterative: wasmJs has a shallow stack and a
    // recursive DFS over an arbitrary diagram is an avoidable cliff.
    val state = IntArray(nodes.size)
    val forward = Array(nodes.size) { mutableListOf<Int>() }
    val stack = ArrayDeque<Pair<Int, Int>>() // node, next child index
    for (root in nodes.indices) {
        if (state[root] != 0) continue
        state[root] = 1
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (u, ci) = stack.removeLast()
            if (ci >= out[u].size) {
                state[u] = 2
                continue
            }
            stack.addLast(u to ci + 1)
            val v = out[u][ci]
            if (state[v] == 1) continue // back edge — drop from the ranking, keep for drawing
            forward[u] += v
            if (state[v] == 0) {
                state[v] = 1
                stack.addLast(v to 0)
            }
        }
    }

    val inDegree = IntArray(nodes.size)
    for (u in nodes.indices) for (v in forward[u]) inDegree[v]++
    val rank = IntArray(nodes.size)
    val queue = ArrayDeque<Int>()
    for (u in nodes.indices) if (inDegree[u] == 0) queue.addLast(u)
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        for (v in forward[u]) {
            if (rank[v] < rank[u] + 1) rank[v] = rank[u] + 1
            if (--inDegree[v] == 0) queue.addLast(v)
        }
    }

    val depth = (rank.maxOrNull() ?: 0) + 1
    val declared = List(depth) { r -> nodes.filterIndexed { i, _ -> rank[i] == r } }
    val ordered = barycenterOrder(this, declared.map { group -> group.map { it.id } })
    return ordered.map { ids -> ids.map { byId.getValue(it) } }
}

/**
 * Sugiyama phase three: reorder each rank so fewer edges cross, seeded by the incoming order.
 *
 * Four rounds of a down-then-up barycenter sweep. Each pass walks the ranks in turn and sorts one by
 * the mean position of its neighbours in the rank the pass just finished, so the ordering propagates
 * along the graph instead of every rank chasing a stale reference. A node with no neighbour in that
 * adjacent rank barycenters to its own current index, which leaves it where it was rather than
 * flinging it to the front.
 *
 * Two properties matter more than the crossing count itself:
 *
 * - **Deterministic.** Fixed round count, a stable sort, and a tie-break on the previous index, so
 *   the same graph gives the same picture on every run — a diagram that reshuffles between page
 *   loads is worse than one with a crossing in it.
 * - **Monotone.** Barycenter is a heuristic and can make a specific graph worse, so every
 *   intermediate arrangement is costed and the cheapest one wins, with the seed as the baseline.
 *   The output is never worse than the input.
 *
 * ponytail: no dummy nodes for rank-skipping edges, so a rank-0 → rank-2 edge votes in neither
 * band and doesn't count as crossing the rank-1 band it flies over. Real diagrams are 3-7 nodes
 * wide; add the dummy chain if one ever grows enough for that flyover to read as a crossing.
 */
private fun barycenterOrder(graph: FlowGraph, seed: List<List<String>>): List<List<String>> {
    if (seed.size < 2 || seed.all { it.size < 2 }) return seed

    val rank = HashMap<String, Int>()
    seed.forEachIndexed { r, group -> group.forEach { rank[it] = r } }
    val preds = HashMap<String, MutableList<String>>()
    val succs = HashMap<String, MutableList<String>>()
    for (e in graph.edges) {
        val a = rank[e.from] ?: continue
        val b = rank[e.to] ?: continue
        if (b - a != 1) continue // back edges bow around the outside; flyovers vote in no band
        preds.getOrPut(e.to) { mutableListOf() } += e.from
        succs.getOrPut(e.from) { mutableListOf() } += e.to
    }

    var current = seed
    var best = seed
    var bestCost = crossings(graph, seed)
    repeat(BARYCENTER_ROUNDS) {
        for (down in booleanArrayOf(true, false)) {
            current = barycenterPass(current, if (down) preds else succs, down)
            val cost = crossings(graph, current)
            if (cost < bestCost) {
                bestCost = cost
                best = current
            }
            if (bestCost == 0) return best // nothing left to win
        }
    }
    return best
}

/** ~4 rounds is where the heuristic stops improving on graphs this size; more is just churn. */
private const val BARYCENTER_ROUNDS = 4

/**
 * One directional pass. [neighbours] must point at the rank the pass reads *from* — predecessors
 * when walking down, successors when walking up — so the reference rank is always the one already
 * settled by this pass.
 */
private fun barycenterPass(
    order: List<List<String>>,
    neighbours: Map<String, List<String>>,
    down: Boolean,
): List<List<String>> {
    val out = order.map { it.toList() }.toMutableList()
    val range = if (down) 1..out.lastIndex else out.lastIndex - 1 downTo 0
    for (r in range) {
        val reference = out[if (down) r - 1 else r + 1]
        val refPos = HashMap<String, Int>()
        reference.forEachIndexed { i, id -> refPos[id] = i }
        val here = HashMap<String, Int>()
        out[r].forEachIndexed { i, id -> here[id] = i }
        // sortedBy is stable, so equal barycenters keep the previous order — the tie-break that
        // makes this reproducible.
        out[r] = out[r].sortedBy { id ->
            val positions = neighbours[id]?.mapNotNull { refPos[it] } ?: emptyList()
            if (positions.isEmpty()) here.getValue(id).toFloat()
            else positions.sum().toFloat() / positions.size
        }
    }
    return out
}

/**
 * Edge pairs that cross under [ordering] (rank index → node ids in draw order).
 *
 * The standard bilayer count: two edges cross when their endpoints are in opposite relative order.
 * Only edges between adjacent ranks are counted, matching what [barycenterOrder] can actually
 * influence — back edges are drawn as bows outside the content and can't cross a rank boundary.
 */
fun crossings(graph: FlowGraph, ordering: List<List<String>>): Int {
    val rank = HashMap<String, Int>()
    val pos = HashMap<String, Int>()
    ordering.forEachIndexed { r, group ->
        group.forEachIndexed { i, id ->
            rank[id] = r
            pos[id] = i
        }
    }
    val spans =
        graph.edges.filter { e ->
            val a = rank[e.from] ?: return@filter false
            val b = rank[e.to] ?: return@filter false
            b - a == 1
        }
    var total = 0
    for (i in spans.indices) {
        for (j in i + 1 until spans.size) {
            val e1 = spans[i]
            val e2 = spans[j]
            if (rank[e1.from] != rank[e2.from]) continue
            val dFrom = pos.getValue(e1.from) - pos.getValue(e2.from)
            val dTo = pos.getValue(e1.to) - pos.getValue(e2.to)
            if (dFrom * dTo < 0) total++
        }
    }
    return total
}

/** One flat line of prose for the screen-reader description of a canvas that has no text nodes. */
fun FlowGraph.describe(): String {
    val flat = { s: String -> s.replace('\n', ' ') }
    val links = edges.joinToString("; ") { e ->
        val from = flat(byId[e.from]?.label ?: e.from)
        val to = flat(byId[e.to]?.label ?: e.to)
        val via = e.label?.let { " (${flat(it)})" }.orEmpty()
        if (e.arrow) "$from to $to$via" else "$from and $to$via"
    }
    return if (links.isEmpty()) {
        "Diagram of ${nodes.size} steps: " + nodes.joinToString(", ") { flat(it.label) }
    } else {
        "Flow diagram. $links."
    }
}

// -------------------------------------------------------------------------------------------
// Scanner
// -------------------------------------------------------------------------------------------

private val IGNORED_PREFIXES = listOf("classDef", "class ", "style ", "linkStyle", "click ")

/** Longest opener first — `[[` must beat `[`, `((` must beat `(`. */
private val SHAPES: List<Triple<String, String, NodeShape>> = listOf(
    Triple("[[", "]]", NodeShape.Subroutine),
    Triple("([", "])", NodeShape.Stadium),
    Triple("((", "))", NodeShape.Round),
    Triple("{{", "}}", NodeShape.Diamond),
    Triple("[", "]", NodeShape.Box),
    Triple("(", ")", NodeShape.Round),
    Triple("{", "}", NodeShape.Diamond),
)

private fun parseHeader(line: String): FlowDirection? {
    val rest = when {
        line.startsWith("graph") -> line.removePrefix("graph")
        line.startsWith("flowchart") -> line.removePrefix("flowchart")
        else -> return null
    }.trim()
    return when (rest.uppercase()) {
        "TD", "TB", "BT", "" -> FlowDirection.TopDown
        "LR", "RL" -> FlowDirection.LeftRight
        else -> null
    }
}

/**
 * `term (connector term)*`, where a term is one or more `&`-joined node references. Chained
 * connectors reuse the previous right-hand group as the next left-hand group, and `&` on both
 * sides fans out to the cross product — both are Mermaid's own semantics.
 */
private fun parseStatement(
    line: String,
    nodes: LinkedHashMap<String, FlowNode>,
    edges: MutableList<FlowEdge>,
): Boolean {
    val sc = Scan(line)
    var left = sc.readTerm(nodes) ?: return false
    while (!sc.atEnd()) {
        val conn = sc.readConnector() ?: return false
        val label = sc.readEdgeLabel()
        val right = sc.readTerm(nodes) ?: return false
        for (a in left) {
            for (b in right) {
                edges += FlowEdge(a, b, label, conn.dashed, conn.thick, conn.arrow)
            }
        }
        left = right
    }
    return true
}

private class Conn(val dashed: Boolean, val thick: Boolean, val arrow: Boolean)

private class Scan(private val s: String) {
    private var i = 0

    fun atEnd(): Boolean {
        ws()
        return i >= s.length
    }

    private fun ws() {
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
    }

    private fun take(t: String): Boolean {
        if (!s.startsWith(t, i)) return false
        i += t.length
        return true
    }

    fun readTerm(nodes: LinkedHashMap<String, FlowNode>): List<String>? {
        val ids = mutableListOf<String>()
        while (true) {
            ws()
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            if (i == start) return null
            val id = s.substring(start, i)

            var declared: FlowNode? = null
            for ((open, close, shape) in SHAPES) {
                if (take(open)) {
                    val label = readLabel(close) ?: return null
                    declared = FlowNode(id, normalizeLabel(label), shape)
                    break
                }
            }
            // A bare reference never overwrites an earlier declaration; the label can appear on any
            // one mention, and in Mileway's module graph it appears on a line of its own.
            if (declared != null) nodes[id] = declared
            else if (id !in nodes) nodes[id] = FlowNode(id, id, NodeShape.Box)

            ids += id
            ws()
            if (!take("&")) return ids
        }
    }

    /** Quoted bodies are scanned to the closing quote so `reduce()` and `Room(KMP)` survive. */
    private fun readLabel(closer: String): String? {
        ws()
        if (i < s.length && (s[i] == '"' || s[i] == '\'')) {
            val quote = s[i]
            i++
            val start = i
            while (i < s.length && s[i] != quote) i++
            if (i >= s.length) return null
            val body = s.substring(start, i)
            i++
            ws()
            return if (take(closer)) body else null
        }
        val end = s.indexOf(closer, i)
        if (end < 0) return null
        val body = s.substring(i, end)
        i = end + closer.length
        return body
    }

    /**
     * Read the whole run of link characters and classify it, rather than matching a table of
     * literals. `-->`, `--->`, `-.->`, `-..->`, `==>` and `===` all fall out of the same three
     * questions, and a table would silently mis-tokenise the lengths it didn't list.
     */
    fun readConnector(): Conn? {
        ws()
        val start = i
        while (i < s.length && s[i] in "-.=>") i++
        val tok = s.substring(start, i)
        if (tok.length < 3 || tok.trimEnd('>').isEmpty()) {
            i = start
            return null
        }
        return Conn(dashed = '.' in tok, thick = '=' in tok, arrow = tok.endsWith(">"))
    }

    fun readEdgeLabel(): String? {
        ws()
        if (!take("|")) return null
        val end = s.indexOf('|', i)
        if (end < 0) return null
        val body = s.substring(i, end).trim().trim('"', '\'')
        i = end + 1
        return normalizeLabel(body).ifEmpty { null }
    }
}

/** `<br/>` is the only markup the real labels carry. Everything else is literal text. */
private fun normalizeLabel(raw: String): String =
    raw.replace("<br />", "\n")
        .replace("<br/>", "\n")
        .replace("<br>", "\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()

// -------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------

/**
 * ponytail: one runnable check instead of a test module, in the same spirit as `navSelfCheck()`.
 * The inputs are copied verbatim from CvProjectData.kt, because the only thing this parser has to
 * be right about is the strings the site actually ships.
 */
internal fun mermaidParseSelfCheck() {
    // Kursi — chained edge, edge labels, parens inside a quoted label, and a genuine cycle.
    val kursi = parseMermaidFlow(
        """graph LR
  s["GameState"] -->|"+ Intent"| r["reduce()<br/>pure · RNG in state"] --> s2["GameState'"]
  s2 -.->|"byte-for-byte replay"| s""",
    )
    checkNotNull(kursi) { "the Kursi replay diagram must parse" }
    check(kursi.direction == FlowDirection.LeftRight) { "graph LR" }
    check(kursi.nodes.map { it.id } == listOf("s", "r", "s2")) { "declaration order preserved" }
    check(kursi.byId["r"]!!.label == "reduce()\npure · RNG in state") { "<br/> becomes a newline, parens survive" }
    check(kursi.edges.size == 3) { "a --> b --> c is two edges, plus the loop back" }
    check(kursi.edges[0].label == "+ Intent") { "|\"…\"| edge label" }
    check(kursi.edges[1].label == null) { "an unlabelled link in a chain stays unlabelled" }
    check(kursi.edges[2].let { it.from == "s2" && it.to == "s" && it.dashed }) { "-.-> is dashed" }
    // The cycle must not hang the layering, and the back edge must not push `s` off rank 0.
    check(kursi.ranks().map { r -> r.map { it.id } } == listOf(listOf("s"), listOf("r"), listOf("s2"))) {
        "longest-path layering ignores the back edge"
    }

    // Mileway — standalone declarations, then `&` groups on both sides of a link.
    val mileway = parseMermaidFlow(
        """graph TD
  app[":app composition root"]
  t["feature: tracking"]
  s["feature: logging"]
  core["core: common · data · ui<br/>design system · Room(KMP)"]
  app --> t & s
  t & s --> core""",
    )
    checkNotNull(mileway) { "the Mileway module diagram must parse" }
    check(mileway.direction == FlowDirection.TopDown) { "graph TD" }
    check(mileway.byId["app"]!!.label == ":app composition root") { "a colon-leading label is not a shape" }
    check(mileway.byId["core"]!!.label.endsWith("Room(KMP)")) { "unquoted-looking parens inside a quoted label" }
    check(mileway.edges.size == 4) { "1x2 fan-out plus 2x1 fan-in" }
    check(mileway.ranks().map { r -> r.map { it.id } } == listOf(listOf("app"), listOf("t", "s"), listOf("core"))) {
        "the fan-in sinks to its own rank"
    }

    // Shapes and connector variants we support but the site doesn't use yet.
    val shapes = parseMermaidFlow("flowchart LR\n  a(Round) === b{Diamond} --- c[[Sub]]")
    checkNotNull(shapes) { "the wider shape/connector set must parse" }
    check(shapes.nodes.map { it.shape } == listOf(NodeShape.Round, NodeShape.Diamond, NodeShape.Subroutine))
    check(shapes.edges[0].thick && !shapes.edges[0].arrow) { "=== is thick and headless" }
    check(!shapes.edges[1].arrow) { "--- is headless" }

    // A node with no declared label falls back to its id rather than rendering an empty box.
    check(parseMermaidFlow("graph TD\n  a --> b")!!.byId["b"]!!.label == "b") { "bare id is its own label" }

    // Anything we can't draw honestly must fall back to raw source.
    check(parseMermaidFlow("") == null) { "empty" }
    check(parseMermaidFlow("sequenceDiagram\n  A->>B: hi") == null) { "not a flowchart" }
    check(parseMermaidFlow("graph TD\n  subgraph core\n  a --> b\n  end") == null) { "subgraph is unsupported, not half-supported" }
    check(parseMermaidFlow("graph TD\n  a[\"unterminated") == null) { "unbalanced bracket" }

    mermaidLayoutSelfCheck()
}

/**
 * Phase three's contract. Kept separate from [mermaidParseSelfCheck] because it checks the layout,
 * not the scanner — but called from it, so it needs no extra wiring in `Prerender.kt`.
 */
/**
 * How many diagrams `projects` ships. Hardcoded on purpose: the per-diagram checks below prove each
 * one is well-formed, and only a count catches a diagram that quietly disappeared.
 */
private const val SHIPPED_DIAGRAMS = 13

internal fun mermaidLayoutSelfCheck() {
    // Hand-built so declaration order is the point rather than an accident of the parser: rank 1 is
    // declared in the reverse of the order its edges want, which is exactly one crossing.
    val forced =
        FlowGraph(
            direction = FlowDirection.TopDown,
            nodes = listOf("a1", "a2", "b1", "b2").map { FlowNode(it, it, NodeShape.Box) },
            edges = listOf(FlowEdge("a1", "b2"), FlowEdge("a2", "b1")),
        )
    val seed = listOf(listOf("a1", "a2"), listOf("b1", "b2"))
    check(crossings(forced, seed) == 1) { "the seed ordering must actually cross, or this proves nothing" }
    val swept = forced.ranks().map { r -> r.map { it.id } }
    check(swept == listOf(listOf("a1", "a2"), listOf("b2", "b1"))) { "the sweep must swap rank 1" }
    check(crossings(forced, swept) == 0) { "barycenter must remove the crossing" }

    // Same graph one rank deeper, so the sweep has to propagate rather than fix a single pair.
    val deep =
        FlowGraph(
            direction = FlowDirection.TopDown,
            nodes = listOf("a1", "a2", "b1", "b2", "c1", "c2").map { FlowNode(it, it, NodeShape.Box) },
            edges =
                listOf(
                    FlowEdge("a1", "b2"),
                    FlowEdge("a2", "b1"),
                    FlowEdge("b1", "c1"),
                    FlowEdge("b2", "c2"),
                ),
        )
    check(crossings(deep, deep.ranks().map { r -> r.map { it.id } }) == 0) { "crossings clear through three ranks" }

    // A crossing that no ordering can remove (K3,3-ish fan) still must not lose or duplicate a node,
    // and must not come out worse than the order we started from.
    val tangled =
        FlowGraph(
            direction = FlowDirection.TopDown,
            nodes = listOf("x1", "x2", "x3", "y1", "y2", "y3").map { FlowNode(it, it, NodeShape.Box) },
            edges =
                listOf("x1" to "y3", "x1" to "y1", "x2" to "y2", "x3" to "y1", "x3" to "y3", "x2" to "y3")
                    .map { (a, b) -> FlowEdge(a, b) },
        )
    val tangledSeed = listOf(listOf("x1", "x2", "x3"), listOf("y1", "y2", "y3"))
    val tangledOut = tangled.ranks().map { r -> r.map { it.id } }
    check(tangledOut.flatten().sorted() == tangled.nodes.map { it.id }.sorted()) { "every node placed exactly once" }
    check(crossings(tangled, tangledOut) <= crossings(tangled, tangledSeed)) { "never ship a worse layout" }

    // Every shipped diagram: still parses, still lays out cleanly, and the sweep is a permutation
    // of the ranking rather than an edit of it.
    val shipped = projects.mapNotNull { it.detail }.flatMap { it.diagrams }
    check(shipped.size == SHIPPED_DIAGRAMS) { "expected $SHIPPED_DIAGRAMS diagrams, got ${shipped.size}" }
    var totalCrossings = 0
    shipped.forEach { diagram ->
        val g = checkNotNull(parseMermaidFlow(diagram.code)) { "'${diagram.title}' must parse" }
        val ranked = g.ranks().map { r -> r.map { it.id } }
        check(ranked.flatten().sorted() == g.nodes.map { it.id }.sorted()) { "'${diagram.title}': every node ranked once" }
        check(ranked.none { it.isEmpty() }) { "'${diagram.title}': no empty rank" }
        totalCrossings += crossings(g, ranked)
        // Determinism: parse and lay out again from scratch, and demand the identical picture. A
        // fresh parse means this also catches a layout that leaks hash iteration order.
        val again = parseMermaidFlow(diagram.code)!!.ranks().map { r -> r.map { it.id } }
        check(again == ranked) { "'${diagram.title}': layout must be deterministic" }
    }
    // Twelve of the thirteen come out crossing-free: they declare their siblings well enough that
    // the sweep finds nothing to fix. The thirteenth cannot. kmp-family's "Three repos, one seam
    // each" has two libraries feeding the same two consumers, a K(2,2), whose crossing number is 1,
    // so no ordering removes it. Checking the total rather than each diagram keeps the guard tight:
    // a sweep that started reordering the hand-tuned ones still trips this. Look before relaxing.
    check(totalCrossings == 1) { "expected exactly one unavoidable crossing across all diagrams, got $totalCrossings" }
}
