package com.kapijuja.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEnginesTest {
    @Test
    fun perCharacterEnginesShareTheCostGuard() {
        assertTrue(CloudEngines.isPerCharacter(SettingsStore.ENGINE_XAI))
        assertTrue(CloudEngines.isPerCharacter(SettingsStore.ENGINE_ELEVEN))
        assertTrue(CloudEngines.isPerCharacter(SettingsStore.ENGINE_INWORLD))
        assertFalse(CloudEngines.isPerCharacter(SettingsStore.ENGINE_EDGE))
    }

    @Test
    fun costIsProportionalToCharactersAndPrice() {
        // 1M characters at $100 per 1M, shown in euro.
        assertEquals(87.0, CloudEngines.costEuro(1_000_000, 100.0), 0.0001)
        assertEquals(8.7, CloudEngines.costEuro(100_000, 100.0), 0.0001)
    }

    @Test
    fun cheaperModelsAreRecognised() {
        assertEquals(80.0, ElevenLabsTtsClient.priceUsdPerMillionChars("eleven_v3"), 0.0)
        assertEquals(40.0, ElevenLabsTtsClient.priceUsdPerMillionChars("eleven_flash_v2_5"), 0.0)
        assertEquals(40.0, ElevenLabsTtsClient.priceUsdPerMillionChars("eleven_v4_turbo"), 0.0)
        assertEquals(25.0, InworldTtsClient.priceUsdPerMillionChars("inworld-tts-2"), 0.0)
        assertEquals(15.0, InworldTtsClient.priceUsdPerMillionChars("inworld-tts-2-flash"), 0.0)
    }

    @Test
    fun everyKeyedProviderHasKeyAndBillingLinks() {
        listOf(
            SettingsStore.ENGINE_OPENAI,
            SettingsStore.ENGINE_AZURE,
            SettingsStore.ENGINE_GOOGLE,
            SettingsStore.ENGINE_XAI,
            SettingsStore.ENGINE_ELEVEN,
            SettingsStore.ENGINE_INWORLD
        ).forEach { engine ->
            val links = CloudEngines.links(engine)
            assertTrue(engine, links != null && links.keyUrl.startsWith("https://"))
            assertTrue(engine, links != null && links.billingUrl.startsWith("https://"))
        }
    }

    @Test
    fun modelsAreListedCheapestFirstWithHourlyPrice() {
        val eleven = CloudEngines.models(SettingsStore.ENGINE_ELEVEN)
        val prices = eleven.map {
            CloudEngines.priceUsdPerMillionChars(SettingsStore.ENGINE_ELEVEN, it.id)
        }
        assertEquals(prices.sorted(), prices)
        assertEquals(ElevenLabsTtsClient.models.size, eleven.size)

        assertEquals(
            "inworld-tts-2-flash",
            CloudEngines.models(SettingsStore.ENGINE_INWORLD).first().id
        )
        assertEquals(
            "≈ $4.8/час",
            CloudEngines.pricePerHour(SettingsStore.ENGINE_ELEVEN, "eleven_v3", "час")
        )
        assertEquals("Eleven v3", CloudEngines.modelName(SettingsStore.ENGINE_ELEVEN, "eleven_v3"))
        assertTrue(CloudEngines.models(SettingsStore.ENGINE_EDGE).isEmpty())
    }
}
