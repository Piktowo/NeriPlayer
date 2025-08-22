package moe.ouom.neriplayer.ui.screen.host

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import moe.ouom.neriplayer.ui.screen.playlist.PlaylistDetailScreen
import moe.ouom.neriplayer.ui.screen.tab.HomeScreen
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteasePlaylist
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem

@Composable
fun HomeHostScreen(
    onSongClick: (List<SongItem>, Int) -> Unit = { _, _ -> }
) {
    var selected by rememberSaveable { mutableStateOf<NeteasePlaylist?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    val gridState = remember {
        LazyGridState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }

    Surface(color = Color.Transparent) {
        AnimatedContent(
            targetState = selected,
            label = "home_host_switch",
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
                HomeScreen(
                    gridState = gridState,
                    onItemClick = { pl -> selected = pl }
                )
            } else {
                PlaylistDetailScreen(
                    playlist = current,
                    onBack = { selected = null },
                    onSongClick = onSongClick
                )
            }
        }
    }
}
