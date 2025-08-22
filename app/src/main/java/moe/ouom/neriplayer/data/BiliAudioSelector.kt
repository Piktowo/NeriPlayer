package moe.ouom.neriplayer.data

import kotlin.collections.firstOrNull

data class BiliAudioStreamInfo(
    val id: Int?,
    val mimeType: String,
    val bitrateKbps: Int,
    val qualityTag: String?,
    val url: String
)

enum class BiliQuality(val key: String, val minBitrateKbps: Int) {
    DOLBY("dolby",      0),
    HIRES("hires",    1000),
    LOSSLESS("lossless", 500),
    HIGH("high",       180),
    MEDIUM("medium",   120),
    LOW("low",          60);

    companion object {
        private val order = listOf(DOLBY, HIRES, LOSSLESS, HIGH, MEDIUM, LOW)

        fun fromKey(key: String): BiliQuality =
            order.find { it.key == key } ?: HIGH

        fun degradeChain(from: BiliQuality): List<BiliQuality> {
            val startIdx = order.indexOf(from).coerceAtLeast(0)
            return order.drop(startIdx)
        }
    }
}

fun selectStreamByPreference(
    available: List<BiliAudioStreamInfo>,
    preferredKey: String
): BiliAudioStreamInfo? {
    if (available.isEmpty()) return null
    val pref = BiliQuality.fromKey(preferredKey)

    val sorted = available.sortedByDescending { it.bitrateKbps }

    when (pref) {
        BiliQuality.DOLBY ->
            sorted.firstOrNull { it.qualityTag == "dolby" }?.let { return it }
        BiliQuality.HIRES ->
            sorted.firstOrNull { it.qualityTag == "hires" }?.let { return it }
        else -> Unit
    }

    for (q in BiliQuality.degradeChain(pref)) {
        val hit = when (q) {
            BiliQuality.DOLBY   -> sorted.firstOrNull { it.qualityTag == "dolby" }
            BiliQuality.HIRES   -> sorted.firstOrNull { it.qualityTag == "hires" }
            else -> {
                val candidates = sorted.filter { it.bitrateKbps >= q.minBitrateKbps }
                candidates.lastOrNull()
            }
        }
        if (hit != null) return hit
    }

    return sorted.firstOrNull()
}