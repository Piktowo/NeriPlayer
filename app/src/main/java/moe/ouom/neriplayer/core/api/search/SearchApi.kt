package moe.ouom.neriplayer.core.api.search

interface SearchApi {

    suspend fun search(keyword: String, page: Int): List<SongSearchInfo>

    suspend fun getSongInfo(id: String): SongDetails
}