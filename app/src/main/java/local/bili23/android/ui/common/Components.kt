package local.bili23.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import local.bili23.android.core.Video
import local.bili23.android.core.countLabel
import local.bili23.android.core.timeLabel
import local.bili23.android.ui.theme.AppColors

@Composable
fun Cover(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = AppColors.current
    Box(modifier.background(colors.fill)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun FollowedTag(modifier: Modifier = Modifier) {
    val colors = AppColors.current
    Text(
        text = "已关注",
        color = colors.pink,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 12.sp,
        maxLines = 1,
        modifier = modifier
            .background(colors.pinkSoft, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun VideoCard(
    video: Video,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    followed: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Cover(video.coverUrl, Modifier.fillMaxSize(), video.title)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        )
                    ),
            )
            if (rank != null) {
                Text(
                    text = if (rank <= 3) "TOP $rank" else "$rank",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(6.dp)
                        .background(
                            if (rank <= 3) colors.pink else Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            if (video.duration > 0) {
                Text(
                    text = timeLabel(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.text1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (followed) {
                FollowedTag()
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = video.author,
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${countLabel(video.plays)}播放",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun VideoRow(
    video: Video,
    modifier: Modifier = Modifier,
    followed: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            Cover(video.coverUrl, Modifier.fillMaxSize(), video.title)
            if (video.duration > 0) {
                Text(
                    text = timeLabel(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (followed) {
                    FollowedTag()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = video.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${countLabel(video.plays)}播放",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
            )
        }
    }
}

@Composable
fun LevelBadge(level: Int) {
    val color = when (level) {
        6 -> Color(0xFFFF0000)
        5 -> Color(0xFFFF6C00)
        4 -> Color(0xFFFFB37C)
        3 -> Color(0xFF92D1E5)
        2 -> Color(0xFF95DDB2)
        else -> Color(0xFFBFBFBF)
    }
    Text(
        text = "LV${level.coerceIn(0, 6)}",
        color = Color.White,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
fun VipBadge(text: String = "大会员") {
    Text(
        text = text,
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(AppColors.current.pink, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AppColors.current.pink,
            strokeWidth = 2.dp,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = AppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text2,
        )
        if (onRetry != null) {
            Text(
                text = "点击重试",
                style = MaterialTheme.typography.labelLarge,
                color = colors.pink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.pinkSoft)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppColors.current.text3)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val colors = AppColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text1,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.bodySmall, color = colors.text3)
        }
    }
}
