package moe.ouom.neriplayer.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Destinations(val route: String, val label: String) {

    data object Home : Destinations("home", "首页")
    data object Explore : Destinations("explore", "探索")
    data object Library : Destinations("library", "媒体库")
    data object Settings : Destinations("settings", "设置")

    data object Debug : Destinations("debug", "调试")
    data object DebugBili : Destinations("debug/bili", "B 站调试")
    data object DebugNetease : Destinations("debug/netease", "网易云调试")
    data object DebugSearch : Destinations("debug/search", "搜索调试")
    data object DebugLogsList : Destinations("debug_logs_list", "日志列表")

    data object PlaylistDetail : Destinations("playlist_detail/{playlistJson}", "歌单详情") {
        fun createRoute(playlistJson: String) = "playlist_detail/$playlistJson"
    }

    data object BiliPlaylistDetail : Destinations("bili_playlist_detail/{playlistJson}", "B站收藏夹详情") {
        fun createRoute(playlistJson: String) = "bili_playlist_detail/$playlistJson"
    }

    data object LocalPlaylistDetail : Destinations("local_playlist_detail/{playlistId}", "本地歌单详情") {
        fun createRoute(playlistId: Long) = "local_playlist_detail/$playlistId"
    }

    data object DownloadManager : Destinations("download_manager", "下载管理")

    data object DebugLogViewer : Destinations("debug_log_viewer/{filePath}", "日志查看") {
        fun createRoute(filePath: String): String {
            val encodedPath = URLEncoder.encode(filePath, StandardCharsets.UTF_8.name())
            return "debug_log_viewer/$encodedPath"
        }
    }
}