@file:OptIn(UnstableApi::class)

package moe.ouom.neriplayer.core.player

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.di.AppContainer.biliCookieRepo
import moe.ouom.neriplayer.core.di.AppContainer.settingsRepo
import moe.ouom.neriplayer.core.player.AudioDownloadManager
import moe.ouom.neriplayer.data.LocalPlaylist
import moe.ouom.neriplayer.data.LocalPlaylistRepository
import moe.ouom.neriplayer.ui.component.LyricEntry
import moe.ouom.neriplayer.ui.component.parseNeteaseLrc
import moe.ouom.neriplayer.ui.viewmodel.playlist.BiliVideoItem
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

data class AudioDevice(
    val name: String,
    val type: Int,
    val icon: ImageVector
)

sealed class PlayerEvent {
    data class ShowLoginPrompt(val message: String) : PlayerEvent()
    data class ShowError(val message: String) : PlayerEvent()
}

private sealed class SongUrlResult {
    data class Success(val url: String) : SongUrlResult()
    object RequiresLogin : SongUrlResult()
    object Failure : SongUrlResult()
}

object PlayerManager {
    private const val FAVORITES_NAME = "我喜欢的音乐"
    const val BILI_SOURCE_TAG = "Bilibili"

    private var initialized = false
    private lateinit var application: Application
    private lateinit var player: ExoPlayer

    private lateinit var cache: Cache

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private lateinit var localRepo: LocalPlaylistRepository

    private lateinit var stateFile: File

    private var preferredQuality: String = "exhigh"
    private var biliPreferredQuality: String = "high"

    private var currentPlaylist: List<SongItem> = emptyList()
    private var currentIndex = -1

    private val shuffleHistory = mutableListOf<Int>()
    private val shuffleFuture  = mutableListOf<Int>()
    private var shuffleBag     = mutableListOf<Int>()

    private var consecutivePlayFailures = 0
    private const val MAX_CONSECUTIVE_FAILURES = 10

    private val _currentSongFlow = MutableStateFlow<SongItem?>(null)
    val currentSongFlow: StateFlow<SongItem?> = _currentSongFlow

    private val _currentQueueFlow = MutableStateFlow<List<SongItem>>(emptyList())
    val currentQueueFlow: StateFlow<List<SongItem>> = _currentQueueFlow

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionFlow: StateFlow<Long> = _playbackPositionMs

    private val _shuffleModeFlow = MutableStateFlow(false)
    val shuffleModeFlow: StateFlow<Boolean> = _shuffleModeFlow

    private val _repeatModeFlow = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatModeFlow: StateFlow<Int> = _repeatModeFlow

    private val _currentAudioDevice = MutableStateFlow<AudioDevice?>(null)
    private var audioDeviceCallback: AudioDeviceCallback? = null

    private val _playerEventFlow = MutableSharedFlow<PlayerEvent>()
    val playerEventFlow: SharedFlow<PlayerEvent> = _playerEventFlow.asSharedFlow()

    private val _currentMediaUrl = MutableStateFlow<String?>(null)
    val currentMediaUrlFlow: StateFlow<String?> = _currentMediaUrl

    private val _playlistsFlow = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlistsFlow: StateFlow<List<LocalPlaylist>> = _playlistsFlow

    private var playJob: Job? = null

    val audioLevelFlow get() = AudioReactive.level
    val beatImpulseFlow get() = AudioReactive.beat

    var biliRepo = AppContainer.biliPlaybackRepository
    var biliClient = AppContainer.biliClient
    var neteaseClient = AppContainer.neteaseClient

    val cloudMusicSearchApi = AppContainer.cloudMusicSearchApi
    val qqMusicSearchApi = AppContainer.qqMusicSearchApi

    private fun isPreparedInPlayer(): Boolean = player.currentMediaItem != null

    private val gson = Gson()

    private fun postPlayerEvent(event: PlayerEvent) {
        ioScope.launch { _playerEventFlow.emit(event) }
    }

    private fun computeCacheKey(song: SongItem): String {
        val isBili = song.album.startsWith(BILI_SOURCE_TAG)
        return if (isBili) {
            val parts = song.album.split('|')
            val cidPart = if (parts.size > 1) parts[1] else null
            if (cidPart != null) {
                "bili-${song.id}-$cidPart-$biliPreferredQuality"
            } else {
                "bili-${song.id}-$biliPreferredQuality"
            }
        } else {
            "netease-${song.id}-$preferredQuality"
        }
    }

    private fun buildMediaItem(song: SongItem, url: String, cacheKey: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(Uri.parse(url))
            .setCustomCacheKey(cacheKey)
            .build()
    }

    private fun handleTrackEnded() {
        _playbackPositionMs.value = 0L
        when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> playAtIndex(currentIndex)
            Player.REPEAT_MODE_ALL -> next(force = true)
            else -> {
                if (player.shuffleModeEnabled) {
                    if (shuffleFuture.isNotEmpty() || shuffleBag.isNotEmpty()) next(force = false)
                    else stopAndClearPlaylist()
                } else {
                    if (currentIndex < currentPlaylist.lastIndex) next(force = false)
                    else stopAndClearPlaylist()
                }
            }
        }
    }

    private data class PersistedState(
        val playlist: List<SongItem>,
        val index: Int
    )

    fun initialize(app: Application) {
        if (initialized) return
        initialized = true
        application = app

        localRepo = LocalPlaylistRepository.getInstance(app)
        stateFile = File(app.filesDir, "last_playlist.json")

        val cacheDir = File(app.cacheDir, "media_cache")
        val dbProvider = StandaloneDatabaseProvider(app)
        cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(10L * 1024 * 1024 * 1024),
            dbProvider
        )

        val okHttpClient = moe.ouom.neriplayer.core.di.AppContainer.sharedOkHttpClient
        val upstreamFactory: HttpDataSource.Factory = OkHttpDataSource.Factory(okHttpClient)
        val conditionalHttpFactory = ConditionalHttpDataSourceFactory(upstreamFactory, biliCookieRepo)

        val defaultDsFactory = androidx.media3.datasource.DefaultDataSource.Factory(app, conditionalHttpFactory)

        val cacheDsFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDsFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDsFactory)

        val renderersFactory = ReactiveRenderersFactory(app)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(app, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val audioOffload = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            )
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(audioOffload)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                NPLogger.e("NERI-Player", "onPlayerError: ${error.errorCodeName}", error)
                consecutivePlayFailures++

                val cause = error.cause
                val msg = when {
                    cause?.message?.contains("no protocol: null", ignoreCase = true) == true ->
                        "播放地址无效\n请尝试登录或切换音质\n或检查你是否对此歌曲有访问权限"
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "网络连接失败，请检查网络后重试"
                    else ->
                        "播放失败：${error.errorCodeName}"
                }
                postPlayerEvent(PlayerEvent.ShowError(msg))

                pause()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) handleTrackEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlayingFlow.value = isPlaying
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleModeFlow.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatModeFlow.value = repeatMode
            }
        })

        player.playWhenReady = false

        ioScope.launch {
            settingsRepo.audioQualityFlow.collect { q -> preferredQuality = q }
        }
        ioScope.launch {
            settingsRepo.biliAudioQualityFlow.collect { q -> biliPreferredQuality = q }
        }

        ioScope.launch {
            localRepo.playlists.collect { repoLists ->
                _playlistsFlow.value = deepCopyPlaylists(repoLists)
            }
        }

        setupAudioDeviceCallback()
        restoreState()
    }

    private fun setupAudioDeviceCallback() {
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        _currentAudioDevice.value = getCurrentAudioDevice(audioManager)
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                handleDeviceChange(audioManager)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                handleDeviceChange(audioManager)
            }
        }

        audioDeviceCallback = deviceCallback
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun handleDeviceChange(audioManager: AudioManager) {
        val previousDevice = _currentAudioDevice.value
        val newDevice = getCurrentAudioDevice(audioManager)
        _currentAudioDevice.value = newDevice
        if (player.isPlaying &&
            previousDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
            newDevice.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            NPLogger.d("NERI-PlayerManager", "Audio output changed to speaker, pausing playback.")
            pause()
        }
    }

    private fun getCurrentAudioDevice(audioManager: AudioManager): AudioDevice {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (bluetoothDevice != null) {
            return try {
                AudioDevice(
                    name = bluetoothDevice.productName.toString().ifBlank { "蓝牙耳机" },
                    type = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    icon = Icons.Default.BluetoothAudio
                )
            } catch (_: SecurityException) {
                AudioDevice("蓝牙耳机", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, Icons.Default.BluetoothAudio)
            }
        }
        val wiredHeadset = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
        if (wiredHeadset != null) {
            return AudioDevice("有线耳机", AudioDeviceInfo.TYPE_WIRED_HEADSET, Icons.Default.Headset)
        }
        return AudioDevice("手机扬声器", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, Icons.Default.SpeakerGroup)
    }

    fun playPlaylist(songs: List<SongItem>, startIndex: Int) {
        check(initialized) { "Call PlayerManager.initialize(application) first." }
        if (songs.isEmpty()) {
            NPLogger.w("NERI-Player", "playPlaylist called with EMPTY list")
            return
        }
        consecutivePlayFailures = 0
        currentPlaylist = songs
        _currentQueueFlow.value = currentPlaylist
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)

        shuffleHistory.clear()
        shuffleFuture.clear()
        if (player.shuffleModeEnabled) {
            rebuildShuffleBag(excludeIndex = currentIndex)
        } else {
            shuffleBag.clear()
        }

        playAtIndex(currentIndex)
        ioScope.launch {
            persistState()
        }
    }

    private fun rebuildShuffleBag(excludeIndex: Int? = null) {
        shuffleBag = currentPlaylist.indices.toMutableList()
        if (excludeIndex != null) shuffleBag.remove(excludeIndex)
        shuffleBag.shuffle()
    }

    private fun playAtIndex(index: Int) {
        if (currentPlaylist.isEmpty() || index !in currentPlaylist.indices) {
            NPLogger.w("NERI-Player", "playAtIndex called with invalid index: $index")
            return
        }

        if (consecutivePlayFailures >= MAX_CONSECUTIVE_FAILURES) {
            NPLogger.e("NERI-PlayerManager", "已连续失败 $consecutivePlayFailures 次，停止播放")
            mainScope.launch { Toast.makeText(application, "多首歌曲无法播放，已停止", Toast.LENGTH_SHORT).show() }
            stopAndClearPlaylist()
            return
        }

        val song = currentPlaylist[index]
        _currentSongFlow.value = song
        ioScope.launch {
            persistState()
        }

        if (player.shuffleModeEnabled) {
            shuffleBag.remove(index)
        }

        playJob?.cancel()
        _playbackPositionMs.value = 0L
        playJob = ioScope.launch {
            val result = resolveSongUrl(song)

            when (result) {
                is SongUrlResult.Success -> {
                    consecutivePlayFailures = 0

                    val cacheKey = computeCacheKey(song)
                    NPLogger.d("NERI-PlayerManager", "Using custom cache key: $cacheKey for song: ${song.name}")

                    val mediaItem = buildMediaItem(song, result.url, cacheKey)

                    _currentMediaUrl.value = result.url

                    withContext(Dispatchers.Main) {
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    }
                }
                is SongUrlResult.RequiresLogin -> {
                    NPLogger.w("NERI-PlayerManager", "需要登录才能播放: id=${song.id}, source=${song.album}")
                    postPlayerEvent(PlayerEvent.ShowLoginPrompt("播放失败，请尝试登录对应的平台"))
                    withContext(Dispatchers.Main) { next() }
                }
                is SongUrlResult.Failure -> {
                    NPLogger.e("NERI-PlayerManager", "获取播放 URL 失败, 跳过: id=${song.id}, source=${song.album}")
                    consecutivePlayFailures++
                    withContext(Dispatchers.Main) { next() }
                }
            }
        }
    }

    private suspend fun resolveSongUrl(song: SongItem): SongUrlResult {

        val localResult = checkLocalCache(song)
        if (localResult != null) return localResult

        return if (song.album.startsWith(BILI_SOURCE_TAG)) {

            val parts = song.album.split('|')
            val cid = if (parts.size > 1) parts[1].toLongOrNull() ?: 0L else 0L
            getBiliAudioUrl(song.id, cid)
        } else {
            getNeteaseSongUrl(song.id)
        }
    }

    private fun checkLocalCache(song: SongItem): SongUrlResult? {
        val context = application
        val localPath = AudioDownloadManager.getLocalFilePath(context, song)
        return if (localPath != null) {
            SongUrlResult.Success("file://$localPath")
        } else null
    }

    private suspend fun getNeteaseSongUrl(songId: Long): SongUrlResult = withContext(Dispatchers.IO) {
        try {
            val resp = neteaseClient.getSongDownloadUrl(
                songId,
                level = preferredQuality
            )
            NPLogger.d("NERI-PlayerManager", "id=$songId, resp=$resp")

            val root = JSONObject(resp)
            when (root.optInt("code")) {
                301 -> SongUrlResult.RequiresLogin
                200 -> {
                    val url = when (val dataObj = root.opt("data")) {
                        is JSONObject -> dataObj.optString("url", "")
                        is JSONArray -> dataObj.optJSONObject(0)?.optString("url", "")
                        else -> ""
                    }
                    if (url.isNullOrBlank()) {
                        postPlayerEvent(PlayerEvent.ShowError("该歌曲暂无可用播放地址（可能需要登录或版权限制）"))
                        SongUrlResult.Failure
                    } else {
                        val finalUrl = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
                        SongUrlResult.Success(finalUrl)
                    }
                }
                else -> {
                    postPlayerEvent(PlayerEvent.ShowError("获取播放地址失败（${root.optInt("code")}）"))
                    SongUrlResult.Failure
                }
            }
        } catch (e: Exception) {
            NPLogger.e("NERI-PlayerManager", "获取URL时出错", e)
            SongUrlResult.Failure
        }
    }

    private suspend fun getBiliAudioUrl(avid: Long, cid: Long = 0): SongUrlResult = withContext(Dispatchers.IO) {
        try {
            var finalCid = cid
            val bvid: String
            if (finalCid == 0L) {
                val videoInfo = biliClient.getVideoBasicInfoByAvid(avid)
                bvid = videoInfo.bvid
                finalCid = videoInfo.pages.firstOrNull()?.cid ?: 0L
                if (finalCid == 0L) {
                    postPlayerEvent(PlayerEvent.ShowError("无法获取视频信息 (cid)"))
                    return@withContext SongUrlResult.Failure
                }
            } else {
                bvid = biliClient.getVideoBasicInfoByAvid(avid).bvid
            }

            val audioStream = biliRepo.getBestPlayableAudio(bvid, finalCid)

            if (audioStream?.url != null) {
                NPLogger.d("NERI-PlayerManager-BiliAudioUrl", audioStream.url)
                SongUrlResult.Success(audioStream.url)
            } else {
                postPlayerEvent(PlayerEvent.ShowError("无法获取播放地址"))
                SongUrlResult.Failure
            }
        } catch (e: Exception) {
            NPLogger.e("NERI-PlayerManager", "获取B站音频URL时出错", e)
            postPlayerEvent(PlayerEvent.ShowError("获取播放地址失败: ${e.message}"))
            SongUrlResult.Failure
        }
    }

    fun playBiliVideoParts(videoInfo: BiliClient.VideoBasicInfo, startIndex: Int, coverUrl: String) {
        check(initialized) { "Call PlayerManager.initialize(application) first." }
        val songs = videoInfo.pages.map { page ->
            SongItem(
                id = videoInfo.aid,
                name = page.part,
                artist = videoInfo.ownerName,
                album = "$BILI_SOURCE_TAG|${page.cid}",
                durationMs = page.durationSec * 1000L,
                coverUrl = coverUrl
            )
        }
        playPlaylist(songs, startIndex)
    }

    fun play() {
        when {
            isPreparedInPlayer() -> player.play()
            currentPlaylist.isNotEmpty() && currentIndex != -1 -> playAtIndex(currentIndex)
            currentPlaylist.isNotEmpty() -> playAtIndex(0)
            else -> {}
        }
    }

    fun pause() { player.pause() }
    fun togglePlayPause() { if (player.isPlaying) pause() else play() }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackPositionMs.value = positionMs
    }

    fun next(force: Boolean = false) {
        if (currentPlaylist.isEmpty()) return
        val isShuffle = player.shuffleModeEnabled

        if (isShuffle) {

            if (shuffleFuture.isNotEmpty()) {
                val nextIdx = shuffleFuture.removeLast()
                if (currentIndex != -1) shuffleHistory.add(currentIndex)
                currentIndex = nextIdx
                playAtIndex(currentIndex)
                return
            }

            if (shuffleBag.isEmpty()) {
                if (force || player.repeatMode == Player.REPEAT_MODE_ALL) {
                    rebuildShuffleBag(excludeIndex = currentIndex)
                } else {
                    NPLogger.d("NERI-Player", "Shuffle finished and repeat is off, stopping.")
                    stopAndClearPlaylist()
                    return
                }
            }

            if (shuffleBag.isEmpty()) {

                playAtIndex(currentIndex)
                return
            }

            if (currentIndex != -1) shuffleHistory.add(currentIndex)

            shuffleFuture.clear()

            val pick = if (shuffleBag.size == 1) 0 else Random.nextInt(shuffleBag.size)
            currentIndex = shuffleBag.removeAt(pick)
            playAtIndex(currentIndex)
        } else {

            if (currentIndex < currentPlaylist.lastIndex) {
                currentIndex++
            } else {
                if (force || player.repeatMode == Player.REPEAT_MODE_ALL) {
                    currentIndex = 0
                } else {
                    NPLogger.d("NERI-Player", "Already at the end of the playlist.")
                    return
                }
            }
            playAtIndex(currentIndex)
        }
    }

    fun previous() {
        if (currentPlaylist.isEmpty()) return
        val isShuffle = player.shuffleModeEnabled

        if (isShuffle) {
            if (shuffleHistory.isNotEmpty()) {

                if (currentIndex != -1) shuffleFuture.add(currentIndex)
                val prev = shuffleHistory.removeLast()
                currentIndex = prev
                playAtIndex(currentIndex)
            } else {
                NPLogger.d("NERI-Player", "No previous track in shuffle history.")
            }
        } else {
            if (currentIndex > 0) {
                currentIndex--
                playAtIndex(currentIndex)
            } else {
                if (player.repeatMode == Player.REPEAT_MODE_ALL && currentPlaylist.isNotEmpty()) {
                    currentIndex = currentPlaylist.lastIndex
                    playAtIndex(currentIndex)
                } else {
                    NPLogger.d("NERI-Player", "Already at the start of the playlist.")
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val newMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = newMode
    }

    fun release() {
        try {
            val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        } catch (_: Exception) { }
        audioDeviceCallback = null
        player.release()
        cache.release()
        mainScope.cancel()
        ioScope.cancel()
    }

    fun setShuffle(enabled: Boolean) {
        if (player.shuffleModeEnabled == enabled) return
        player.shuffleModeEnabled = enabled
        shuffleHistory.clear()
        shuffleFuture.clear()
        if (enabled) {
            rebuildShuffleBag(excludeIndex = currentIndex)
        } else {
            shuffleBag.clear()
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = mainScope.launch {
            while (isActive) {
                _playbackPositionMs.value = player.currentPosition
                delay(40)
            }
        }
    }

    private fun stopProgressUpdates() { progressJob?.cancel(); progressJob = null }

    private fun stopAndClearPlaylist() {
        playJob?.cancel()
        playJob = null
        player.stop()
        player.clearMediaItems()
        _isPlayingFlow.value = false
        _currentSongFlow.value = null
        _currentMediaUrl.value = null
        _playbackPositionMs.value = 0L
        currentIndex = -1
        currentPlaylist = emptyList()
        _currentQueueFlow.value = emptyList()
        consecutivePlayFailures = 0
        shuffleBag.clear()
        shuffleHistory.clear()
        shuffleFuture.clear()
        ioScope.launch {
            persistState()
        }
    }

    fun hasItems(): Boolean = currentPlaylist.isNotEmpty()

    fun addCurrentToFavorites() {
        val song = _currentSongFlow.value ?: return
        val updatedLists = optimisticUpdateFavorites(add = true, song = song)
        _playlistsFlow.value = deepCopyPlaylists(updatedLists)
        ioScope.launch {
            try {

                if (_playlistsFlow.value.none { it.name == FAVORITES_NAME }) {
                    localRepo.createPlaylist(FAVORITES_NAME)
                }
                localRepo.addToFavorites(song)
            } catch (e: Exception) {
                NPLogger.e("NERI-PlayerManager", "addToFavorites failed: ${e.message}", e)
            }
        }
    }

    fun removeCurrentFromFavorites() {
        val songId = _currentSongFlow.value?.id ?: return
        val updatedLists = optimisticUpdateFavorites(add = false, songId = songId)
        _playlistsFlow.value = deepCopyPlaylists(updatedLists)
        ioScope.launch {
            try {
                localRepo.removeFromFavorites(songId)
            } catch (e: Exception) {
                NPLogger.e("NERI-PlayerManager", "removeFromFavorites failed: ${e.message}", e)
            }
        }
    }

    fun toggleCurrentFavorite() {
        val song = _currentSongFlow.value ?: return
        val fav = _playlistsFlow.value.firstOrNull { it.name == FAVORITES_NAME }
        val isFav = fav?.songs?.any { it.id == song.id } == true
        if (isFav) removeCurrentFromFavorites() else addCurrentToFavorites()
    }

    private fun optimisticUpdateFavorites(
        add: Boolean,
        song: SongItem? = null,
        songId: Long? = null
    ): List<LocalPlaylist> {
        val lists = _playlistsFlow.value
        val favIdx = lists.indexOfFirst { it.name == FAVORITES_NAME }
        val base = lists.map { LocalPlaylist(it.id, it.name, it.songs.toMutableList()) }.toMutableList()

        if (favIdx >= 0) {
            val fav = base[favIdx]
            if (add && song != null) {
                if (fav.songs.none { it.id == song.id }) fav.songs.add(song)
            } else if (!add && songId != null) {
                fav.songs.removeAll { it.id == songId }
            }
        } else {
            if (add && song != null) {
                base += LocalPlaylist(
                    id = System.currentTimeMillis(),
                    name = FAVORITES_NAME,
                    songs = mutableListOf(song)
                )
            }
        }
        return base
    }

    private fun deepCopyPlaylists(src: List<LocalPlaylist>): List<LocalPlaylist> {
        return src.map { pl ->
            LocalPlaylist(
                id = pl.id,
                name = pl.name,
                songs = pl.songs.toMutableList()
            )
        }
    }

    private suspend fun persistState() {
        withContext(Dispatchers.IO) {
            try {
                if (currentPlaylist.isEmpty()) {
                    if (stateFile.exists()) stateFile.delete()
                } else {
                    val data = PersistedState(currentPlaylist, currentIndex)
                    stateFile.writeText(gson.toJson(data))
                }
            } catch (e: Exception) {
                NPLogger.e("PlayerManager", "Failed to persist state", e)
            }
        }
    }

    fun addCurrentToPlaylist(playlistId: Long) {
        val song = _currentSongFlow.value ?: return
        ioScope.launch {
            try {
                localRepo.addSongToPlaylist(playlistId, song)
            } catch (e: Exception) {
                NPLogger.e("NERI-PlayerManager", "addCurrentToPlaylist failed: ${e.message}", e)
            }
        }
    }

    fun playBiliVideoAsAudio(videos: List<BiliVideoItem>, startIndex: Int) {
        check(initialized) { "Call PlayerManager.initialize(application) first." }
        if (videos.isEmpty()) {
            NPLogger.w("NERI-Player", "playBiliVideoAsAudio called with EMPTY list")
            return
        }

        val songs = videos.map { it.toSongItem() }
        playPlaylist(songs, startIndex)
    }

    suspend fun getNeteaseLyrics(songId: Long): List<LyricEntry> {
        return withContext(Dispatchers.IO) {
            try {
                val raw = neteaseClient.getLyricNew(songId)
                val lrc = JSONObject(raw).optJSONObject("lrc")?.optString("lyric") ?: ""
                parseNeteaseLrc(lrc)
            } catch (e: Exception) {
                NPLogger.e("NERI-PlayerManager", "getNeteaseLyrics failed: ${e.message}", e)
                emptyList()
            }
        }
    }

    suspend fun getLyrics(song: SongItem): List<LyricEntry> {

        if (!song.matchedLyric.isNullOrBlank()) {
            try {
                return parseNeteaseLrc(song.matchedLyric)
            } catch (e: Exception) {
                NPLogger.w("NERI-PlayerManager", "匹配歌词解析失败: ${e.message}")
            }
        }

        val context = application
        val localLyricPath = AudioDownloadManager.getLyricFilePath(context, song)
        if (localLyricPath != null) {
            try {
                val lrcContent = File(localLyricPath).readText()
                return parseNeteaseLrc(lrcContent)
            } catch (e: Exception) {
                NPLogger.w("NERI-PlayerManager", "本地歌词读取失败: ${e.message}")
            }
        }

        return if (song.album.startsWith(BILI_SOURCE_TAG)) {
            emptyList()
        } else {
            getNeteaseLyrics(song.id)
        }
    }

    fun playFromQueue(index: Int) {
        if (currentPlaylist.isEmpty()) return
        if (index !in currentPlaylist.indices) return

        if (player.shuffleModeEnabled) {
            if (currentIndex != -1) shuffleHistory.add(currentIndex)
            shuffleFuture.clear()
            shuffleBag.remove(index)
        }

        currentIndex = index
        playAtIndex(index)
    }

    fun addToQueueNext(song: SongItem) {
        if (currentPlaylist.isEmpty()) {

            playPlaylist(listOf(song), 0)
            return
        }

        val newPlaylist = currentPlaylist.toMutableList()
        val insertIndex = (currentIndex + 1).coerceIn(0, newPlaylist.size)

        val existingIndex = newPlaylist.indexOfFirst { it.id == song.id && it.album == song.album }
        if (existingIndex != -1) {
            newPlaylist.removeAt(existingIndex)

            val adjustedInsertIndex = if (existingIndex < insertIndex) insertIndex - 1 else insertIndex
            newPlaylist.add(adjustedInsertIndex, song)
        } else {

            newPlaylist.add(insertIndex, song)
        }

        currentPlaylist = newPlaylist
        _currentQueueFlow.value = currentPlaylist

        if (player.shuffleModeEnabled) {
            rebuildShuffleBag()
        }

        ioScope.launch {
            persistState()
        }
    }

    fun addToQueueEnd(song: SongItem) {
        if (currentPlaylist.isEmpty()) {

            playPlaylist(listOf(song), 0)
            return
        }

        val newPlaylist = currentPlaylist.toMutableList()

        val existingIndex = newPlaylist.indexOfFirst { it.id == song.id && it.album == song.album }
        if (existingIndex != -1) {
            newPlaylist.removeAt(existingIndex)
        }

        newPlaylist.add(song)

        currentPlaylist = newPlaylist
        _currentQueueFlow.value = currentPlaylist

        if (player.shuffleModeEnabled) {
            rebuildShuffleBag()
        }

        ioScope.launch {
            persistState()
        }
    }

    private fun restoreState() {
        try {
            if (!stateFile.exists()) return
            val type = object : TypeToken<PersistedState>() {}.type
            val data: PersistedState = gson.fromJson(stateFile.readText(), type)
            currentPlaylist = data.playlist
            currentIndex = data.index
            _currentQueueFlow.value = currentPlaylist
            _currentSongFlow.value = currentPlaylist.getOrNull(currentIndex)
        } catch (e: Exception) {
            NPLogger.w("NERI-PlayerManager", "Failed to restore state: ${e.message}")
        }
    }

    fun replaceMetadataFromSearch(originalSong: SongItem, selectedSong: SongSearchInfo) {
        ioScope.launch {
            val platform = selectedSong.source

            val api = when (platform) {
                MusicPlatform.CLOUD_MUSIC -> cloudMusicSearchApi
                MusicPlatform.QQ_MUSIC -> qqMusicSearchApi
            }

            try {
                val newDetails = api.getSongInfo(selectedSong.id)

                val updatedSong = originalSong.copy(
                    name = newDetails.songName,
                    artist = newDetails.singer,
                    coverUrl = newDetails.coverUrl,
                    matchedLyric = newDetails.lyric,
                    matchedLyricSource = selectedSong.source
                )

                updateSongInAllPlaces(originalSong, updatedSong)

            } catch (e: Exception) {
                mainScope.launch { Toast.makeText(application, "匹配失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    suspend fun updateUserLyricOffset(songToUpdate: SongItem, newOffset: Long) {
        val queueIndex = currentPlaylist.indexOfFirst { it.id == songToUpdate.id && it.album == songToUpdate.album }
        if (queueIndex != -1) {
            val updatedSong = currentPlaylist[queueIndex].copy(userLyricOffsetMs = newOffset)
            val newList = currentPlaylist.toMutableList()
            newList[queueIndex] = updatedSong
            currentPlaylist = newList
            _currentQueueFlow.value = currentPlaylist
        }

        if (_currentSongFlow.value?.id == songToUpdate.id && _currentSongFlow.value?.album == songToUpdate.album) {
            _currentSongFlow.value = _currentSongFlow.value?.copy(userLyricOffsetMs = newOffset)
        }

        withContext(Dispatchers.IO) {
            localRepo.updateSongMetadata(
                songToUpdate.id,
                songToUpdate.album,
                songToUpdate.copy(userLyricOffsetMs = newOffset)
            )
        }

        persistState()
    }

    private suspend fun updateSongInAllPlaces(originalSong: SongItem, updatedSong: SongItem) {
        val originalId = originalSong.id
        val originalAlbum = originalSong.album

        val queueIndex = currentPlaylist.indexOfFirst { it.id == originalId && it.album == originalAlbum }
        if (queueIndex != -1) {
            val newList = currentPlaylist.toMutableList()
            newList[queueIndex] = updatedSong
            currentPlaylist = newList
            _currentQueueFlow.value = currentPlaylist
        }

        if (_currentSongFlow.value?.id == originalId && _currentSongFlow.value?.album == originalAlbum) {
            _currentSongFlow.value = updatedSong
        }

        withContext(Dispatchers.IO) {
            localRepo.updateSongMetadata(originalId, originalAlbum, updatedSong)
        }

        persistState()
    }

}

private fun BiliVideoItem.toSongItem(): SongItem {
    return SongItem(
        id = this.id,
        name = this.title,
        artist = this.uploader,
        album = PlayerManager.BILI_SOURCE_TAG,
        durationMs = this.durationSec * 1000L,
        coverUrl = this.coverUrl
    )
}