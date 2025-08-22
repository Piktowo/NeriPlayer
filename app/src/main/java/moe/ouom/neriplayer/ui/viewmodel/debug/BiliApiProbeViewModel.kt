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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.di.AppContainer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class BiliProbeUiState(
    val running: Boolean = false,
    val lastMessage: String = "",
    val lastJsonPreview: String = "",
    val keyword: String = "",
    val bvid: String = "",
    val cid: String = "",
    val page: String = "1",
    val upMid: String = "",
    val mediaId: String = ""
)

class BiliApiProbeViewModel(app: Application) : AndroidViewModel(app) {
    private val client = AppContainer.biliClient

    private val _ui = MutableStateFlow(BiliProbeUiState())
    val ui: StateFlow<BiliProbeUiState> = _ui

    fun onKeywordChange(s: String) { _ui.value = _ui.value.copy(keyword = s) }
    fun onBvidChange(s: String) { _ui.value = _ui.value.copy(bvid = s) }
    fun onCidChange(s: String) { _ui.value = _ui.value.copy(cid = s.filter { it.isDigit() }) }
    fun onPageChange(s: String) { _ui.value = _ui.value.copy(page = s.filter { it.isDigit() }) }
    fun onUpMidChange(s: String) { _ui.value = _ui.value.copy(upMid = s.filter { it.isDigit() }) }
    fun onMediaIdChange(s: String) { _ui.value = _ui.value.copy(mediaId = s.filter { it.isDigit() }) }

    fun clearPreview() { _ui.value = _ui.value.copy(lastJsonPreview = "", lastMessage = "") }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private inline fun launchAndCopy(label: String, crossinline block: suspend () -> String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(running = true, lastMessage = "调用中：$label ...", lastJsonPreview = "")
            try {
                val raw = withContext(Dispatchers.IO) { block() }
                copyToClipboard("bili_api_$label", raw)
                _ui.value = _ui.value.copy(
                    running = false,
                    lastMessage = "已复制到剪贴板：$label",
                    lastJsonPreview = raw
                )
            } catch (e: IOException) {
                _ui.value = _ui.value.copy(
                    running = false,
                    lastMessage = "网络/服务器异常：${e.message ?: e.javaClass.simpleName}"
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    running = false,
                    lastMessage = "调用/解析失败：${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    private suspend fun getCidByBvidAndPage(bvid: String, page: Int): Long {
        val pages = client.getVideoPageList(bvid = bvid)
        val targetPage = pages.find { it.page == page }
            ?: pages.firstOrNull()
            ?: throw IllegalArgumentException("找不到第 $page P")
        return targetPage.cid
    }

    private suspend fun getCidByAidAndPage(aid: Long, page: Int): Long {
        val pages = client.getVideoPageList(aid = aid)
        val targetPage = pages.find { it.page == page }
            ?: pages.firstOrNull()
            ?: throw IllegalArgumentException("找不到第 $page P")
        return targetPage.cid
    }

    fun searchAndCopy() = launchAndCopy("search") {
        val kw = ui.value.keyword.ifBlank { "bilibili" }
        val page = 1
        val result = client.searchVideos(keyword = kw, page = page)
        val arr = JSONArray()
        result.items.take(10).forEach { it ->
            arr.put(JSONObject().apply {
                put("aid", it.aid)
                put("bvid", it.bvid)
                put("title", it.titlePlain)
                put("author", it.author)
                put("mid", it.mid)
                put("durationSec", it.durationSec)
                put("play", it.play)
                put("coverUrl", it.coverUrl)
            })
        }
        JSONObject().apply {
            put("code", 0)
            put("page", result.page)
            put("pageSize", result.pageSize)
            put("items_preview", arr)
        }.toString()
    }

    fun viewByBvidAndCopy() = launchAndCopy("view_by_bvid") {
        val bvid = ui.value.bvid
        val info = client.getVideoBasicInfoByBvid(bvid)
        JSONObject().apply {
            put("aid", info.aid)
            put("bvid", info.bvid)
            put("title", info.title)
            put("coverUrl", info.coverUrl)
            put("desc", info.desc)
            put("durationSec", info.durationSec)
            put("owner", JSONObject().apply {
                put("mid", info.ownerMid)
                put("name", info.ownerName)
                put("face", info.ownerFace)
            })
            put("stat", JSONObject().apply {
                put("view", info.stats.view)
                put("like", info.stats.like)
                put("reply", info.stats.reply)
                put("favorite", info.stats.favorite)
                put("coin", info.stats.coin)
                put("share", info.stats.share)
                put("danmaku", info.stats.danmaku)
            })
            put("pages", JSONArray().apply {
                info.pages.forEach { p ->
                    put(JSONObject().apply {
                        put("cid", p.cid)
                        put("page", p.page)
                        put("part", p.part)
                        put("durationSec", p.durationSec)
                        put("w", p.width)
                        put("h", p.height)
                    })
                }
            })
        }.toString()
    }

    fun playInfoByBvidCidAndCopy() = launchAndCopy("playinfo_by_bvid_cid") {
        val bvid = ui.value.bvid
        val cid = if (ui.value.cid.isNotBlank()) {
            ui.value.cid.toLongOrNull() ?: 0L
        } else {

            val page = ui.value.page.toIntOrNull() ?: 1
            getCidByBvidAndPage(bvid, page)
        }
        val info = client.getPlayInfoByBvid(bvid, cid)

        info.raw.toString()
    }

    fun playInfoByBvidPageAndCopy() = launchAndCopy("playinfo_by_bvid_page") {
        val bvid = ui.value.bvid
        val page = ui.value.page.toIntOrNull() ?: 1
        val cid = getCidByBvidAndPage(bvid, page)
        val info = client.getPlayInfoByBvid(bvid, cid)
        JSONObject().apply {
            put("resolved_cid", cid)
            put("page", page)
            put("playinfo", info.raw)
        }.toString()
    }

    fun hasLikeByBvidAndCopy() = launchAndCopy("has_like_recently") {
        val bvid = ui.value.bvid
        val liked = client.hasLikedRecentlyByBvid(bvid)
        JSONObject().apply {
            put("code", 0)
            put("bvid", bvid)
            put("liked_recently", liked)
        }.toString()
    }

    fun createdFavsAndCopy() = launchAndCopy("fav_created_list") {
        val mid = ui.value.upMid.toLongOrNull() ?: 0L
        val list = client.getUserCreatedFavFolders(mid)
        JSONArray().apply {
            list.forEach { f ->
                put(JSONObject().apply {
                    put("media_id", f.mediaId)
                    put("fid", f.fid)
                    put("mid", f.mid)
                    put("title", f.title)
                    put("count", f.count)
                    put("cover", f.coverUrl)
                })
            }
        }.let { arr -> JSONObject().put("code", 0).put("list", arr).toString() }
    }

    fun favInfoAndCopy() = launchAndCopy("fav_folder_info") {
        val mediaId = ui.value.mediaId.toLongOrNull() ?: 0L
        val f = client.getFavFolderInfo(mediaId)
        JSONObject().apply {
            put("code", 0)
            put("info", JSONObject().apply {
                put("media_id", f.mediaId)
                put("fid", f.fid)
                put("mid", f.mid)
                put("title", f.title)
                put("count", f.count)
                put("cover", f.coverUrl)
                put("intro", f.intro)
            })
        }.toString()
    }

    fun favContentsAndCopy() = launchAndCopy("fav_folder_contents") {
        val mediaId = ui.value.mediaId.toLongOrNull() ?: 0L
        val page = 1
        val data = client.getFavFolderContents(mediaId, page = page, pageSize = 20)
        JSONObject().apply {
            put("code", 0)
            put("folder", JSONObject().apply {
                put("media_id", data.info.mediaId)
                put("title", data.info.title)
                put("count", data.info.count)
            })
            put("has_more", data.hasMore)
            put("items", JSONArray().apply {
                data.items.forEach { itx ->
                    put(JSONObject().apply {
                        put("type", itx.type)
                        put("id", itx.id)
                        put("bvid", itx.bvid)
                        put("title", itx.title)
                        put("durationSec", itx.durationSec)
                        put("upper_mid", itx.upperMid)
                        put("upper_name", itx.upperName)
                        put("play", itx.play)
                    })
                }
            })
        }.toString()
    }

    fun playInfoByAvidCidAndCopy() = launchAndCopy("playinfo_by_avid_cid") {
        val avid = ui.value.bvid.removePrefix("av").toLongOrNull()
            ?: throw IllegalArgumentException("请输入有效 avid（数字）到 BV 输入框")

        val cid = if (ui.value.cid.isNotBlank()) {
            ui.value.cid.toLongOrNull() ?: 0L
        } else {

            val page = ui.value.page.toIntOrNull() ?: 1
            getCidByAidAndPage(avid, page)
        }

        val info = client.getPlayInfoByAvid(avid, cid)
        info.raw.toString()
    }

    fun allAudioStreamsByBvidCidAndCopy() = launchAndCopy("all_audio_streams_by_bvid_cid") {
        val bvid = ui.value.bvid
        val cid = if (ui.value.cid.isNotBlank()) {
            ui.value.cid.toLongOrNull() ?: 0L
        } else {

            val page = ui.value.page.toIntOrNull() ?: 1
            getCidByBvidAndPage(bvid, page)
        }

        val list = client.getAllAudioStreams(bvid, cid, BiliClient.PlayOptions())
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("mime", a.mimeType)
                put("kbps", a.bitrateKbps)
                put("qualityTag", a.qualityTag)
                put("url", a.url)
            })
        }
        JSONObject().put("code", 0).put("audios", arr).toString()
    }

    fun allAudioStreamsByBvidPageAndCopy() = launchAndCopy("all_audio_streams_by_bvid_page") {
        val bvid = ui.value.bvid
        val page = ui.value.page.toIntOrNull() ?: 1
        val cid = getCidByBvidAndPage(bvid, page)

        val list = client.getAllAudioStreams(bvid, cid, BiliClient.PlayOptions())
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("mime", a.mimeType)
                put("kbps", a.bitrateKbps)
                put("qualityTag", a.qualityTag)
                put("url", a.url)
            })
        }
        JSONObject().apply {
            put("code", 0)
            put("resolved_cid", cid)
            put("page", page)
            put("audios", arr)
        }.toString()
    }

    fun mp4DurlByBvidCidAndCopy() = launchAndCopy("mp4_durl_by_bvid_cid") {
        val bvid = ui.value.bvid
        val cid = if (ui.value.cid.isNotBlank()) {
            ui.value.cid.toLongOrNull() ?: 0L
        } else {

            val page = ui.value.page.toIntOrNull() ?: 1
            getCidByBvidAndPage(bvid, page)
        }

        val opts = BiliClient.PlayOptions(
            qn = ui.value.cid.toIntOrNull(),
            fnval = 0,
            platform = "html5",
            highQuality = 1
        )
        val info = client.getPlayInfoByBvid(bvid, cid, opts)
        val arr = JSONArray().apply {
            info.durl.forEach { d ->
                put(JSONObject().apply {
                    put("order", d.order)
                    put("lengthMs", d.lengthMs)
                    put("sizeBytes", d.sizeBytes)
                    put("url", d.url)
                    put("backupUrls", JSONArray(d.backupUrls))
                })
            }
        }
        JSONObject().put("code", info.code).put("message", info.message).put("durl", arr).toString()
    }

    fun mp4DurlByBvidPageAndCopy() = launchAndCopy("mp4_durl_by_bvid_page") {
        val bvid = ui.value.bvid
        val page = ui.value.page.toIntOrNull() ?: 1
        val cid = getCidByBvidAndPage(bvid, page)

        val opts = BiliClient.PlayOptions(
            qn = null,
            fnval = 0,
            platform = "html5",
            highQuality = 1
        )
        val info = client.getPlayInfoByBvid(bvid, cid, opts)
        val arr = JSONArray().apply {
            info.durl.forEach { d ->
                put(JSONObject().apply {
                    put("order", d.order)
                    put("lengthMs", d.lengthMs)
                    put("sizeBytes", d.sizeBytes)
                    put("url", d.url)
                    put("backupUrls", JSONArray(d.backupUrls))
                })
            }
        }
        JSONObject().apply {
            put("code", info.code)
            put("message", info.message)
            put("resolved_cid", cid)
            put("page", page)
            put("durl", arr)
        }.toString()
    }
}