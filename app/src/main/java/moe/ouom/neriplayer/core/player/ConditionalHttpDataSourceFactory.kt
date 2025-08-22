package moe.ouom.neriplayer.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.data.BiliCookieRepository
import android.net.Uri
import moe.ouom.neriplayer.util.NPLogger

@UnstableApi
class ConditionalHttpDataSourceFactory(
    private val baseFactory: HttpDataSource.Factory,
    cookieRepo: BiliCookieRepository
) : HttpDataSource.Factory {

    companion object {
        private const val BILI_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }

    @Volatile
    private var latestCookieHeader: String = ""
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    init {
        scope.launch {
            cookieRepo.cookieFlow.collect { cookies ->
                latestCookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }
        }
    }

    override fun createDataSource(): HttpDataSource {
        val delegate = baseFactory.createDataSource()
        return object : HttpDataSource by delegate {

            override fun open(dataSpec: DataSpec): Long {
                NPLogger.i("createDataSource", dataSpec.uri)
                val finalSpec = if (shouldInjectBiliHeaders(dataSpec.uri)) {
                    val headers = buildBiliHeaders(dataSpec.httpRequestHeaders)
                    dataSpec.buildUpon()
                        .setHttpRequestHeaders(headers)
                        .build()
                } else dataSpec

                return delegate.open(finalSpec)
            }
        }
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
        baseFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }

    private fun shouldInjectBiliHeaders(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("bilivideo.") || uri.toString().contains("https://upos-hz-")
    }

    private fun buildBiliHeaders(original: Map<String, String>): Map<String, String> {
        val newHeaders = LinkedHashMap<String, String>(original)
        newHeaders["Referer"] = "https://www.bilibili.com"
        newHeaders["User-Agent"] = BILI_USER_AGENT
        if (latestCookieHeader.isNotBlank()) newHeaders["Cookie"] = latestCookieHeader
        return newHeaders
    }
}