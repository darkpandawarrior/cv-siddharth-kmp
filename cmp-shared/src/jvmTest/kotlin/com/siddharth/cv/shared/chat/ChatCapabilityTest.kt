package com.siddharth.cv.shared.chat

import com.siddharth.kmp.result.AiFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [chatCapability]'s only two paths: a real engine, and one that can't be built. Uses
 * [MockEngine] (already a jvmTest dependency, see [ChatClientTest]) rather than a real target
 * engine like CIO — CIO is `:network`'s own `implementation` dependency, not exposed on
 * cmp-shared's compile classpath, and [chatCapability] never inspects which engine it got.
 */
class ChatCapabilityTest {

    @Test
    fun chatCapability_reportsAvailable_whenEngineConstructs() {
        val capability = chatCapability(engineProvider = { MockEngine { respondOk() } })
        assertNull(capability.unavailableReason)
    }

    @Test
    fun chatCapability_reportsNotSupported_whenEngineConstructionThrows() {
        val capability = chatCapability(engineProvider = { error("no engine on this target") })
        assertEquals(AiFailure.NotSupportedOnPlatform, capability.unavailableReason)
    }
}
