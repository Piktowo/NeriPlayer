package moe.ouom.neriplayer.ui.viewmodel.tab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.PlayerManager.biliClient
import moe.ouom.neriplayer.core.player.PlayerManager.neteaseClient
import moe.ouom.neriplayer.data.NeteaseCookieRepository
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import org.json.JSONObject

private const val TAG = "NERI-ExploreVM"

enum class SearchSource(val displayName: String) {
    NETEASE("网易云"),
    BILIBILI("哔哩哔哩")
}

data class ExploreUiState(
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val playlists: List<NeteasePlaylist> = emptyList(),
    val selectedTag: String = "全部",
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<SongItem> = emptyList(),
    val selectedSearchSource: SearchSource = SearchSource.NETEASE
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val neteaseRepo = NeteaseCookieRepository(application)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState

    init {
        viewModelScope.launch {
            neteaseRepo.cookieFlow.collect { raw ->
                loadHighQuality()
            }
        }
    }

    fun setSearchSource(source: SearchSource) {
        if (source == _uiState.value.selectedSearchSource) return
        _uiState.value = _uiState.value.copy(
            selectedSearchSource = source,
            searchResults = emptyList(),
            searchError = null
        )
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchError = null)
            return
        }
        when (_uiState.value.selectedSearchSource) {
            SearchSource.NETEASE -> searchNetease(keyword)
            SearchSource.BILIBILI -> searchBilibili(keyword)
        }
    }

    private fun searchBilibili(keyword: String) {
        _uiState.value = _uiState.value.copy(searching = true, searchError = null)
        viewModelScope.launch {
            try {
                val searchPage = withContext(Dispatchers.IO) {
                    biliClient.searchVideos(keyword = keyword, page = 1)
                }

                val songs = searchPage.items.map { it.toSongItem() }
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    searchError = null,
                    searchResults = songs
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    searchError = "Bilibili 搜索失败: ${e.message}",
                    searchResults = emptyList()
                )
            }
        }
    }

    fun toggleExpanded() {
        _uiState.value = _uiState.value.copy(expanded = !_uiState.value.expanded)
    }

    fun loadHighQuality(cat: String? = null) {
        val realCat = cat ?: _uiState.value.selectedTag
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    neteaseClient.getHighQualityPlaylists(realCat, 50, 0L)
                }
                val mapped = parsePlaylists(raw)

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = null,
                    playlists = mapped,
                    selectedTag = realCat
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "加载歌单失败: ${e.message}"
                )
            }
        }
    }

    private fun parsePlaylists(raw: String): List<NeteasePlaylist> {
        val result = mutableListOf<NeteasePlaylist>()
        val root = JSONObject(raw)
        if (root.optInt("code") != 200) return emptyList()
        val arr = root.optJSONArray("playlists") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(NeteasePlaylist(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                picUrl = obj.optString("coverImgUrl").replace("http://", "https://"),
                playCount = obj.optLong("playCount"),
                trackCount = obj.optInt("trackCount")
            ))
        }
        return result
    }

    private fun searchNetease(keyword: String) {
        _uiState.value = _uiState.value.copy(searching = true, searchError = null)
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    neteaseClient.searchSongs(keyword, limit = 30, offset = 0, type = 1)
                }
                val songs = parseSongs(raw)
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    searchError = null,
                    searchResults = songs
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searching = false,
                    searchError = "网易云搜索失败: ${e.message}",
                    searchResults = emptyList()
                )
            }
        }
    }

    private fun parseSongs(raw: String): List<SongItem> {
        val list = mutableListOf<SongItem>()
        val root = JSONObject(raw)
        if (root.optInt("code") != 200) return emptyList()
        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        for (i in 0 until songs.length()) {
            val obj = songs.optJSONObject(i) ?: continue
            val artistsArr = obj.optJSONArray("ar")
            val artistNames = if (artistsArr != null) (0 until artistsArr.length())
                .mapNotNull { artistsArr.optJSONObject(it)?.optString("name") } else emptyList()
            val albumObj = obj.optJSONObject("al")
            list.add(SongItem(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                artist = artistNames.joinToString(" / "),
                album = albumObj?.optString("name").orEmpty(),
                durationMs = obj.optLong("dt"),
                coverUrl = albumObj?.optString("picUrl")?.replace("http://", "https://")
            ))
        }
        return list
    }

    suspend fun getVideoInfoByAvid(avid: Long): BiliClient.VideoBasicInfo {
        return withContext(Dispatchers.IO) {
            biliClient.getVideoBasicInfoByAvid(avid)
        }
    }

    fun toSongItem(page: BiliClient.VideoPage, basicInfo: BiliClient.VideoBasicInfo, coverUrl: String): SongItem {
        return SongItem(
            id = basicInfo.aid * 10000 + page.page,
            name = page.part,
            artist = basicInfo.ownerName,
            album = PlayerManager.BILI_SOURCE_TAG,
            durationMs = page.durationSec * 1000L,
            coverUrl = coverUrl
        )
    }
}

private fun BiliClient.SearchVideoItem.toSongItem(): SongItem {
    return SongItem(
        id = this.aid,
        name = this.titlePlain,
        artist = this.author,
        album = PlayerManager.BILI_SOURCE_TAG,
        durationMs = this.durationSec * 1000L,
        coverUrl = this.coverUrl
    )
}