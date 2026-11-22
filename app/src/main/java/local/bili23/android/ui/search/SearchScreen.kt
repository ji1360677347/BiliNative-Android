package local.bili23.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import local.bili23.android.core.Video
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LoadingBox
import local.bili23.android.ui.common.VideoCard
import local.bili23.android.ui.theme.AppColors

private val orders = listOf(
    "totalrank" to "综合",
    "click" to "最多播放",
    "pubdate" to "最新发布",
    "dm" to "最多弹幕",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onOpen: (Video) -> Unit) {
    val vm: SearchViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索视频、UP 主", fontSize = 13.sp, color = colors.text3) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.text3, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    Text(
                        text = "搜索",
                        color = colors.pink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { vm.search() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.pink,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = colors.fill,
                    unfocusedContainerColor = colors.fill,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
        }

        if (state.searched) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                orders.forEach { (order, label) ->
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (state.order == order) colors.pink else colors.text2,
                        fontWeight = if (state.order == order) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { vm.setOrder(order) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.items.isEmpty() -> ErrorBox(
                    message = state.error ?: "搜索失败",
                    modifier = Modifier.align(Alignment.Center),
                    onRetry = { vm.search() },
                )
                state.searched && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text("没有找到相关视频", color = colors.text3, style = MaterialTheme.typography.bodyMedium)
                }
                state.searched -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, video -> video.bvid }) { _, video ->
                        VideoCard(video = video, onClick = { onOpen(video) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                            if (state.end) {
                                Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = colors.text3)
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
                else -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (state.history.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "搜索历史",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.text1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "清空",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text3,
                                modifier = Modifier.clickable { vm.clearHistory() },
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.history.forEach { keyword ->
                                Text(
                                    text = keyword,
                                    fontSize = 12.sp,
                                    color = colors.text2,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.fill)
                                        .clickable { vm.choose(keyword) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    if (state.hot.isNotEmpty()) {
                        Text(
                            "B 站热搜",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text1,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.hot.take(18).forEachIndexed { index, keyword ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.fill)
                                        .clickable { vm.choose(keyword) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index < 3) colors.pink else colors.text3,
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(keyword, fontSize = 12.sp, color = colors.text1, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (state.history.isEmpty() && state.hot.isEmpty()) {
                        Text(
                            "输入关键词，搜索 B 站视频",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text3,
                            modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                }
            }
        }
        if (state.error != null && state.items.isNotEmpty()) {
            Text(
                text = state.error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                modifier = Modifier.fillMaxWidth().background(colors.fill).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
