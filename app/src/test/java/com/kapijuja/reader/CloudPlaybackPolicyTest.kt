package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudPlaybackPolicyTest {
    @Test
    fun everyCloudEnginePrefetchesNextChunkImmediately() {
        listOf(
            SettingsStore.ENGINE_EDGE,
            SettingsStore.ENGINE_AZURE,
            SettingsStore.ENGINE_GOOGLE,
            SettingsStore.ENGINE_OPENAI,
            SettingsStore.ENGINE_XAI,
            SettingsStore.ENGINE_ELEVEN,
            SettingsStore.ENGINE_INWORLD
        ).forEach { engine ->
            assertEquals(
                engine,
                0L,
                CloudPlaybackPolicy.prefetchDelayMs(engine, 10_000L)
            )
        }
    }

    @Test
    fun unknownDurationDoesNotPrefetch() {
        assertNull(
            CloudPlaybackPolicy.prefetchDelayMs(SettingsStore.ENGINE_XAI, 0L)
        )
    }
}
