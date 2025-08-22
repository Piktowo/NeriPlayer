package moe.ouom.neriplayer.core.download

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import moe.ouom.neriplayer.core.player.AudioDownloadManager
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import java.io.File

object GlobalDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: StateFlow<List<DownloadedSong>> = _downloadedSongs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        observeDownloadProgress(context)

        scanLocalFiles(context)
    }

    private fun observeDownloadProgress(context: Context) {
    scope.launch {
        AudioDownloadManager.progressFlow.collect { progress ->
            progress?.let { updateDownloadProgress(it) }
        }
    }
    scope.launch {
        AudioDownloadManager.batchProgressFlow.collect { batchProgress ->
            batchProgress?.let {
                updateBatchProgress(context, it)
            }
        }
    }
    scope.launch {
        AudioDownloadManager.isCancelledFlow.collect { isCancelled ->
            if (isCancelled) {
                updateAllTasksStatus(DownloadStatus.CANCELLED)
            }
        }
    }
}


    private fun updateDownloadProgress(progress: AudioDownloadManager.DownloadProgress) {
        _downloadTasks.value = _downloadTasks.value.map { task ->
            if (task.status == DownloadStatus.DOWNLOADING) {
                task.copy(progress = progress)
            } else {
                task
            }
        }
    }

    private fun updateBatchProgress(context: Context, batchProgress: AudioDownloadManager.BatchDownloadProgress) {
        batchProgress?.let { progress ->

            if (progress.currentProgress != null) {
                updateDownloadProgress(progress.currentProgress)
            }

            if (progress.completedSongs >= progress.totalSongs) {
                scope.launch {
                    delay(1000)
                    scanLocalFiles(context)
                }
            }
        }
    }

    fun scanLocalFiles(context: Context) {
        scope.launch {
            _isRefreshing.value = true
            try {
                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: context.filesDir
                val downloadDir = File(baseDir, "NeriPlayer")

                if (!downloadDir.exists()) {
                    _downloadedSongs.value = emptyList()
                    return@launch
                }

                val songs = mutableListOf<DownloadedSong>()
                val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg")

                downloadDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in audioExtensions) {
                        try {

                            val nameWithoutExt = file.nameWithoutExtension
                            val parts = nameWithoutExt.split(" - ", limit = 2)

                            if (parts.size >= 2) {
                                val artist = parts[0].trim()
                                val title = parts[1].trim()

                                val songId = file.hashCode().toLong()

                                var coverPath = findCoverFile(downloadDir, songId)
                                if (coverPath == null) {

                                    val coverDir = File(downloadDir, "Covers")
                                    val baseName = nameWithoutExt
                                    listOf("jpg","jpeg","png","webp").forEach { ext ->
                                        val cf = File(coverDir, "$baseName.$ext")
                                        if (cf.exists()) { coverPath = cf.absolutePath; return@forEach }
                                    }
                                }

                                val matchedLyric = findLyricFile(downloadDir, songId, nameWithoutExt)

                                val song = DownloadedSong(
                                    id = songId,
                                    name = title,
                                    artist = artist,
                                    album = "本地文件",
                                    filePath = file.absolutePath,
                                    fileSize = file.length(),
                                    downloadTime = file.lastModified(),
                                    coverPath = coverPath,
                                    matchedLyric = matchedLyric
                                )
                                songs.add(song)
                            }
                        } catch (e: Exception) {
                            NPLogger.w("GlobalDownloadManager", "解析文件失败: ${file.name} - ${e.message}")
                        }
                    }
                }

                _downloadedSongs.value = songs.sortedByDescending { it.downloadTime }

            } catch (e: Exception) {
                NPLogger.e("GlobalDownloadManager", "扫描本地文件失败: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun findCoverFile(downloadDir: File, songId: Long): String? {
        val coverDir = File(downloadDir, "Covers")
        if (!coverDir.exists()) return null

        listOf("jpg","jpeg","png","webp").forEach { ext ->
            val f = File(coverDir, "${songId}.$ext"); if (f.exists()) return f.absolutePath
        }

        return null
    }

    private fun findLyricFile(downloadDir: File, songId: Long, baseName: String): String? {
        val lyricsDir = File(downloadDir, "Lyrics")
        if (!lyricsDir.exists()) return null

        val hashIdFile = File(lyricsDir, "${songId}.lrc")
        if (hashIdFile.exists()) {
            return try {
                hashIdFile.readText()
            } catch (e: Exception) {
                NPLogger.w("GlobalDownloadManager", "读取歌词文件失败: ${hashIdFile.name}")
                null
            }
        }

        val baseNameFile = File(lyricsDir, "$baseName.lrc")
        if (baseNameFile.exists()) {
            return try {
                baseNameFile.readText()
            } catch (e: Exception) {
                NPLogger.w("GlobalDownloadManager", "读取歌词文件失败: ${baseNameFile.name}")
                null
            }
        }

        return null
    }

    fun deleteDownloadedSong(context: Context, song: DownloadedSong) {
        scope.launch {
            try {
                val file = File(song.filePath)
                if (file.exists() && file.delete()) {

                    val lyricsFile = File(song.filePath.replaceAfterLast('.', "lrc"))
                    if (lyricsFile.exists()) {
                        lyricsFile.delete()
                    }

                    song.coverPath?.let { coverPath ->
                        val coverFile = File(coverPath)
                        if (coverFile.exists()) {
                            coverFile.delete()
                        }
                    }

                    scanLocalFiles(context)
                    NPLogger.d("GlobalDownloadManager", "删除文件成功: ${song.name}")
                }
            } catch (e: Exception) {
                NPLogger.e("GlobalDownloadManager", "删除文件失败: ${e.message}")
            }
        }
    }

    fun playDownloadedSong(context: Context, song: DownloadedSong) {
        try {
            val file = File(song.filePath)
            if (file.exists()) {

                val durationMs = getAudioDuration(context, file)

                val songItem = SongItem(
                    id = song.id,
                    name = song.name,
                    artist = song.artist,
                    album = "本地文件",
                    durationMs = durationMs,
                    coverUrl = song.coverPath,
                    userLyricOffsetMs = 0L
                )

                PlayerManager.playPlaylist(listOf(songItem), 0)
                NPLogger.d("GlobalDownloadManager", "使用PlayerManager播放本地文件: ${song.name}, 时长: ${durationMs}ms")
            } else {
                NPLogger.w("GlobalDownloadManager", "文件不存在: ${song.filePath}")
            }
        } catch (e: Exception) {
            NPLogger.e("GlobalDownloadManager", "播放文件失败: ${e.message}")
        }
    }

    private fun getAudioDuration(context: Context, file: File): Long {
        return try {
            val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(file.absolutePath)
            val durationStr = mediaMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            mediaMetadataRetriever.release()

            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            NPLogger.w("GlobalDownloadManager", "获取音频时长失败: ${e.message}")
            0L
        }
    }

    fun startDownload(context: Context, song: SongItem, onError: ((String) -> Unit)? = null) {
        scope.launch {
            try {

                addDownloadTask(song)

                AudioDownloadManager.downloadSong(context, song)

                updateTaskStatus(song.id, DownloadStatus.COMPLETED)
                removeDownloadTask(song.id)
                scanLocalFiles(context)

                NPLogger.d("GlobalDownloadManager", "开始下载: ${song.name}")
            } catch (e: Exception) {
                NPLogger.e("GlobalDownloadManager", "开始下载失败: ${e.message}")
                updateTaskStatus(song.id, DownloadStatus.FAILED)
                onError?.invoke("下载失败：${song.name}")
            }
        }
    }

    fun startBatchDownload(context: Context, songs: List<SongItem>, onError: ((String) -> Unit)? = null, onBatchComplete: () -> Unit = {}) {
        if (songs.isEmpty()) return

        scope.launch {
            try {

                songs.forEach { song ->
                    addDownloadTask(song)
                }

                AudioDownloadManager.downloadPlaylist(context, songs)

                updateAllTasksStatus(DownloadStatus.COMPLETED)
                _downloadTasks.value = emptyList()
                scanLocalFiles(context)

                NPLogger.d("GlobalDownloadManager", "开始批量下载: ${songs.size} 首歌曲")
            } catch (e: Exception) {
                NPLogger.e("GlobalDownloadManager", "批量下载失败: ${e.message}")
                songs.forEach { song ->
                    updateTaskStatus(song.id, DownloadStatus.FAILED)
                onError?.invoke("下载失败：${song.name}")
                }
            }
        }
    }

    private fun addDownloadTask(song: SongItem) {
        val existingTask = _downloadTasks.value.find { it.song.id == song.id }
        if (existingTask == null) {
            val newTask = DownloadTask(
                song = song,
                progress = null,
                status = DownloadStatus.DOWNLOADING
            )
            _downloadTasks.value = _downloadTasks.value + newTask
        }
    }

    private fun updateTaskStatus(songId: Long, status: DownloadStatus) {
        _downloadTasks.value = _downloadTasks.value.map { task ->
            if (task.song.id == songId) {
                task.copy(status = status)
            } else {
                task
            }
        }
    }

    private fun updateAllTasksStatus(status: DownloadStatus) {
        _downloadTasks.value = _downloadTasks.value.map { task ->
            task.copy(status = status)
        }
    }

    fun removeDownloadTask(songId: Long) {
        _downloadTasks.value = _downloadTasks.value.filter { it.song.id != songId }
    }


data class DownloadedSong(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val fileSize: Long,
    val downloadTime: Long,
    val coverPath: String? = null,
    val matchedLyric: String? = null
)

data class DownloadTask(
    val song: SongItem,
    val progress: AudioDownloadManager.DownloadProgress?,
    val status: DownloadStatus
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

}
