package com.kapijuja.reader

/**
 * Playback prefetch policy.
 *
 * Every cloud engine prepares exactly one next chunk as soon as the current
 * chunk starts playing, so there is no audible network gap between chunks.
 *
 * For paid engines this means that stopping playback can leave one generated
 * but unheard chunk (about 700 characters). That small, bounded overspend was
 * accepted on purpose in exchange for gapless reading. Never more than one
 * future chunk is generated.
 */
object CloudPlaybackPolicy {
    fun prefetchDelayMs(
        @Suppress("UNUSED_PARAMETER") engine: String,
        durationMs: Long
    ): Long? =
        if (durationMs <= 0L) null else 0L
}
