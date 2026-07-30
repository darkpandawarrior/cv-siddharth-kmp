package com.siddharth.cv.shared.playground

import androidx.compose.runtime.mutableStateMapOf

/**
 * A tokenizer, recursive-descent parser and evaluator for the slice of Jetpack Compose the
 * playground understands — a line-for-line port of `cv-siddharth/src/composeInterpreter.ts`.
 *
 * **Why a port and not a rewrite.** The React site parses this language too, and the two
 * implementations have to agree: [ComposeAst] is the shared contract, so the same source text must
 * produce the same tree on both sides. Any divergence is a bug in one of them, not a design
 * choice — which is why the structure here follows the TS file's shape (same functions, same
 * order, same forgiveness) rather than the shape a from-scratch Kotlin parser would take.
 *
 * **What the port changes, and why.** The React renderer walks this tree and emits *CSS
 * approximations*; that pressure leaked back into its parser, which throws away information the
 * CSS could not use. This port keeps it, because the target here is Compose itself:
 *
 *  1. `Arrangement.spacedBy(12.dp)` — TS drops the argument (`skipParens`) and the renderer then
 *     hardcodes `gap: 8`. Kept here, encoded on the [Expr.Member] path as `…spacedBy:12`, read
 *     back with [memberBase] / [memberArg]. Same trick the TS file already uses for `ColorHex:…`,
 *     so it does not widen the pinned AST. Applies to every skipped call, so
 *     `RoundedCornerShape(24.dp)` keeps its 24 too — TS renders that as a flat 16px radius.
 *  2. `Text(size.dp)` — TS's `resolveText` reads `expr.value` and ignores `expr.ref`, printing 0
 *     for a state-driven number. Here [resolveText] resolves the ref, like [resolveNum] does.
 *  3. A parse that throws is a worse product than a parse that renders partially, so
 *     [parseCompose] never throws: a `ParseError` ends the top-level loop and whatever parsed
 *     cleanly is returned. TS lets it escape and the site swaps the phone frame for
 *     "compile error". The pinned [Program] has nowhere to carry a message, so the message is
 *     dropped rather than surfaced — see the note on [parseCompose].
 *
 * Everything else is deliberately identical, including the forgiveness: unknown modifiers,
 * unrecognised named arguments and unknown composables are recorded or skipped, never fatal, so a
 * half-finished snippet still renders something.
 *
 * State is real. [ComposeState] is backed by `mutableStateMapOf`, so a `Button`'s `onClick`
 * mutating a var recomposes the tree the way the source text says it should — no snapshot, no
 * re-parse.
 *
 * Coverage lives in [composeInterpreterSelfCheck] (wired into `Prerender.selfCheck()`).
 */

// -------------------------------------------------------------------------------------------------
// Tokenizer
// -------------------------------------------------------------------------------------------------

/** `v` is always the raw source text, so a hex literal can be recovered verbatim for `Color(…)`. */
private sealed interface Tok {
    val v: String

    data class Id(override val v: String) : Tok

    data class Num(override val v: String) : Tok

    data class Str(override val v: String) : Tok

    data class Punc(override val v: String) : Tok
}

/** ASCII-only, matching the TS character classes — a unicode letter is skipped there, so it is here. */
private fun isIdStart(c: Char): Boolean = c == '_' || c in 'a'..'z' || c in 'A'..'Z'

private fun isIdPart(c: Char): Boolean = isIdStart(c) || c in '0'..'9'

private fun isDigit(c: Char): Boolean = c in '0'..'9'

private val twoCharPunc = setOf("++", "--", "+=", "-=", "==", "!=", "->", "||", "&&")

private const val SINGLE_PUNC = "{}()[].,=!+-*/:<>"

private fun tokenize(src: String): List<Tok> {
    val toks = ArrayList<Tok>()
    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]
        // whitespace
        if (c.isWhitespace()) {
            i++
            continue
        }
        // line comment
        if (c == '/' && i + 1 < n && src[i + 1] == '/') {
            while (i < n && src[i] != '\n') i++
            continue
        }
        // block comment — unterminated is fine, the index just runs off the end
        if (c == '/' && i + 1 < n && src[i + 1] == '*') {
            i += 2
            while (i < n && !(src[i] == '*' && i + 1 < n && src[i + 1] == '/')) i++
            i += 2
            continue
        }
        // string, double-quoted, backslash escapes kept raw so interpolation can be split later
        if (c == '"') {
            i++
            val s = StringBuilder()
            while (i < n && src[i] != '"') {
                if (src[i] == '\\' && i + 1 < n) {
                    s.append(src[i]).append(src[i + 1])
                    i += 2
                    continue
                }
                s.append(src[i++])
            }
            i++ // closing quote
            toks.add(Tok.Str(s.toString()))
            continue
        }
        // hex literal 0xAARRGGBB, for Color(0x…)
        if (c == '0' && i + 1 < n && (src[i + 1] == 'x' || src[i + 1] == 'X')) {
            val s = StringBuilder("0x")
            i += 2
            while (i < n && (isDigit(src[i]) || src[i] in 'a'..'f' || src[i] in 'A'..'F')) s.append(src[i++])
            toks.add(Tok.Num(s.toString()))
            continue
        }
        // number, integer or decimal; the .dp / .sp unit is the parser's problem
        if (isDigit(c)) {
            val s = StringBuilder()
            while (i < n && isDigit(src[i])) s.append(src[i++])
            if (i + 1 < n && src[i] == '.' && isDigit(src[i + 1])) {
                s.append(src[i++])
                while (i < n && isDigit(src[i])) s.append(src[i++])
            }
            if (i < n && (src[i] == 'f' || src[i] == 'F')) i++ // 12f float literal
            toks.add(Tok.Num(s.toString()))
            continue
        }
        // identifier / keyword
        if (isIdStart(c)) {
            val s = StringBuilder()
            while (i < n && isIdPart(src[i])) s.append(src[i++])
            toks.add(Tok.Id(s.toString()))
            continue
        }
        // multi-char punctuation
        if (i + 2 <= n && src.substring(i, i + 2) in twoCharPunc) {
            toks.add(Tok.Punc(src.substring(i, i + 2)))
            i += 2
            continue
        }
        // single-char punctuation
        if (c in SINGLE_PUNC) {
            toks.add(Tok.Punc(c.toString()))
            i++
            continue
        }
        // anything else — skipped, so a stray character never wedges the parser
        i++
    }
    return toks
}

// -------------------------------------------------------------------------------------------------
// Parser
// -------------------------------------------------------------------------------------------------

/**
 * Thrown internally when a structural token is missing. Caught by [parseCompose], never escapes —
 * a `RuntimeException` rather than a bespoke hierarchy so the catch there also nets the
 * unexpected: in a wasm canvas app an exception out of the parser blanks the page.
 */
private class ParseError(message: String) : RuntimeException(message)

private val emptyStr: Expr = Expr.Str(listOf(StrPart.Literal("")))

/** What an argument list can contain. All parts optional; see [Parser.parseArgs]. */
private class Args {
    val positional = ArrayList<Expr>()
    val named = LinkedHashMap<String, Expr>()
    var modifiers: List<ModifierCall> = emptyList()
    var onClick: List<Action>? = null
    var onValueChange: String? = null
}

private class Parser(private val toks: List<Tok>) {
    private var p = 0

    private fun peek(o: Int = 0): Tok? = toks.getOrNull(p + o)

    private fun next(): Tok? = toks.getOrNull(p++)

    private fun atPunc(v: String, o: Int = 0): Boolean {
        val t = peek(o)
        return t is Tok.Punc && t.v == v
    }

    private fun atId(v: String, o: Int = 0): Boolean {
        val t = peek(o)
        return t is Tok.Id && t.v == v
    }

    private fun eatPunc(v: String) {
        if (!atPunc(v)) throw ParseError("Expected \"$v\" near ${describe()}")
        p++
    }

    private fun describe(): String = peek()?.let { "\"${it.v}\"" } ?: "end of code"

    fun parseProgram(): Program {
        val state = ArrayList<StateDecl>()
        val tree = ArrayList<Node>()
        while (peek() != null) {
            val before = p
            val progressed = try {
                if (atId("var") || atId("val")) {
                    state.add(parseStateDecl())
                    true
                } else {
                    val node = parseNode()
                    if (node == null) false else { tree.add(node); true }
                }
            } catch (e: RuntimeException) {
                // Divergence #3: keep what parsed instead of losing the whole tree.
                false
            }
            if (!progressed || p == before) break
        }
        return Program(state, tree)
    }

    /** `var count by remember { mutableStateOf(0) }` */
    private fun parseStateDecl(): StateDecl {
        next() // var / val
        val nameTok = next()
        if (nameTok !is Tok.Id) throw ParseError("Expected a name after var")
        val name = nameTok.v
        when {
            atId("by") -> next()
            atPunc("=") -> next()
            else -> throw ParseError("Expected \"by\" or \"=\" in the declaration of $name")
        }
        if (!atId("remember")) throw ParseError("$name needs remember { mutableStateOf(...) }")
        next()
        eatPunc("{")
        if (!atId("mutableStateOf")) throw ParseError("$name needs mutableStateOf(...)")
        next()
        eatPunc("(")
        val init = parseExpr()
        eatPunc(")")
        eatPunc("}")
        val value: StateValue = when (init) {
            // The AST pins state numbers to Int; the TS keeps a double. `mutableStateOf(0.5)`
            // therefore truncates here. ponytail: nothing in the subset animates a fractional
            // state var — widen StateValue if that ever stops being true.
            is Expr.Num -> StateValue.IntValue(init.value.toInt())
            is Expr.Bool -> StateValue.BoolValue(init.value)
            // Interpolations in an initialiser have nothing to read yet, so refs contribute "".
            is Expr.Str -> StateValue.StringValue(
                init.parts.filterIsInstance<StrPart.Literal>().joinToString("") { it.text },
            )
            else -> StateValue.IntValue(0)
        }
        return StateDecl(name, value)
    }

    private fun parseNode(): Node? {
        val t = peek()
        if (t !is Tok.Id) return null
        val name = t.v
        when (name) {
            "Text" -> return parseText()
            "Button" -> return parseButton()
            "Spacer" -> return parseSpacer()
            "AnimatedVisibility" -> return parseAnimated()
            "TextField", "OutlinedTextField", "BasicTextField" -> return parseTextField()
        }
        containerNames[name]?.let { return parseContainer(it) }
        // Unknown composable: consume its call + trailing lambda so parsing can keep going.
        next()
        skipParens()
        skipBraces()
        return Node.Unknown(name)
    }

    private fun parseText(): Node {
        next() // Text
        val a = parseArgs()
        val value = a.positional.firstOrNull() ?: a.named["text"] ?: emptyStr
        return Node.Text(value = value, named = a.named, modifiers = a.modifiers)
    }

    private fun parseButton(): Node {
        next() // Button
        val a = parseArgs()
        val children = parseLambdaChildren()
        return Node.Button(
            onClick = a.onClick ?: emptyList(),
            named = a.named,
            modifiers = a.modifiers,
            children = children,
        )
    }

    private fun parseSpacer(): Node {
        next() // Spacer
        return Node.Spacer(modifiers = parseArgs().modifiers)
    }

    private fun parseTextField(): Node {
        next() // TextField / OutlinedTextField / BasicTextField
        val a = parseArgs()
        return Node.TextField(
            value = a.named["value"] ?: emptyStr,
            bindTo = a.onValueChange,
            named = a.named,
            modifiers = a.modifiers,
        )
    }

    private fun parseAnimated(): Node {
        next() // AnimatedVisibility
        val a = parseArgs()
        val children = parseLambdaChildren()
        val visible = a.named["visible"] ?: a.positional.firstOrNull() ?: Expr.Bool(true)
        return Node.Animated(visible = visible, modifiers = a.modifiers, children = children)
    }

    private fun parseContainer(kind: ContainerKind): Node {
        next()
        val a = parseArgs()
        val children = parseLambdaChildren()
        return Node.Container(name = kind, modifiers = a.modifiers, named = a.named, children = children)
    }

    /**
     * An optional `(…)` argument list: positional exprs, named `k = v`, a bare `Modifier` chain,
     * `onClick = { actions }` and `onValueChange = { name = it }`. All parts optional.
     */
    private fun parseArgs(): Args {
        val a = Args()
        if (!atPunc("(")) return a
        eatPunc("(")
        while (!atPunc(")") && peek() != null) {
            val head = peek()
            if (head is Tok.Id && atPunc("=", 1)) {
                val key = head.v
                next() // key
                next() // =
                when (key) {
                    "onClick" -> a.onClick = parseActionLambda()
                    "modifier" -> a.modifiers = parseModifierChain()
                    "onValueChange" -> a.onValueChange = parseValueChangeBinding()
                    else -> a.named[key] = parseExpr()
                }
            } else if (atId("Modifier")) {
                a.modifiers = parseModifierChain()
            } else {
                a.positional.add(parseExpr())
            }
            if (atPunc(",")) next() else break
        }
        eatPunc(")")
        return a
    }

    /**
     * `{ username = it }` — the one shape that matters: bind the field straight to a state var.
     * Anything else in the lambda is skipped, not fatal.
     */
    private fun parseValueChangeBinding(): String? {
        if (!atPunc("{")) return null
        eatPunc("{")
        var bound: String? = null
        val head = peek()
        if (head is Tok.Id && atPunc("=", 1) && atId("it", 2)) {
            bound = head.v
            next() // name
            next() // =
            next() // it
        }
        var depth = 1
        while (depth > 0 && peek() != null) {
            if (atPunc("{")) {
                depth++
            } else if (atPunc("}")) {
                depth--
                if (depth == 0) break
            }
            next()
        }
        eatPunc("}")
        return bound
    }

    /** `Modifier.padding(16.dp).fillMaxWidth()…` — unknown names are kept, the renderer ignores them. */
    private fun parseModifierChain(): List<ModifierCall> {
        val mods = ArrayList<ModifierCall>()
        if (atId("Modifier")) next()
        while (atPunc(".")) {
            next()
            val m = next()
            if (m !is Tok.Id) break
            val args = ArrayList<Expr>()
            if (atPunc("(")) {
                next()
                while (!atPunc(")") && peek() != null) {
                    // names on modifier args are ignored (e.g. horizontal = 12.dp); values kept
                    if (peek() is Tok.Id && atPunc("=", 1)) {
                        next()
                        next()
                    }
                    args.add(parseExpr())
                    if (atPunc(",")) next() else break
                }
                eatPunc(")")
            }
            mods.add(ModifierCall(m.v, args))
        }
        return mods
    }

    /** `{ count++  count = !count  count += 2 … }` */
    private fun parseActionLambda(): List<Action> {
        val actions = ArrayList<Action>()
        if (!atPunc("{")) return actions
        eatPunc("{")
        while (!atPunc("}") && peek() != null) {
            val head = peek()
            if (head is Tok.Id) {
                val name = head.v
                next()
                when {
                    atPunc("++") -> { next(); actions.add(Action.Inc(name)) }
                    atPunc("--") -> { next(); actions.add(Action.Dec(name)) }
                    atPunc("+=") -> {
                        next()
                        val e = parseExpr()
                        actions.add(Action.AddAssign(name, (e as? Expr.Num)?.value ?: 0.0))
                    }
                    atPunc("-=") -> {
                        next()
                        val e = parseExpr()
                        actions.add(Action.SubAssign(name, (e as? Expr.Num)?.value ?: 0.0))
                    }
                    atPunc("=") -> {
                        next()
                        if (atPunc("!")) {
                            next() // !
                            next() // the var being negated
                            actions.add(Action.Toggle(name))
                        } else {
                            actions.add(Action.Set(name, parseExpr()))
                        }
                    }
                }
            } else {
                next() // skip anything unrecognised
            }
        }
        eatPunc("}")
        return actions
    }

    private fun parseLambdaChildren(): List<Node> {
        val children = ArrayList<Node>()
        if (!atPunc("{")) return children
        eatPunc("{")
        while (!atPunc("}") && peek() != null) {
            val before = p
            val node = parseNode()
            if (node != null) children.add(node)
            if (p == before) next() // guarantee progress
        }
        eatPunc("}")
        return children
    }

    /**
     * `a.isEmpty() || b.isEmpty()` — left-associative, no precedence between `&&` and `||` (this
     * subset never needs it); each side is a plain atom.
     */
    private fun parseExpr(): Expr {
        var left = parseAtom()
        while (atPunc("||") || atPunc("&&")) {
            val op = if (next()?.v == "||") LogicOp.Or else LogicOp.And
            left = Expr.Logic(op, left, parseAtom())
        }
        return left
    }

    private fun parseAtom(): Expr {
        val t = peek() ?: return emptyStr
        if (t is Tok.Str) {
            next()
            return Expr.Str(parseInterpolation(t.v))
        }
        if (t is Tok.Num) {
            next()
            var unit: NumUnit? = null
            if (atPunc(".") && (atId("dp", 1) || atId("sp", 1))) {
                next() // .
                unit = if (next()?.v == "dp") NumUnit.Dp else NumUnit.Sp
            }
            return Expr.Num(parseNumber(t.v), unit)
        }
        if (t is Tok.Id) {
            if (t.v == "true" || t.v == "false") {
                next()
                return Expr.Bool(t.v == "true")
            }
            // Color(0xAARRGGBB) — a custom colour literal, carried on the path like the TS does.
            if (t.v == "Color" && atPunc("(", 1)) {
                next() // Color
                next() // (
                var hex = ""
                val arg = peek()
                if (arg is Tok.Num) {
                    hex = arg.v
                    next()
                }
                while (peek() != null && !atPunc(")")) next()
                if (atPunc(")")) next()
                return Expr.Member("ColorHex:$hex")
            }
            // state-driven dimension: `size.dp` / `padding.sp` reads the number out of state
            if (atPunc(".", 1) && (atId("dp", 2) || atId("sp", 2))) {
                val ref = t.v
                next() // ref
                next() // .
                val unit = if (next()?.v == "dp") NumUnit.Dp else NumUnit.Sp
                return Expr.Num(0.0, unit, ref)
            }
            // member path: Color.Green, Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally
            next()
            var path = t.v
            // A call form we don't model: keep the name, and — divergence #1 — the first numeric
            // argument, which is the whole information content of spacedBy / RoundedCornerShape.
            if (atPunc("(")) {
                val arg = skipParens()
                return Expr.Member(if (arg == null) path else "$path:$arg")
            }
            val isMember = atPunc(".")
            while (atPunc(".") && peek(1) is Tok.Id) {
                next()
                path += "." + (next()?.v ?: "")
                if (atPunc("(")) {
                    val arg = skipParens()
                    if (arg != null) path += ":$arg"
                }
            }
            return if (isMember) Expr.Member(path) else Expr.Ident(path)
        }
        // Unrecognised — consume one token and yield an empty string.
        next()
        return emptyStr
    }

    /** Skips a balanced `(…)`, returning the raw text of the first number inside it, if any. */
    private fun skipParens(): String? {
        if (!atPunc("(")) return null
        var depth = 0
        var firstNum: String? = null
        do {
            val t = next() ?: break
            if (t is Tok.Punc && t.v == "(") depth++
            else if (t is Tok.Punc && t.v == ")") depth--
            else if (t is Tok.Num && firstNum == null) firstNum = t.v
        } while (depth > 0 && peek() != null)
        return firstNum
    }

    private fun skipBraces() {
        if (!atPunc("{")) return
        var depth = 0
        do {
            val t = next() ?: break
            if (t is Tok.Punc && t.v == "{") depth++
            else if (t is Tok.Punc && t.v == "}") depth--
        } while (depth > 0 && peek() != null)
    }
}

/**
 * JS `parseFloat` semantics, narrowed to what the tokenizer can emit: a decimal run, or a hex
 * literal — which `parseFloat` reads as 0 because it stops at the `x`. Hex only ever reaches here
 * outside `Color(…)`, where the value is meaningless anyway; the digits survive on the
 * `ColorHex:` path.
 */
private fun parseNumber(text: String): Double =
    if (text.startsWith("0x") || text.startsWith("0X")) 0.0 else text.toDoubleOrNull() ?: 0.0

/** Splits `"Count: $count times ${n}"` into literal + reference parts. */
private fun parseInterpolation(raw: String): List<StrPart> {
    // unescape the three escapes the editor produces
    val s = raw.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t")
    val parts = ArrayList<StrPart>()
    val buf = StringBuilder()
    fun flush() {
        if (buf.isNotEmpty()) {
            parts.add(StrPart.Literal(buf.toString()))
            buf.clear()
        }
    }
    var i = 0
    while (i < s.length) {
        if (s[i] == '$') {
            val nextChar = s.getOrNull(i + 1)
            if (nextChar == '{') {
                val end = s.indexOf('}', i + 2)
                if (end != -1) {
                    flush()
                    parts.add(StrPart.Ref(s.substring(i + 2, end).trim()))
                    i = end + 1
                    continue
                }
            } else if (nextChar != null && isIdStart(nextChar)) {
                var j = i + 1
                while (j < s.length && isIdPart(s[j])) j++
                flush()
                parts.add(StrPart.Ref(s.substring(i + 1, j)))
                i = j
                continue
            }
        }
        buf.append(s[i++])
        continue
    }
    flush()
    return parts
}

/**
 * Parse a Compose snippet. Never throws — see divergence #3 in the file KDoc: a malformed tail
 * truncates the tree instead of failing the whole render, which for a live editor is the better
 * trade. The cost is that the error *message* is gone, because the pinned [Program] has nowhere to
 * put it; the renderer signals trouble by what is missing (or by a [Node.Unknown]) rather than by
 * a "compile error" panel.
 */
fun parseCompose(src: String): Program = Parser(tokenize(src)).parseProgram()

// -------------------------------------------------------------------------------------------------
// Member-path encoding (see divergence #1)
// -------------------------------------------------------------------------------------------------

/** `"Arrangement.spacedBy:12"` -> `"Arrangement.spacedBy"`. Safe on paths with no argument. */
fun memberBase(path: String): String = path.substringBefore(':')

/**
 * `"Arrangement.spacedBy:12"` -> `12.0`; null when the call carried no number (`CircleShape`) or
 * carried something that is not one (`ColorHex:0xFF3DDC84` — read that with `hexFromArgb`
 * instead). Hand-rolled rather than `toDoubleOrNull` so the accepted grammar is the same on wasm
 * and on the JVM prerender.
 */
fun memberArg(path: String): Double? {
    val raw = path.substringAfter(':', "")
    if (raw.isEmpty() || !raw.all { isDigit(it) || it == '.' }) return null
    return raw.toDoubleOrNull()
}

// -------------------------------------------------------------------------------------------------
// State
// -------------------------------------------------------------------------------------------------

/**
 * The declared vars, as observable Compose state.
 *
 * Backed by `mutableStateMapOf` on purpose: a read inside composition subscribes, so
 * `applyAction` from a real `onClick` recomposes exactly the subtree that read the var. The React
 * version rebuilds an immutable map and re-renders from the root; this is the thing that version
 * is simulating.
 */
class ComposeState(decls: List<StateDecl> = emptyList()) {
    private val values = mutableStateMapOf<String, StateValue>()

    init {
        reset(decls)
    }

    /** Re-seed from declarations. Call when the *declarations* change, not on every keystroke. */
    fun reset(decls: List<StateDecl>) {
        values.clear()
        decls.forEach { values[it.name] = it.init }
    }

    operator fun get(name: String): StateValue? = values[name]

    operator fun set(name: String, value: StateValue) {
        values[name] = value
    }

    /** The number in `name`, or null when it holds something else / nothing. */
    fun number(name: String): Double? = (values[name] as? StateValue.IntValue)?.value?.toDouble()

    /** Display form, `""` for an undeclared var — matching the TS `String(state[x] ?? "")`. */
    fun text(name: String): String = when (val v = values[name]) {
        null -> ""
        is StateValue.IntValue -> v.value.toString()
        is StateValue.BoolValue -> v.value.toString()
        is StateValue.StringValue -> v.value
    }

    /** JS truthiness, which is what the reference implementation's `!!state[x]` means: 0 and "" are false. */
    fun truthy(name: String): Boolean = when (val v = values[name]) {
        null -> false
        is StateValue.IntValue -> v.value != 0
        is StateValue.BoolValue -> v.value
        is StateValue.StringValue -> v.value.isNotEmpty()
    }

    /** For a TextField's `onValueChange` binding. */
    fun setText(name: String, value: String) {
        values[name] = StateValue.StringValue(value)
    }
}

private fun intOf(state: ComposeState, name: String): Int =
    (state[name] as? StateValue.IntValue)?.value ?: 0

/** Applies one `onClick` action. Mutates [state], which is what makes the tree recompose. */
fun applyAction(action: Action, state: ComposeState) {
    when (action) {
        is Action.Inc -> state[action.name] = StateValue.IntValue(intOf(state, action.name) + 1)
        is Action.Dec -> state[action.name] = StateValue.IntValue(intOf(state, action.name) - 1)
        is Action.AddAssign ->
            state[action.name] = StateValue.IntValue(intOf(state, action.name) + action.value.toInt())
        is Action.SubAssign ->
            state[action.name] = StateValue.IntValue(intOf(state, action.name) - action.value.toInt())
        is Action.Toggle -> state[action.name] = StateValue.BoolValue(!state.truthy(action.name))
        is Action.Set -> state[action.name] = when (val v = action.value) {
            is Expr.Num -> StateValue.IntValue(resolveNum(v, state, v.value).toInt())
            is Expr.Bool -> StateValue.BoolValue(v.value)
            else -> StateValue.StringValue(resolveText(v, state))
        }
    }
}

/** Every action of one click, in source order. */
fun applyActions(actions: List<Action>, state: ComposeState) = actions.forEach { applyAction(it, state) }

/**
 * A signature of the *declarations*. Editing the UI around a counter must not reset the counter,
 * so the renderer re-seeds [ComposeState] only when this string changes.
 */
fun stateSignature(program: Program): String = program.state.joinToString("|") { d ->
    when (val v = d.init) {
        is StateValue.IntValue -> "${d.name}:int:${v.value}"
        is StateValue.BoolValue -> "${d.name}:bool:${v.value}"
        is StateValue.StringValue -> "${d.name}:string:${v.value}"
    }
}

// -------------------------------------------------------------------------------------------------
// Expression evaluation
// -------------------------------------------------------------------------------------------------

/**
 * Generic evaluation: [String] for strings and unresolvable member paths, [Double] for numbers,
 * [Boolean] for booleans and logic. The renderer mostly wants the focused helpers below —
 * [resolveText], [resolveNum], [resolveBool] — because each call site already knows which kind it
 * needs; this exists for the ones that don't.
 */
fun evalExpr(expr: Expr, state: ComposeState): Any? = when (expr) {
    is Expr.Str -> resolveText(expr, state)
    is Expr.Num -> resolveNum(expr, state, expr.value)
    is Expr.Bool -> expr.value
    is Expr.Ident -> state[expr.name]?.let {
        when (it) {
            is StateValue.IntValue -> it.value.toDouble()
            is StateValue.BoolValue -> it.value
            is StateValue.StringValue -> it.value
        }
    } ?: expr.name
    is Expr.Logic -> resolveBool(expr, state)
    is Expr.Member -> emptinessOf(expr.path, state) ?: expr.path
}

/**
 * The string a `Text` shows: literal chunks with `$refs` substituted from live state. Divergence
 * #2 — a `Num` with a `ref` resolves through state here, where the TS prints its (always 0) literal.
 */
fun resolveText(expr: Expr?, state: ComposeState): String = when (expr) {
    null -> ""
    is Expr.Str -> expr.parts.joinToString("") {
        when (it) {
            is StrPart.Literal -> it.text
            is StrPart.Ref -> state.text(it.name)
        }
    }
    is Expr.Num -> formatNumber(resolveNum(expr, state, expr.value))
    is Expr.Bool -> expr.value.toString()
    is Expr.Ident -> if (state[expr.name] != null) state.text(expr.name) else expr.name
    is Expr.Member, is Expr.Logic -> ""
}

/**
 * A dimension: `16.dp` -> 16, `size.dp` -> whatever `size` holds. Anything that is not a number
 * expression yields [fallback], so `Modifier.padding(SomethingWeird)` degrades to the default
 * rather than to zero.
 */
fun resolveNum(expr: Expr?, state: ComposeState, fallback: Double = 0.0): Double {
    if (expr !is Expr.Num) return fallback
    val ref = expr.ref ?: return expr.value
    return state.number(ref) ?: fallback
}

/**
 * Truthiness for `AnimatedVisibility(visible = …)` and friends. Defaults to *true* for anything
 * unrecognised: an expression the parser could not model should not make content vanish.
 */
fun resolveBool(expr: Expr?, state: ComposeState): Boolean = when (expr) {
    null -> true
    is Expr.Bool -> expr.value
    is Expr.Num -> resolveNum(expr, state, expr.value) != 0.0
    is Expr.Ident -> state.truthy(expr.name)
    is Expr.Str -> resolveText(expr, state) == "true"
    is Expr.Logic -> if (expr.op == LogicOp.And) {
        resolveBool(expr.left, state) && resolveBool(expr.right, state)
    } else {
        resolveBool(expr.left, state) || resolveBool(expr.right, state)
    }
    is Expr.Member -> emptinessOf(expr.path, state) ?: true
}

/**
 * `stateVar.isEmpty` / `stateVar.isNotEmpty` — the one method-call shape generated visibility
 * conditions actually reach for. Null when the path is something else. Hand-split rather than a
 * `Regex` so nothing depends on the wasm regex backend.
 */
private fun emptinessOf(path: String, state: ComposeState): Boolean? {
    val dot = path.lastIndexOf('.')
    if (dot <= 0) return null
    val name = path.substring(0, dot)
    val call = path.substring(dot + 1)
    if (call != "isEmpty" && call != "isNotEmpty") return null
    if (!name.all { isIdPart(it) }) return null
    val empty = !state.truthy(name)
    return if (call == "isEmpty") empty else !empty
}

/** `72.0` -> `"72"`, `12.5` -> `"12.5"`. No `String.format` on wasm, and a trailing `.0` on a dp reads as a bug. */
private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val whole = value.toLong()
    return if (whole.toDouble() == value) whole.toString() else value.toString()
}

// -------------------------------------------------------------------------------------------------
// Presets — the editor's starting points, and the parser's fixtures
// -------------------------------------------------------------------------------------------------

data class ComposePreset(val label: String, val code: String)

/**
 * Transcribed verbatim from `PRESETS` in `cv-siddharth/src/ComposePlayground.tsx`, so the two
 * playgrounds open on the same code. They double as the parser's end-to-end fixtures — see
 * [composeInterpreterSelfCheck], which parses all seven and asserts nothing degrades to
 * [Node.Unknown].
 */
val composePresets: List<ComposePreset> = listOf(
    ComposePreset(
        "Counter",
        """var count by remember { mutableStateOf(0) }

Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text("you tapped", color = Color.Gray, fontSize = 13.sp)
    Text(
        "${'$'}count",
        color = Color.Green,
        fontSize = 72.sp,
        fontWeight = FontWeight.Bold
    )
    Text("times", color = Color.Gray, fontSize = 13.sp)
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { count-- }) { Text("remove") }
        Button(onClick = { count++ }) { Text("add one") }
    }
}""",
    ),
    ComposePreset(
        "Profile card",
        """Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Green)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Siddharth", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Senior Android Engineer", color = Color.Gray, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Takes Android apps from prototype to platform.",
                color = Color.LightGray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Follow") }
        }
    }
}""",
    ),
    ComposePreset(
        "Toggle",
        """var on by remember { mutableStateOf(false) }

Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Box(
        modifier = Modifier.size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF3DDC84))
    )
    Spacer(Modifier.height(24.dp))
    Text("state is ${'$'}{on}", color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Button(onClick = { on = !on }) { Text("toggle") }
}""",
    ),
    ComposePreset(
        "Kursi role",
        """// theme tokens imported from the real Kursi app
Column(
    modifier = Modifier.fillMaxSize().background(Kursi.ink).padding(18.dp),
    verticalArrangement = Arrangement.Center
) {
    Card(modifier = Modifier.fillMaxWidth().background(Kursi.card)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0072B2))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Netaji Vachan", color = Kursi.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("The Politician", color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Tax +3  ·  GHOTALA", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("blocks Foreign Aid", color = Color.Gray, fontSize = 12.sp)
        }
    }
}""",
    ),
    ComposePreset(
        "Mileway",
        """// theme tokens imported from the real Mileway app
Column(
    modifier = Modifier.fillMaxSize().background(Mileway.ink).padding(20.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text("Mileway", color = Mileway.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("one Kotlin codebase", color = Color.Gray, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.background(Mileway.card)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("5", color = Mileway.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("platforms", color = Color.Gray, fontSize = 11.sp)
            }
        }
        Card(modifier = Modifier.background(Mileway.card)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("35", color = Mileway.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("modules", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}""",
    ),
    ComposePreset(
        "Animation",
        """var size by remember { mutableStateOf(84) }
var shown by remember { mutableStateOf(true) }

Column(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // size is state — the box animates between values on tap
    Box(
        modifier = Modifier.size(size.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Green)
    )
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { size = 56 }) { Text("small") }
        Button(onClick = { size = 150 }) { Text("big") }
        Button(onClick = { shown = !shown }) { Text("reveal") }
    }
    Spacer(Modifier.height(22.dp))
    // AnimatedVisibility slides its content in and out
    AnimatedVisibility(visible = shown) {
        Card(modifier = Modifier.fillMaxWidth().background(Color(0xFF171E1A))) {
            Text(
                "now you see me",
                modifier = Modifier.padding(18.dp),
                color = Color.Green,
                fontWeight = FontWeight.Bold
            )
        }
    }
}""",
    ),
    ComposePreset(
        "Layout",
        """Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Rows & weights", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Box(modifier = Modifier.weight(1.dp).fillMaxHeight().background(Color.Green))
        Box(modifier = Modifier.weight(2.dp).fillMaxHeight().background(Color.Blue))
        Box(modifier = Modifier.weight(1.dp).fillMaxHeight().background(Color.Magenta))
    }
    Text("Cards stack", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Card(modifier = Modifier.fillMaxWidth()) {
        Text("A Material card", modifier = Modifier.padding(16.dp), color = Color.LightGray)
    }
}""",
    ),
)

// -------------------------------------------------------------------------------------------------
// Self-check
// -------------------------------------------------------------------------------------------------

/** Every node in the tree, parents before children. */
private fun flattenNodes(nodes: List<Node>): List<Node> = nodes.flatMap { n ->
    listOf(n) + when (n) {
        is Node.Container -> flattenNodes(n.children)
        is Node.Button -> flattenNodes(n.children)
        is Node.Animated -> flattenNodes(n.children)
        else -> emptyList()
    }
}

/**
 * ponytail: one runnable check instead of a test module — same shape as [themeLabSelfCheck]. This
 * is a hand-written parser sharing a contract with a TypeScript one, so the coverage is real:
 * tree shape, every [Action], both units, the interpolation split, the argument the reference
 * implementation throws away, forgiveness of garbage, and all seven presets end to end.
 */
internal fun composeInterpreterSelfCheck() {
    val noState = ComposeState()

    // ── the Counter preset: exact state + exact tree shape ──────────────────────────────────────
    val counter = parseCompose(composePresets[0].code)
    check(counter.state == listOf(StateDecl("count", StateValue.IntValue(0)))) {
        "Counter must declare exactly count = 0, got ${counter.state}"
    }
    check(counter.tree.size == 1) { "Counter is one root Column, got ${counter.tree.size} roots" }
    val col = counter.tree[0] as? Node.Container ?: error("Counter root is not a container")
    check(col.name == ContainerKind.Column) { "Counter root should be a Column" }
    check(col.modifiers.map { it.name } == listOf("fillMaxSize", "padding")) {
        "Counter modifier chain lost its order/names: ${col.modifiers}"
    }
    check(resolveNum(col.modifiers[1].args[0], noState) == 24.0) { "padding(24.dp) lost its 24" }
    check((col.named["verticalArrangement"] as Expr.Member).path == "Arrangement.Center")
    check((col.named["horizontalAlignment"] as Expr.Member).path == "Alignment.CenterHorizontally")
    check(col.children.size == 5) { "Counter Column has 3 Texts, a Spacer and a Row, got ${col.children}" }

    val tapped = col.children[0] as Node.Text
    check(resolveText(tapped.value, noState) == "you tapped")
    check((tapped.named["color"] as Expr.Member).path == "Color.Gray")

    // dp vs sp both survive, on the same tree
    val fontSize = tapped.named["fontSize"] as Expr.Num
    check(fontSize.unit == NumUnit.Sp && fontSize.value == 13.0) { "13.sp did not survive: $fontSize" }
    val padding = col.modifiers[1].args[0] as Expr.Num
    check(padding.unit == NumUnit.Dp) { "24.dp lost its unit" }
    val spacerHeight = (col.children[3] as Node.Spacer).modifiers[0]
    check(spacerHeight.name == "height" && (spacerHeight.args[0] as Expr.Num).unit == NumUnit.Dp)

    // "$count" is a bare interpolation: one Ref, no literals
    val countText = col.children[1] as Node.Text
    check((countText.value as Expr.Str).parts == listOf(StrPart.Ref("count"))) {
        "bare \$count should parse to a single Ref, got ${countText.value}"
    }
    check((countText.named["fontWeight"] as Expr.Member).path == "FontWeight.Bold")

    // ── divergence #1: spacedBy keeps its argument (the TS drops it and hardcodes 8) ────────────
    val row = col.children[4] as Node.Container
    check(row.name == ContainerKind.Row)
    val arrangement = (row.named["horizontalArrangement"] as Expr.Member).path
    check(memberBase(arrangement) == "Arrangement.spacedBy") { "spacedBy base mangled: $arrangement" }
    check(memberArg(arrangement) == 12.0) { "spacedBy(12.dp) lost its 12: $arrangement" }
    check(memberArg("Alignment.CenterHorizontally") == null) { "a plain path must have no argument" }
    check(memberBase("ColorHex:0xFF3DDC84") == "ColorHex") { "the ColorHex encoding must still split" }
    check(memberArg("ColorHex:0xFF3DDC84") == null) { "a hex payload is not a numeric argument" }

    // the two buttons, and their actions
    val buttons = row.children.map { it as Node.Button }
    check(buttons.size == 2)
    check(buttons[0].onClick == listOf(Action.Dec("count")))
    check(buttons[1].onClick == listOf(Action.Inc("count")))
    check(resolveText((buttons[1].children[0] as Node.Text).value, noState) == "add one")

    // ── interpolation splitting, including ${…} and escapes ─────────────────────────────────────
    val interp = parseCompose("""Text("Count: ${'$'}count times ${'$'}{ n }!\nbye")""")
    val parts = ((interp.tree[0] as Node.Text).value as Expr.Str).parts
    check(
        parts == listOf(
            StrPart.Literal("Count: "),
            StrPart.Ref("count"),
            StrPart.Literal(" times "),
            StrPart.Ref("n"),
            StrPart.Literal("!\nbye"),
        ),
    ) { "interpolation split wrong: $parts" }
    // a lone $ is a literal, not a ref
    val lone = parseCompose("""Text("cost: ${'$'} 5")""")
    check(((lone.tree[0] as Node.Text).value as Expr.Str).parts == listOf(StrPart.Literal("cost: ${'$'} 5")))

    // ── every Action shape, parsed and then applied to live state ────────────────────────────────
    val actions = parseCompose(
        """
        var n by remember { mutableStateOf(10) }
        var flag by remember { mutableStateOf(false) }
        var who by remember { mutableStateOf("sid") }
        Column {
            Button(onClick = { n++ }) { Text("a") }
            Button(onClick = { n-- }) { Text("b") }
            Button(onClick = { n += 5 }) { Text("c") }
            Button(onClick = { n -= 3 }) { Text("d") }
            Button(onClick = { flag = !flag }) { Text("e") }
            Button(onClick = { n = 7 }) { Text("f") }
            Button(onClick = { who = "ada" }) { Text("g") }
        }
        """.trimIndent(),
    )
    val clicks = (actions.tree[0] as Node.Container).children.map { (it as Node.Button).onClick.single() }
    check(
        clicks == listOf(
            Action.Inc("n"),
            Action.Dec("n"),
            Action.AddAssign("n", 5.0),
            Action.SubAssign("n", 3.0),
            Action.Toggle("flag"),
            Action.Set("n", Expr.Num(7.0)),
            Action.Set("who", Expr.Str(listOf(StrPart.Literal("ada")))),
        ),
    ) { "action shapes parsed wrong: $clicks" }

    val live = ComposeState(actions.state)
    check(live.text("n") == "10" && live.text("who") == "sid" && !live.truthy("flag"))
    applyActions(clicks.take(4), live) // ++, --, += 5, -= 3
    check(live.number("n") == 12.0) { "10 +1 -1 +5 -3 should be 12, got ${live.number("n")}" }
    applyAction(clicks[4], live)
    check(live.truthy("flag")) { "toggle must flip false to true" }
    applyAction(clicks[4], live)
    check(!live.truthy("flag")) { "toggle must flip back" }
    applyAction(clicks[5], live)
    check(live.number("n") == 7.0)
    applyAction(clicks[6], live)
    check(live.text("who") == "ada")
    // an action on an undeclared var starts from 0 rather than throwing
    applyAction(Action.Inc("ghost"), live)
    check(live.number("ghost") == 1.0)

    // ── state-driven dimensions and interpolation read through live state ───────────────────────
    val anim = parseCompose(composePresets[5].code)
    val animState = ComposeState(anim.state)
    val sizeArg = flattenNodes(anim.tree)
        .filterIsInstance<Node.Container>()
        .first { it.name == ContainerKind.Box }
        .modifiers.first { it.name == "size" }
        .args[0] as Expr.Num
    check(sizeArg.ref == "size" && sizeArg.unit == NumUnit.Dp) { "size.dp lost its ref/unit: $sizeArg" }
    check(resolveNum(sizeArg, animState) == 84.0) { "size.dp should read 84 out of state" }
    animState["size"] = StateValue.IntValue(150)
    check(resolveNum(sizeArg, animState) == 150.0) { "size.dp must track state" }
    // divergence #2: a ref'd number in a Text resolves (the TS prints 0)
    check(resolveText(sizeArg, animState) == "150") { "Text(size.dp) should print the state value" }

    // Color(0x…) keeps its digits; RoundedCornerShape keeps its radius (the TS flattens it to 16)
    val clip = flattenNodes(anim.tree)
        .filterIsInstance<Node.Container>()
        .first { it.name == ContainerKind.Box }
        .modifiers.first { it.name == "clip" }
        .args[0] as Expr.Member
    check(memberBase(clip.path) == "RoundedCornerShape" && memberArg(clip.path) == 20.0) {
        "RoundedCornerShape(20.dp) lost its radius: ${clip.path}"
    }
    val toggle = parseCompose(composePresets[2].code)
    val hex = flattenNodes(toggle.tree)
        .filterIsInstance<Node.Container>()
        .first { it.name == ContainerKind.Box }
        .modifiers.first { it.name == "background" }
        .args[0] as Expr.Member
    check(hex.path == "ColorHex:0xFF3DDC84") { "Color(0xFF3DDC84) mangled: ${hex.path}" }
    // "state is ${on}" against real state
    val toggleState = ComposeState(toggle.state)
    val stateText = flattenNodes(toggle.tree).filterIsInstance<Node.Text>().first()
    check(resolveText(stateText.value, toggleState) == "state is false")
    applyActions(flattenNodes(toggle.tree).filterIsInstance<Node.Button>().first().onClick, toggleState)
    check(resolveText(stateText.value, toggleState) == "state is true") { "toggling must change the Text" }

    // ── TextField binding, AnimatedVisibility, logic and .isEmpty ────────────────────────────────
    val form = parseCompose(
        """
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        Column {
            OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth())
            TextField(value = password, onValueChange = { it.trim() })
            AnimatedVisibility(visible = username.isEmpty() || password.isEmpty()) {
                Text("fill both fields")
            }
            AnimatedVisibility(visible = username.isNotEmpty() && password.isNotEmpty()) {
                Text("hello ${'$'}username")
            }
        }
        """.trimIndent(),
    )
    val fields = flattenNodes(form.tree).filterIsInstance<Node.TextField>()
    check(fields.size == 2)
    check(fields[0].bindTo == "username") { "onValueChange = { username = it } must bind" }
    check(fields[0].value == Expr.Ident("username"))
    check(fields[0].modifiers.map { it.name } == listOf("fillMaxWidth"))
    // an unrecognised onValueChange body is not fatal — it just doesn't bind
    check(fields[1].bindTo == null) { "an unrecognised onValueChange must yield null, not throw" }

    val visibles = flattenNodes(form.tree).filterIsInstance<Node.Animated>().map { it.visible }
    check((visibles[0] as Expr.Logic).op == LogicOp.Or)
    check((visibles[1] as Expr.Logic).op == LogicOp.And)
    val formState = ComposeState(form.state)
    check(resolveBool(visibles[0], formState)) { "both fields empty -> the prompt shows" }
    check(!resolveBool(visibles[1], formState)) { "both fields empty -> the greeting hides" }
    formState.setText("username", "sid")
    formState.setText("password", "hunter2")
    check(!resolveBool(visibles[0], formState) && resolveBool(visibles[1], formState)) {
        "both filled should flip both AnimatedVisibility conditions"
    }
    check(resolveText((flattenNodes(form.tree).filterIsInstance<Node.Text>()[1]).value, formState) == "hello sid")
    // unmodellable conditions stay visible rather than vanishing
    check(resolveBool(Expr.Member("Something.weird"), formState))
    check(resolveBool(null, formState))

    // ── forgiveness: unknown modifiers, unknown composables, garbage ─────────────────────────────
    val unknownMod = parseCompose("""Text("hi", modifier = Modifier.shimmer(4.dp).padding(2.dp).glow())""")
    val modNames = (unknownMod.tree[0] as Node.Text).modifiers.map { it.name }
    check(modNames == listOf("shimmer", "padding", "glow")) {
        "an unknown modifier must be kept for the renderer to ignore, not throw: $modNames"
    }
    val unknownNamed = parseCompose("""Text("hi", maxLines = 2, overflow = TextOverflow.Ellipsis)""")
    check((unknownNamed.tree[0] as Node.Text).named.keys.containsAll(listOf("maxLines", "overflow")))
    val unknownComposable = parseCompose("""LazyColumn(modifier = Modifier.fillMaxSize()) { Text("row") }""")
    check(unknownComposable.tree == listOf<Node>(Node.Unknown("LazyColumn"))) {
        "an unsupported composable becomes one Unknown node: ${unknownComposable.tree}"
    }

    // Nothing here may throw. A live editor reparses mid-keystroke, so every prefix of every
    // snippet above is also an input this has to survive.
    val malformed = listOf(
        "",
        "   \n\t ",
        "}}}}",
        "@@@ *** ### €¥",
        "var",
        "var x by",
        "var x by remember {",
        "var x = 5",
        "Text(",
        """Text("unterminated""",
        "Column {",
        "Column(modifier = Modifier.padding(16.",
        "Button(onClick = { count++ ) { Text(\"x\") }",
        "Row(horizontalArrangement = Arrangement.spacedBy(",
        "AnimatedVisibility(visible = ) { }",
        "/* never closed",
        "Card(((((",
        "Column { Column { Column { Text(\"deep\")",
    ) + composePresets.flatMap { p ->
        // every prefix at a 40-char stride: the shapes a typist actually produces
        (0..p.code.length step 40).map { p.code.substring(0, it) }
    }
    malformed.forEach { src ->
        val program = parseCompose(src) // must not throw
        program.tree.forEach { resolveBool((it as? Node.Animated)?.visible, noState) }
    }

    // ── all seven presets, end to end ───────────────────────────────────────────────────────────
    check(composePresets.size == 7) { "the React site ships 7 presets" }
    composePresets.forEach { preset ->
        val program = parseCompose(preset.code)
        check(program.tree.size == 1) { "${preset.label}: expected one root node, got ${program.tree}" }
        val nodes = flattenNodes(program.tree)
        check(nodes.none { it is Node.Unknown }) {
            "${preset.label}: parsed to Unknown ${nodes.filterIsInstance<Node.Unknown>().map { it.name }}"
        }
        check(nodes.filterIsInstance<Node.Text>().isNotEmpty()) { "${preset.label}: no Text survived" }
        // every preset renders under a fresh state without an evaluator blowing up
        val state = ComposeState(program.state)
        nodes.forEach { node ->
            when (node) {
                is Node.Text -> resolveText(node.value, state)
                is Node.Animated -> resolveBool(node.visible, state)
                is Node.Container -> node.modifiers.forEach { m -> m.args.forEach { resolveNum(it, state) } }
                else -> Unit
            }
        }
    }
    // the declaration signature is what gates a state reset, so it must key on the decls only
    check(stateSignature(parseCompose(composePresets[0].code)) == "count:int:0")
    check(stateSignature(parseCompose(composePresets[5].code)) == "size:int:84|shown:bool:true")
    check(stateSignature(parseCompose(composePresets[1].code)) == "") { "a stateless preset has an empty signature" }

    // evalExpr's dynamic form, for the renderer paths that don't know the kind up front
    check(evalExpr(Expr.Num(4.0, NumUnit.Dp), noState) == 4.0)
    check(evalExpr(Expr.Bool(true), noState) == true)
    check(evalExpr(Expr.Ident("nope"), noState) == "nope") { "an unknown ident evaluates to its own name" }
    check(evalExpr(Expr.Member("Color.Green"), noState) == "Color.Green")
    check(evalExpr(Expr.Member("username.isEmpty"), noState) == true)
    check(evalExpr(Expr.Str(listOf(StrPart.Ref("n"))), live) == "7") { "a Ref must read live state" }
}
