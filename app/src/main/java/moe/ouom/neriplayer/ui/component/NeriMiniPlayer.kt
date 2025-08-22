package moe.ouom.neriplayer.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import moe.ouom.neriplayer.util.HapticIconButton

@Composable
fun NeriMiniPlayer(
    title: String,
    artist: String,
    coverUrl: String?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onHeightChanged: (height: Int) -> Unit,
    hazeState: HazeState
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(.4f)
        ),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier
            .padding(start = 16.dp, end = 8.dp)
            .clickable { onExpand() }
            .onSizeChanged { size ->
                onHeightChanged(size.height)
            }
            .hazeChild(state = hazeState, shape = shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (coverUrl != null) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(coverUrl).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {

                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HapticIconButton(onClick = { onPlayPause() }) {
                AnimatedContent(
                    targetState = isPlaying,
                    label = "mini_play_pause_icon",
                    transitionSpec = {
                        (scaleIn(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            initialScale = 0.7f
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 150)
                        )) togetherWith (scaleOut(
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                            targetScale = 0.7f
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 100)
                        ))
                    }
                ) { currentlyPlaying ->
                    Icon(
                        imageVector = if (currentlyPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (currentlyPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}