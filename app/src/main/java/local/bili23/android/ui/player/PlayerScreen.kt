package local.bili23.android.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import local.bili23.android.BiliApp
import local.bili23.android.core.Danmaku
import local.bili23.android.core.Http
import local.bili23.android.core.MediaTrack
import local.bili23.android.core.Video
import local.bili23.android.core.VideoComment
import local.bili23.android.core.countLabel
import local.bili23.android.core.qualityName
import local.bili23.android.core.relativeTime
import local.bili23.android.core.timeLabel
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LevelBadge
import local.bili23.android.ui.common.VideoRow
import local.bili23.android.ui.theme.AppColors

@UnstableApi
@Composable
fun PlayerScreen(
    videoId: String,
    initialCid: Long,
    onBack: () -> Unit,
    onOpen: (Video) -> Unit,
    onOpenSpace: (Int, String) -> Unit,
) {
    val vm: PlayerViewModel = viewModel(key = "player-$videoId-$initialCid") {
        PlayerViewModel(videoId, initialCid)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AppColors.current

    Box(Modifier.fillMaxSize()) {

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
    }
    DisposableEffect(exo) {
        onDispose { exo.release() }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var lastCid by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(exo) {
        while (true) {
            positionMs = exo.currentPosition.coerceAtLeast(0L)
            val exoDuration = exo.duration
            durationMs = if (exoDuration != C.TIME_UNSET && exoDuration > 0) exoDuration else 0L
            playing = exo.isPlaying
            buffering = exo.playbackState == Player.STATE_BUFFERING || exo.playbackState == Player.STATE_IDLE
            delay(200)
        }
    }

    val source = state.source
    val video = state.video
    LaunchedEffect(source, state.selectedQuality) {
        val s = source ?: return@LaunchedEffect
        val v = video ?: return@LaunchedEffect
        val track = s.video(state.selectedQuality) ?: s.videos.firstOrNull() ?: return@LaunchedEffect
        val keep = lastCid == v.cid
        val resume = if (keep) exo.currentPosition.coerceAtLeast(0L) else 0L
        lastCid = v.cid
        exo.setMediaSource(buildMediaSource(context, track, s.audio))
        exo.prepare()
        if (resume > 0) exo.seekTo(resume)
        exo.setPlaybackSpeed(BiliApp.store.speed)
        exo.playWhenReady = true
    }

    var fullscreen by remember { mutableStateOf(false) }
    val activity = context.findActivity()

    DisposableEffect(fullscreen) {
        activity?.applyFullscreen(fullscreen)
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.rememberPosition(video?.cid ?: 0L, exo.currentPosition)
            activity?.applyFullscreen(false)
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen) {
        vm.rememberPosition(video?.cid ?: 0L, exo.currentPosition)
        onBack()
    }

    var tab by remember { mutableIntStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    var showSendDanmaku by remember { mutableStateOf(false) }
    var showParts by remember { mutableStateOf(false) }

    val stageModifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    val stage: @Composable () -> Unit = {
        PlayerStage(
            exo = exo,
            video = video,
            danmaku = state.danmaku,
            danmakuOn = state.danmakuOn,
            positionMs = positionMs,
            durationMs = durationMs.takeIf { it > 0 } ?: ((video?.duration ?: 0) * 1000L),
            playing = playing,
            buffering = buffering,
            fullscreen = fullscreen,
            qualityText = shortQuality(state.selectedQuality),
            qualities = source?.qualities ?: emptyList(),
            speed = BiliApp.store.speed,
            modifier = stageModifier,
            onBack = {
                if (fullscreen) fullscreen = false else {
                    vm.rememberPosition(video?.cid ?: 0L, exo.currentPosition)
                    onBack()
                }
            },
            onToggleFullscreen = { fullscreen = !fullscreen },
            onTogglePlay = { if (exo.isPlaying) exo.pause() else exo.play() },
            onSeek = { exo.seekTo(it) },
            onQuality = vm::selectQuality,
            onSpeed = {
                BiliApp.store.speed = it
                exo.setPlaybackSpeed(it)
            },
            onDanmaku = vm::toggleDanmaku,
            onSendDanmaku = { showSendDanmaku = true },
        )
    }

    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { stage() }
    } else {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            stage()
            if (state.error != null) {
                ErrorBox(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.padding(top = 24.dp),
                    onRetry = vm::load,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    item {
                        VideoHeader(
                            video = video,
                            notice = state.notice,
                            liked = state.liked,
                            coined = state.coined,
                            favorited = state.favorited,
                            onLike = vm::toggleLike,
                            onCoin = vm::coin,
                            onFavorite = vm::openFavoritePicker,
                            onShare = {
                                val url = video?.webUrl ?: "https://www.bilibili.com"
                                val ctx = context
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                                    }.let { android.content.Intent.createChooser(it, "分享视频") }
                                )
                            },
                        )
                    }
                    item {
                        UploaderRow(
                            video = video,
                            onOpenSpace = {
                                val mid = video?.authorId ?: 0
                                if (mid > 0) onOpenSpace(mid, video?.author.orEmpty())
                                else vm.toast("未取得 UP 主主页信息")
                            },
                        )
                    }
                    item {
                        TabRow(
                            selectedTabIndex = tab,
                            containerColor = colors.background,
                            contentColor = colors.pink,
                            divider = {},
                        ) {
                            listOf("简介", "评论 " + countLabel(video?.replyCount ?: 0)).forEachIndexed { index, title ->
                                Tab(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    text = {
                                        Text(
                                            title,
                                            fontSize = 13.sp,
                                            fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tab == index) colors.pink else colors.text2,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (tab == 0) {
                        item {
                            Text(
                                text = video?.description?.ifBlank { "这个视频还没有简介" } ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text2,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        if ((video?.parts?.size ?: 0) > 1) {
                            item {
                                Column(Modifier.padding(horizontal = 14.dp)) {
                                    Text(
                                        "选集（${video?.parts?.size}）",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.text1,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                    val parts = video?.parts.orEmpty()
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(parts.take(20)) { index, part ->
                                            val current = part.cid == video?.cid
                                            Text(
                                                text = "P${index + 1}",
                                                fontSize = 12.sp,
                                                color = if (current) Color.White else colors.text2,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(if (current) colors.pink else colors.fill)
                                                    .clickable { vm.selectPart(index) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
                                        if (parts.size > 20) {
                                            item {
                                                Text(
                                                    text = "全部 ${parts.size} 集",
                                                    fontSize = 12.sp,
                                                    color = colors.pink,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(colors.pinkSoft)
                                                        .clickable { showParts = true }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                    val currentPart = video?.parts?.indexOfFirst { it.cid == video?.cid } ?: -1
                                    if (currentPart >= 0) {
                                        Text(
                                            text = "正在播放：${parts[currentPart].title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.text3,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (state.related.isNotEmpty()) {
                            item {
                                Text(
                                    "相关推荐",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.text1,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                            items(state.related, key = { it.routeId }) { item ->
                                VideoRow(video = item, onClick = { onOpen(item) })
                            }
                        }
                    } else {
                        item {
                            CommentComposer(
                                value = commentText,
                                onValueChange = { commentText = it },
                                onSend = {
                                    if (commentText.isNotBlank()) {
                                        vm.addComment(commentText)
                                        commentText = ""
                                    }
                                },
                                loggedIn = BiliApp.store.personalCookie.isNotBlank(),
                                onNeedLogin = { vm.toast("请先到「我的」登录 B 站账号") },
                            )
                        }
                        if (state.comments.isEmpty() && !state.commentsLoading) {
                            item {
                                Text(
                                    text = "还没有评论，来抢沙发吧",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text3,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        items(state.comments, key = { it.id }) { comment ->
                            CommentItem(
                                comment = comment,
                                replies = state.replies[comment.id],
                                loadingReplies = state.replyLoading.contains(comment.id),
                                onLike = { vm.likeComment(comment) },
                                onLoadReplies = { vm.loadReplies(comment.id) },
                            )
                        }
                        item {
                            if (state.commentsLoading) {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.pink,
                                    )
                                }
                            } else if (!state.commentsEnd) {
                                Text(
                                    text = "加载更多评论",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.pink,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.loadComments() }
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.toast?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2200)
            vm.consumeToast()
        }
        Box(
            Modifier.fillMaxSize().padding(bottom = 96.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    if (showSendDanmaku) {
        SendDanmakuDialog(
            positionMs = positionMs,
            onDismiss = { showSendDanmaku = false },
            onSend = { text, mode ->
                vm.sendDanmaku(text, mode, 0xFFFFFF, positionMs)
                showSendDanmaku = false
            },
        )
    }

    if (showParts) {
        val parts = video?.parts.orEmpty()
        AlertDialog(
            onDismissRequest = { showParts = false },
            title = { Text("选集（${parts.size}）") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(parts) { index, part ->
                        val current = part.cid == video?.cid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (current) colors.pinkSoft else Color.Transparent)
                                .clickable {
                                    vm.selectPart(index)
                                    showParts = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "P${index + 1}",
                                fontSize = 11.sp,
                                color = if (current) colors.pink else colors.text3,
                                modifier = Modifier.width(36.dp),
                            )
                            Text(
                                text = part.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (current) colors.pink else colors.text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParts = false }) { Text("关闭") }
            },
        )
    }

    state.folders?.let { folders ->
        AlertDialog(
            onDismissRequest = vm::closeFavoritePicker,
            title = { Text("选择收藏夹") },
            text = {
                if (folders.isEmpty()) {
                    Text("还没有创建收藏夹，请先在 B 站创建")
                } else {
                    Column {
                        folders.forEach { folder ->
                            Text(
                                text = "${folder.title}（${folder.count}）",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.favoriteTo(folder.id.toLong()) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::closeFavoritePicker) { Text("取消") }
            },
        )
    }
    }
}

// ---------- 播放器 ----------

@UnstableApi
@Composable
private fun PlayerStage(
    exo: ExoPlayer,
    video: Video?,
    danmaku: List<Danmaku>,
    danmakuOn: Boolean,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    buffering: Boolean,
    fullscreen: Boolean,
    qualityText: String,
    qualities: List<Int>,
    speed: Float,
    modifier: Modifier,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onQuality: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onDanmaku: () -> Unit,
    onSendDanmaku: () -> Unit,
) {
    val store = BiliApp.store
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(3200)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { onTogglePlay() },
                )
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragAccum += amount
                    },
                    onDragEnd = {
                        if (durationMs > 0 && size.width > 0) {
                            val target = positionMs + (dragAccum / size.width) * durationMs
                            onSeek(target.toLong().coerceIn(0L, durationMs))
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exo
                }
            },
            update = { it.player = exo },
            modifier = Modifier.fillMaxSize(),
        )

        DanmakuOverlay(
            items = danmaku,
            enabled = danmakuOn,
            opacity = store.danmakuOpacity,
            scale = store.danmakuScale,
            area = store.danmakuArea,
            positionProvider = { exo.currentPosition },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && !scrubbing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 2.dp,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Text(
                        text = video?.title.orEmpty(),
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(54.dp)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape),
                ) {
                    PlayPauseIcon(playing = playing)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val total = durationMs.coerceAtLeast(1L)
                    val fraction = if (scrubbing) scrubValue else (positionMs.toFloat() / total).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeLabel(((if (scrubbing) scrubValue * total else positionMs.toFloat()) / 1000).toInt()),
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                        Slider(
                            value = fraction,
                            onValueChange = {
                                scrubbing = true
                                scrubValue = it
                            },
                            onValueChangeFinished = {
                                onSeek((scrubValue * total).toLong())
                                scrubbing = false
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = AppColors.current.pink,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Text(
                            text = timeLabel((total / 1000).toInt()),
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ControlText("弹幕", active = danmakuOn, onClick = onDanmaku)
                        ControlText("发弹幕", active = false, onClick = onSendDanmaku)

                        Spacer(Modifier.weight(1f))
                        if (qualities.size > 1) {
                            Box {
                                var open by remember { mutableStateOf(false) }
                                ControlText(qualityText, active = true, onClick = { open = true })
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    qualities.forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(qualityName(quality), fontSize = 13.sp) },
                                            onClick = {
                                                open = false
                                                onQuality(quality)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Box {
                            var open by remember { mutableStateOf(false) }
                            ControlText("${speed}x", active = true, onClick = { open = true })
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text("${value}x", fontSize = 13.sp) },
                                        onClick = {
                                            open = false
                                            onSpeed(value)
                                        },
                                    )
                                }
                            }
                        }
                        ControlText(
                            if (fullscreen) "退出" else "全屏",
                            active = true,
                            onClick = onToggleFullscreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlText(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlayPauseIcon(playing: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        if (playing) {
            val barWidth = size.width * 0.22f
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.14f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.14f),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
        } else {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.15f)
                lineTo(size.width * 0.82f, size.height * 0.5f)
                lineTo(size.width * 0.3f, size.height * 0.85f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

// ---------- 详情内容 ----------

@Composable
private fun VideoHeader(
    video: Video?,
    notice: String?,
    liked: Boolean,
    coined: Boolean,
    favorited: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = AppColors.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
            text = video?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = colors.text1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append("${countLabel(video?.plays ?: 0)}播放")
                video?.danmakuCount?.let { append(" · ${countLabel(it)}弹幕") }
                video?.pubdate?.takeIf { it > 0 }?.let { append(" · ${relativeTime(it.toLong())}") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.text3,
        )
        if (!notice.isNullOrBlank()) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = colors.orange,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ActionItem(
                icon = { Icon(Icons.Filled.ThumbUp, null, tint = if (liked) colors.pink else colors.text1) },
                label = countLabel(video?.likeCount ?: 0),
                active = liked,
                onClick = onLike,
            )
            ActionItem(
                icon = { Text("币", color = if (coined) colors.pink else colors.text1, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                label = countLabel(video?.coinCount ?: 0),
                active = coined,
                onClick = onCoin,
            )
            ActionItem(
                icon = { Icon(Icons.Filled.Star, null, tint = if (favorited) colors.pink else colors.text1) },
                label = countLabel(video?.favoriteCount ?: 0),
                active = favorited,
                onClick = onFavorite,
            )
            ActionItem(
                icon = { Icon(Icons.Filled.Share, null, tint = colors.text1) },
                label = countLabel(video?.shareCount ?: 0),
                active = false,
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: @Composable () -> Unit,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (active) colors.pink else colors.text3,
        )
    }
}

@Composable
private fun UploaderRow(video: Video?, onOpenSpace: () -> Unit) {
    val colors = AppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSpace)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video?.authorAvatar,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video?.author.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.text1,
            )
            Text(
                text = "UP 主",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
            )
        }
        Text(
            text = "主页",
            fontSize = 12.sp,
            color = colors.pink,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.pinkSoft)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    loggedIn: Boolean,
    onNeedLogin: () -> Unit,
) {
    val colors = AppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    if (loggedIn) "发一条友善的评论" else "登录后可发表评论",
                    fontSize = 12.sp,
                    color = colors.text3,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { if (loggedIn) onSend() else onNeedLogin() },
            modifier = Modifier.background(colors.pink, CircleShape).size(38.dp),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CommentItem(
    comment: VideoComment,
    replies: List<VideoComment>?,
    loadingReplies: Boolean,
    onLike: () -> Unit,
    onLoadReplies: () -> Unit,
) {
    val colors = AppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        AsyncImage(
            model = comment.avatar,
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                if (comment.level > 0) LevelBadge(comment.level)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = relativeTime(comment.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = comment.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text1,
                lineHeight = 20.sp,
            )
            if (comment.pictures.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    comment.pictures.take(3).forEach { picture ->
                        AsyncImage(
                            model = picture,
                            contentDescription = null,
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onLike),
                ) {
                    Icon(
                        Icons.Filled.ThumbUp,
                        contentDescription = "点赞",
                        tint = colors.text3,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (comment.likes > 0) countLabel(comment.likes) else "赞",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                    )
                }
            }
            if (comment.previews.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.fill, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.previews.forEach { preview ->
                        Text(
                            text = "${preview.name}：${preview.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (comment.replyCount > comment.previews.size) {
                        Text(
                            text = "共 ${comment.replyCount} 条回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.blue,
                            modifier = Modifier.clickable(onClick = onLoadReplies),
                        )
                    }
                }
            }
            if (replies != null && replies.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.fill, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    replies.forEach { reply ->
                        Text(
                            text = "${reply.name}：${reply.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text2,
                        )
                    }
                }
            } else if (loadingReplies) {
                Text(
                    "加载回复…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SendDanmakuDialog(
    positionMs: Long,
    onDismiss: () -> Unit,
    onSend: (String, Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送弹幕") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 100) text = it },
                    placeholder = { Text("这一刻想说什么…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "滚动", 5 to "顶部", 4 to "底部").forEach { (value, label) ->
                        val selected = mode == value
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = if (selected) Color.White else AppColors.current.text2,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) AppColors.current.pink else AppColors.current.fill)
                                .clickable { mode = value }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    text = "将发送在 ${timeLabel((positionMs / 1000).toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.current.text3,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSend(text.trim(), mode) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.current.pink),
            ) { Text("发送") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ---------- 工具 ----------

private fun buildMediaSource(
    context: android.content.Context,
    video: MediaTrack,
    audio: MediaTrack?,
): MediaSource {
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com/",
        "User-Agent" to Http.UA,
    )
    val httpFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
        .setAllowCrossProtocolRedirects(true)
    val dataSource = DefaultDataSource.Factory(context, httpFactory)
    val videoSource = ProgressiveMediaSource.Factory(dataSource)
        .createMediaSource(MediaItem.fromUri(video.urls.first()))
    if (audio == null) return videoSource
    val audioSource = ProgressiveMediaSource.Factory(dataSource)
        .createMediaSource(MediaItem.fromUri(audio.urls.first()))
    return MergingMediaSource(videoSource, audioSource)
}

private fun shortQuality(quality: Int): String = when {
    quality >= 125 -> "HDR"
    quality >= 120 -> "4K"
    quality >= 116 -> "1080P60"
    quality >= 112 -> "1080P+"
    quality >= 80 -> "1080P"
    quality >= 74 -> "720P60"
    quality >= 64 -> "720P"
    quality >= 32 -> "480P"
    quality > 0 -> "360P"
    else -> "画质"
}

private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Activity.applyFullscreen(enabled: Boolean) {
    requestedOrientation = if (enabled) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        // 应用本身是竖屏体验，退出全屏后明确回竖屏，避免部分设备停留在横屏。
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
