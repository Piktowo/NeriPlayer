package moe.ouom.neriplayer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONObject

private val Context.cookieDataStore by preferencesDataStore("auth_store")

object CookieKeys {
    val NETEASE_COOKIE_JSON = stringPreferencesKey("netease_cookie_json")
}

class NeteaseCookieRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _cookieFlow = MutableStateFlow(runBlocking { getCookiesOnce() })

    val cookieFlow: StateFlow<Map<String, String>> = _cookieFlow.asStateFlow()

    init {
        scope.launch {
            context.cookieDataStore.data.map { prefs ->
                val json = prefs[CookieKeys.NETEASE_COOKIE_JSON] ?: "{}"
                jsonToMap(json)
            }.collect { newCookies ->
                _cookieFlow.value = newCookies
            }
        }
    }

    suspend fun getCookiesOnce(): Map<String, String> {
        val prefs = context.cookieDataStore.data.first()
        val json = prefs[CookieKeys.NETEASE_COOKIE_JSON] ?: "{}"
        return jsonToMap(json)
    }

    suspend fun saveCookies(cookies: Map<String, String>) {

        context.cookieDataStore.edit { prefs ->
            prefs[CookieKeys.NETEASE_COOKIE_JSON] = mapToJson(cookies)
        }
        NPLogger.d("NERI-CookieRepo", "Saved cookies to DataStore: keys=${cookies.keys.joinToString()}")
    }

    suspend fun clear() {
        context.cookieDataStore.edit { prefs ->
            prefs[CookieKeys.NETEASE_COOKIE_JSON] = "{}"
        }
        NPLogger.d("NERI-CookieRepo", "Cleared all saved cookies.")
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun jsonToMap(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val result = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            result[key] = obj.optString(key, "")
        }
        return result
    }
}