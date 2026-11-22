package local.bili23.android.core

import android.util.Log
import local.bili23.android.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * B 站 Web 接口封装。所有请求都带设备 Cookie（buvid3/buvid4），
 * 个人 / 会员 Cookie 仅用于各自作用域，避免越权混用。
 */
class BiliClient(private val store: Store) {

    private var wbiKey = ""
    private var wbiAt = 0L
    private val deviceMutex = Mutex()
    private val memberRefreshMutex = Mutex()

    // ---------- 基础请求 ----------

    private fun errorFor(root: JSONObject, code: Int): BiliException {
        val message = root.str("message", "请求失败")
        return when (code) {
            -101 -> BiliException("Cookie 已失效，请重新登录或导入")
            -352, -412, -403 -> BiliException("Bilibili 暂时限制了请求，请稍后重试（$code）")
            -111 -> BiliException("CSRF 校验失败，请重新登录")
            else -> BiliException("$message（$code）")
        }
    }

    private fun cookieFor(role: Identity?): String = role?.let { store.cookie(it) } ?: store.deviceCookie

    private suspend fun raw(path: String, query: Map<String, String> = emptyMap(), role: Identity? = null): JSONObject {
        // 首次启动往往在 Wi-Fi 就绪前发出请求，buvid 拿不到会导致后续接口 412，这里按需补取。
        if (store.buvid3.isBlank()) runCatching { ensureDevice() }
        val url = Http.apiUrl(path, query)
        if (BuildConfig.DEBUG) Log.d("BiliApi", "GET $url")
        val text = Http.get(url, cookieFor(role))
        return runCatching { JSONObject(text) }.getOrElse { throw BiliException("接口返回格式异常") }
    }

    private suspend fun api(
        path: String,
        query: Map<String, String> = emptyMap(),
        role: Identity? = null,
        allowGuest: Boolean = false,
    ): JSONObject {
        val root = raw(path, query, role)
        val code = root.int("code", -1)
        if (code != 0 && BuildConfig.DEBUG) Log.w("BiliApi", "code=$code path=$path")
        if (code != 0 && !(allowGuest && code == -101)) throw errorFor(root, code)
        return root.obj("data") ?: root.obj("result") ?: JSONObject()
    }

    private suspend fun apiArray(
        path: String,
        query: Map<String, String> = emptyMap(),
        role: Identity? = null,
    ): JSONArray {
        val root = raw(path, query, role)
        val code = root.int("code", -1)
        if (code != 0) throw errorFor(root, code)
        return root.optJSONArray("data") ?: JSONArray()
    }

    private suspend fun postApi(path: String, form: Map<String, String>, role: Identity = Identity.PERSONAL): JSONObject {
        val root = runCatching { JSONObject(Http.postForm("${Http.API}$path", form, cookieFor(role))) }
            .getOrElse { throw BiliException("接口返回格式异常") }
        val code = root.int("code", -1)
        if (code != 0) throw errorFor(root, code)
        return root.obj("data") ?: JSONObject()
    }

    // ---------- 设备与账号 ----------

    /** 首次启动取一次 buvid3 / buvid4，游客访问也更容易通过风控。 */
    suspend fun ensureDevice() {
        if (store.buvid3.isNotBlank()) return
        deviceMutex.withLock {
            if (store.buvid3.isNotBlank()) return@withLock
            val text = Http.get("${Http.API}/x/frontend/finger/spi", store.deviceCookie)
            val data = runCatching { JSONObject(text).obj("data") }.getOrNull() ?: return@withLock
            val b3 = data.str("b_3")
            val b4 = data.str("b_4")
            if (b3.isNotBlank()) store.buvid3 = b3
            if (b4.isNotBlank()) store.buvid4 = b4
            if (store.bNut == 0L) store.bNut = System.currentTimeMillis() / 1000
        }
    }

    suspend fun account(role: Identity): Account? {
        val cookie = when (role) {
            Identity.PERSONAL -> store.personalCookie
            Identity.MEMBER -> store.memberCookie
        }
        if (cookie.isBlank()) return null
        val data = api("/x/web-interface/nav", role = role)
        if (!data.bool("isLogin")) throw BiliException("Cookie 已失效，请重新登录或导入")
        val vip = data.obj("vip") ?: JSONObject()
        val active = data.int("vipStatus") == 1 || vip.int("status") == 1
        if (role == Identity.MEMBER && !active) throw BiliException("该账号没有有效的大会员权益")
        return Account(
            name = data.str("uname", "Bilibili 用户"),
            avatar = normalizeUrl(data.str("face")),
            isVip = active,
            mid = data.int("mid"),
            vipDue = vip.long("due_date") / 1000,
        )
    }

    suspend fun importCookie(
        input: String,
        role: Identity,
        dropMemberApi: Boolean = true,
    ): Account {
        val cookie = parseCookieInput(input)
        val previous = when (role) {
            Identity.PERSONAL -> store.personalCookie
            Identity.MEMBER -> store.memberCookie
        }
        when (role) {
            Identity.PERSONAL -> store.personalCookie = cookie
            Identity.MEMBER -> store.memberCookie = cookie
        }
        val account = try {
            account(role) ?: throw BiliException("Cookie 无效")
        } catch (e: Exception) {
            when (role) {
                Identity.PERSONAL -> store.personalCookie = previous
                Identity.MEMBER -> store.memberCookie = previous
            }
            throw e
        }
        // 手动粘贴会员 Cookie 后停止 API 自动更新，避免被下一次刷新覆盖。
        if (role == Identity.MEMBER && dropMemberApi) resetMemberApi()
        return account
    }

    fun logout(role: Identity) {
        when (role) {
            Identity.PERSONAL -> store.personalCookie = ""
            Identity.MEMBER -> {
                store.memberCookie = ""
                resetMemberApi()
            }
        }
    }

    // ---------- 会员 Cookie API ----------

    fun memberApiUrl(): String? = store.memberApiUrl.takeIf { it.isNotBlank() }

    fun memberRefreshedAt(): Long = store.memberRefreshedAt

    fun configureMemberApi(input: String): String {
        val url = MemberApi.validateUrl(input)
        store.memberApiUrl = url
        store.memberRefreshedAt = 0
        store.memberApiFailedAt = 0
        return url
    }

    fun resetMemberApi() {
        store.memberApiUrl = ""
        store.memberRefreshedAt = 0
        store.memberApiFailedAt = 0
    }

    /** 从已保存的 API 拉取 Cookie 并校验大会员权益；失败不会覆盖现有会员 Cookie。 */
    suspend fun refreshMemberFromApi(manual: Boolean = false): Account {
        val url = memberApiUrl() ?: throw BiliException("请先在设置中配置会员 Cookie API")
        return memberRefreshMutex.withLock {
            val now = System.currentTimeMillis()
            if (!manual && store.memberApiFailedAt > 0 && now - store.memberApiFailedAt < 10 * 60_000L) {
                throw BiliException("会员 API 最近刷新失败，请稍后再试")
            }
            try {
                val text = Http.getExternal(url)
                val cookie = try {
                    MemberApi.parseResponse(text)
                } catch (e: Exception) {
                    throw BiliException("会员 API 返回内容无效：${e.message}")
                }
                val account = try {
                    importCookie(cookie, Identity.MEMBER, dropMemberApi = false)
                } catch (e: Exception) {
                    throw BiliException("会员 API 返回的 Cookie 未通过校验：${e.message}")
                }
                store.memberRefreshedAt = System.currentTimeMillis()
                store.memberApiFailedAt = 0
                account
            } catch (e: Exception) {
                store.memberApiFailedAt = System.currentTimeMillis()
                throw e
            }
        }
    }

    /** 到期（6 小时）时自动刷新；没有配置 API 或未到期返回 null。 */
    suspend fun refreshMemberIfDue(): Account? {
        if (store.memberApiUrl.isBlank()) return null
        val due = store.memberCookie.isBlank() ||
            System.currentTimeMillis() - store.memberRefreshedAt >= 6 * 3_600_000L
        if (!due) return null
        return runCatching { refreshMemberFromApi() }.getOrNull()
    }

    // ---------- WBI ----------

    private suspend fun ensureWbiKey(): String {
        val now = System.currentTimeMillis()
        if (wbiKey.isNotBlank() && now - wbiAt < 3_600_000) return wbiKey
        val nav = api("/x/web-interface/nav", role = Identity.PERSONAL, allowGuest = true)
        val image = nav.obj("wbi_img") ?: throw BiliException("未能取得接口签名，请稍后重试")
        val img = image.str("img_url").substringAfterLast('/').substringBeforeLast('.')
        val sub = image.str("sub_url").substringAfterLast('/').substringBeforeLast('.')
        wbiKey = Wbi.mixinKey(img, sub)
        wbiAt = now
        return wbiKey
    }

    private suspend fun signed(params: Map<String, String>): Map<String, String> =
        Wbi.sign(params, ensureWbiKey())

    // ---------- 浏览 ----------

    suspend fun recommended(page: Int = 1): List<Video> {
        val data = api(
            "/x/web-interface/index/top/feed/rcmd",
            mapOf(
                "ps" to "20",
                "fresh_idx" to page.toString(),
                "fresh_idx_1h" to page.toString(),
            ),
            role = Identity.PERSONAL,
        )
        return data.arr("item").objects()
            .filter { it.str("goto") == "av" }
            .mapNotNull { parseVideo(it) }
    }

    suspend fun popular(page: Int = 1): List<Video> = api(
        "/x/web-interface/popular",
        mapOf("pn" to page.toString(), "ps" to "24"),
        role = Identity.PERSONAL,
    ).arr("list").objects().mapNotNull { parseVideo(it) }

    suspend fun ranking(): List<Video> = api(
        "/x/web-interface/ranking/v2",
        mapOf("rid" to "0", "type" to "all"),
        role = Identity.PERSONAL,
    ).arr("list").objects().mapNotNull { parseVideo(it) }

    suspend fun search(keyword: String, page: Int = 1, order: String = "totalrank"): List<Video> {
        val text = keyword.trim()
        if (text.isEmpty()) throw BiliException("请输入搜索关键词")
        if (text.length > 200) throw BiliException("搜索关键词过长")
        val params = signed(
            mapOf(
                "search_type" to "video",
                "keyword" to text,
                "page" to page.coerceAtLeast(1).toString(),
                "page_size" to "24",
                "order" to order,
                "platform" to "web",
                "web_location" to "1550101",
            )
        )
        val data = api("/x/web-interface/wbi/search/type", params, role = Identity.PERSONAL)
        return data.arr("result").objects().mapNotNull { item ->
            val bvid = item.str("bvid")
            if (bvid.isBlank()) null else Video(
                bvid = bvid,
                aid = item.long("aid"),
                title = cleanText(item.str("title", "视频")),
                cover = normalizeUrl(item.str("pic")),
                author = cleanText(item.str("author")),
                plays = item.int("play"),
                duration = videoDuration(item),
                description = item.str("description"),
                pubdate = item.int("pubdate").takeIf { it > 0 },
            )
        }
    }

    suspend fun hotSearches(): List<String> = runCatching {
        api(
            "/x/web-interface/search/square",
            mapOf("limit" to "20"),
            role = Identity.PERSONAL,
        ).obj("trending")?.arr("list")?.objects()?.map { it.str("keyword") }
            ?.filter { it.isNotBlank() } ?: emptyList()
    }.getOrDefault(emptyList())

    suspend fun resolve(input: String): Video {
        val text = input.trim()
        Regex("BV[0-9A-Za-z]{10}").find(text)?.let { return detail(Video(bvid = it.value, title = "载入中")) }
        Regex("ep[0-9]+").find(text)?.let { match ->
            val ep = match.value.drop(2).toIntOrNull()
            if (ep != null) return detail(Video(bvid = "", title = "载入中", episodeId = ep))
        }
        throw BiliException("请输入 BV 号、视频链接或 ep 番剧链接")
    }

    suspend fun detail(video: Video): Video {
        val episode = video.episodeId
        if (episode != null) {
            val data = api("/pgc/view/web/season", mapOf("ep_id" to episode.toString()), role = Identity.PERSONAL)
            val episodes = data.arr("episodes").objects()
            val target = episodes.firstOrNull { it.int("id") == episode } ?: episodes.firstOrNull()
                ?: throw BiliException("没有可播放的分集")
            return Video(
                bvid = target.str("bvid"),
                cid = target.long("cid"),
                title = cleanText(data.str("title", video.title)),
                cover = normalizeUrl(data.str("cover")),
                author = "番剧 · ${target.str("long_title")}",
                episodeId = target.int("id"),
                description = data.str("evaluate"),
                parts = episodes.mapNotNull { e ->
                    val cid = e.long("cid")
                    if (cid <= 0) null else VideoPart(
                        cid = cid,
                        title = "${e.str("title")} ${e.str("long_title")}".trim(),
                        episodeId = e.int("id").takeIf { it > 0 },
                    )
                },
            )
        }
        val data = api("/x/web-interface/view", mapOf("bvid" to video.bvid), role = Identity.PERSONAL)
        val parsed = parseVideo(data) ?: throw BiliException("视频不存在或暂不可见")
        return parsed.copy(cid = parsed.cid.takeIf { it > 0 } ?: video.cid)
    }

    suspend fun related(video: Video): List<Video> {
        if (video.bvid.isBlank()) return emptyList()
        return apiArray(
            "/x/web-interface/archive/related",
            mapOf("bvid" to video.bvid),
            role = Identity.PERSONAL,
        ).objects().mapNotNull { parseVideo(it) }
    }

    // ---------- 播放 ----------

    private fun parseTrack(json: JSONObject): MediaTrack? {
        val base = json.str("baseUrl").ifBlank { json.str("base_url") }
        val backups = json.arr("backupUrl").let { array ->
            if (array.length() == 0) json.arr("backup_url") else array
        }.let { array -> (0 until array.length()).map { array.optString(it) } }
        val urls = (listOf(base) + backups)
            .map { normalizeUrl(it) }
            .filter { it.startsWith("https://") && isMediaHost(it) }
            .distinct()
        if (urls.isEmpty()) return null
        return MediaTrack(
            quality = json.int("id"),
            codec = json.str("codecs"),
            bandwidth = json.int("bandwidth"),
            width = json.int("width"),
            height = json.int("height"),
            urls = urls,
        )
    }

    private fun isMediaHost(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return listOf("bilivideo.com", "bilivideo.cn", "biliapi.net", "akamaized.net", "szbdyd.com", "bstarstatic.com")
            .any { host == it || host.endsWith(".$it") }
    }

    private suspend fun fetchPlayback(
        video: Video,
        cid: Long,
        quality: Int,
        role: Identity,
    ): PlaybackSource {
        val params = mutableMapOf(
            "cid" to cid.toString(),
            "qn" to quality.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
        )
        val data: JSONObject
        if (video.episodeId != null) {
            params["ep_id"] = video.episodeId.toString()
            data = api("/pgc/player/web/v2/playurl", params, role = role)
        } else {
            params["bvid"] = video.bvid
            // Web 端已切到 wbi/playurl；未签名接口在部分视频上会返回 -404，保留回退。
            data = runCatching { api("/x/player/wbi/playurl", signed(params), role = role) }
                .getOrElse {
                    params["bvid"] = video.bvid
                    api("/x/player/playurl", params, role = role)
                }
        }
        val info = data.obj("video_info") ?: data.obj("result") ?: data
        val check = data.obj("play_check") ?: JSONObject()
        if (info.bool("is_preview") || data.str("play_video_type") == "preview" || check.str("play_detail") == "PLAY_PREVIEW") {
            throw BiliException("当前账号仅有试看权限")
        }
        val dash = info.obj("dash") ?: data.obj("dash") ?: JSONObject()
        val videos = dash.arr("video").objects().mapNotNull { parseTrack(it) }
            .filter {
                val codec = it.codec
                codec.startsWith("avc") || codec.startsWith("hev") || codec.startsWith("hvc") || codec.startsWith("dvh")
            }
        val audios = dash.arr("audio").objects().mapNotNull { parseTrack(it) }
        val audio = audios.filter { it.codec.startsWith("mp4a") }.maxByOrNull { it.bandwidth }
            ?: audios.maxByOrNull { it.bandwidth }
        if (videos.isEmpty() || audio == null) {
            throw BiliException("没有可用的视频源，可能受版权、地区或账号权限限制")
        }
        return PlaybackSource(
            videos = videos,
            audio = audio,
            usedMember = role == Identity.MEMBER,
            notice = null,
        )
    }

    suspend fun playback(
        video: Video,
        cid: Long,
        quality: Int = 127,
        preference: PlaybackPreference = store.preference,
    ): PlaybackSource {
        if (cid <= 0) throw BiliException("视频分集信息缺失")
        var notice: String? = null
        if (preference != PlaybackPreference.PERSONAL && store.memberApiUrl.isNotBlank()) {
            val due = store.memberCookie.isBlank() ||
                System.currentTimeMillis() - store.memberRefreshedAt >= 6 * 3_600_000L
            if (due) {
                try {
                    refreshMemberFromApi()
                } catch (e: Exception) {
                    notice = "会员源自动刷新失败：${e.message}"
                }
            }
        }
        if (preference != PlaybackPreference.PERSONAL && store.memberCookie.isNotBlank()) {
            try {
                val memberSource = fetchPlayback(video, cid, quality, Identity.MEMBER)
                return memberSource.copy(notice = notice)
            } catch (e: Exception) {
                if (preference == PlaybackPreference.MEMBER) {
                    throw BiliException("会员源不可用：${e.message}")
                }
                notice = "会员源暂不可用，已使用当前账号可用画质"
            }
        } else if (preference == PlaybackPreference.MEMBER) {
            throw BiliException(
                notice?.let { "$it，且没有可用的会员 Cookie" }
                    ?: "会员源尚未配置，请在设置中导入会员 Cookie 或配置 API"
            )
        }
        val source = fetchPlayback(video, cid, quality, Identity.PERSONAL)
        return source.copy(notice = notice)
    }

    // ---------- 弹幕 ----------

    suspend fun danmaku(cid: Long, segment: Int): List<Danmaku> {
        if (cid <= 0 || segment !in 1..1680) return emptyList()
        val query = signed(
            mapOf("type" to "1", "oid" to cid.toString(), "segment_index" to segment.toString())
        )
        val bytes = Http.getBytes(
            Http.apiUrl("/x/v2/dm/wbi/web/seg.so", query),
            store.cookie(Identity.PERSONAL),
        )
        return DanmakuParser.parse(bytes)
    }

    suspend fun danmakuForDuration(cid: Long, durationSeconds: Int): List<Danmaku> = coroutineScope {
        if (cid <= 0) return@coroutineScope emptyList()
        val segments = ((durationSeconds.coerceAtLeast(1) + 359) / 360).coerceIn(1, 12)
        val parts = (1..segments).map { index -> async { runCatching { danmaku(cid, index) }.getOrDefault(emptyList()) } }
        parts.flatMap { it.await() }.distinctBy { it.id }.sortedWith(compareBy({ it.time }, { it.id }))
    }

    suspend fun sendDanmaku(video: Video, cid: Long, progressMs: Long, mode: Int, color: Int, content: String): Danmaku {
        val text = content.trim()
        if (text.isEmpty()) throw BiliException("弹幕内容不能为空")
        if (text.length > 100) throw BiliException("弹幕最多 100 个字")
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        if (video.aid <= 0 && video.bvid.isBlank()) throw BiliException("视频信息缺失，暂不能发送弹幕")
        val query = signed(mapOf("csrf" to csrf, "web_location" to "1315873"))
        val url = Http.apiUrl("/x/v2/dm/post", query)
        val form = mutableMapOf(
            "type" to "1",
            "oid" to cid.toString(),
            "msg" to text,
            "progress" to progressMs.coerceAtLeast(0).toString(),
            "color" to (color and 0xFFFFFF).toString(),
            "fontsize" to "25",
            "mode" to mode.toString(),
            "pool" to "0",
            "rnd" to (System.currentTimeMillis() * 1000).toString(),
            "csrf" to csrf,
            "csrf_token" to csrf,
        )
        if (video.aid > 0) form["aid"] = video.aid.toString() else form["bvid"] = video.bvid
        val root = runCatching { JSONObject(Http.postForm(url, form, store.cookie(Identity.PERSONAL))) }
            .getOrElse { throw BiliException("弹幕发送响应异常") }
        val code = root.int("code", -1)
        if (code != 0) {
            when (code) {
                -352, -412, -509, 36703 -> throw BiliException("发送过于频繁，请稍后再试")
                36702, 10005, 36712 -> throw BiliException("弹幕长度超过限制")
                36701 -> throw BiliException("弹幕包含被禁止的内容")
                36711 -> throw BiliException("该视频禁止发送弹幕")
                -101, -111 -> throw BiliException("登录状态已失效，请重新登录")
                else -> throw errorFor(root, code)
            }
        }
        val data = root.obj("data") ?: JSONObject()
        val id = data.long("dmid").takeIf { it > 0 } ?: data.str("dmid_str").toLongOrNull() ?: 0L
        if (id <= 0) throw BiliException("弹幕已发送，但未取得编号")
        return Danmaku(id, progressMs / 1000.0, mode, color and 0xFFFFFF, text)
    }

    // ---------- 评论 ----------

    private fun parseComment(json: JSONObject, depth: Int): VideoComment? {
        val rpid = json.long("rpid").takeIf { it > 0 } ?: json.str("rpid_str").toLongOrNull() ?: return null
        val member = json.obj("member") ?: JSONObject()
        val content = json.obj("content") ?: JSONObject()
        val vip = member.obj("vip") ?: JSONObject()
        val level = (member.obj("level_info") ?: JSONObject()).int("current_level")
        val previews = if (depth == 0) {
            json.arr("replies").objects().take(3).mapNotNull { parseComment(it, 1) }
        } else emptyList()
        return VideoComment(
            id = rpid,
            name = member.str("uname", "用户"),
            avatar = normalizeUrl(member.str("avatar")),
            message = content.str("message"),
            likes = json.int("like"),
            created = json.long("ctime"),
            mid = member.long("mid"),
            level = level,
            isVip = vip.int("vipStatus") == 1,
            replyCount = json.int("rcount"),
            location = (json.obj("reply_control") ?: JSONObject()).str("location"),
            previews = previews,
            pictures = content.arr("pictures").objects().map { normalizeUrl(it.str("img_src")) }.filter { it.isNotBlank() },
        )
    }

    private suspend fun aidOf(video: Video): Long {
        if (video.aid > 0) return video.aid
        if (video.bvid.isBlank()) throw BiliException("视频信息缺失")
        val data = api("/x/web-interface/view", mapOf("bvid" to video.bvid), role = Identity.PERSONAL)
        return data.long("aid").takeIf { it > 0 } ?: throw BiliException("无法取得视频编号")
    }

    suspend fun comments(video: Video, page: Int = 1): List<VideoComment> {
        val aid = aidOf(video)
        val data = api(
            "/x/v2/reply",
            mapOf("type" to "1", "oid" to aid.toString(), "pn" to page.toString(), "ps" to "20", "sort" to "2"),
            role = Identity.PERSONAL,
        )
        return data.arr("replies").objects().mapNotNull { parseComment(it, 0) }
    }

    suspend fun commentReplies(video: Video, root: Long, page: Int = 1): List<VideoComment> {
        val aid = aidOf(video)
        val data = api(
            "/x/v2/reply/reply",
            mapOf("type" to "1", "oid" to aid.toString(), "root" to root.toString(), "pn" to page.toString(), "ps" to "20"),
            role = Identity.PERSONAL,
        )
        return data.arr("replies").objects().mapNotNull { parseComment(it, 1) }
    }

    suspend fun addComment(video: Video, message: String): VideoComment {
        val text = message.trim()
        if (text.isEmpty()) throw BiliException("评论内容不能为空")
        if (text.length > 1000) throw BiliException("评论最多 1000 字")
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        val aid = aidOf(video)
        val data = postApi(
            "/x/v2/reply/add",
            mapOf("oid" to aid.toString(), "type" to "1", "message" to text, "plat" to "1", "csrf" to csrf),
        )
        val reply = data.obj("reply")
        if (reply != null) parseComment(reply, 0)?.let { return it }
        val account = runCatching { account(Identity.PERSONAL) }.getOrNull()
        return VideoComment(
            id = data.long("rpid").takeIf { it > 0 } ?: System.currentTimeMillis(),
            name = account?.name ?: "我",
            avatar = account?.avatar.orEmpty(),
            message = text,
            likes = 0,
            created = System.currentTimeMillis() / 1000,
        )
    }

    suspend fun likeComment(video: Video, rpid: Long, like: Boolean) {
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        postApi(
            "/x/v2/reply/action",
            mapOf(
                "oid" to aidOf(video).toString(),
                "type" to "1",
                "rpid" to rpid.toString(),
                "action" to if (like) "1" else "0",
                "csrf" to csrf,
            ),
        )
    }

    // ---------- 视频互动 ----------

    suspend fun likeVideo(video: Video, like: Boolean) {
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        postApi(
            "/x/v2/view/like",
            mapOf("aid" to aidOf(video).toString(), "like" to if (like) "1" else "0", "csrf" to csrf),
        )
    }

    suspend fun coinVideo(video: Video, count: Int = 1) {
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        postApi(
            "/x/web-interface/coin/add",
            mapOf(
                "aid" to aidOf(video).toString(),
                "multiply" to count.coerceIn(1, 2).toString(),
                "select_like" to "0",
                "csrf" to csrf,
            ),
        )
    }

    suspend fun favoriteVideo(video: Video, folderId: Long, add: Boolean = true) {
        val csrf = store.csrf() ?: throw BiliException("请先登录你的个人 B 站账号")
        val form = mutableMapOf(
            "rid" to aidOf(video).toString(),
            "type" to "2",
            "csrf" to csrf,
        )
        form[if (add) "add_media_ids" else "del_media_ids"] = folderId.toString()
        postApi("/x/v3/fav/resource/deal", form)
    }

    // ---------- 动态 / 收藏 / 历史 ----------

    suspend fun dynamicFeed(offset: String? = null): DynamicPage {
        if (store.personalCookie.isBlank()) throw BiliException("请先登录你的个人 B 站账号")
        val query = mutableMapOf("type" to "all", "platform" to "web")
        if (!offset.isNullOrBlank()) query["offset"] = offset
        val data = api("/x/polymer/web-dynamic/v1/feed/all", query, role = Identity.PERSONAL)
        val items = data.arr("items").objects().mapNotNull { post ->
            val id = post.str("id_str")
            if (id.isBlank()) return@mapNotNull null
            val modules = post.obj("modules") ?: JSONObject()
            val author = modules.obj("module_author") ?: JSONObject()
            val content = modules.obj("module_dynamic") ?: JSONObject()
            val major = content.obj("major") ?: JSONObject()
            val archive = major.obj("archive")
            val video = archive?.let { parseVideo(it) }?.copy(
                author = author.str("name"),
                authorAvatar = normalizeUrl(author.str("face")),
            )
            val images = (major.obj("draw") ?: JSONObject()).arr("items").objects()
                .map { normalizeUrl(it.str("src")) }
                .filter { it.startsWith("https://") }
            val stat = modules.obj("module_stat") ?: JSONObject()
            val text = (content.obj("desc") ?: JSONObject()).str("text")
            DynamicPost(
                id = id,
                author = author.str("name", "动态"),
                avatar = normalizeUrl(author.str("face")),
                published = author.str("pub_time"),
                text = text,
                video = video,
                images = images,
                likes = (stat.obj("like") ?: JSONObject()).int("count"),
                comments = (stat.obj("comment") ?: JSONObject()).int("count"),
                forwards = (stat.obj("forward") ?: JSONObject()).int("count"),
            )
        }
        val next = if (data.bool("has_more")) data.str("offset").ifBlank { null } else null
        return DynamicPage(items, next)
    }

    suspend fun favoriteFolders(): List<FavoriteFolder> {
        val account = account(Identity.PERSONAL) ?: throw BiliException("请先登录你的个人 B 站账号")
        val data = api(
            "/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to account.mid.toString()),
            role = Identity.PERSONAL,
        )
        return data.arr("list").objects().mapNotNull { folder ->
            val id = folder.int("id")
            if (id <= 0) null else FavoriteFolder(id, folder.str("title", "收藏夹"), folder.int("media_count"))
        }
    }

    suspend fun favoriteVideos(folderId: Int, page: Int = 1): VideoPage {
        val data = api(
            "/x/v3/fav/resource/list",
            mapOf(
                "media_id" to folderId.toString(),
                "pn" to page.toString(),
                "ps" to "20",
                "order" to "mtime",
                "type" to "0",
                "platform" to "web",
            ),
            role = Identity.PERSONAL,
        )
        val videos = data.arr("medias").objects().mapNotNull { media ->
            if (media.int("type") != 2) return@mapNotNull null
            val bvid = media.str("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            val upper = media.obj("upper") ?: JSONObject()
            val count = media.obj("cnt_info") ?: JSONObject()
            Video(
                bvid = bvid,
                aid = media.long("id"),
                title = cleanText(media.str("title", "视频")),
                cover = normalizeUrl(media.str("cover")),
                author = upper.str("name"),
                plays = count.int("play"),
                duration = media.int("duration"),
            )
        }
        return VideoPage(videos, data.bool("has_more"))
    }

    suspend fun cloudHistory(cursor: String? = null): VideoPage {
        if (store.personalCookie.isBlank()) throw BiliException("请先登录你的个人 B 站账号")
        val pieces = cursor?.split('|') ?: emptyList()
        val query = mutableMapOf(
            "max" to (pieces.getOrNull(0)?.ifBlank { "0" } ?: "0"),
            "view_at" to (pieces.getOrNull(1)?.ifBlank { "0" } ?: "0"),
            "business" to (pieces.getOrNull(2) ?: ""),
        )
        val data = api("/x/web-interface/history/cursor", query, role = Identity.PERSONAL)
        val videos = data.arr("list").objects().mapNotNull { item ->
            val history = item.obj("history") ?: JSONObject()
            val bvid = history.str("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            val viewAt = item.long("view_at")
            Video(
                bvid = bvid,
                aid = history.long("oid"),
                cid = history.long("cid"),
                title = cleanText(item.str("title", "视频")),
                cover = normalizeUrl(item.str("cover")),
                author = item.str("author_name"),
                duration = item.int("duration"),
                pubdate = viewAt.toInt(),
                pubTimeText = relativeTime(viewAt),
            )
        }
        val info = data.obj("cursor") ?: JSONObject()
        val next = listOf(info.str("max", "0"), info.str("view_at", "0"), info.str("business"))
            .joinToString("|")
        return VideoPage(
            videos = videos,
            hasMore = !info.bool("is_end") && videos.isNotEmpty(),
            cursor = next,
        )
    }

    suspend fun watchLater(): List<Video> = api(
        "/x/v2/history/toview",
        role = Identity.PERSONAL,
    ).arr("list").objects().mapNotNull { parseVideo(it) }

    suspend fun following(page: Int = 1): List<FollowedAccount> {
        val account = account(Identity.PERSONAL) ?: throw BiliException("请先登录你的个人 B 站账号")
        val data = api(
            "/x/relation/followings",
            mapOf("vmid" to account.mid.toString(), "pn" to page.toString(), "ps" to "50", "order" to "desc"),
            role = Identity.PERSONAL,
        )
        return data.arr("list").objects().mapNotNull { item ->
            val mid = item.int("mid")
            if (mid <= 0) null else FollowedAccount(
                mid = mid,
                name = item.str("uname", "UP 主"),
                avatar = normalizeUrl(item.str("face")),
                sign = item.str("sign"),
            )
        }
    }

    suspend fun uploaderVideos(mid: Int, page: Int = 1): VideoPage {
        val query = signed(
            mapOf(
                "mid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to "30",
                "order" to "pubdate",
                "platform" to "web",
                "web_location" to "1550101",
            )
        )
        val data = api("/x/space/wbi/arc/search", query, role = Identity.PERSONAL)
        val list = data.obj("list") ?: JSONObject()
        val videos = list.arr("vlist").objects().mapNotNull { parseVideo(it) }
        val count = (data.obj("page") ?: JSONObject()).int("count")
        return VideoPage(videos, hasMore = page * 30 < count)
    }

    // ---------- 扫码登录 ----------

    suspend fun qrGenerate(): QRLogin {
        val text = Http.get(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header",
            store.deviceCookie,
        )
        val root = runCatching { JSONObject(text) }.getOrElse { throw BiliException("无法生成登录二维码") }
        val data = root.obj("data") ?: JSONObject()
        val url = data.str("url")
        val key = data.str("qrcode_key")
        if (root.int("code") != 0 || url.isBlank() || key.isBlank()) throw BiliException("无法生成登录二维码")
        return QRLogin(url, key)
    }

    suspend fun qrPoll(key: String): QRPoll {
        val response = Http.getResponse(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key",
            store.deviceCookie,
        )
        val root = runCatching { JSONObject(response.body) }
            .getOrElse { throw BiliException("登录验证请求失败") }
        if (root.int("code") != 0) throw BiliException("登录验证请求失败")
        val data = root.obj("data") ?: JSONObject()
        val state = data.int("code")
        if (BuildConfig.DEBUG) Log.d("BiliLogin", "poll state=$state hasUrl=${data.str("url").isNotBlank()}")
        return when (state) {
            86101 -> QRPoll.Waiting
            86090 -> QRPoll.Confirmation
            86038 -> QRPoll.Expired
            0 -> {
                val names = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
                val values = LinkedHashMap<String, String>()
                // 优先使用回调 URL 中的参数；部分账号/环境下 url 为空，需要退回 Set-Cookie。
                val query = data.str("url").substringAfter('?', "")
                query.split("&").forEach { pair ->
                    val index = pair.indexOf('=')
                    if (index > 0) {
                        val name = pair.substring(0, index)
                        val value = pair.substring(index + 1)
                        if (name in names && value.isNotBlank()) values[name] = value
                    }
                }
                response.headers.entries
                    .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                    .flatMap { it.value }
                    .forEach { header ->
                        val pair = header.substringBefore(';').trim()
                        val name = pair.substringBefore('=').trim()
                        val value = pair.substringAfter('=', "").trim()
                        if (name in names && value.isNotBlank() && values[name].isNullOrBlank()) {
                            values[name] = value
                        }
                    }
                if (values["SESSDATA"].isNullOrBlank()) {
                    if (BuildConfig.DEBUG) Log.w("BiliLogin", "poll success without SESSDATA, names=${values.keys}")
                    throw BiliException("登录成功但未取得 Cookie，请重试或改用网页登录")
                }
                val cookie = names.mapNotNull { name -> values[name]?.let { "$name=$it" } }.joinToString("; ")
                QRPoll.Success(importCookie(cookie, Identity.PERSONAL))
            }
            else -> throw BiliException("登录状态异常，请刷新二维码")
        }
    }
}
