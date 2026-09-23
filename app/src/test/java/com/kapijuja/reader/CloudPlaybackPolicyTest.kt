package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudPlaybackPolicyTest {
    @Test
    fun grokPrefetchesOnlyAfterHalfCurrentAudio() {
        assertEquals(
            5_000L,
            CloudPlaybackPolicy.prefetchDelayMs(
                SettingsStore.ENGINE_XAI,
                10_000L
            )
        )
    }

    @Test
    fun grokShortAudioStillWaitsBeforePrefetch() {
        assertEquals(
            600L,
            CloudPlaybackPolicy.prefetchDelayMs(
                SettingsStore.ENGINE_XAI,
                800L
            )
        )
    }

    @Test
    fun freeCloudEngineMayPrefetchImmediately() {
        assertEquals(
            0L,
            CloudPlaybackPolicy.prefetchDelayMs(
                SettingsStore.ENGINE_EDGE,
                10_000L
            )
        )
    }

    @Test
    fun openAiKeepsNoPrefetchPolicy() {
        assertNull(
            CloudPlaybackPolicy.prefetchDelayMs(
                SettingsStore.ENGINE_OPENAI,
                10_000L
            )
        )
    }
}
