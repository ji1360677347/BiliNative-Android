package local.bili23.android.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 会员 Cookie API：从用户自建接口（如 NAS 页面）读取一份大会员 Cookie。
 * 支持三种响应：
 *   [{"name":"SESSDATA","value":"...","domain":".bilibili.com"}, ...]
 *   {"cookies":[...]}
 *   {"header":"SESSDATA=...; bili_jct=..."}
 * 只接受 bilibili.com 域下的 Cookie，避免把其它站点的会话带进来。
 */
object MemberApi {
    private val NAMES = listOf(
        "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5",
        "buvid3", "buvid4", "buvid_fp", "sid",
    )

    fun validateUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) throw BiliException("请输入会员 API 地址")
        val url = trimmed.toHttpUrlOrNull() ?: throw BiliException("请输入有效的 HTTPS 地址")
        if (!url.isHttps) throw BiliException("会员 API 必须使用 HTTPS")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw BiliException("会员 API 地址不能包含用户名或密码")
        }
        if (url.fragment != null) throw BiliException("会员 API 地址不能包含 # 片段")
        return url.toString()
    }

    fun parseResponse(raw: String): String {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.startsWith("[") || text.startsWith("{")) {
            val value = runCatching { JSONTokener(text).nextValue() }.getOrNull()
                ?: throw BiliException("会员 API 未返回有效 JSON")
            when (value) {
                is JSONArray -> return fromArray(value)
                is JSONObject -> {
                    if (value.has("success") && !value.bool("success", true)) {
                        throw BiliException("会员 API 返回失败，请检查访问密钥")
                    }
                    value.optJSONArray("cookies")?.let { return fromArray(it) }
                    val header = value.str("header")
                    if (header.isNotBlank()) return parseCookieInput(header)
                    throw BiliException("会员 API 未返回可识别的 Cookie")
                }
                else -> throw BiliException("会员 API 未返回可识别的 Cookie")
            }
        }
        return parseCookieInput(text)
    }

    private fun fromArray(array: JSONArray): String {
        val fields = LinkedHashMap<String, String>()
        array.objects().forEach { item ->
            val name = item.str("name")
            val value = item.str("value")
            if (name !in NAMES || value.isBlank()) return@forEach
            if (value.any { it == ';' || it == '\n' || it == '\r' }) return@forEach
            val domain = item.str("domain", ".bilibili.com").lowercase()
            if (domain != "bilibili.com" && !domain.endsWith(".bilibili.com")) return@forEach
            fields[name] = value
        }
        val session = fields["SESSDATA"]
        if (session.isNullOrBlank() || session.length < 10) {
            throw BiliException("会员 API 未返回有效的 SESSDATA")
        }
        return NAMES.mapNotNull { name -> fields[name]?.let { "$name=$it" } }.joinToString("; ")
    }
}
