package com.artesys.netcon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NativeGeminiRecommenderTest {
    private val nodes = listOf(
        node("node-a", "vless"),
        node("node-b", "vmess"),
    )

    @Test
    fun mapsSelectionIdToOriginalNodeIdAndSkipsThoughtParts() {
        val response = """
            {"candidates":[{"content":{"parts":[
              {"thought":true,"text":"I should choose the second node."},
              {"text":"{\"selectionId\":\"2\",\"reason\":\"Available metadata fits.\"}"}
            ]}}]}
        """.trimIndent()

        val recommendation = NativeGeminiRecommender.parseRecommendation(response, nodes)

        assertEquals("node-b", recommendation.nodeId)
        assertEquals("Available metadata fits.", recommendation.reason)
    }

    @Test
    fun rejectsSelectionOutsideAvailableNodes() {
        val response = """{"candidates":[{"content":{"parts":[{"text":"{\"selectionId\":\"3\",\"reason\":\"No match\"}"}]}}]}"""

        assertThrows(IllegalStateException::class.java) {
            NativeGeminiRecommender.parseRecommendation(response, nodes)
        }
    }

    private fun node(id: String, protocol: String) = NativePublicDirectory.Node(
        id = id,
        name = protocol,
        protocol = protocol,
        country = "Unknown location",
        countryCode = null,
        configJson = "{}",
    )
}
