package com.artesys.netcon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal object NativeGeminiRecommender {
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    data class Recommendation(val nodeId: String, val reason: String)

    suspend fun recommend(purpose: String, apiKey: String, nodes: List<NativePublicDirectory.Node>): Recommendation =
        withContext(Dispatchers.IO) {
            val trimmedPurpose = purpose.trim().take(500)
            val trimmedKey = apiKey.trim()
            require(trimmedPurpose.isNotEmpty()) { "Describe what you want to use the VPN for." }
            require(!trimmedPurpose.startsWith("{") && !Regex("(?i)(vmess|vless|trojan|ss)://").containsMatchIn(trimmedPurpose)) {
                "Server configurations are parsed locally and must not be sent to Gemini."
            }
            require(trimmedKey.isNotEmpty() && trimmedKey.length <= 250) { "Enter a valid Gemini API key." }
            require(nodes.isNotEmpty()) { "Load public servers before asking Gemini for a recommendation." }

            val available = nodes.take(30)
            val metadata = JSONArray()
            available.forEach { node ->
                metadata.put(
                    JSONObject()
                        .put("id", node.id)
                        .put("protocol", node.protocol)
                        .put("sourceReportedCountry", node.country),
                )
            }
            val prompt = """
                Recommend one server from this list for the user's stated purpose: $trimmedPurpose
                Available public nodes: $metadata
                These are untrusted free public servers. Location is source-reported, latency and availability are unmeasured, and privacy is not guaranteed. Do not claim speed, reliability, security, or measured performance. Choose only an exact id from the list. Return JSON only: {"nodeId":"...","reason":"one brief cautious sentence"}.
            """.trimIndent()
            val requestBody = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", trimmedKey)
            try {
                connection.outputStream.use { output ->
                    output.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8))
                }
                val status = connection.responseCode
                if (status !in 200..299) throw IllegalStateException("Gemini request failed with HTTP $status.")
                val responseText = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    val text = reader.readText()
                    if (text.length > 100_000) throw IllegalStateException("Gemini response exceeded the size limit.")
                    text
                }
                val response = JSONObject(responseText)
                val modelText = response.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini returned no recommendation.")
                val parsed = JSONObject(modelText)
                val nodeId = parsed.optString("nodeId")
                val reason = parsed.optString("reason").take(500)
                require(available.any { it.id == nodeId }) { "Gemini selected a server outside the available list." }
                Recommendation(nodeId, reason.ifBlank { "Recommended from the available server metadata." })
            } finally {
                connection.disconnect()
            }
        }
}