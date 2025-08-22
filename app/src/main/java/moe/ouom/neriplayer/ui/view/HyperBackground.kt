package moe.ouom.neriplayer.ui.view

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import kotlinx.coroutines.isActive
import moe.ouom.neriplayer.core.player.PlayerManager
import kotlin.math.max

@Composable
fun HyperBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean
) {
    val context = LocalContext.current
    val currentIsDark by rememberUpdatedState(isDark)

    val painter = remember(currentIsDark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BgEffectPainter(context)
        } else null
    }

    var hostView by remember { mutableStateOf<View?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                setWillNotDraw(false)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                hostView = this
            }
        },
        update = { v ->
            hostView = v
        }
    )

    suspend fun awaitViewReady(v: View) {
        while (
            !v.isAttachedToWindow ||
            v.parent == null ||
            !ViewCompat.isLaidOut(v) ||
            v.width == 0 || v.height == 0
        ) {
            withFrameNanos {  }
        }
    }

    val level by PlayerManager.audioLevelFlow.collectAsState(0f)
    val beat  by PlayerManager.beatImpulseFlow.collectAsState(0f)

    LaunchedEffect(painter, hostView, currentIsDark) {
        if (painter == null || hostView == null) return@LaunchedEffect
        val v = hostView!!

        awaitViewReady(v)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                painter.showRuntimeShader(context, v, null, currentIsDark)
            } catch (_: Throwable) { return@LaunchedEffect }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var startNs = 0L
            var beatEnv = 0f
            while (isActive) {
                withFrameNanos { t ->
                    if (startNs == 0L) startNs = t
                    val seconds = ((t - startNs) / 1_000_000_000.0).toFloat()
                    painter.setAnimTime(seconds % 62.831852f)

                    beatEnv = max(beatEnv * 0.92f, beat)
                    painter.setReactive(level, beatEnv)

                    val w = v.width; val h = v.height
                    if (w > 0 && h > 0) painter.setResolution(floatArrayOf(w.toFloat(), h.toFloat()))
                    painter.updateMaterials()
                    v.setRenderEffect(painter.renderEffect)
                }
            }
        }
    }
}