package com.kapijuja.reader

/**
 * Playback prefetch policy.
 *
 * Free/non-sensitive cloud engines may prefetch immediately.
 * xAI Grok is paid per generated text, so only one next chunk is prefetched
 * and only after the current chunk is already half played.
 * OpenAI remains no-prefetch because its existing cost guard treats it as
 * paid-sensitive and we do not change that policy here.
 */
object CloudPlaybackPolicy {
    fun prefetchDelayMs(
        engine: String,
        durationMs: Long
    ): Long? =
        when {
            durationMs <= 0L -> null
            engine == SettingsStore.ENGINE_XAI ->
                (durationMs / 2L).coerceAtLeast(600L)
            !CloudTtsDispatcher.isPaidPrefetchSensitive(engine) ->
                0L
            else ->
                null
        }
}
