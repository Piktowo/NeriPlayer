package moe.ouom.neriplayer.ui.viewmodel.debug

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.di.AppContainer

data class SearchProbeUiState(
    val running: Boolean = false,
    val keyword: String = "mili",
    val lastMessage: String = "",
    val lastJsonPreview: String = ""
)

class SearchApiProbeViewModel(app: Application) : AndroidViewModel(app) {

    private val cookieRepo = AppContainer.neteaseCookieRepo

    private val neteaseClient = AppContainer.neteaseClient

    private val cloudMusicApi = AppContainer.cloudMusicSearchApi
    private val qqMusicApi = AppContainer.qqMusicSearchApi
    private val json = Json { prettyPrint = true }

    private val _ui = MutableStateFlow(SearchProbeUiState())
    val ui: StateFlow<SearchProbeUiState> = _ui.asStateFlow()

    fun onKeywordChange(newKeyword: String) {
        _ui.value = _ui.value.copy(keyword = newKeyword)
    }

    private suspend fun ensureCookies() {
        withContext(Dispatchers.IO) { cookieRepo.getCookiesOnce() }
    }

    fun callSearchAndCopy(platform: MusicPlatform) {
        val keyword = _ui.value.keyword
        if (keyword.isBlank()) {
            _ui.value = _ui.value.copy(lastMessage = "错误：关键词不能为空")
            return
        }

        viewModelScope.launch {
            _ui.value = _ui.value.copy(running = true, lastMessage = "正在搜索 [${platform.name}]：$keyword ...", lastJsonPreview = "")
            try {
                if (platform == MusicPlatform.CLOUD_MUSIC) {
                    ensureCookies()
                }

                val resultList = withContext(Dispatchers.IO) {
                    when (platform) {
                        MusicPlatform.CLOUD_MUSIC -> cloudMusicApi.search(keyword, 1)
                        MusicPlatform.QQ_MUSIC -> qqMusicApi.search(keyword, 1)
                    }
                }
                val resultJson = json.encodeToString(resultList)

                copyToClipboard("search_api_${platform.name}", resultJson)
                _ui.value = _ui.value.copy(
                    running = false,
                    lastMessage = "OK，[${platform.name}] 搜索完成并复制到剪贴板",
                    lastJsonPreview = resultJson
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    running = false,
                    lastMessage = "调用/解析失败：${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}