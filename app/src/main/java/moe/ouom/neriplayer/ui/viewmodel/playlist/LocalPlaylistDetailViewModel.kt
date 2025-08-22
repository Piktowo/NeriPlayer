package moe.ouom.neriplayer.ui.viewmodel.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.data.LocalPlaylist
import moe.ouom.neriplayer.data.LocalPlaylistRepository
import moe.ouom.neriplayer.data.LocalPlaylistRepository.Companion.FAVORITES_NAME

data class LocalPlaylistDetailUiState(
    val playlist: LocalPlaylist? = null
)

class LocalPlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LocalPlaylistRepository.getInstance(application)

    private val _uiState = MutableStateFlow(LocalPlaylistDetailUiState())
    val uiState: StateFlow<LocalPlaylistDetailUiState> = _uiState

    private var playlistId: Long = 0L

    fun start(id: Long) {
        if (playlistId == id && _uiState.value.playlist != null) return
        playlistId = id
        viewModelScope.launch {
            repo.playlists.collect { list ->
                _uiState.value = LocalPlaylistDetailUiState(list.firstOrNull { it.id == id })
            }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            var name = newName
            if (newName == FAVORITES_NAME) {
                name = FAVORITES_NAME + "_2"
            }
            repo.renamePlaylist(playlistId, name)
        }
    }

    fun removeSongs(ids: List<Long>) {
        viewModelScope.launch {
            val pid = uiState.value.playlist?.id ?: return@launch
            repo.removeSongsFromPlaylist(pid, ids)
        }
    }

    fun delete(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.deletePlaylist(playlistId)
            onResult(ok)
        }
    }

    fun moveSong(from: Int, to: Int) {
        viewModelScope.launch { repo.moveSong(playlistId, from, to) }
    }

    fun removeSong(songId: Long) {
        viewModelScope.launch { repo.removeSongFromPlaylist(playlistId, songId) }
    }
}
