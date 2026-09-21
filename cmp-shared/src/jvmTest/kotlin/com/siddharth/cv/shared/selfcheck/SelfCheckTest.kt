package com.siddharth.cv.shared.selfcheck

import com.siddharth.cv.shared.prerender.selfCheck
import com.siddharth.cv.shared.terminal.demo
import kotlin.test.Test

/**
 * The project's self-checks, run by `check` instead of only by `prerenderSite`.
 *
 * They were already the real coverage for the parser, the tone ladder, the lab simulations, the
 * particle integrator, the Mermaid layout and every ported screen — twenty-one of them, plus a
 * render of every prerendered route. `Prerender.selfCheck` calls all of them, and its own comment
 * asks for exactly this: "move all four into a proper commonTest module the day this project has
 * one." There is no commonTest, but jvmTest is an associated compilation of jvmMain, so `internal`
 * is visible here and jvmMain already reaches composeMain through skikoMain.
 *
 * Until now the only thing that ran them was `prerenderSite`, which is on the deploy path and not
 * on CI's `assemble check`. A check nothing runs reads as coverage while being none.
 *
 * This does NOT replace the calls in `Prerender.selfCheck`. Those exist to stop wasm DCE deleting
 * `internal` functions nothing else calls, which is a different job from running them in CI.
 */
class SelfCheckTest {
    /**
     * Twenty-one self-checks plus a full render of every prerendered route, escaping, description
     * clamping and output-path mapping. One test because that is one entry point; the assertions
     * carry their own messages, so a failure still names what broke.
     */
    @Test
    fun everySelfCheckPasses() = selfCheck()

    /** Named `demo` in TerminalEngine.kt, where its own comment calls it "the whole test suite". */
    @Test
    fun terminalCommandTableStillResolves() = demo()
}
