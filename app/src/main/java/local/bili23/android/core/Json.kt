package local.bili23.android.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * B 站接口字段类型并不稳定（同一字段可能是 int / string / null / double），
 * 这里统一做宽松读取，避免每个解析点都写一遍类型判断。
 */

fun JSONObject.str(key: String, def: String = ""): String {
    if (!has(key) || isNull(key)) return def
    return when (val v = opt(key)) {
        is String -> v
        is Number -> v.toString()
        is Boolean -> v.toString()
        else -> def
    }
}

fun JSONObject.int(key: String, def: Int = 0): Int = when (val v = opt(key)) {
    is Int -> v
    is Long -> v.toInt()
    is Double -> v.toInt()
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: def
    else -> def
}

fun JSONObject.long(key: String, def: Long = 0L): Long = when (val v = opt(key)) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> v.toLong()
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: def
    else -> def
}

fun JSONObject.bool(key: String, def: Boolean = false): Boolean = when (val v = opt(key)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v == "true" || v == "1"
    else -> def
}

fun JSONObject.obj(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

fun JSONObject.arr(key: String): JSONArray =
    if (has(key) && !isNull(key)) optJSONArray(key) ?: JSONArray() else JSONArray()

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** 去掉 B 站标题里的 <em class="keyword"> 等高亮标签与 HTML 实体。 */
fun cleanText(raw: String): String = raw
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

fun normalizeUrl(raw: String): String = when {
    raw.isBlank() -> ""
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
    else -> raw
}
