package local.bili23.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.FollowedAccount
import local.bili23.android.core.Video
import local.bili23.android.ui.common.BackTopBar
import local.bili23.android.ui.common.EmptyBox
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LoadingBox
import local.bili23.android.ui.common.VideoCard
import local.bili23.android.ui.theme.AppColors

class FollowingViewModel : ViewModel() {
    data class UiState(
        val items: List<FollowedAccount> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val loggedIn: Boolean = BiliApp.store.personalCookie.isNotBlank(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!_state.value.loggedIn) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = BiliApp.client.following(1)
                _state.update { it.copy(items = list, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "关注列表加载失败") }
            }
        }
    }
}

@Composable
fun FollowingScreen(
    onOpenAccount: (FollowedAccount) -> Unit,
    onBack: () -> Unit,
) {
    val vm: FollowingViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        BackTopBar(title = "关注的人", onBack = onBack)
        when {
            !state.loggedIn -> Column(Modifier.fillMaxSize().padding(32.dp)) {
                Text("登录后可查看关注列表", color = colors.text2, style = MaterialTheme.typography.bodyMedium)
            }
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(
                message = state.error ?: "加载失败",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onRetry = vm::load,
            )
            state.items.isEmpty() -> EmptyBox("还没有关注任何 UP 主")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.items, key = { it.mid }) { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAccount(account) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = account.avatar,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (account.sign.isNotBlank()) {
                                Text(
                                    account.sign,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.text3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text("›", color = colors.text3)
                    }
                }
            }
        }
    }
}

class UploaderViewModel(private val mid: Int) : ViewModel() {
    data class UiState(
        val videos: List<Video> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val page: Int = 1,
        val hasMore: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load(false)
    }

    fun load(more: Boolean) {
        if (more && (_state.value.loadingMore || !_state.value.hasMore)) return
        val page = if (more) _state.value.page + 1 else 1
        _state.update { it.copy(loading = !more, loadingMore = more, error = null, page = page) }
        viewModelScope.launch {
            try {
                val result = BiliApp.client.uploaderVideos(mid, page)
                _state.update {
                    it.copy(
                        videos = if (more) (it.videos + result.videos).distinctBy { v -> v.bvid } else result.videos,
                        loading = false,
                        loadingMore = false,
                        hasMore = result.hasMore,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "加载失败") }
            }
        }
    }
}

@Composable
fun UploaderScreen(
    mid: Int,
    name: String,
    onOpen: (Video) -> Unit,
    onBack: () -> Unit,
) {
    val vm: UploaderViewModel = viewModel(key = "space-$mid") { UploaderViewModel(mid) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        BackTopBar(title = name.ifBlank { "UP 主主页" }, onBack = onBack)
        when {
            state.loading -> LoadingBox()
            state.error != null && state.videos.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorBox(
                    message = state.error ?: "加载失败",
                    onRetry = { vm.load(false) },
                )
                Text(
                    "B 站对 UP 主投稿接口有额外风控，若持续失败可先在浏览器查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    "在浏览器打开",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.pink,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .background(colors.pinkSoft)
                        .clickable {
                            val context = context
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://space.bilibili.com/$mid"),
                                )
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
            state.videos.isEmpty() -> EmptyBox("该 UP 主暂时没有投稿")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(state.videos, key = { _, video -> video.bvid }) { _, video ->
                    VideoCard(video = video, onClick = { onOpen(video) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                        if (state.loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colors.pink,
                            )
                        } else if (state.hasMore) {
                            LaunchedEffect(state.videos.size) { vm.load(true) }
                            Text("加载更多投稿", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                        } else {
                            Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                        }
                    }
                }
            }
        }
    }
}
