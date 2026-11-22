package local.bili23.android.ui.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import local.bili23.android.core.DynamicPost
import local.bili23.android.core.Video
import local.bili23.android.core.countLabel
import local.bili23.android.core.timeLabel
import local.bili23.android.ui.common.Cover
import local.bili23.android.ui.common.EmptyBox
import local.bili23.android.ui.common.ErrorBox
import local.bili23.android.ui.common.LoadingBox
import local.bili23.android.ui.theme.AppColors

@Composable
fun DynamicScreen(
    onOpen: (Video) -> Unit,
    onLogin: () -> Unit,
) {
    val vm: DynamicViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "动态",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            !state.loggedIn -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text("登录后查看关注 UP 主的动态", color = colors.text2, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.pink),
                ) { Text("去登录") }
            }
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(
                message = state.error ?: "加载失败",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onRetry = vm::refresh,
            )
            state.items.isEmpty() -> EmptyBox("关注的人还没有发布动态")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                items(state.items, key = { it.id }) { post ->
                    DynamicCard(post) { video -> onOpen(video) }
                }
                item {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.end -> Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            state.loadingMore -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colors.pink,
                            )
                            else -> {
                                LaunchedEffect(state.items.size) { vm.loadMore() }
                                Text("上拉加载更多", style = MaterialTheme.typography.bodySmall, color = colors.text3)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicCard(post: DynamicPost, onOpen: (Video) -> Unit) {
    val colors = AppColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = post.avatar,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = post.author,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.text1,
                )
                Text(post.published, style = MaterialTheme.typography.bodySmall, color = colors.text3)
            }
        }
        if (post.text.isNotBlank()) {
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text1,
                lineHeight = 20.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        post.video?.let { video ->
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.fill)
                    .clickable { onOpen(video) }
                    .padding(6.dp),
            ) {
                Box(
                    Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(5.dp)),
                ) {
                    Cover(video.coverUrl, Modifier.fillMaxSize(), video.title)
                    if (video.duration > 0) {
                        Text(
                            text = timeLabel(video.duration),
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (post.images.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                post.images.take(3).forEach { image ->
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.fill),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("转发 ${countLabel(post.forwards)}", fontSize = 11.sp, color = colors.text3)
            Text("评论 ${countLabel(post.comments)}", fontSize = 11.sp, color = colors.text3)
            Text("赞 ${countLabel(post.likes)}", fontSize = 11.sp, color = colors.text3)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
    }
}
