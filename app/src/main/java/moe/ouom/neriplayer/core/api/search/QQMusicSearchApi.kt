package moe.ouom.neriplayer.core.api.search

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.ouom.neriplayer.util.NPLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import moe.ouom.neriplayer.core.di.AppContainer

@Serializable private data class QQMusicSearchResponse(val data: QQMusicSearchData?)
@Serializable private data class QQMusicSearchData(val song: QQMusicSearchSong?)
@Serializable private data class QQMusicSearchSong(val list: List<QQMusicSongSummary>?)
@Serializable private data class QQMusicSongSummary(
    @SerialName("songmid") val songMid: String,
    @SerialName("songname") val songName: String,
    val singer: List<QQMusicArtist>,
    @SerialName("albummid") val albumMid: String?,
    @SerialName("albumname") val albumName: String?,
    val interval: Long
)

@Serializable private data class QQMusicArtist(val name: String)

@Serializable private data class QQMusicDetailContainer(
    @SerialName("songinfo") val songInfo: QQMusicDetailResponse
)
@Serializable private data class QQMusicDetailResponse(val data: QQMusicDetailData?)
@Serializable private data class QQMusicDetailData(@SerialName("track_info") val trackInfo: QQMusicTrackInfo?)
@Serializable private data class QQMusicTrackInfo(
    val mid: String,
    val name: String,
    val singer: List<QQMusicArtist>,
    val album: QQMusicAlbum
)
@Serializable private data class QQMusicAlbum(val name: String, val mid: String)

@Serializable private data class QQMusicLyricResponse(val lyric: String?)

class QQMusicSearchApi : SearchApi {

    companion object {
        private const val TAG = "QQMusicSearchApi"
    }

    private val client: OkHttpClient = AppContainer.sharedOkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(keyword: String, page: Int): List<SongSearchInfo> {
        return withContext(Dispatchers.IO) {
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("n", "20")
                .addQueryParameter("p", page.toString())
                .addQueryParameter("w", keyword)
                .addQueryParameter("cr", "1")
                .addQueryParameter("g_tk", "5381")
                .build()

            val responseJson = executeRequest(url.toString()) as String
            val searchResult = json.decodeFromString<QQMusicSearchResponse>(responseJson)

            searchResult.data?.song?.list?.map { song ->
                SongSearchInfo(
                    id = song.songMid,
                    songName = song.songName,
                    singer = song.singer.joinToString("/") { it.name },
                    duration = formatDuration(song.interval),
                    source = MusicPlatform.QQ_MUSIC,
                    albumName = song.albumName,
                    coverUrl = song.albumMid?.let { "https://y.qq.com/music/photo_new/T002R800x800M000$it.jpg" }
                )
            } ?: emptyList()
        }
    }

    override suspend fun getSongInfo(id: String): SongDetails {
        return withContext(Dispatchers.IO) {
            val detailRequestData = JSONObject().put(
                "songinfo", JSONObject()
                    .put("method", "get_song_detail_yqq")
                    .put("module", "music.pf_song_detail_svr")
                    .put("param", JSONObject().put("song_mid", id))
            ).toString()

            val url = "https://u.y.qq.com/cgi-bin/musicu.fcg".toHttpUrl().newBuilder()
                .addQueryParameter("data", detailRequestData)
                .build()

            val responseJson = executeRequest(url.toString()) as String
            NPLogger.d(TAG, "获取歌曲详情的原始 JSON 响应: $responseJson")

            val songInfoJson = JSONObject(responseJson).optJSONObject("songinfo")?.toString()
                ?: throw IOException("响应中找不到 songinfo 字段")

            val songData = json.decodeFromString<QQMusicDetailResponse>(songInfoJson).data?.trackInfo
                ?: throw IOException("找不到ID为 $id 的歌曲详情")

            coroutineScope {
                val lyricDeferred = async { fetchQQMusicLyric(id) }

                SongDetails(
                    id = songData.mid,
                    songName = songData.name,
                    singer = songData.singer.joinToString("/") { it.name },
                    album = songData.album.name,
                    coverUrl = "https://y.qq.com/music/photo_new/T002R800x800M000${songData.album.mid}.jpg",
                    lyric = lyricDeferred.await()
                )
            }
        }
    }

    private fun fetchQQMusicLyric(songMid: String): String? {
        return try {
            val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg".toHttpUrl().newBuilder()
                .addQueryParameter("songmid", songMid)
                .addQueryParameter("format", "json")
                .addQueryParameter("inCharset", "utf8")
                .addQueryParameter("outCharset", "utf-8")
                .build()

            val request = Request.Builder().url(url)
                .header("Referer", "https://y.qq.com")
                .build()

            val responseJson = executeRequest(request) as String
            val base64Lyric = json.decodeFromString<QQMusicLyricResponse>(responseJson).lyric

            if (base64Lyric != null) {
                String(Base64.getDecoder().decode(base64Lyric))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取QQ音乐歌词失败", e)
            null
        }
    }

    @Throws(IOException::class)
    private fun executeRequest(url: String, asBytes: Boolean = false): Any {
        val request = Request.Builder().url(url).build()
        return executeRequest(request, asBytes)
    }

    @Throws(IOException::class)
    private fun executeRequest(request: Request, asBytes: Boolean = false): Any {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("请求失败: ${response.code} for url: ${request.url}")
            val body = response.body
            return if (asBytes) body.bytes() else body.string()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}