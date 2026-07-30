package com.siddharth.cv.shared.playground

/**
 * The AST for the Compose subset the playground understands.
 *
 * A faithful Kotlin translation of the types in `cv-siddharth/src/composeInterpreter.ts`, kept
 * structurally identical on purpose: the two implementations must parse the *same language*, so the
 * AST is the shared contract and any divergence is a bug in one of them rather than a design choice.
 *
 * The difference is entirely downstream. The React site walks this tree and emits CSS
 * approximations — `Arrangement.Center` becomes `justify-content: center`, a `Card` becomes a `div`
 * with a `border-radius`. This port walks the same tree and emits the *actual* Compose calls the
 * source text names: `Arrangement.Center` becomes `Arrangement.Center`. Nothing is approximated,
 * because the target and the source are the same framework.
 *
 * Note the road not taken: a *real* playground compiles server-side — the standard architecture for
 * every language playground, JetBrains' Kotlin Playground included: POST the source to a warm Gradle
 * daemon, build against a prebuilt Compose base module so only a tiny user module is recompiled, and
 * mount the resulting wasm. Authentic, but it needs a long-lived VM and it means compiling untrusted
 * code from the internet. Interpreting a curated subset instead costs generality and buys instant
 * feedback with no backend and no arbitrary-code-execution surface — and because this port is itself
 * Compose, the subset renders through the real composables rather than an approximation of them.
 */

/** A value expression. `ref` on [Num] means "read this number out of state" (e.g. `size.dp`). */
sealed interface Expr {
    /** An interpolated string: literal chunks interleaved with `$state` reads. */
    data class Str(val parts: List<StrPart>) : Expr

    data class Num(val value: Double, val unit: NumUnit? = null, val ref: String? = null) : Expr

    data class Bool(val value: Boolean) : Expr

    data class Ident(val name: String) : Expr

    /** A dotted path: `Color.Green`, `Arrangement.Center`, `FontWeight.Bold`, `password.isEmpty`. */
    data class Member(val path: String) : Expr

    data class Logic(val op: LogicOp, val left: Expr, val right: Expr) : Expr
}

sealed interface StrPart {
    data class Literal(val text: String) : StrPart

    data class Ref(val name: String) : StrPart
}

enum class NumUnit { Dp, Sp }

enum class LogicOp { And, Or }

data class ModifierCall(val name: String, val args: List<Expr>)

/** What a `Button(onClick = { … })` lambda does to state. */
sealed interface Action {
    data class Inc(val name: String) : Action

    data class Dec(val name: String) : Action

    data class Toggle(val name: String) : Action

    data class AddAssign(val name: String, val value: Double) : Action

    data class SubAssign(val name: String, val value: Double) : Action

    data class Set(val name: String, val value: Expr) : Action
}

enum class ContainerKind { Column, Row, Box, Card, Surface }

sealed interface Node {
    data class Container(
        val name: ContainerKind,
        val modifiers: List<ModifierCall> = emptyList(),
        val named: Map<String, Expr> = emptyMap(),
        val children: List<Node> = emptyList(),
    ) : Node

    data class Text(
        val value: Expr,
        val named: Map<String, Expr> = emptyMap(),
        val modifiers: List<ModifierCall> = emptyList(),
    ) : Node

    data class Button(
        val onClick: List<Action> = emptyList(),
        val named: Map<String, Expr> = emptyMap(),
        val modifiers: List<ModifierCall> = emptyList(),
        val children: List<Node> = emptyList(),
    ) : Node

    data class Spacer(val modifiers: List<ModifierCall> = emptyList()) : Node

    data class Animated(
        val visible: Expr,
        val modifiers: List<ModifierCall> = emptyList(),
        val children: List<Node> = emptyList(),
    ) : Node

    /**
     * [bindTo] is the state var an `onValueChange = { name = it }` lambda assigns to, or null when
     * the author wrote something the parser doesn't recognise — in which case the field still
     * renders but does not feed back into state. Forgiving by design.
     */
    data class TextField(
        val value: Expr,
        val bindTo: String? = null,
        val named: Map<String, Expr> = emptyMap(),
        val modifiers: List<ModifierCall> = emptyList(),
    ) : Node

    data class Unknown(val name: String) : Node
}

/** A `var x by remember { mutableStateOf(init) }` declaration. */
data class StateDecl(val name: String, val init: StateValue)

/** State is Int/Boolean/String only — the three `mutableStateOf` shapes the subset supports. */
sealed interface StateValue {
    data class IntValue(val value: Int) : StateValue

    data class BoolValue(val value: Boolean) : StateValue

    data class StringValue(val value: String) : StateValue
}

data class Program(val state: List<StateDecl>, val tree: List<Node>)

/** Containers, mirroring the TS `CONTAINERS` set exactly. */
internal val containerNames: Map<String, ContainerKind> =
    ContainerKind.entries.associateBy { it.name }
