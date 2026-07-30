package com.siddharth.cv.shared.detail

/**
 * A hand-rolled parser for the *exact* Mermaid subset the twelve diagrams in
 * [com.siddharth.cv.shared.data.projects] actually use. Not a Mermaid implementation.
 *
 * The inventory (grepped from cv-siddharth/src/data/profile.ts and mirrored in CvProjectData.kt):
 *
 * - headers: `graph TD` and `graph LR`, nothing else
 * - one node shape: `id["quoted label"]` — every single node, all twelve diagrams
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
 * ponytail: within a rank the order is declaration order, not a barycenter sweep. Every real
 * diagram declares siblings in the order it wants them, so crossing minimisation would be work
 * with no visible effect. Add the sweep the first time two edges visibly cross.
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
    return List(depth) { r -> nodes.filterIndexed { i, _ -> rank[i] == r } }
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
 * be right about is the twelve strings the site actually ships.
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
}
