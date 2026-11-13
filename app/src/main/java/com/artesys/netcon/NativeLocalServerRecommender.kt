package com.artesys.netcon

import java.util.Locale

internal object NativeLocalServerRecommender {
    data class Recommendation(val nodeId: String, val reason: String)

    private val countryCodes = NativePublicDirectory.countryCodes
    private val countryAliases = mapOf(
        "AE" to listOf("UAE", "United Arab Emirates"),
        "GB" to listOf("UK", "Britain", "Great Britain", "England"),
        "HK" to listOf("Hong Kong"),
        "KR" to listOf("South Korea", "Korea"),
        "US" to listOf("USA", "United States", "America"),
    )
    private val protocols = listOf("shadowsocks", "vless", "vmess", "trojan")

    fun countryCodePreference(purpose: String): String? {
        val trimmedPurpose = purpose.trim().take(500)
        return countryCodes.firstOrNull { code ->
            val name = Locale("", code).displayCountry
            (listOf(name) + countryAliases[code].orEmpty()).any { trimmedPurpose.containsPhrase(it) }
        }
    }

    fun recommend(purpose: String, nodes: List<NativePublicDirectory.Node>): Recommendation {
        val trimmedPurpose = purpose.trim().take(500)
        require(trimmedPurpose.isNotEmpty()) { "Describe your use or preferred country or protocol." }
        require(!trimmedPurpose.startsWith("{") && !Regex("(?i)(vmess|vless|trojan|ss)://").containsMatchIn(trimmedPurpose)) {
            "Import server configurations in the local import field instead."
        }
        require(nodes.isNotEmpty()) { "Load public servers before requesting a local match." }

        val available = nodes.take(30)
        val requestedCountry = countryCodePreference(trimmedPurpose)
        val requestedProtocol = protocols.firstOrNull { trimmedPurpose.containsPhrase(it) }
        val selected = available.firstOrNull { node ->
            (requestedCountry == null || node.countryCode.equals(requestedCountry, ignoreCase = true)) &&
                (requestedProtocol == null || node.protocol.equals(requestedProtocol, ignoreCase = true))
        } ?: if (requestedCountry != null || requestedProtocol != null) {
            available.firstOrNull { node ->
                (requestedCountry != null && node.countryCode.equals(requestedCountry, ignoreCase = true)) ||
                    (requestedProtocol != null && node.protocol.equals(requestedProtocol, ignoreCase = true))
            } ?: available.first()
        } else {
            available.first()
        }

        val matchedCountry = requestedCountry != null &&
            selected.countryCode.equals(requestedCountry, ignoreCase = true)
        val matchedProtocol = requestedProtocol != null &&
            selected.protocol.equals(requestedProtocol, ignoreCase = true)
        val matchedPreferences = buildList {
            if (matchedCountry) add("source-reported ${selected.country}")
            if (matchedProtocol) add(selected.protocol.uppercase(Locale.ROOT))
        }
        val reason = if (matchedPreferences.isEmpty()) {
            "No requested country or protocol matched the available feed; selected its first entry. " +
                "Latency and availability are unmeasured, and public servers are untrusted."
        } else {
            "Matched ${matchedPreferences.joinToString(" and ")} from feed metadata. " +
                "Latency and availability are unmeasured, and public servers are untrusted."
        }
        return Recommendation(selected.id, reason)
    }

    private fun String.containsPhrase(phrase: String): Boolean {
        if (phrase.isBlank()) return false
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
        return pattern.containsMatchIn(this)
    }
}
