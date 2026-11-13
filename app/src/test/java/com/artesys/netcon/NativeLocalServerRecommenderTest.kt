package com.artesys.netcon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NativeLocalServerRecommenderTest {
    private val nodes = listOf(
        node("node-hk", "vless", "HK", "Hong Kong"),
        node("node-sg", "vmess", "SG", "Singapore"),
        node("node-us", "vless", "US", "United States"),
    )

    @Test
    fun matchesExplicitCountryPreference() {
        val recommendation = NativeLocalServerRecommender.recommend("Connect through Singapore", nodes)

        assertEquals("node-sg", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("Singapore"))
    }

    @Test
    fun extractsCountryForCountrySpecificDirectoryFetch() {
        assertEquals("SG", NativeLocalServerRecommender.countryCodePreference("Connect through Singapore"))
        assertEquals("GB", NativeLocalServerRecommender.countryCodePreference("Use the UK"))
        assertEquals(null, NativeLocalServerRecommender.countryCodePreference("PUBG Mobile"))
    }

    @Test
    fun matchesExplicitProtocolPreference() {
        val recommendation = NativeLocalServerRecommender.recommend("I prefer VLESS", nodes)

        assertEquals("node-hk", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("VLESS"))
    }

    @Test
    fun prefersCountryAndProtocolWhenBothMatch() {
        val recommendation = NativeLocalServerRecommender.recommend("VLESS in the United States", nodes)

        assertEquals("node-us", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("United States"))
        assertTrue(recommendation.reason.contains("VLESS"))
    }

    @Test
    fun explainsWhenPurposeHasNoMatchableMetadata() {
        val recommendation = NativeLocalServerRecommender.recommend("PUBG Mobile", nodes)

        assertEquals("node-hk", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("No requested country or protocol matched"))
        assertTrue(recommendation.reason.contains("unmeasured"))
    }

    @Test
    fun rejectsEmptyPurposeAndConfigurationLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeLocalServerRecommender.recommend("  ", nodes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeLocalServerRecommender.recommend("vless://sensitive-config", nodes)
        }
    }

    private fun node(id: String, protocol: String, countryCode: String, country: String) =
        NativePublicDirectory.Node(
            id = id,
            name = protocol,
            protocol = protocol,
            country = country,
            countryCode = countryCode,
            configJson = "{}",
        )
}
