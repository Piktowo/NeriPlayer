package moe.ouom.neriplayer.util

import moe.ouom.neriplayer.core.api.netease.NeteaseClient
import moe.ouom.neriplayer.core.api.search.CloudMusicSearchApi
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.QQMusicSearchApi
import moe.ouom.neriplayer.core.api.search.SongDetails
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import moe.ouom.neriplayer.core.di.AppContainer

object SearchManager {
    private val qqApi by lazy { QQMusicSearchApi() }

    suspend fun search(
        keyword: String,
        platform: MusicPlatform,
    ): List<SongSearchInfo> {
        val api = if (platform == MusicPlatform.CLOUD_MUSIC) {
            CloudMusicSearchApi(AppContainer.neteaseClient)
        } else {
            qqApi
        }

        NPLogger.d("SearchManager", "try to search $keyword")
        return try {
            api.search(keyword, page = 1).take(10)
        } catch (e: Exception) {
            NPLogger.e("SearchManager", "Failed to find match", e)
            null
        }!!
    }

    suspend fun findBestMatch(
        songName: String,
        platform: MusicPlatform,
        neteaseClient: NeteaseClient
    ): SongDetails? {
        val api = if (platform == MusicPlatform.CLOUD_MUSIC) {
            CloudMusicSearchApi(neteaseClient)
        } else {
            qqApi
        }

        NPLogger.d("SearchManager", "try to search $songName")
        return try {
            val searchResults = api.search(songName, page = 1)
            val bestMatch = searchResults.firstOrNull() ?: return null
            api.getSongInfo(bestMatch.id)
        } catch (e: Exception) {
            NPLogger.e("SearchManager", "Failed to find match", e)
            null
        }
    }
}