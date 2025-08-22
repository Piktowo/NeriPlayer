package moe.ouom.neriplayer.ui.viewmodel.tab

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.LocalPlaylist
import moe.ouom.neriplayer.data.LocalPlaylistRepository
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONObject
import java.io.IOException

@Parcelize
data class BiliPlaylist(
    val mediaId: Long,
    val fid: Long,
    val mid: Long,
    val title: String,
    val count: Int,
    val coverUrl: String
) : Parcelable

data class LibraryUiState(
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val neteasePlaylists: List<NeteasePlaylist> = emptyList(),
    val neteaseError: String? = null,
    val biliPlaylists: List<BiliPlaylist> = emptyList(),
    val biliError: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalPlaylistRepository.getInstance(application)

    private val neteaseCookieRepo = AppContainer.neteaseCookieRepo
    private val neteaseClient = AppContainer.neteaseClient

    private val biliCookieRepo = AppContainer.biliCookieRepo
    private val biliClient = AppContainer.biliClient

    private val _uiState = MutableStateFlow(
        LibraryUiState(localPlaylists = localRepo.playlists.value)
    )
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {

        viewModelScope.launch {
            localRepo.playlists.collect { list ->
                _uiState.value = _uiState.value.copy(localPlaylists = list)
            }
        }

        viewModelScope.launch {
            neteaseCookieRepo.cookieFlow.collect { cookies ->
                val mutable = cookies.toMutableMap()
                mutable.putIfAbsent("os", "pc")
                if (!cookies["MUSIC_U"].isNullOrBlank()) {
                    refreshNetease()
                } else {
                    _uiState.value = _uiState.value.copy(neteasePlaylists = emptyList())
                }
            }
        }

        viewModelScope.launch {
            biliCookieRepo.cookieFlow.collect { cookies ->
                if (!cookies["SESSDATA"].isNullOrBlank()) {
                    refreshBilibili()
                } else {
                    _uiState.value = _uiState.value.copy(biliPlaylists = emptyList())
                }
            }
        }
    }

    private fun refreshBilibili() {
        viewModelScope.launch {
            try {
                val mid = biliCookieRepo.getCookiesOnce()["DedeUserID"]?.toLongOrNull() ?: 0L
                if (mid == 0L) {
                    _uiState.value = _uiState.value.copy(biliError = "无法获取用户ID，请重新登录")
                    return@launch
                }
                val rawList = withContext(Dispatchers.IO) { biliClient.getUserCreatedFavFolders(mid) }

                val mapped = withContext(Dispatchers.IO) {
                    rawList.map { folder ->
                        async {
                            try {
                                val folderInfo = biliClient.getFavFolderInfo(folder.mediaId)
                                BiliPlaylist(
                                    mediaId = folderInfo.mediaId,
                                    fid = folderInfo.fid,
                                    mid = folderInfo.mid,
                                    title = folderInfo.title,
                                    count = folderInfo.count,
                                    coverUrl = folderInfo.coverUrl.replace("http://", "https://")
                                )
                            } catch (e: Exception) {

                                NPLogger.e("LibraryViewModel-Bili", "获取详情失败",e)
                                BiliPlaylist(
                                    mediaId = folder.mediaId,
                                    fid = folder.fid,
                                    mid = folder.mid,
                                    title = folder.title,
                                    count = folder.count,
                                    coverUrl = ""
                                )
                            }
                        }
                    }.awaitAll()
                }

                NPLogger.d("LibraryViewModel-Bili",mapped)

                _uiState.value = _uiState.value.copy(biliPlaylists = mapped, biliError = null)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            }
        }
    }

    fun refreshNetease() {
        viewModelScope.launch {
            try {
                val uid = withContext(Dispatchers.IO) { neteaseClient.getCurrentUserId() }
                val raw = withContext(Dispatchers.IO) { neteaseClient.getUserPlaylists(uid) }
                val mapped = parseNeteasePlaylists(raw)
                _uiState.value = _uiState.value.copy(
                    neteasePlaylists = mapped,
                    neteaseError = null
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            }
        }
    }

    fun createLocalPlaylist(name: String) {
        viewModelScope.launch { localRepo.createPlaylist(name) }
    }

    fun addSongToFavorites(song: SongItem) {
        viewModelScope.launch { localRepo.addToFavorites(song) }
    }

    private fun parseNeteasePlaylists(raw: String): List<NeteasePlaylist> {
        val result = mutableListOf<NeteasePlaylist>()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val size = arr.length()
        for (i in 0 until size) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", "")
            val cover = obj.optString("coverImgUrl", "").replaceFirst("http://", "https://")
            val playCount = obj.optLong("playCount", 0L)
            val trackCount = obj.optInt("trackCount", 0)
            if (id != 0L && name.isNotBlank()) {
                result.add(NeteasePlaylist(id, name, cover, playCount, trackCount))
            }
        }
        return result
    }
}