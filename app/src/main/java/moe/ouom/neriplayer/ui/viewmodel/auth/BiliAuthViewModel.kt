package moe.ouom.neriplayer.ui.viewmodel.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.data.BiliCookieRepository
import org.json.JSONObject

sealed interface BiliAuthEvent {
    data class ShowSnack(val message: String) : BiliAuthEvent
    data class ShowCookies(val cookies: Map<String, String>) : BiliAuthEvent
    data object LoginSuccess : BiliAuthEvent
}

class BiliAuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BiliCookieRepository(app)

    private val _events = Channel<BiliAuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun importCookiesFromRaw(raw: String) {
        val map = linkedMapOf<String, String>()
        raw.split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .forEach {
                val idx = it.indexOf('=')
                val k = it.substring(0, idx).trim()
                val v = it.substring(idx + 1).trim()
                if (k.isNotBlank()) map[k] = v
            }
        importCookiesFromMap(map)
    }

    fun importCookiesFromMap(map: Map<String, String>) {
        viewModelScope.launch {
            if (map.isEmpty()) {
                _events.send(BiliAuthEvent.ShowSnack("Cookie 为空"))
                return@launch
            }
            repo.saveCookies(map)
            _events.send(BiliAuthEvent.ShowCookies(map))
            _events.send(BiliAuthEvent.LoginSuccess)
        }
    }

    fun parseJsonToMap(json: String): Map<String, String> {
        return runCatching {
            val obj = JSONObject(json)
            val out = linkedMapOf<String, String>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = obj.optString(k, "")
            }
            out
        }.getOrElse { emptyMap() }
    }
}