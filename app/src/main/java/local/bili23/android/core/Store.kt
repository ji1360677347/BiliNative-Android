package local.bili23.android.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 本机设置与凭据。Cookie 只落在应用私有 SharedPreferences 中，不上传。 */
class Store(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("bili_native", Context.MODE_PRIVATE)

    var personalCookie: String
        get() = sp.getString("cookie.personal", "").orEmpty()
        set(value) = sp.edit().putString("cookie.personal", value).apply()

    var memberCookie: String
        get() = sp.getString("cookie.member", "").orEmpty()
        set(value) = sp.edit().putString("cookie.member", value).apply()

    var buvid3: String
        get() = sp.getString("device.buvid3", "").orEmpty()
        set(value) = sp.edit().putString("device.buvid3", value).apply()

    var buvid4: String
        get() = sp.getString("device.buvid4", "").orEmpty()
        set(value) = sp.edit().putString("device.buvid4", value).apply()

    var bNut: Long
        get() = sp.getLong("device.bNut", 0L)
        set(value) = sp.edit().putLong("device.bNut", value).apply()

    var quality: Int
        get() = sp.getInt("play.quality", 80)
        set(value) = sp.edit().putInt("play.quality", value).apply()

    var preference: PlaybackPreference
        get() = runCatching { PlaybackPreference.valueOf(sp.getString("play.preference", "AUTO")!!) }
            .getOrDefault(PlaybackPreference.AUTO)
        set(value) = sp.edit().putString("play.preference", value.name).apply()

    var speed: Float
        get() = sp.getFloat("play.speed", 1f)
        set(value) = sp.edit().putFloat("play.speed", value).apply()

    var danmakuEnabled: Boolean
        get() = sp.getBoolean("danmaku.enabled", true)
        set(value) = sp.edit().putBoolean("danmaku.enabled", value).apply()

    var danmakuOpacity: Float
        get() = sp.getFloat("danmaku.opacity", 0.9f)
        set(value) = sp.edit().putFloat("danmaku.opacity", value).apply()

    var danmakuScale: Float
        get() = sp.getFloat("danmaku.scale", 1f)
        set(value) = sp.edit().putFloat("danmaku.scale", value).apply()

    var danmakuArea: Float
        get() = sp.getFloat("danmaku.area", 0.5f)
        set(value) = sp.edit().putFloat("danmaku.area", value).apply()

    /** 0 跟随系统，1 浅色，2 深色。 */
    var themeMode: Int
        get() = sp.getInt("theme.mode", 0)
        set(value) = sp.edit().putInt("theme.mode", value).apply()

    val deviceCookie: String
        get() {
            val parts = mutableListOf<String>()
            if (buvid3.isNotBlank()) parts += "buvid3=$buvid3"
            if (buvid4.isNotBlank()) parts += "buvid4=$buvid4"
            if (bNut > 0) parts += "b_nut=$bNut"
            return parts.joinToString("; ")
        }

    fun cookie(role: Identity): String {
        val roleCookie = when (role) {
            Identity.PERSONAL -> personalCookie
            Identity.MEMBER -> memberCookie
        }
        return listOf(roleCookie, deviceCookie).filter { it.isNotBlank() }.joinToString("; ")
    }

    fun csrf(): String? = cookieField(personalCookie, "bili_jct")

    fun cookieField(cookie: String, name: String): String? {
        cookie.split(";").forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0 && pair.substring(0, index).trim() == name) {
                return pair.substring(index + 1).trim().ifBlank { null }
            }
        }
        return null
    }

    var searchHistory: List<String>
        get() = runCatching {
            val array = JSONArray(sp.getString("search.history", "[]"))
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        set(value) = sp.edit().putString(
            "search.history",
            JSONArray(value.distinct().take(12)).toString(),
        ).apply()

    var localHistory: List<Video>
        get() = runCatching {
            val array = JSONArray(sp.getString("history.local", "[]"))
            array.objects().map { Video.fromJson(it) }
        }.getOrDefault(emptyList())
        set(value) = sp.edit().putString(
            "history.local",
            JSONArray().apply {
                value.distinctBy { it.routeId }.take(60).forEach { put(it.toJson()) }
            }.toString(),
        ).apply()

    fun remember(video: Video, cid: Int, positionMs: Long) {
        if (video.bvid.isBlank()) return
        val entry = video.copy(cid = cid)
        val rest = localHistory.filterNot { it.routeId == entry.routeId }
        localHistory = listOf(entry) + rest
    }
}

/** 把用户粘贴的内容整理成标准 Cookie 串，支持 `Cookie: xxx`、JSON 数组和裸串。 */
fun parseCookieInput(raw: String): String {
    var text = raw.trim()
    if (text.isEmpty()) throw BiliException("请粘贴 B 站 Cookie")
    if (text.startsWith("Cookie:", ignoreCase = true)) text = text.substring(7).trim()
    if (text.startsWith("[")) {
        val array = runCatching { JSONArray(text) }.getOrElse { throw BiliException("Cookie JSON 格式不正确") }
        text = array.objects().joinToString("; ") { "${it.str("name")}=${it.str("value")}" }
    }
    text = text.replace("\r", "; ").replace("\n", "; ")
    val pairs = text.split(";").map { it.trim() }.filter { it.contains("=") }
    if (pairs.isEmpty()) throw BiliException("Cookie 格式不正确")
    val hasSession = pairs.any { it.startsWith("SESSDATA=") }
    val hasCsrf = pairs.any { it.startsWith("bili_jct=") }
    if (!hasSession && !hasCsrf) throw BiliException("Cookie 中缺少 SESSDATA 或 bili_jct")
    return pairs.joinToString("; ")
}

fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { str(it) }
