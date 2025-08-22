package moe.ouom.neriplayer.ui.viewmodel.playlist

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import java.io.IOException

@Parcelize
data class BiliVideoItem(
    val id: Long,
    val bvid: String,
    val title: String,
    val uploader: String,
    val coverUrl: String,
    val durationSec: Int
) : Parcelable

data class BiliPlaylistDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val header: BiliPlaylist? = null,
    val videos: List<BiliVideoItem> = emptyList()
)

class BiliPlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AppContainer.biliClient

    private val _uiState = MutableStateFlow(BiliPlaylistDetailUiState())
    val uiState: StateFlow<BiliPlaylistDetailUiState> = _uiState

    private var mediaId: Long = 0L

    fun start(playlist: BiliPlaylist) {
        if (mediaId == playlist.mediaId && uiState.value.videos.isNotEmpty()) return
        mediaId = playlist.mediaId

        _uiState.value = BiliPlaylistDetailUiState(
            loading = true,
            header = playlist,
            videos = emptyList()
        )
        loadContent()
    }

    fun retry() {
        uiState.value.header?.let { start(it) }
    }

    suspend fun getVideoInfo(bvid: String): BiliClient.VideoBasicInfo {
        return withContext(Dispatchers.IO) {
            client.getVideoBasicInfoByBvid(bvid)
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val items = withContext(Dispatchers.IO) {
                    client.getAllFavFolderItems(mediaId)
                }

                val videos = items.mapNotNull {

                    if (it.type == 2) {
                        BiliVideoItem(
                            id = it.id,
                            bvid = it.bvid ?: "",
                            title = it.title,
                            uploader = it.upperName,
                            coverUrl = it.coverUrl.replaceFirst("http://", "https://"),
                            durationSec = it.durationSec
                        )
                    } else {
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    videos = videos
                )

            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "网络异常: ${e.message}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun toSongItem(page: BiliClient.VideoPage, basicInfo: BiliClient.VideoBasicInfo, coverUrl: String): SongItem {
        return SongItem(
            id = basicInfo.aid * 10000 + page.page,
            name = page.part,
            artist = basicInfo.ownerName,
            album = "Bilibili",
            durationMs = page.durationSec * 1000L,
            coverUrl = coverUrl
        )
    }
}