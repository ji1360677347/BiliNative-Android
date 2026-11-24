package local.bili23.android.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import local.bili23.android.core.Video
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LoadingBox
import local.bili23.android.ui.common.VideoCard
import local.bili23.android.ui.theme.AppColors

private val tabTitles = listOf("推荐", "热门", "排行榜")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (Video) -> Unit,
    onSearch: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "哔哩轻看",
                style = MaterialTheme.typography.titleLarge,
                color = colors.pink,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", tint = colors.text2)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tabTitles.forEachIndexed { index, title ->
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (state.tab == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (state.tab == index) colors.pink else colors.text2,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (state.tab == index) colors.pinkSoft else Color.Transparent)
                        .clickable { vm.selectTab(index) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.items.isEmpty() -> LoadingBox()
                state.error != null && state.items.isEmpty() -> ErrorBox(
                    message = state.error ?: "加载失败",
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = vm::retry,
                )
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.items, key = { _, video -> video.routeId }) { index, video ->
                            VideoCard(
                                video = video,
                                rank = if (state.tab == 2) index + 1 else null,
                                followed = video.authorId != null && video.authorId in state.followedMids,
                                onClick = { onOpen(video) },
                            )
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.end) {
                                    Text(
                                        "没有更多了",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.text3,
                                    )
                                } else {
                                    LaunchedEffect(state.items.size) { vm.loadMore() }
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.pink,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.error != null && state.items.isNotEmpty()) {
            Text(
                text = state.error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.fill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
