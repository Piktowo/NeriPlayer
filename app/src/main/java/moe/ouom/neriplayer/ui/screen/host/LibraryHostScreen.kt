package moe.ouom.neriplayer.ui.screen.host

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import kotlinx.parcelize.Parcelize
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import moe.ouom.neriplayer.ui.screen.playlist.LocalPlaylistDetailScreen
import moe.ouom.neriplayer.ui.screen.playlist.PlaylistDetailScreen
import moe.ouom.neriplayer.ui.screen.playlist.BiliPlaylistDetailScreen
import moe.ouom.neriplayer.ui.screen.tab.LibraryScreen
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteasePlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.data.LocalPlaylistRepository
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.player.PlayerManager

@Parcelize
sealed class LibrarySelectedItem : Parcelable {
    @Parcelize
    data class Local(val playlistId: Long) : LibrarySelectedItem()
    @Parcelize
    data class Netease(val playlist: NeteasePlaylist) : LibrarySelectedItem()
    @Parcelize
    data class Bili(val playlist: BiliPlaylist) : LibrarySelectedItem()
}

@Composable
fun LibraryHostScreen(
    onSongClick: (List<SongItem>, Int) -> Unit = { _, _ -> },
    onPlayParts: (BiliClient.VideoBasicInfo, Int, String) -> Unit = { _, _, _ -> }
) {
    var selected by rememberSaveable { mutableStateOf<LibrarySelectedItem?>(null) }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = selected != null) { selected = null }

    val localListSaver: Saver<LazyListState, *> = LazyListState.Saver
    val neteaseListSaver: Saver<LazyListState, *> = LazyListState.Saver
    val biliListSaver: Saver<LazyListState, *> = LazyListState.Saver
    val qqMusicListSaver: Saver<LazyListState, *> = LazyListState.Saver

    val localListState = rememberSaveable(saver = localListSaver) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val neteaseListState = rememberSaveable(saver = neteaseListSaver) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val biliListState = rememberSaveable(saver = biliListSaver) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val qqMusicListState = rememberSaveable(saver = qqMusicListSaver) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }

    Surface(color = Color.Transparent) {
        AnimatedContent(
            targetState = selected,
            label = "library_host_switch",
            transitionSpec = {
                if (initialState == null && targetState != null) {
                    (slideInVertically(animationSpec = tween(220)) { it } + fadeIn()) togetherWith
                            (fadeOut(animationSpec = tween(160)))
                } else {
                    (slideInVertically(animationSpec = tween(200)) { full -> -full / 6 } + fadeIn()) togetherWith
                            (slideOutVertically(animationSpec = tween(240)) { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            }
        ) { current ->
            if (current == null) {
                LibraryScreen(
                    initialTabIndex = selectedTabIndex,
                    onTabIndexChange = { selectedTabIndex = it },
                    localListState = localListState,
                    neteaseListState = neteaseListState,
                    biliListState = biliListState,
                    qqMusicListState = qqMusicListState,
                    onLocalPlaylistClick = { playlist ->
                        selected = LibrarySelectedItem.Local(playlist.id)
                    },
                    onNeteasePlaylistClick = { playlist ->
                        selected = LibrarySelectedItem.Netease(playlist)
                    },
                    onBiliPlaylistClick = { playlist ->
                        selected = LibrarySelectedItem.Bili(playlist)
                    }
                )
            } else {
                when (current) {
                    is LibrarySelectedItem.Local -> {
                        LocalPlaylistDetailScreen(
                            playlistId = current.playlistId,
                            onBack = { selected = null },
                            onDeleted = { selected = null },
                            onSongClick = onSongClick
                        )
                    }
                    is LibrarySelectedItem.Netease -> {
                        PlaylistDetailScreen(
                            playlist = current.playlist,
                            onBack = { selected = null },
                            onSongClick = onSongClick
                        )
                    }
                    is LibrarySelectedItem.Bili -> {
                        BiliPlaylistDetailScreen(
                            playlist = current.playlist,
                            onBack = { selected = null },
                            onPlayAudio = { videos, index ->
                                PlayerManager.playBiliVideoAsAudio(videos, index)
                            },
                            onPlayParts = onPlayParts
                        )
                    }
                }
            }
        }
    }
}
