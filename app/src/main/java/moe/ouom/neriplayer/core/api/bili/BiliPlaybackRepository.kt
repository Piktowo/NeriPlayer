package moe.ouom.neriplayer.core.api.bili

import kotlinx.coroutines.flow.first
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.BiliAudioStreamInfo
import moe.ouom.neriplayer.data.SettingsRepository
import moe.ouom.neriplayer.data.selectStreamByPreference

interface BiliAudioDataSource {
    val client: BiliClient
        get() = AppContainer.biliClient

    suspend fun fetchAudioStreams(
        bvid: String,
        cid: Long
    ): List<BiliAudioStreamInfo>
}

class BiliPlaybackRepository(
    private val source: BiliAudioDataSource,
    private val settings: SettingsRepository
) {
    suspend fun getBestPlayableAudio(
        bvid: String,
        cid: Long,
        preferredKeyOverride: String? = null
    ): BiliAudioStreamInfo? {
        val streams = source.fetchAudioStreams(bvid, cid)
        val prefKey = preferredKeyOverride ?: settings.biliAudioQualityFlow.first()
        return selectStreamByPreference(streams, prefKey)
    }

    suspend fun getAudioWithDecision(
        bvid: String,
        cid: Long,
        preferredKeyOverride: String? = null
    ): Pair<List<BiliAudioStreamInfo>, BiliAudioStreamInfo?> {
        val streams = source.fetchAudioStreams(bvid, cid)
        val prefKey = preferredKeyOverride ?: settings.biliAudioQualityFlow.first()
        val chosen = selectStreamByPreference(streams, prefKey)
        return streams to chosen
    }
}
