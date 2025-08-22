package moe.ouom.neriplayer.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import moe.ouom.neriplayer.core.download.GlobalDownloadManager
import moe.ouom.neriplayer.core.download.DownloadedSong
import moe.ouom.neriplayer.core.download.DownloadTask
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem

class DownloadManagerViewModel(application: Application) : AndroidViewModel(application) {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = GlobalDownloadManager.downloadedSongs
    val isRefreshing: StateFlow<Boolean> = GlobalDownloadManager.isRefreshing
    val downloadTasks: StateFlow<List<DownloadTask>> = GlobalDownloadManager.downloadTasks

    fun refreshDownloadedSongs() {
        val appContext = getApplication<Application>()
        GlobalDownloadManager.scanLocalFiles(appContext)
    }

    fun deleteDownloadedSong(song: DownloadedSong) {
        val appContext = getApplication<Application>()
        GlobalDownloadManager.deleteDownloadedSong(appContext, song)
    }

    fun playDownloadedSong(context: Context, song: DownloadedSong) {
        GlobalDownloadManager.playDownloadedSong(context, song)
    }

    fun startBatchDownload(
        context: Context,
        songs: List<SongItem>,
        onError: ((String) -> Unit)? = null,
        onBatchComplete: () -> Unit = {}
    ) {
        GlobalDownloadManager.startBatchDownload(
            context = context,
            songs = songs,
            onError = onError,
            onBatchComplete = onBatchComplete
        )
    }
}
