package com.artesys.netcon

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object NativeLocalServerRecommender {
    val activities = listOf("Gaming", "Streaming", "Video calls", "Browsing")

    data class Recommendation(val nodeId: String, val reason: String)

    fun nearestCountryCodes(latitude: Double, longitude: Double, limit: Int = 3): List<String> {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Choose a valid map location."
        }
        return WorldHubNodes
            .sortedBy { distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .take(limit.coerceAtLeast(1))
            .map { it.code }
    }

    fun recommend(
        activity: String,
        userLatitude: Double,
        userLongitude: Double,
        nodes: List<NativePublicDirectory.Node>,
    ): Recommendation {
        require(activity in activities) { "Choose an activity for server matching." }
        require(userLatitude in -90.0..90.0 && userLongitude in -180.0..180.0) {
            "Choose a valid map location."
        }
        require(nodes.isNotEmpty()) { "Load public servers before requesting a local match." }

        val available = nodes.take(30)
        val hubsByCode = WorldHubNodes.associateBy { it.code }
        val selected = available.minByOrNull { node ->
            val hub = node.countryCode?.let(hubsByCode::get)
            hub?.let { distanceKm(userLatitude, userLongitude, it.latitude, it.longitude) }
                ?: Double.POSITIVE_INFINITY
        } ?: throw IllegalStateException("No public servers were available to compare.")
        val selectedHub = selected.countryCode?.let(hubsByCode::get)
        val reason = if (selectedHub == null) {
            "$activity: server location is unknown, so the first available server was selected. " +
                "Actual latency, capacity, and reliability are not measured; public servers are untrusted."
        } else {
            val distance = distanceKm(
                userLatitude,
                userLongitude,
                selectedHub.latitude,
                selectedHub.longitude,
            ).toInt()
            val focus = when (activity) {
                "Gaming" -> "Geographic distance is used as a latency proxy for gaming"
                "Video calls" -> "Geographic distance is used as a latency proxy for calls"
                "Streaming" -> "The nearest region is used because streaming capacity is not provided"
                else -> "The nearest listed region is used for general traffic"
            }
            "$activity: selected a server reported in ${selected.country}; its approximate region hub " +
                "(${selectedHub.cityLabel}) is about $distance km away. $focus. The feed does not provide " +
                "exact server location, latency, throughput, or reliability; public servers are untrusted."
        }
        return Recommendation(selected.id, reason)
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6_371.0
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val haversine = (sin(latDelta / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(lonDelta / 2).let { it * it }).coerceIn(0.0, 1.0)
        return earthRadiusKm * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
