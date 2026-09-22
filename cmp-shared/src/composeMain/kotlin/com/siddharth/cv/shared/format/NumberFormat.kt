package com.siddharth.cv.shared.format

import kotlin.math.abs

/**
 * The two number formats this site prints, in one place.
 *
 * `Int.grouped()` existed four times — AnthologyScreen, InkScreen, OpsScreen and WeebScreen — as
 * the same one-line expression, twice under the name `num`. [TenthsPerUnit] was the literal `10`
 * repeated at six one-decimal readouts. Neither is worth a dependency and `kotlin.text` has no
 * grouping formatter on every target, so this is the smallest shared home for both.
 */

/**
 * `Number.prototype.toLocaleString()`, minus the locale.
 *
 * ponytail: no negatives in this corpus, so no sign handling. Add it the day a figure can go below
 * zero — the reversal trick would put the minus at the wrong end.
 */
internal fun Int.grouped(): String =
    toString()
        .reversed()
        .chunked(GroupDigits)
        .joinToString(",")
        .reversed()

private const val GroupDigits = 3

/**
 * Ten tenths to the unit.
 *
 * The readouts hold fixed-point tenths as an Int and split them with `/ 10` and `% 10` rather than
 * formatting a Float, because `kotlin.text` has no `%.1f` on wasm. This is that 10, named, so the
 * divisor and the modulus are visibly the same number.
 */
internal const val TenthsPerUnit = 10

/**
 * `125` -> `"12.5"`. The split every one-decimal readout in this repo was writing by hand, five
 * times, as `"${'$'}{x / 10}.${'$'}{x % 10}"`.
 *
 * `abs` on the fractional half so a negative keeps its sign on the whole part only. Kotlin's `/`
 * truncates toward zero, so a value between -1 and 0 still prints as `0.x` rather than `-0.x` —
 * unchanged from what every call site did before, and none of them can produce one.
 */
internal fun tenthsToString(tenths: Int): String = "${tenths / TenthsPerUnit}.${abs(tenths) % TenthsPerUnit}"
