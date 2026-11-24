package local.bili23.android.ui.player

import android.util.Log
import local.bili23.android.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.BiliException
import local.bili23.android.core.Danmaku
import local.bili23.android.core.FavoriteFolder
import local.bili23.android.core.PlaybackSource
import local.bili23.android.core.Video
import local.bili23.android.core.VideoComment
import local.bili23.android.core.qualityName

class PlayerViewModel(
    private val routeId: String,
    private val initialCid: Long,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val video: Video? = null,
        val source: PlaybackSource? = null,
        val selectedQuality: Int = 0,
        val notice: String? = null,
        val related: List<Video> = emptyList(),
        val comments: List<VideoComment> = emptyList(),
        val commentsLoading: Boolean = false,
        val commentsEnd: Boolean = false,
        val commentsError: String? = null,
        val commentPage: Int = 1,
        val commentNextOffset: String? = null,
        val commentsUseWbi: Boolean = false,
        val danmaku: List<Danmaku> = emptyList(),
        val danmakuOn: Boolean = true,
        val liked: Boolean = false,
        val coined: Boolean = false,
        val favorited: Boolean = false,
        val replies: Map<Long, List<VideoComment>> = emptyMap(),
        val replyLoading: Set<Long> = emptySet(),
        val folders: List<FavoriteFolder>? = null,
        val toast: String? = null,
    )

    private val base = parseRouteId(routeId)
    private val _state = MutableStateFlow(UiState(danmakuOn = BiliApp.store.danmakuEnabled))
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                if (initialCid > 0) {
                    coroutineScope {
                        val detailDeferred = async { runCatching { BiliApp.client.detail(base) }.getOrNull() }
                        val playbackDeferred = async {
                            BiliApp.client.playback(
                                video = base.copy(cid = initialCid),
                                cid = initialCid,
                                quality = 127,
                                preference = BiliApp.store.preference,
                            )
                        }
                        val source = playbackDeferred.await()
                        val quality = source.video(BiliApp.store.quality)?.quality
                            ?: source.qualities.firstOrNull() ?: 0
                        _state.update {
                            it.copy(
                                loading = false,
                                video = it.video ?: base.copy(cid = initialCid),
                                source = source,
                                selectedQuality = quality,
                                notice = source.notice,
                            )
                        }
                        val detail = detailDeferred.await()
                        val video = (detail ?: base).copy(cid = initialCid)
                        if (BuildConfig.DEBUG) Log.d("BiliPlayer", "route=$routeId cid=$initialCid parts=${video.parts.size}")
                        _state.update { it.copy(video = video) }
                        loadRelated(video)
                        loadComments(reset = true)
                        loadDanmaku(initialCid, video.duration)
                    }
                } else {
                    val detail = BiliApp.client.detail(base)
                    if (BuildConfig.DEBUG) Log.d("BiliPlayer", "route=$routeId cid=${detail.cid} parts=${detail.parts.size}")
                    val cid = detail.cid
                    if (cid <= 0) throw BiliException("视频分集信息缺失")
                    val video = detail.copy(cid = cid)
                    _state.update { it.copy(video = video) }
                    loadRelated(video)
                    loadComments(reset = true)
                    loadDanmaku(cid, video.duration)

                    val source = BiliApp.client.playback(
                        video = video,
                        cid = cid,
                        quality = 127,
                        preference = BiliApp.store.preference,
                    )
                    val quality = source.video(BiliApp.store.quality)?.quality
                        ?: source.qualities.firstOrNull() ?: 0
                    _state.update {
                        it.copy(
                            loading = false,
                            source = source,
                            selectedQuality = quality,
                            notice = source.notice,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "加载失败",
                    )
                }
            }
        }
    }

    private fun loadRelated(video: Video) {
        viewModelScope.launch {
            runCatching { BiliApp.client.related(video) }
                .onSuccess { list -> _state.value = _state.value.copy(related = list) }
        }
    }

    private fun loadDanmaku(cid: Long, duration: Int) {
        viewModelScope.launch {
            runCatching { BiliApp.client.danmakuForDuration(cid, duration) }
                .onSuccess { list -> _state.value = _state.value.copy(danmaku = list) }
        }
    }

    fun retryComments() = loadComments(reset = true)

    fun loadComments(reset: Boolean = false) {
        val video = _state.value.video ?: return
        val current = _state.value
        if (current.commentsLoading) return
        if (!reset && current.commentsEnd) return
        if (video.aid <= 0 && video.bvid.isBlank()) return
        val page = if (reset) 1 else current.commentPage + 1
        val nextOffset = if (reset) null else current.commentNextOffset
        val useWbi = if (reset) false else current.commentsUseWbi
        _state.update {
            it.copy(
                commentsLoading = true,
                commentsError = null,
                commentNextOffset = if (reset) null else it.commentNextOffset,
                commentsUseWbi = if (reset) false else it.commentsUseWbi,
            )
        }
        viewModelScope.launch {
            try {
                val result = BiliApp.client.comments(
                    video = video,
                    page = page,
                    nextOffset = nextOffset,
                    useWbi = useWbi,
                )
                _state.update {
                    it.copy(
                        comments = if (reset) result.items else (it.comments + result.items).distinctBy { item -> item.id },
                        commentsLoading = false,
                        commentsEnd = result.isEnd,
                        commentsError = null,
                        commentPage = page,
                        commentNextOffset = result.nextOffset,
                        commentsUseWbi = result.usedWbi,
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: "评论加载失败"
                _state.update {
                    it.copy(
                        commentsLoading = false,
                        commentsError = if (reset || it.comments.isEmpty()) msg else it.commentsError,
                    )
                }
                if (!reset && _state.value.comments.isNotEmpty()) toast(msg)
            }
        }
    }

    fun loadReplies(root: Long) {
        val video = _state.value.video ?: return
        if (_state.value.replyLoading.contains(root)) return
        _state.value = _state.value.copy(replyLoading = _state.value.replyLoading + root)
        viewModelScope.launch {
            try {
                val list = BiliApp.client.commentReplies(video, root)
                _state.value = _state.value.copy(
                    replies = _state.value.replies + (root to list),
                    replyLoading = _state.value.replyLoading - root,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(replyLoading = _state.value.replyLoading - root)
                toast(e.message ?: "回复加载失败")
            }
        }
    }

    fun selectPart(index: Int) {
        val video = _state.value.video ?: return
        val part = video.parts.getOrNull(index) ?: return
        if (part.cid == video.cid) return
        val updated = video.copy(cid = part.cid, episodeId = part.episodeId ?: video.episodeId)
        _state.value = _state.value.copy(video = updated, loading = true, error = null, danmaku = emptyList())
        viewModelScope.launch {
            try {
                val source = BiliApp.client.playback(
                    video = updated,
                    cid = part.cid,
                    quality = 127,
                    preference = BiliApp.store.preference,
                )
                val quality = source.video(BiliApp.store.quality)?.quality
                    ?: source.qualities.firstOrNull() ?: 0
                _state.value = _state.value.copy(
                    loading = false,
                    source = source,
                    selectedQuality = quality,
                    notice = source.notice,
                )
                loadDanmaku(part.cid, updated.duration)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "切换分集失败")
            }
        }
    }

    fun selectQuality(quality: Int) {
        BiliApp.store.quality = quality
        _state.value = _state.value.copy(selectedQuality = quality)
    }

    fun toggleDanmaku() {
        val next = !_state.value.danmakuOn
        BiliApp.store.danmakuEnabled = next
        _state.value = _state.value.copy(danmakuOn = next)
    }

    fun sendDanmaku(content: String, mode: Int, color: Int, progressMs: Long) {
        val video = _state.value.video ?: return
        viewModelScope.launch {
            try {
                val item = BiliApp.client.sendDanmaku(
                    video = video,
                    cid = video.cid,
                    progressMs = progressMs,
                    mode = mode,
                    color = color,
                    content = content,
                )
                _state.value = _state.value.copy(
                    danmaku = (_state.value.danmaku + item).sortedBy { it.time },
                    toast = "弹幕已发送",
                )
            } catch (e: Exception) {
                toast(e.message ?: "弹幕发送失败")
            }
        }
    }

    fun toggleLike() = requireLogin {
        viewModelScope.launch {
            val target = !_state.value.liked
            try {
                val video = _state.value.video ?: return@launch
                BiliApp.client.likeVideo(video, target)
                _state.value = _state.value.copy(liked = target, toast = if (target) "已点赞" else "已取消点赞")
            } catch (e: Exception) {
                toast(e.message ?: "操作失败")
            }
        }
    }

    fun coin() = requireLogin {
        viewModelScope.launch {
            try {
                val video = _state.value.video ?: return@launch
                BiliApp.client.coinVideo(video, 1)
                _state.value = _state.value.copy(coined = true, toast = "已投币")
            } catch (e: Exception) {
                toast(e.message ?: "投币失败")
            }
        }
    }

    fun openFavoritePicker() = requireLogin {
        viewModelScope.launch {
            try {
                val folders = BiliApp.client.favoriteFolders()
                _state.value = _state.value.copy(folders = folders)
            } catch (e: Exception) {
                toast(e.message ?: "收藏夹加载失败")
            }
        }
    }

    fun closeFavoritePicker() {
        _state.value = _state.value.copy(folders = null)
    }

    fun favoriteTo(folderId: Long) {
        viewModelScope.launch {
            try {
                val video = _state.value.video ?: return@launch
                BiliApp.client.favoriteVideo(video, folderId, add = true)
                _state.value = _state.value.copy(favorited = true, folders = null, toast = "已收藏")
            } catch (e: Exception) {
                toast(e.message ?: "收藏失败")
            }
        }
    }

    fun addComment(text: String) = requireLogin {
        viewModelScope.launch {
            try {
                val video = _state.value.video ?: return@launch
                val comment = BiliApp.client.addComment(video, text)
                _state.value = _state.value.copy(
                    comments = listOf(comment) + _state.value.comments,
                    toast = "评论已发布",
                )
            } catch (e: Exception) {
                toast(e.message ?: "评论失败")
            }
        }
    }

    fun likeComment(comment: VideoComment) {
        val video = _state.value.video ?: return
        viewModelScope.launch {
            runCatching { BiliApp.client.likeComment(video, comment.id, true) }
                .onFailure { toast(it.message ?: "点赞失败") }
                .onSuccess { toast("已点赞") }
        }
    }

    fun rememberPosition(cid: Long, positionMs: Long) {
        val video = _state.value.video ?: return
        if (positionMs < 3000) return
        BiliApp.store.remember(video.copy(cid = cid), cid, positionMs)
    }

    fun toast(message: String) {
        _state.value = _state.value.copy(toast = message)
    }

    fun consumeToast() {
        _state.value = _state.value.copy(toast = null)
    }

    fun qualityLabel(quality: Int): String = qualityName(quality)

    private fun requireLogin(block: () -> Unit) {
        if (BiliApp.store.personalCookie.isBlank()) {
            toast("请先到「我的」登录 B 站账号")
            return
        }
        block()
    }

    companion object {
        fun parseRouteId(id: String): Video {
            if (id.startsWith("ep")) {
                id.drop(2).toIntOrNull()?.let { return Video(bvid = "", title = "", episodeId = it) }
            }
            return Video(bvid = id, title = "")
        }
    }
}
