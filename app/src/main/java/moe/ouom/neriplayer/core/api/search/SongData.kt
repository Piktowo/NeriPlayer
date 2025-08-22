package moe.ouom.neriplayer.core.api.search

import kotlinx.serialization.Serializable

enum class MusicPlatform {
    CLOUD_MUSIC, QQ_MUSIC
}

@Serializable
data class SongSearchInfo(
    val id: String,
    val songName: String,
    val singer: String,
    val duration: String,
    val source: MusicPlatform,
    val albumName: String?,
    val coverUrl: String?
)

@Serializable
data class SongDetails(
    val id: String,
    val songName: String,
    val singer: String,
    val album: String,
    val coverUrl: String?,
    val lyric: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SongDetails

        if (id != other.id) return false
        if (songName != other.songName) return false
        if (singer != other.singer) return false
        if (album != other.album) return false
        if (lyric != other.lyric) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + songName.hashCode()
        result = 31 * result + singer.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (lyric?.hashCode() ?: 0)
        return result
    }
}
