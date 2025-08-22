package moe.ouom.neriplayer

import android.app.Application
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.download.GlobalDownloadManager
import coil.Coil
import coil.ImageLoader

class NeriPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(this)

        GlobalDownloadManager.initialize(this)

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient { AppContainer.sharedOkHttpClient }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}