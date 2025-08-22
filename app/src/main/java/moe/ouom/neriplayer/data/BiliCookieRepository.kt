package moe.ouom.neriplayer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONObject

private val Context.biliCookieStore by preferencesDataStore("bili_auth_store")

object BiliCookieKeys {
    val COOKIE_JSON = stringPreferencesKey("bili_cookie_json")
}

class BiliCookieRepository(private val context: Context) {

    val cookieFlow: Flow<Map<String, String>> =
        context.biliCookieStore.data.map { prefs ->
            val json = prefs[BiliCookieKeys.COOKIE_JSON] ?: "{}"
            jsonToMap(json)
        }

    suspend fun getCookiesOnce(): Map<String, String> {
        val prefs = context.biliCookieStore.data.first()
        val json = prefs[BiliCookieKeys.COOKIE_JSON] ?: "{}"
        return jsonToMap(json)
    }

    suspend fun saveCookies(cookies: Map<String, String>) {
        clear()
        context.biliCookieStore.edit { it[BiliCookieKeys.COOKIE_JSON] = mapToJson(cookies) }
        NPLogger.d("NERI-BiliCookieRepo", "Saved Bili cookies: keys=${cookies.keys.joinToString()}")
    }

    suspend fun clear() {
        context.biliCookieStore.edit { it[BiliCookieKeys.COOKIE_JSON] = "{}" }
        NPLogger.d("NERI-BiliCookieRepo", "Cleared Bili cookies")
    }

    private fun mapToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun jsonToMap(json: String): Map<String, String> {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, String>()
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            out[k] = obj.optString(k, "")
        }
        return out
    }
}