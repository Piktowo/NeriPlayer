package moe.ouom.neriplayer.core.di

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.bili.BiliClientAudioDataSource
import moe.ouom.neriplayer.core.api.bili.BiliPlaybackRepository
import moe.ouom.neriplayer.core.api.netease.NeteaseClient
import moe.ouom.neriplayer.core.api.search.CloudMusicSearchApi
import moe.ouom.neriplayer.core.api.search.QQMusicSearchApi
import moe.ouom.neriplayer.data.BiliCookieRepository
import moe.ouom.neriplayer.data.NeteaseCookieRepository
import moe.ouom.neriplayer.data.SettingsRepository
import moe.ouom.neriplayer.util.DynamicProxySelector
import okhttp3.OkHttpClient

object AppContainer {

    private lateinit var application: Application

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val settingsRepo by lazy { SettingsRepository(application) }
    val neteaseCookieRepo by lazy { NeteaseCookieRepository(application) }
    val biliCookieRepo by lazy { BiliCookieRepository(application) }

    val sharedOkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(DynamicProxySelector)
            .build()
    }

    val neteaseClient by lazy {
        NeteaseClient().also { client ->
            val cookies = neteaseCookieRepo.cookieFlow.value.toMutableMap()
            cookies.putIfAbsent("os", "pc")
            client.setPersistedCookies(cookies)
        }
    }

    val biliClient by lazy { BiliClient(biliCookieRepo, client = sharedOkHttpClient) }

    val biliPlaybackRepository by lazy {
        val dataSource = BiliClientAudioDataSource(biliClient)
        BiliPlaybackRepository(dataSource, settingsRepo)
    }

    val cloudMusicSearchApi by lazy { CloudMusicSearchApi(neteaseClient) }
    val qqMusicSearchApi by lazy { QQMusicSearchApi() }

    fun initialize(app: Application) {
        this.application = app
        startCookieObserver()
        startSettingsObserver()
    }

    private fun startCookieObserver() {
        neteaseCookieRepo.cookieFlow
            .onEach { cookies ->
                val mutableCookies = cookies.toMutableMap()
                mutableCookies.putIfAbsent("os", "pc")

                neteaseClient.setPersistedCookies(mutableCookies)
            }
            .launchIn(scope)
    }

    private fun startSettingsObserver() {
        settingsRepo.bypassProxyFlow
            .onEach { enabled ->
                DynamicProxySelector.bypassProxy = enabled
            }
            .launchIn(scope)
    }
}