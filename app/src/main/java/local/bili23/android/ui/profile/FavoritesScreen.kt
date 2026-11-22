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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.FavoriteFolder
import local.bili23.android.core.Video
import local.bili23.android.ui.common.BackTopBar
import local.bili23.android.ui.common.EmptyBox
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LoadingBox
import local.bili23.android.ui.common.VideoCard
import local.bili23.android.ui.theme.AppColors

class FavoritesViewModel : ViewModel() {
    data class UiState(
        val folders: List<FavoriteFolder> = emptyList(),
        val loadingFolders: Boolean = false,
        val selected: FavoriteFolder? = null,
        val videos: List<Video> = emptyList(),
        val loadingVideos: Boolean = false,
        val page: Int = 1,
        val hasMore: Boolean = false,
        val error: String? = null,
        val loggedIn: Boolean = BiliApp.store.personalCookie.isNotBlank(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        if (!_state.value.loggedIn) return
        _state.update { it.copy(loadingFolders = true, error = null) }
        viewModelScope.launch {
            try {
                val folders = BiliApp.client.favoriteFolders()
                _state.update { it.copy(folders = folders, loadingFolders = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loadingFolders = false, error = e.message ?: "收藏夹加载失败") }
            }
        }
    }

    fun openFolder(folder: FavoriteFolder) {
        _state.update { it.copy(selected = folder, videos = emptyList(), page = 1) }
        loadVideos(reset = true)
    }

    fun backToFolders() {
        _state.update { it.copy(selected = null, videos = emptyList(), error = null) }
    }

    fun loadVideos(reset: Boolean) {
        val folder = _state.value.selected ?: return
        if (!reset && (_state.value.loadingVideos || !_state.value.hasMore)) return
        val page = if (reset) 1 else _state.value.page + 1
        _state.update { it.copy(loadingVideos = true, error = null, page = page) }
        viewModelScope.launch {
            try {
                val result = BiliApp.client.favoriteVideos(folder.id, page)
                _state.update {
                    it.copy(
                        videos = if (reset) result.videos else (it.videos + result.videos).distinctBy { v -> v.bvid },
                        loadingVideos = false,
                        hasMore = result.hasMore,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingVideos = false, error = e.message ?: "收藏内容加载失败") }
            }
        }
    }
}

@Composable
fun FavoritesScreen(onOpen: (Video) -> Unit, onBack: () -> Unit) {
    val vm: FavoritesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current
    val selected = state.selected

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        BackTopBar(
            title = selected?.title ?: "我的收藏",
            onBack = { if (selected != null) vm.backToFolders() else onBack() },
        )
        when {
            !state.loggedIn -> Column(Modifier.fillMaxSize().padding(32.dp)) {
                Text("登录后可查看收藏夹", color = colors.text2, style = MaterialTheme.typography.bodyMedium)
            }
            selected == null -> when {
                state.loadingFolders -> LoadingBox()
                state.error != null -> ErrorBox(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onRetry = vm::loadFolders,
                )
                state.folders.isEmpty() -> EmptyBox("还没有创建收藏夹")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.folders, key = { it.id }) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.openFolder(folder) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.pinkSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("藏", color = colors.pink, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.title, style = MaterialTheme.typography.bodyMedium, color = colors.text1)
                                Text(
                                    "${folder.count} 个视频",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.text3,
                                )
                            }
                            Text("›", color = colors.text3)
                        }
                    }
                }
            }
            else -> when {
                state.loadingVideos && state.videos.isEmpty() -> LoadingBox()
                state.error != null && state.videos.isEmpty() -> ErrorBox(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onRetry = { vm.loadVideos(true) },
                )
                state.videos.isEmpty() -> EmptyBox("这个收藏夹还没有视频")
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
                            if (state.loadingVideos) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.pink,
                                )
                            } else if (state.hasMore) {
                                LaunchedEffect(state.videos.size) { vm.loadVideos(false) }
                                Text("加载更多", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            } else {
                                Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            }
                        }
                    }
                }
            }
        }
    }
}
