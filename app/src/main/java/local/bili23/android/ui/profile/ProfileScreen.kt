package local.bili23.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import local.bili23.android.BiliApp
import local.bili23.android.core.Account
import local.bili23.android.ui.common.VipBadge
import local.bili23.android.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    onOpen: (local.bili23.android.core.Video) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val vm: ProfileViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text1,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFB7299), Color(0xFFE45C84))
                    )
                )
                .clickable { if (state.account == null) vm.openLogin() }
                .padding(16.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(56.dp))
            } else if (state.account == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录 B 站账号",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            "同步历史、收藏、动态与互动",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Text(
                        "登录",
                        color = colors.pink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            } else {
                AccountHeader(state.account!!)
            }
        }

        if (state.error != null) {
            Text(
                text = state.error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.orange,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card),
        ) {
            MenuRow("观看历史", Icons.Filled.DateRange) { onNavigate("history") }
            MenuRow("我的收藏", Icons.Filled.Star) { onNavigate("favorites") }
            MenuRow("关注的人", Icons.Filled.Person) { onNavigate("following") }
            MenuRow("设置", Icons.Filled.Settings, last = true) { onNavigate("settings") }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("会员播放源", style = MaterialTheme.typography.bodyMedium, color = colors.text1)
                Text(
                    text = when {
                        state.member != null -> "${state.member!!.name} · 大会员可用"
                        BiliApp.store.memberCookie.isNotBlank() -> "已配置，待验证"
                        else -> "未配置，画质以个人账号为准"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                )
            }
            Text(
                "去设置",
                style = MaterialTheme.typography.bodySmall,
                color = colors.pink,
                modifier = Modifier.clickable { onNavigate("settings") },
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "哔哩轻看 Android 0.1.0 · 非官方客户端\n所有内容版权归哔哩哔哩及原作者所有",
            style = MaterialTheme.typography.bodySmall,
            color = colors.text3,
            lineHeight = 17.sp,
        )

        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(2200)
                vm.consumeMessage()
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.pink,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (state.showLogin) {
        LoginSheet(
            onDismiss = vm::closeLogin,
            onLoggedIn = { account ->
                vm.onLoggedIn(account)
                vm.refresh()
            },
        )
    }
}

@Composable
private fun AccountHeader(account: Account) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = account.avatar,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                if (account.isVip) VipBadge()
            }
            Text(
                text = "UID ${account.mid}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    icon: ImageVector,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = AppColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.pinkSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.pink, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text1, modifier = Modifier.weight(1f))
            Text("›", fontSize = 18.sp, color = colors.text3)
        }
        if (!last) {
            Box(
                Modifier
                    .padding(start = 56.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.divider)
            )
        }
    }
}
