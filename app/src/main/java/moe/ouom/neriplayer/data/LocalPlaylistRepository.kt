package moe.ouom.neriplayer.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import java.io.File

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val songs: MutableList<SongItem> = mutableListOf()
)

class LocalPlaylistRepository private constructor(private val context: Context) {
    private val gson = Gson()
    private val file: File = File(context.filesDir, "local_playlists.json")

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val list = try {
            if (file.exists()) {
                val type = object : TypeToken<List<LocalPlaylist>>() {}.type
                gson.fromJson<List<LocalPlaylist>>(file.readText(), type) ?: emptyList()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }.toMutableList()

        if (list.none { it.name == FAVORITES_NAME }) {
            list.add(0, LocalPlaylist(id = System.currentTimeMillis(), name = FAVORITES_NAME))
        }
        _playlists.value = list
        saveToDisk()
    }

    private fun saveToDisk() {
        runCatching {
            val json = gson.toJson(_playlists.value)
            val parent = file.parentFile ?: context.filesDir
            val tmp = File(parent, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) {
                file.writeText(json)
                tmp.delete()
            }
        }
    }

    suspend fun createPlaylist(name: String) {
        withContext(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            list.add(LocalPlaylist(id = System.currentTimeMillis(), name = name))
            _playlists.value = list
            saveToDisk()
        }
    }

    suspend fun addToFavorites(song: SongItem) {
        val fav = _playlists.value.firstOrNull { it.name == FAVORITES_NAME } ?: return
        addSongToPlaylist(fav.id, song)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        withContext(Dispatchers.IO) {
            val updated = _playlists.value.map { pl ->
                if (pl.id == playlistId) {
                    if (pl.name == FAVORITES_NAME) pl else pl.copy(name = newName)
                } else pl
            }
            _playlists.value = updated
            saveToDisk()
        }
    }

    suspend fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>) {
        withContext(Dispatchers.IO) {
            if (songIds.isEmpty()) return@withContext
            val list = _playlists.value.toMutableList()
            val plIndex = list.indexOfFirst { it.id == playlistId }
            if (plIndex == -1) return@withContext

            val pl = list[plIndex]
            val idSet = songIds.toHashSet()
            val newSongs = pl.songs.filterNot { it.id in idSet }.toMutableList()

            if (newSongs.size != pl.songs.size) {
                list[plIndex] = pl.copy(songs = newSongs)
                _playlists.value = list
                saveToDisk()
            }
        }
    }

    suspend fun deletePlaylist(playlistId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            val pl = list.find { it.id == playlistId } ?: return@withContext false
            if (pl.name == FAVORITES_NAME) return@withContext false
            list.remove(pl)
            _playlists.value = list
            saveToDisk()
            true
        }
    }

    suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) {
        withContext(Dispatchers.IO) {
            val updated = _playlists.value.map { pl ->
                if (pl.id != playlistId) return@map pl
                val songs = pl.songs
                if (fromIndex !in songs.indices || toIndex !in songs.indices) return@map pl
                val newSongs = songs.toMutableList().apply {
                    val s = removeAt(fromIndex)
                    add(toIndex, s)
                }
                pl.copy(songs = newSongs)
            }
            _playlists.value = updated
            saveToDisk()
        }
    }

    suspend fun reorderSongs(playlistId: Long, newOrderIds: List<Long>) {
        withContext(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            val idx = list.indexOfFirst { it.id == playlistId }
            if (idx == -1) return@withContext
            val pl = list[idx]

            val byId = pl.songs.associateBy { it.id }
            val ordered = newOrderIds.mapNotNull { byId[it] }.toMutableList()

            pl.songs.forEach { s -> if (ordered.none { it.id == s.id }) ordered.add(s) }

            list[idx] = pl.copy(songs = ordered)
            _playlists.value = list
            saveToDisk()
        }
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<SongItem>) {
        withContext(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            val idx = list.indexOfFirst { it.id == playlistId }
            if (idx == -1) return@withContext

            val pl = list[idx]
            val exists = pl.songs.asSequence().map { it.id }.toMutableSet()
            val toAdd = songs.filter { exists.add(it.id) }
            if (toAdd.isEmpty()) return@withContext

            list[idx] = pl.copy(songs = (pl.songs + toAdd).toMutableList())
            _playlists.value = list
            saveToDisk()
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: SongItem) {
        addSongsToPlaylist(playlistId, listOf(song))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        withContext(Dispatchers.IO) {
            val list = _playlists.value.toMutableList()
            val plIndex = list.indexOfFirst { it.id == playlistId }
            if (plIndex == -1) return@withContext
            val pl = list[plIndex]
            val newSongs = pl.songs.filter { it.id != songId }.toMutableList()
            if (newSongs.size != pl.songs.size) {
                list[plIndex] = pl.copy(songs = newSongs)
                _playlists.value = list
                saveToDisk()
            }
        }
    }

    suspend fun exportSongsToPlaylist(sourcePlaylistId: Long, targetPlaylistId: Long, songIds: List<Long>) {
        withContext(Dispatchers.IO) {
            val source = _playlists.value.firstOrNull { it.id == sourcePlaylistId } ?: return@withContext
            val inSourceOrder = songIds.mapNotNull { id -> source.songs.firstOrNull { it.id == id } }

            val list = _playlists.value.toMutableList()
            val idx = list.indexOfFirst { it.id == targetPlaylistId }
            if (idx == -1) return@withContext
            val pl = list[idx]

            val exists = pl.songs.asSequence().map { it.id }.toMutableSet()
            val toAdd = inSourceOrder.filter { exists.add(it.id) }
            if (toAdd.isEmpty()) return@withContext

            list[idx] = pl.copy(songs = (pl.songs + toAdd).toMutableList())
            _playlists.value = list
            saveToDisk()
        }
    }

    companion object {
        const val FAVORITES_NAME = "我喜欢的音乐"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: LocalPlaylistRepository? = null

        fun getInstance(context: Context): LocalPlaylistRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalPlaylistRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun removeFromFavorites(songId: Long) {
        withContext(Dispatchers.IO) {
            val updated = _playlists.value.map { pl ->
                if (pl.name == FAVORITES_NAME)
                    pl.copy(songs = pl.songs.filter { it.id != songId }.toMutableList())
                else pl
            }
            _playlists.value = updated
            saveToDisk()
        }
    }

    suspend fun updateSongMetadata(songId: Long, albumIdentifier: String, newSongInfo: SongItem) {
        withContext(Dispatchers.IO) {
            val updatedPlaylists = _playlists.value.map { playlist ->
                val songIndex = playlist.songs.indexOfFirst { it.id == songId && it.album == albumIdentifier }

                if (songIndex != -1) {
                    val updatedSongs = playlist.songs.toMutableList().apply {
                        this[songIndex] = newSongInfo
                    }
                    playlist.copy(songs = updatedSongs)
                } else {
                    playlist
                }
            }

            _playlists.value = updatedPlaylists
            saveToDisk()
        }
    }

    suspend fun updatePlaylists(playlists: List<LocalPlaylist>) {
        withContext(Dispatchers.IO) {
            _playlists.value = playlists
            saveToDisk()
        }
    }
}
