package moe.ouom.neriplayer.ui.viewmodel.playlist

import android.app.Application
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteasePlaylist
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONObject
import java.io.IOException

private const val TAG_PD = "NERI-PlaylistVM"

data class PlaylistHeader(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val playCount: Long,
    val trackCount: Int
)

@Parcelize
data class SongItem(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val matchedLyric: String? = null,
    val matchedLyricSource: MusicPlatform? = null,
    val userLyricOffsetMs: Long = 0L
) : Parcelable

data class PlaylistDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val header: PlaylistHeader? = null,
    val tracks: List<SongItem> = emptyList()
)

class PlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AppContainer.neteaseClient
    private val cookieRepo = AppContainer.neteaseCookieRepo

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState

    private var playlistId: Long = 0L

    init {
        viewModelScope.launch {
            cookieRepo.cookieFlow.collect { saved ->
                val cookies = saved.toMutableMap()
                cookies.putIfAbsent("os", "pc")

                val loggedIn = !cookies["MUSIC_U"].isNullOrBlank()
                val hasCsrf = !cookies["__csrf"].isNullOrBlank()
                if (loggedIn && !hasCsrf) {
                    withContext(Dispatchers.IO) {
                        try {
                            NPLogger.d(TAG_PD, "csrf missing after login, preheating weapi session…")
                            client.ensureWeapiSession()
                        } catch (e: Exception) {
                            NPLogger.w(TAG_PD, "ensureWeapiSession failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun start(playlist: NeteasePlaylist) {
        if (playlistId == playlist.id && _uiState.value.header != null && _uiState.value.tracks.isNotEmpty()) return
        playlistId = playlist.id

        _uiState.value = PlaylistDetailUiState(
            loading = true,
            header = PlaylistHeader(
                id = playlist.id,
                name = playlist.name,
                coverUrl = toHttps(playlist.picUrl) ?: "",
                playCount = playlist.playCount,
                trackCount = playlist.trackCount
            ),
            tracks = emptyList()
        )

        viewModelScope.launch {
            try {

                val cookies = withContext(Dispatchers.IO) { cookieRepo.getCookiesOnce() }.toMutableMap()
                cookies.putIfAbsent("os", "pc")

                val raw = withContext(Dispatchers.IO) { client.getPlaylistDetail(playlistId) }
                NPLogger.d(TAG_PD, "detail head=${raw.take(500)}")

                val (header, tracks) = parseDetail(raw)

                _uiState.value = PlaylistDetailUiState(
                    loading = false,
                    error = null,
                    header = header,
                    tracks = tracks
                )
            } catch (e: IOException) {
                Log.e(TAG_PD, "Network/Server error", e)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "网络异常或服务器异常：${e.message ?: e.javaClass.simpleName}"
                )
            } catch (e: Exception) {
                Log.e(TAG_PD, "Unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "解析/未知错误：${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun retry() {
        val h = _uiState.value.header ?: return
        start(
            NeteasePlaylist(
                id = h.id,
                name = h.name,
                picUrl = h.coverUrl,
                playCount = h.playCount,
                trackCount = h.trackCount
            )
        )
    }

    private fun toHttps(url: String?): String? =
        url?.replaceFirst(Regex("^http://"), "https://")

    private fun parseDetail(raw: String): ParsedDetail {
        val root = JSONObject(raw)
        val code = root.optInt("code", -1)
        require(code == 200) { "接口返回异常 code=$code" }

        val pl = root.optJSONObject("playlist") ?: error("缺少 playlist 节点")

        val header = PlaylistHeader(
            id = pl.optLong("id"),
            name = pl.optString("name"),
            coverUrl = toHttps(pl.optString("coverImgUrl", "")) ?: "",
            playCount = pl.optLong("playCount", 0L),
            trackCount = pl.optInt("trackCount", 0)
        )

        val list = mutableListOf<SongItem>()
        val tracksArr = pl.optJSONArray("tracks")
        if (tracksArr != null) {
            for (i in 0 until tracksArr.length()) {
                val t = tracksArr.optJSONObject(i) ?: continue
                val id = t.optLong("id", 0L)
                val name = t.optString("name", "")
                if (id == 0L || name.isBlank()) continue

                val ar = t.optJSONArray("ar")
                val artist = buildString {
                    if (ar != null) {
                        for (j in 0 until ar.length()) {
                            val a = ar.optJSONObject(j)?.optString("name") ?: continue
                            if (isNotEmpty()) append(" / ")
                            append(a)
                        }
                    }
                }
                val al = t.optJSONObject("al")
                val album = al?.optString("name", "") ?: ""
                val cover = toHttps(al?.optString("picUrl", null))
                val duration = t.optLong("dt", 0L)

                list.add(
                    SongItem(
                        id = id,
                        name = name,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        coverUrl = cover
                    )
                )
            }
        }
        return ParsedDetail(header, list)
    }

    private data class ParsedDetail(
        val header: PlaylistHeader,
        val tracks: List<SongItem>
    )
}