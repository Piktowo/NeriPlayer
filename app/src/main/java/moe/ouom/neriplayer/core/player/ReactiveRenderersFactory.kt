package moe.ouom.neriplayer.core.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList

@UnstableApi
class ReactiveRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val tee = TeeAudioProcessor(AudioReactive.teeSink)
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(tee))
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(true)
            .build()
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        val ffmpegAudioRenderer = FfmpegAudioRenderer(
            eventHandler,
            eventListener,
            audioSink
        )
        out.add(ffmpegAudioRenderer)

        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }
}