package com.siddharth.cv.shared.chat

import com.siddharth.kmp.network.httpClientEngine
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import io.ktor.client.engine.HttpClientEngine

/**
 * Whether this build can even ATTEMPT a chat request — checked once, before the panel opens,
 * rather than letting a visitor open it and watch the first question fail.
 *
 * Every target `composeMain` ships on (android/jvm/iosArm64/iosSimulatorArm64/wasmJs) now has a
 * real `:network` [httpClientEngine] actual, so [AiCapabilities.unavailableReason] is null in
 * practice today — this exists for the target that doesn't: a future addition to composeMain's
 * target set (or a source set that borrows this file) without an engine actual, which would
 * otherwise fail loudly at [engineProvider] construction time instead of hiding the button.
 * [engineProvider] is a seam for exactly that failure — production always passes the real
 * [httpClientEngine], a test passes one that throws.
 */
fun chatCapability(engineProvider: () -> HttpClientEngine = ::httpClientEngine): AiCapabilities {
    val reason = runCatching(engineProvider).exceptionOrNull()?.let { AiFailure.NotSupportedOnPlatform }
    return AiCapabilities(
        streaming = true,
        multimodal = false,
        honoredConfigFields = emptySet(),
        unavailableReason = reason,
    )
}
