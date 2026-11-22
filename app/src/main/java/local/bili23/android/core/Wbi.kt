package local.bili23.android.core

import java.security.MessageDigest

/** B 站 Web 端 WBI 签名（w_rid + wts）。 */
object Wbi {
    private val MIXIN = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
    )

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        if (raw.length < 64) throw BiliException("未能取得接口签名，请稍后重试")
        return MIXIN.map { raw[it] }.joinToString("")
    }

    fun sign(params: Map<String, String>, key: String, timestamp: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        val filtered = params.mapValues { (_, v) -> v.filter { it !in "!'()*" } }.toMutableMap()
        filtered["wts"] = timestamp.toString()
        val query = filtered.toSortedMap().entries.joinToString("&") {
            "${Http.percentEncode(it.key)}=${Http.percentEncode(it.value)}"
        }
        filtered["w_rid"] = md5(query + key)
        return filtered
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
