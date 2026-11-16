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
        node("node-hk", "HK"),
        node("node-sg", "SG"),
        node("node-us", "US"),
    )

    @Test
    fun ordersDirectoryRegionsByDistanceFromPinnedLocation() {
        assertEquals(
            "SG",
            NativeLocalServerRecommender.nearestCountryCodes(1.3521, 103.8198).first(),
        )
        assertEquals(
            "US",
            NativeLocalServerRecommender.nearestCountryCodes(39.0438, -77.4874).first(),
        )
    }

    @Test
    fun selectsClosestAvailableRegionAndIncludesActivityCaveat() {
        val recommendation = NativeLocalServerRecommender.recommend(
            activity = "Gaming",
            userLatitude = 1.3521,
            userLongitude = 103.8198,
            nodes = nodes,
        )

        assertEquals("node-sg", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("Gaming"))
        assertTrue(recommendation.reason.contains("approximate region hub"))
        assertTrue(recommendation.reason.contains("exact server location, latency"))
    }

    @Test
    fun streamingRecommendationDisclosesMissingCapacityData() {
        val recommendation = NativeLocalServerRecommender.recommend(
            activity = "Streaming",
            userLatitude = 1.3521,
            userLongitude = 103.8198,
            nodes = nodes,
        )

        assertTrue(recommendation.reason.contains("Streaming"))
        assertTrue(recommendation.reason.contains("capacity is not provided"))
    }

    @Test
    fun unknownServerRegionFallsBackToFirstEntryWithWarning() {
        val recommendation = NativeLocalServerRecommender.recommend(
            activity = "Video calls",
            userLatitude = 1.3521,
            userLongitude = 103.8198,
            nodes = listOf(node("unknown", "ZZ")),
        )

        assertEquals("unknown", recommendation.nodeId)
        assertTrue(recommendation.reason.contains("location is unknown"))
    }

    @Test
    fun rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeLocalServerRecommender.nearestCountryCodes(91.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeLocalServerRecommender.recommend("Other", 0.0, 0.0, nodes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeLocalServerRecommender.recommend("Gaming", 0.0, 0.0, emptyList())
        }
    }

    private fun node(id: String, countryCode: String) = NativePublicDirectory.Node(
        id = id,
        name = "server-$id",
        protocol = "vless",
        country = countryCode,
        countryCode = countryCode,
        configJson = "{}",
    )
}
