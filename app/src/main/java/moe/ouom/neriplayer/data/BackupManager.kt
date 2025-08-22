package moe.ouom.neriplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(private val context: Context) {
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    companion object {
        private const val TAG = "BackupManager"
        private const val BACKUP_FILE_PREFIX = "neriplayer_backup"
        private const val BACKUP_FILE_EXTENSION = ".json"
    }

    data class BackupData(
        val version: String = "1.0",
        val timestamp: Long = System.currentTimeMillis(),
        val playlists: List<LocalPlaylist>,
        val exportDate: String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    )

    suspend fun exportPlaylists(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val playlistRepo = LocalPlaylistRepository.getInstance(context)
            val playlists = playlistRepo.playlists.value

            val backupData = BackupData(
                playlists = playlists,
                exportDate = dateFormat.format(Date())
            )

            val json = gson.toJson(backupData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法打开输出流")

            val fileName = "${BACKUP_FILE_PREFIX}_${dateFormat.format(Date())}$BACKUP_FILE_EXTENSION"
            Log.d(TAG, "歌单导出成功: $fileName")
            Result.success(fileName)

        } catch (e: Exception) {
            Log.e(TAG, "歌单导出失败", e)
            Result.failure(e)
        }
    }

    suspend fun importPlaylists(uri: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开输入流")

            val json = inputStream.bufferedReader().use { it.readText() }
            val backupData = gson.fromJson<BackupData>(json, object : TypeToken<BackupData>() {}.type)

            if (backupData.playlists.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("备份文件中没有歌单数据"))
            }

            val playlistRepo = LocalPlaylistRepository.getInstance(context)
            val currentPlaylists = playlistRepo.playlists.value.toMutableList()

            var importedCount = 0
            var skippedCount = 0
            var mergedCount = 0

            for (importedPlaylist in backupData.playlists) {

                val existingIndex = currentPlaylists.indexOfFirst { it.name == importedPlaylist.name }

                if (existingIndex != -1) {

                    val existingPlaylist = currentPlaylists[existingIndex]
                    val mergeResult = mergePlaylists(existingPlaylist, importedPlaylist)

                    if (mergeResult.hasChanges) {
                        currentPlaylists[existingIndex] = mergeResult.mergedPlaylist
                        mergedCount++
                        Log.d(TAG, "歌单 '${importedPlaylist.name}' 已合并，新增 ${mergeResult.addedSongs} 首歌曲")
                    } else {
                        skippedCount++
                        Log.d(TAG, "歌单 '${importedPlaylist.name}' 无需更新")
                    }
                } else {

                    val newPlaylist = LocalPlaylist(
                        id = System.currentTimeMillis() + importedCount,
                        name = importedPlaylist.name,
                        songs = importedPlaylist.songs.toMutableList()
                    )

                    currentPlaylists.add(newPlaylist)
                    importedCount++
                    Log.d(TAG, "歌单 '${importedPlaylist.name}' 已创建，包含 ${newPlaylist.songs.size} 首歌曲")
                }
            }

            playlistRepo.updatePlaylists(currentPlaylists)

            val result = ImportResult(
                importedCount = importedCount,
                skippedCount = skippedCount,
                mergedCount = mergedCount,
                totalCount = backupData.playlists.size,
                backupDate = backupData.exportDate
            )

            Log.d(TAG, "歌单导入成功: $result")
            Result.success(result)

        } catch (e: Exception) {
            Log.e(TAG, "歌单导入失败", e)
            Result.failure(e)
        }
    }

    private fun mergePlaylists(existing: LocalPlaylist, imported: LocalPlaylist): MergeResult {
        val existingSongIds = existing.songs.map { it.id }.toSet()
        val newSongs = imported.songs.filter { it.id !in existingSongIds }

        if (newSongs.isEmpty()) {
            return MergeResult(
                mergedPlaylist = existing,
                hasChanges = false,
                addedSongs = 0
            )
        }

        val mergedSongs = (existing.songs + newSongs).toMutableList()
        val mergedPlaylist = existing.copy(songs = mergedSongs)

        return MergeResult(
            mergedPlaylist = mergedPlaylist,
            hasChanges = true,
            addedSongs = newSongs.size
        )
    }

    suspend fun analyzeDifferences(uri: Uri): Result<DifferenceAnalysis> = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开输入流")

            val json = inputStream.bufferedReader().use { it.readText() }
            val backupData = gson.fromJson<BackupData>(json, object : TypeToken<BackupData>() {}.type)

            val playlistRepo = LocalPlaylistRepository.getInstance(context)
            val currentPlaylists = playlistRepo.playlists.value

            val differences = mutableListOf<PlaylistDifference>()

            for (backupPlaylist in backupData.playlists) {
                val currentPlaylist = currentPlaylists.find { it.name == backupPlaylist.name }

                if (currentPlaylist == null) {

                    differences.add(PlaylistDifference(
                        playlistName = backupPlaylist.name,
                        type = DifferenceType.NEW_PLAYLIST,
                        missingSongs = backupPlaylist.songs.size,
                        existingSongs = 0,
                        totalSongs = backupPlaylist.songs.size
                    ))
                } else {

                    val currentSongIds = currentPlaylist.songs.map { it.id }.toSet()
                    val missingSongs = backupPlaylist.songs.filter { it.id !in currentSongIds }

                    if (missingSongs.isNotEmpty()) {
                        differences.add(PlaylistDifference(
                            playlistName = backupPlaylist.name,
                            type = DifferenceType.MISSING_SONGS,
                            missingSongs = missingSongs.size,
                            existingSongs = currentPlaylist.songs.size,
                            totalSongs = backupPlaylist.songs.size
                        ))
                    }
                }
            }

            val analysis = DifferenceAnalysis(
                backupDate = backupData.exportDate,
                differences = differences,
                totalMissingSongs = differences.sumOf { it.missingSongs }
            )

            Result.success(analysis)

        } catch (e: Exception) {
            Log.e(TAG, "差异分析失败", e)
            Result.failure(e)
        }
    }

    fun generateBackupFileName(): String {
        return "${BACKUP_FILE_PREFIX}_${dateFormat.format(Date())}$BACKUP_FILE_EXTENSION"
    }

    data class ImportResult(
        val importedCount: Int,
        val skippedCount: Int,
        val mergedCount: Int,
        val totalCount: Int,
        val backupDate: String
    ) {
        val successCount: Int get() = importedCount + mergedCount
        val hasSkipped: Boolean get() = skippedCount > 0
        val hasMerged: Boolean get() = mergedCount > 0
    }

    data class MergeResult(
        val mergedPlaylist: LocalPlaylist,
        val hasChanges: Boolean,
        val addedSongs: Int
    )

    enum class DifferenceType {
        NEW_PLAYLIST,
        MISSING_SONGS
    }

    data class PlaylistDifference(
        val playlistName: String,
        val type: DifferenceType,
        val missingSongs: Int,
        val existingSongs: Int,
        val totalSongs: Int
    )

    data class DifferenceAnalysis(
        val backupDate: String,
        val differences: List<PlaylistDifference>,
        val totalMissingSongs: Int
    ) {
        val hasDifferences: Boolean get() = differences.isNotEmpty()
        val newPlaylistsCount: Int get() = differences.count { it.type == DifferenceType.NEW_PLAYLIST }
        val playlistsWithMissingSongs: Int get() = differences.count { it.type == DifferenceType.MISSING_SONGS }
    }
}
