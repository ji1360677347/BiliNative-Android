package local.bili23.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import local.bili23.android.core.Video
import local.bili23.android.ui.common.BackTopBar
import local.bili23.android.ui.common.EmptyBox
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.VideoRow
import local.bili23.android.ui.theme.AppColors

class HistoryViewModel : ViewModel() {
    data class UiState(
        val local: List<Video> = BiliApp.store.localHistory,
        val cloud: List<Video> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val cursor: String? = null,
        val end: Boolean = false,
        val loggedIn: Boolean = BiliApp.store.personalCookie.isNotBlank(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        if (_state.value.loggedIn) loadCloud(true)
    }

    fun loadCloud(reset: Boolean) {
        if (!_state.value.loggedIn) return
        if (!reset && (_state.value.loading || _state.value.loadingMore || _state.value.end)) return
        _state.update {
            it.copy(loading = reset, loadingMore = !reset, error = null)
        }
        viewModelScope.launch {
            try {
                val page = BiliApp.client.cloudHistory(if (reset) null else _state.value.cursor)
                _state.update {
                    it.copy(
                        local = BiliApp.store.localHistory,
                        cloud = if (reset) page.videos else (it.cloud + page.videos).distinctBy { v -> v.bvid },
                        loading = false,
                        loadingMore = false,
                        cursor = page.cursor,
                        end = !page.hasMore,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "历史加载失败") }
            }
        }
    }
}

@Composable
fun HistoryScreen(onOpen: (Video) -> Unit, onBack: () -> Unit) {
    val vm: HistoryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        BackTopBar(title = "观看历史", onBack = onBack)
        when {
            !state.loggedIn && state.local.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text("登录后可同步云端观看历史", color = colors.text2, style = MaterialTheme.typography.bodyMedium)
            }
            state.loading && state.cloud.isEmpty() && state.local.isEmpty() ->
                local.bili23.android.ui.common.LoadingBox()
            state.error != null && state.cloud.isEmpty() -> ErrorBox(
                message = state.error ?: "加载失败",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onRetry = { vm.loadCloud(true) },
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.local.isNotEmpty()) {
                    item {
                        Text(
                            "本机记录",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    items(state.local, key = { "local-${it.routeId}" }) { video ->
                        VideoRow(video = video, onClick = { onOpen(video) })
                    }
                }
                if (state.loggedIn) {
                    item {
                        Text(
                            "云端历史",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    if (state.cloud.isEmpty() && !state.loading) {
                        item { EmptyBox("云端还没有观看记录") }
                    }
                    items(state.cloud, key = { "cloud-${it.bvid}-${it.pubdate}" }) { video ->
                        Column {
                            VideoRow(video = video, onClick = { onOpen(video) })
                            if (video.pubTimeText.isNotBlank()) {
                                Text(
                                    text = video.pubTimeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.text3,
                                    modifier = Modifier.padding(start = 166.dp, bottom = 2.dp),
                                )
                            }
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                            if (state.loadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.pink,
                                )
                            } else if (!state.end) {
                                LaunchedEffect(state.cloud.size) { vm.loadCloud(false) }
                                Text("加载更多", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            } else {
                                Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            }
                        }
                    }
                }
                if (!state.loggedIn && state.local.isNotEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "登录后可同步云端历史",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text3,
                            )
                        }
                    }
                }
            }
        }
    }
}
