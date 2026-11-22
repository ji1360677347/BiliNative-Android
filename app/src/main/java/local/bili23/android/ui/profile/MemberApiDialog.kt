package local.bili23.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.relativeTime
import local.bili23.android.ui.theme.AppColors

/**
 * 会员 Cookie API 配置：保存一个返回 B 站 Cookie 的 HTTPS 地址，
 * 取流前按 6 小时间隔自动拉取；拉取失败不会覆盖已有会员 Cookie。
 */
@Composable
fun MemberApiDialog(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val colors = AppColors.current
    val store = BiliApp.store
    var url by remember { mutableStateOf(store.memberApiUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember {
        mutableStateOf(
            if (store.memberApiUrl.isNotBlank() && store.memberApiFailedAt > store.memberRefreshedAt) {
                "上次刷新失败，请检查地址、访问密钥或返回内容"
            } else null
        )
    }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runRefresh(saveFirst: Boolean) {
        error = null
        success = null
        busy = true
        scope.launch {
            try {
                if (saveFirst) BiliApp.client.configureMemberApi(url)
                val account = BiliApp.client.refreshMemberFromApi(manual = true)
                url = store.memberApiUrl
                success = "已刷新：${account.name}"
                onMessage("会员播放源已更新：${account.name}")
            } catch (e: Exception) {
                error = e.message ?: "会员 API 刷新失败"
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.card,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("会员 Cookie API", style = MaterialTheme.typography.titleMedium, color = colors.text1)
                Text(
                    "填入返回 B 站 Cookie 的 HTTPS 地址，客户端每 6 小时自动刷新一次。不会向该地址发送你的个人 Cookie。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 6.dp),
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = {
                        Text(
                            "https://example.com/api/pages/<id>/cookies?key=<访问密钥>",
                            fontSize = 11.sp,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(116.dp).padding(top = 12.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                Text(
                    "支持 JSON 数组、{\"cookies\":[...]}、{\"header\":\"...\"} 或直接返回 Cookie 文本；只接受 bilibili.com 域的 Cookie。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text3,
                    modifier = Modifier.padding(top = 8.dp),
                )

                val refreshedAt = store.memberRefreshedAt
                if (refreshedAt > 0) {
                    Text(
                        text = "上次自动刷新：${relativeTime(refreshedAt / 1000)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (store.memberCookie.isNotBlank()) {
                    Text(
                        text = "当前已有会员 Cookie，刷新失败时会继续使用它。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.orange,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                success?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.pink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { runRefresh(saveFirst = true) },
                        enabled = url.isNotBlank() && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.pink),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        } else {
                            Text("保存并刷新")
                        }
                    }
                    TextButton(
                        onClick = { runRefresh(saveFirst = false) },
                        enabled = store.memberApiUrl.isNotBlank() && !busy,
                    ) {
                        Text("立即刷新")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            BiliApp.client.resetMemberApi()
                            url = ""
                            error = null
                            success = "已清除会员 API 配置"
                            onMessage("已清除会员 API 配置")
                        },
                        enabled = !busy && (store.memberApiUrl.isNotBlank() || url.isNotBlank()),
                    ) {
                        Text("清除", color = colors.text3)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭", color = colors.pink)
                }
            }
        }
    }
}
