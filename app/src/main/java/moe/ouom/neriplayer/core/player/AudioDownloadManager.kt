package moe.ouom.neriplayer.core.player

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.BiliAudioStreamInfo
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLConnection
import java.text.Normalizer
import kotlin.math.roundToInt

object AudioDownloadManager {

    private const val TAG = "NERI-Downloader"
    private const val BILI_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BILI_REFERER = "https://www.bilibili.com"

    private val _progressFlow = MutableStateFlow<DownloadProgress?>(null)
    val progressFlow: StateFlow<DownloadProgress?> = _progressFlow

    private val _batchProgressFlow = MutableStateFlow<BatchDownloadProgress?>(null)
    val batchProgressFlow: StateFlow<BatchDownloadProgress?> = _batchProgressFlow

    private val _isCancelled = MutableStateFlow(false)
    val isCancelledFlow: StateFlow<Boolean> = _isCancelled

    data class DownloadProgress(
        val fileName: String,
        val bytesRead: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) {
        val percentage: Int get() = if (totalBytes > 0) ((bytesRead * 100.0 / totalBytes).roundToInt()) else -1
    }

    data class BatchDownloadProgress(
        val totalSongs: Int,
        val completedSongs: Int,
        val currentSong: String,
        val currentProgress: DownloadProgress?,
        val currentSongIndex: Int = 0
    ) {
        val percentage: Int get() = if (totalSongs > 0) {
            val baseProgress = (completedSongs * 100.0 / totalSongs)
            val currentSongProgress = currentProgress?.let { progress ->
                if (progress.totalBytes > 0) {
                    (progress.bytesRead.toDouble() / progress.totalBytes) / totalSongs
                } else 0.0
            } ?: 0.0
            (baseProgress + currentSongProgress * 100).roundToInt()
        } else 0
    }

    suspend fun downloadSong(context: Context, song: SongItem) {
        withContext(Dispatchers.IO) {
            try {

                val existingFile = getLocalFilePath(context, song)
                if (existingFile != null) {
                    NPLogger.d(TAG, "文件已存在，跳过下载: ${song.name}")
                    return@withContext
                }

                val isBili = song.album.startsWith(PlayerManager.BILI_SOURCE_TAG)
                val resolved = if (isBili) resolveBili(song) else resolveNetease(song.id)
                if (resolved == null) {
                    NPLogger.e(TAG, "无法获取下载链接: ${song.name}")
                    return@withContext
                }

                val (url, mime, extGuess) = resolved

                val ext = when {
                    !mime.isNullOrBlank() -> mimeToExt(mime)
                    else -> extFromUrl(url) ?: extGuess
                }

                val baseName = sanitizeFileName("${song.artist} - ${song.name}")
                val fileName = if (ext.isNullOrBlank()) baseName else "$baseName.$ext"

                val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val downloadDir = File(baseDir, "NeriPlayer").apply { mkdirs() }
                val destFile = uniqueFile(downloadDir, fileName)

                if (!song.album.startsWith("Bilibili")) {
                    downloadLyrics(context, song)
                }

                try {
                    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                    val downloadDir = File(baseDir, "NeriPlayer")
                    val coverDir = File(downloadDir, "Covers").apply { mkdirs() }

                    val coverUrl = song.coverUrl
                    if (!coverUrl.isNullOrBlank()) {
                        val coverFile = File(coverDir, "$baseName.jpg")
                        val req = Request.Builder().url(coverUrl).build()
                        val resp = AppContainer.sharedOkHttpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            resp.body.byteStream().use { input ->
                                coverFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                        resp.close()
                    }
                } catch (_: Exception) {}

                val reqBuilder = Request.Builder().url(url)
                if (isBili) {
                    val cookieMap = AppContainer.biliCookieRepo.getCookiesOnce()
                    val cookieHeader = cookieMap.entries.joinToString("; ") { (k, v) -> "$k=$v" }
                    reqBuilder
                        .header("User-Agent", BILI_UA)
                        .header("Referer", BILI_REFERER)
                        .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
                }

                val request = reqBuilder.build()
                val client = AppContainer.sharedOkHttpClient

                singleThreadDownload(client, request, destFile)

                _progressFlow.value = null

                try {
                    context.contentResolver.openInputStream(Uri.fromFile(destFile))?.close()
                } catch (_: Exception) { }

            } catch (e: Exception) {
                NPLogger.e(TAG, "下载失败", e)
                _progressFlow.value = null
            }
        }
    }

    suspend fun downloadPlaylist(context: Context, songs: List<SongItem>) {
        withContext(Dispatchers.IO) {
            try {
                _isCancelled.value = false
                _batchProgressFlow.value = BatchDownloadProgress(
                    totalSongs = songs.size,
                    completedSongs = 0,
                    currentSong = "",
                    currentProgress = null
                )

                for (index in songs.indices) {
                    val song = songs[index]

                    if (_isCancelled.value) {
                        NPLogger.d(TAG, "下载已取消")
                        break
                    }

                    try {
                        _batchProgressFlow.value = _batchProgressFlow.value?.copy(
                            currentSong = song.name,
                            currentProgress = null,
                            currentSongIndex = index
                        )

                        val progressJob = launch {
                            _progressFlow.collect { progress ->
                                _batchProgressFlow.value?.let { current ->
                                    _batchProgressFlow.value = current.copy(currentProgress = progress)
                                }
                            }
                        }

                        downloadSong(context, song)

                        progressJob.cancel()

                        _batchProgressFlow.value?.let { current ->
                            _batchProgressFlow.value = current.copy(
                                completedSongs = index + 1,
                                currentProgress = null
                            )
                        }
                    } catch (e: Exception) {
                        NPLogger.e(TAG, "批量下载失败: ${song.name} - ${e.message}", e)
                    }
                }

                _batchProgressFlow.value = null
            } catch (e: Exception) {
                NPLogger.e(TAG, "批量下载失败: ${e.message}", e)
                _batchProgressFlow.value = null
            }
        }
    }

    fun cancelDownload() {
        _isCancelled.value = true
        _progressFlow.value = null
        _batchProgressFlow.value = null
    }

    private fun downloadLyrics(context: Context, song: SongItem) {
        try {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            val lyricsDir = File(baseDir, "NeriPlayer/Lyrics").apply { mkdirs() }
            val lyricFile = File(lyricsDir, "${song.id}_${song.album}.lrc")

            if (!song.matchedLyric.isNullOrBlank()) {
                lyricFile.writeText(song.matchedLyric)
                NPLogger.d(TAG, "使用匹配的歌词保存: ${song.name}")
                return
            }

            val isFromNetease = !song.album.startsWith("Bilibili")
            if (!isFromNetease) return

            val lyrics = AppContainer.neteaseClient.getLyricNew(song.id)
            val root = JSONObject(lyrics)
            if (root.optInt("code") != 200) return

            val lrc = root.optJSONObject("lrc")?.optString("lyric") ?: ""
            if (lrc.isBlank()) return

            lyricFile.writeText(lrc)
            NPLogger.d(TAG, "从API获取歌词保存: ${song.name}")
        } catch (e: Exception) {
            NPLogger.w(TAG, "歌词下载失败: ${song.name} - ${e.message}")
        }
    }

    fun getLocalFilePath(context: Context, song: SongItem): String? {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val downloadDir = File(baseDir, "NeriPlayer")

        val possibleExtensions = listOf("flac", "m4a", "mp3", "eac3")
        for (ext in possibleExtensions) {
            val fileName = generateFileName(song, ext)
            val file = File(downloadDir, fileName)
            if (file.exists()) return file.absolutePath
        }
        return null
    }

    private fun generateFileName(song: SongItem, ext: String? = null): String {
        val baseName = sanitizeFileName("${song.artist} - ${song.name}")
        return if (ext.isNullOrBlank()) baseName else "$baseName.$ext"
    }

    fun getLyricFilePath(context: Context, song: SongItem): String? {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val lyricsDir = File(baseDir, "NeriPlayer/Lyrics")
        val lyricFile = File(lyricsDir, "${song.id}_${song.album}.lrc")
        return if (lyricFile.exists()) lyricFile.absolutePath else null
    }

    private suspend fun resolveNetease(songId: Long): Triple<String, String?, String?>? {
        val quality = try { AppContainer.settingsRepo.audioQualityFlow.first() } catch (_: Exception) { "exhigh" }
        val raw = AppContainer.neteaseClient.getSongDownloadUrl(songId, level = quality)
        return try {
            val root = JSONObject(raw)
            if (root.optInt("code") != 200) return null
            val data = when (val d = root.opt("data")) {
                is JSONObject -> d
                is JSONArray -> d.optJSONObject(0)
                else -> null
            } ?: return null
            val url = data.optString("url", "")
            if (url.isNullOrBlank()) return null
            val type = data.optString("type", "")
            val mime = guessMimeFromUrl(url)
            Triple(ensureHttps(url), mime, type.lowercase())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveBili(song: SongItem): Triple<String, String?, String?>? {

        val parts = song.album.split('|')
        val cid = if (parts.size > 1) parts[1].toLongOrNull() ?: 0L else 0L

        val videoInfo = AppContainer.biliClient.getVideoBasicInfoByAvid(song.id)
        val bvid = videoInfo.bvid
        val finalCid = if (cid != 0L) cid else videoInfo.pages.firstOrNull()?.cid ?: 0L
        if (finalCid == 0L) return null

        val chosen: BiliAudioStreamInfo? = AppContainer.biliPlaybackRepository.getBestPlayableAudio(bvid, finalCid)
        val url = chosen?.url ?: return null
        val mime = chosen.mimeType
        val ext = mimeToExt(mime)
        return Triple(url, mime, ext)
    }

    private fun sanitizeFileName(name: String): String {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD)
        return n.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "audio" }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var idx = 1
        while (f.exists() && idx < 10_000) {
            val candidate = if (ext.isBlank()) "$base (${idx})" else "$base (${idx}).${ext}"
            f = File(dir, candidate)
            idx++
        }
        return f
    }

    private fun ensureHttps(url: String): String = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url

    private fun mimeToExt(mime: String): String? = when (mime.lowercase()) {
        "audio/flac" -> "flac"
        "audio/x-flac" -> "flac"
        "audio/eac3", "audio/e-ac-3" -> "eac3"
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/mpeg" -> "mp3"
        else -> null
    }

    private fun guessMimeFromUrl(url: String): String? {
        return try {
            URLConnection.guessContentTypeFromName(url.toUri().lastPathSegment)
        } catch (_: Exception) { null }
    }

    private fun extFromUrl(url: String): String? {
        val p = url.toUri().lastPathSegment ?: return null
        val dot = p.lastIndexOf('.')
        if (dot <= 0 || dot == p.length - 1) return null
        return p.substring(dot + 1).lowercase().take(6)
    }

    private suspend fun singleThreadDownload(
        client: okhttp3.OkHttpClient,
        request: Request,
        destFile: File
    ) = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")

            val total = resp.body.contentLength()
            val source = resp.body.source()
            destFile.sink().buffer().use { sink ->
                var readSoFar = 0L
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, 8L * 1024L)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    readSoFar += read
                    val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.001)
                    val speed = (readSoFar / elapsedSec).toLong()
                    val progress = DownloadProgress(destFile.name, readSoFar, total, speed)
                    _progressFlow.value = progress
                }
                sink.flush()
            }
        }
    }
}

