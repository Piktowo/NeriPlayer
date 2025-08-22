package moe.ouom.neriplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.SearchManager

data class ManualSearchState(
    val keyword: String = "",
    val selectedPlatform: MusicPlatform = MusicPlatform.CLOUD_MUSIC,
    val searchResults: List<SongSearchInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class NowPlayingViewModel : ViewModel() {

    private val _manualSearchState = MutableStateFlow(ManualSearchState())
    val manualSearchState = _manualSearchState.asStateFlow()

    fun prepareForSearch(initialKeyword: String) {
        _manualSearchState.update {
            it.copy(
                keyword = initialKeyword,
                searchResults = emptyList(),
                error = null
            )
        }
    }

    fun onKeywordChange(newKeyword: String) {
        _manualSearchState.update { it.copy(keyword = newKeyword) }
    }

    fun selectPlatform(platform: MusicPlatform) {
        _manualSearchState.update { it.copy(selectedPlatform = platform) }
        performSearch()
    }

    fun performSearch() {
        if (_manualSearchState.value.keyword.isBlank()) return

        viewModelScope.launch {
            _manualSearchState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = SearchManager.search(
                    keyword = _manualSearchState.value.keyword,
                    platform = _manualSearchState.value.selectedPlatform,
                )
                _manualSearchState.update { it.copy(isLoading = false, searchResults = results) }

            } catch (e: Exception) {
                _manualSearchState.update { it.copy(isLoading = false, error = "搜索失败: ${e.message}") }
            }
        }
    }

    fun onSongSelected(originalSong: SongItem, selectedSong: SongSearchInfo) {
        PlayerManager.replaceMetadataFromSearch(originalSong, selectedSong)
    }
}