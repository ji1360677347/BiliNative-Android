package local.bili23.android.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BiliException(message: String) : Exception(message)

/** 全局 HTTP 出口：统一 UA / Referer，Cookie 由调用方显式传入，不做隐式持久化。 */
object Http {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    const val REFERER = "https://www.bilibili.com/"
    const val API = "https://api.bilibili.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 图片加载复用同一连接池（Coil 使用）。 */
    val imageClient: OkHttpClient get() = client

    fun apiUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val builder = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("api.bilibili.com")
        path.trimStart('/').split('/').forEach { builder.addPathSegment(it) }
        query.toSortedMap().forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    suspend fun get(
        url: String,
        cookie: String = "",
        referer: String = REFERER,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Origin", "https://www.bilibili.com")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val hint = body.replace(Regex("\\s+"), " ").take(140)
                throw BiliException("网络请求失败（HTTP ${response.code}）${hint}")
            }
            body
        }
    }

    data class Response(val body: String, val headers: Map<String, List<String>>)

    /** 需要读取 Set-Cookie 的场景（扫码登录回调）。 */
    suspend fun getResponse(
        url: String,
        cookie: String = "",
        referer: String = REFERER,
    ): Response = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw BiliException("网络请求失败（HTTP ${response.code}）")
            Response(body, response.headers.toMultimap())
        }
    }

    suspend fun getBytes(
        url: String,
        cookie: String = "",
        referer: String = REFERER,
    ): ByteArray = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "*/*")
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw BiliException("网络请求失败（HTTP ${response.code}）")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        cookie: String = "",
    ): String = withContext(Dispatchers.IO) {
        val body = form.toSortedMap().entries.joinToString("&") {
            "${percentEncode(it.key)}=${percentEncode(it.value)}"
        }.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", UA)
            .header("Referer", REFERER)
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw BiliException("网络请求失败（HTTP ${response.code}）")
            text
        }
    }

    /** RFC 3986 百分号编码；与 WBI 签名使用同一字符集，避免空格被编码成 `+`。 */
    fun percentEncode(value: String): String {
        val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        val sb = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            if (allowed.indexOf(ch) >= 0) sb.append(ch)
            else sb.append('%').append("%02X".format(code))
        }
        return sb.toString()
    }
}
