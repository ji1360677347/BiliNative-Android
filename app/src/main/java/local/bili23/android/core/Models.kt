package local.bili23.android.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class VideoPart(val cid: Long, val title: String, val episodeId: Int? = null)

data class Video(
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val title: String,
    val cover: String = "",
    val author: String = "",
    val plays: Int = 0,
    val duration: Int = 0,
    val episodeId: Int? = null,
    val description: String = "",
    val parts: List<VideoPart> = emptyList(),
    val authorId: Int? = null,
    val authorAvatar: String? = null,
    val danmakuCount: Int? = null,
    val likeCount: Int? = null,
    val coinCount: Int? = null,
    val favoriteCount: Int? = null,
    val shareCount: Int? = null,
    val replyCount: Int? = null,
    val pubdate: Int? = null,
    val pubTimeText: String = "",
) {
    val routeId: String get() = episodeId?.let { "ep$it" } ?: bvid
    val coverUrl: String get() = normalizeUrl(cover)
    val webUrl: String
        get() = episodeId?.let { "https://www.bilibili.com/bangumi/play/ep$it" }
            ?: "https://www.bilibili.com/video/$bvid"

    fun toJson(): JSONObject = JSONObject().apply {
        put("bvid", bvid)
        put("aid", aid)
        put("cid", cid)
        put("title", title)
        put("cover", cover)
        put("author", author)
        put("plays", plays)
        put("duration", duration)
        if (episodeId != null) put("episodeId", episodeId) else put("episodeId", JSONObject.NULL)
        put("description", description)
        put("authorId", authorId ?: 0)
        put("pubdate", pubdate ?: 0)
        put("parts", JSONArray().apply {
            parts.forEach {
                put(JSONObject().apply {
                    put("cid", it.cid)
                    put("title", it.title)
                    put("episodeId", it.episodeId ?: 0)
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): Video = Video(
            bvid = o.str("bvid"),
            aid = o.long("aid"),
            cid = o.long("cid"),
            title = o.str("title"),
            cover = o.str("cover"),
            author = o.str("author"),
            plays = o.int("plays"),
            duration = o.int("duration"),
            episodeId = o.int("episodeId").takeIf { it > 0 },
            description = o.str("description"),
            authorId = o.int("authorId").takeIf { it > 0 },
            pubdate = o.int("pubdate").takeIf { it > 0 },
            parts = o.arr("parts").objects().map {
                VideoPart(it.long("cid"), it.str("title"), it.int("episodeId").takeIf { id -> id > 0 })
            },
        )
    }
}

data class Account(
    val name: String,
    val avatar: String,
    val isVip: Boolean,
    val mid: Int,
    val vipDue: Long = 0L,
)

enum class Identity { PERSONAL, MEMBER }

enum class PlaybackPreference(val label: String) {
    AUTO("自动优先会员"),
    PERSONAL("个人账号"),
    MEMBER("会员账号"),
}

data class MediaTrack(
    val quality: Int,
    val codec: String,
    val bandwidth: Int,
    val width: Int,
    val height: Int,
    val urls: List<String>,
)

data class PlaybackSource(
    val videos: List<MediaTrack>,
    val audio: MediaTrack?,
    val usedMember: Boolean,
    val notice: String?,
) {
    val qualities: List<Int> get() = videos.map { it.quality }.distinct().sortedDescending()

    fun video(quality: Int): MediaTrack? {
        val matching = videos.filter { it.quality == quality }
        // 硬件解码兼容性优先 AVC，其次 HEVC。
        return matching.firstOrNull { it.codec.startsWith("avc") } ?: matching.firstOrNull()
    }
}

data class VideoComment(
    val id: Long,
    val name: String,
    val avatar: String,
    val message: String,
    val likes: Int,
    val created: Long,
    val mid: Long = 0,
    val level: Int = 0,
    val isVip: Boolean = false,
    val replyCount: Int = 0,
    val location: String = "",
    val previews: List<VideoComment> = emptyList(),
    val pictures: List<String> = emptyList(),
)

data class Danmaku(
    val id: Long,
    val time: Double,
    val mode: Int,
    val color: Int,
    val text: String,
)

data class DynamicPost(
    val id: String,
    val author: String,
    val avatar: String,
    val published: String,
    val text: String,
    val video: Video?,
    val images: List<String> = emptyList(),
    val likes: Int = 0,
    val comments: Int = 0,
    val forwards: Int = 0,
)

data class DynamicPage(val items: List<DynamicPost>, val nextOffset: String?)

data class FavoriteFolder(val id: Int, val title: String, val count: Int, val cover: String = "")

data class VideoPage(val videos: List<Video>, val hasMore: Boolean, val cursor: String? = null)

data class CommentPage(
    val items: List<VideoComment>,
    val isEnd: Boolean,
    val nextOffset: String? = null,
    val usedWbi: Boolean = false,
)

data class FollowedAccount(val mid: Int, val name: String, val avatar: String, val sign: String)

data class QRLogin(val url: String, val key: String)

sealed class QRPoll {
    data object Waiting : QRPoll()
    data object Confirmation : QRPoll()
    data object Expired : QRPoll()
    data class Success(val account: Account) : QRPoll()
}

fun qualityName(q: Int): String = when (q) {
    127 -> "8K"
    126 -> "杜比视界"
    125 -> "HDR"
    120 -> "4K 超清"
    116 -> "1080P 60帧"
    112 -> "1080P 高码率"
    100 -> "智能修复"
    80 -> "高清 1080P"
    74 -> "720P 60帧"
    64 -> "高清 720P"
    32 -> "清晰 480P"
    16 -> "流畅 360P"
    else -> "画质 $q"
}

/** B 站风格计数：1.2万 / 3.4亿。 */
fun countLabel(n: Int): String {
    if (n >= 100_000_000) return trimZero(n / 100_000_000.0) + "亿"
    if (n >= 10_000) return trimZero(n / 10_000.0) + "万"
    return n.toString()
}

private fun trimZero(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

fun timeLabel(seconds: Int): String = if (seconds >= 3600) {
    String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
} else {
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}

fun relativeTime(unix: Long): String {
    if (unix <= 0) return ""
    val now = System.currentTimeMillis()
    val delta = now - unix * 1000
    if (delta < 60_000) return "刚刚"
    if (delta < 3_600_000) return "${delta / 60_000}分钟前"
    if (delta < 86_400_000L) return "${delta / 3_600_000}小时前"
    val date = Date(unix * 1000)
    val cal = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val fmt = SimpleDateFormat("MM-dd", Locale.CHINA)
    val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return when {
        isSameDay(date, yesterday) -> "昨天"
        cal.get(Calendar.YEAR) == SimpleDateFormat("yyyy", Locale.CHINA).format(date).toInt() -> fmt.format(date)
        else -> ymd.format(date)
    }
}

private fun isSameDay(a: Date, b: Calendar): Boolean {
    val ca = Calendar.getInstance().apply { time = a }
    return ca.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

fun parseVideo(json: JSONObject): Video? {
    val bvid = json.str("bvid")
    if (bvid.isBlank()) return null
    val owner = json.obj("owner") ?: JSONObject()
    val stat = json.obj("stat") ?: JSONObject()
    val pages = json.arr("pages").objects().mapNotNull { p ->
        val cid = p.long("cid")
        if (cid <= 0) null else VideoPart(cid, p.str("part", "分集"))
    }
    return Video(
        bvid = bvid,
        aid = json.long("aid"),
        cid = json.long("cid").takeIf { it > 0 } ?: pages.firstOrNull()?.cid ?: 0L,
        title = cleanText(json.str("title", "未命名视频")),
        cover = json.str("pic").ifBlank { json.str("cover") },
        author = owner.str("name").ifBlank { json.str("author") },
        plays = stat.int("view").takeIf { it > 0 } ?: json.int("play"),
        duration = videoDuration(json),
        description = json.str("desc").takeIf { it != "-" } ?: "",
        parts = pages,
        authorId = owner.int("mid").takeIf { it > 0 },
        authorAvatar = owner.str("face").ifBlank { null },
        danmakuCount = stat.int("danmaku").takeIf { stat.has("danmaku") },
        likeCount = stat.int("like").takeIf { stat.has("like") },
        coinCount = stat.int("coin").takeIf { stat.has("coin") },
        favoriteCount = stat.int("favorite").takeIf { stat.has("favorite") },
        shareCount = stat.int("share").takeIf { stat.has("share") },
        replyCount = stat.int("reply").takeIf { stat.has("reply") },
        pubdate = json.int("pubdate").takeIf { it > 0 } ?: json.int("created").takeIf { it > 0 },
    )
}

/** feed 用 duration、投稿列表用 length、动态用 duration_text。 */
fun videoDuration(json: JSONObject): Int {
    for (key in listOf("duration", "length", "duration_text")) {
        if (!json.has(key) || json.isNull(key)) continue
        when (val value = json.opt(key)) {
            is Number -> if (value.toInt() > 0) return value.toInt()
            is String -> {
                val parts = value.trim().split(":")
                if (parts.size in 1..3) {
                    var total = 0
                    var valid = true
                    parts.forEachIndexed { index, part ->
                        val n = part.toIntOrNull()
                        if (n == null || n < 0 || (index > 0 && n >= 60)) valid = false
                        else total = total * 60 + n
                    }
                    if (valid && total > 0) return total
                }
            }
        }
    }
    return 0
}
