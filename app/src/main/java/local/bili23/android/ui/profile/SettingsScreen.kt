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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import local.bili23.android.AppRuntime
import local.bili23.android.BuildConfig
import local.bili23.android.BiliApp
import local.bili23.android.core.Identity
import local.bili23.android.core.PlaybackPreference
import local.bili23.android.ui.common.BackTopBar
import local.bili23.android.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val colors = AppColors.current
    val store = BiliApp.store
    var quality by remember { mutableIntStateOf(store.quality) }
    var preference by remember { mutableStateOf(store.preference) }
    var danmakuOn by remember { mutableStateOf(store.danmakuEnabled) }
    var opacity by remember { mutableFloatStateOf(store.danmakuOpacity) }
    var scale by remember { mutableFloatStateOf(store.danmakuScale) }
    var area by remember { mutableFloatStateOf(store.danmakuArea) }
    var theme by remember { mutableIntStateOf(AppRuntime.themeMode) }
    var editingRole by remember { mutableStateOf<Identity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        BackTopBar(title = "设置", onBack = onBack)

        SettingsSection("播放") {
            SettingLabel("默认画质")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(80 to "1080P", 64 to "720P", 32 to "480P", 16 to "360P").forEach { (value, label) ->
                    ChoiceChip(label, quality == value) {
                        quality = value
                        store.quality = value
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingLabel("播放源")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackPreference.entries.forEach { option ->
                    ChoiceChip(option.label, preference == option) {
                        preference = option
                        store.preference = option
                    }
                }
            }
        }

        SettingsSection("弹幕") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("显示弹幕", style = MaterialTheme.typography.bodyMedium, color = colors.text1, modifier = Modifier.weight(1f))
                Switch(
                    checked = danmakuOn,
                    onCheckedChange = {
                        danmakuOn = it
                        store.danmakuEnabled = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.pink),
                )
            }
            SliderRow("透明度", opacity, 0.2f..1f) {
                opacity = it
                store.danmakuOpacity = it
            }
            SliderRow("字号", scale, 0.6f..1.6f) {
                scale = it
                store.danmakuScale = it
            }
            SliderRow("显示区域", area, 0.25f..1f) {
                area = it
                store.danmakuArea = it
            }
        }

        SettingsSection("播放器外观") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (value, label) ->
                    ChoiceChip(label, theme == value) {
                        theme = value
                        AppRuntime.applyTheme(value)
                    }
                }
            }
        }

        SettingsSection("账号") {
            AccountRow(
                title = "个人账号",
                subtitle = if (store.personalCookie.isBlank()) "未登录" else "已登录，可用于历史、收藏、评论与弹幕",
                action = if (store.personalCookie.isBlank()) "导入" else "重新导入",
            ) { editingRole = Identity.PERSONAL }
            Spacer(Modifier.height(10.dp))
            AccountRow(
                title = "会员播放源",
                subtitle = if (store.memberCookie.isBlank()) "未配置（可选）" else "已配置，仅用于取会员画质",
                action = if (store.memberCookie.isBlank()) "导入" else "更新",
            ) { editingRole = Identity.MEMBER }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "退出个人账号",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.pink,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.pinkSoft)
                        .clickable {
                            BiliApp.client.logout(Identity.PERSONAL)
                            message = "已退出个人账号"
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    "清除会员源",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.pink,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.pinkSoft)
                        .clickable {
                            BiliApp.client.logout(Identity.MEMBER)
                            message = "已清除会员源"
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        SettingsSection("本机数据") {
            Text(
                "清除本机观看记录",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text1,
                modifier = Modifier
                    .clickable {
                        store.localHistory = emptyList()
                        message = "已清除本机观看记录"
                    }
                    .padding(vertical = 6.dp),
            )
            Text(
                "搜索历史与 Cookie 均已保存在应用私有空间，不会上传服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SettingsSection("关于") {
            Text("哔哩轻看 Android", style = MaterialTheme.typography.bodyMedium, color = colors.text1)
            Text(
                "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
            )
            Text(
                "非官方第三方客户端，仅供学习与个人使用。视频、弹幕、评论等内容版权归哔哩哔哩及原作者所有。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                modifier = Modifier.padding(top = 6.dp),
                lineHeight = 17.sp,
            )
        }

        message?.let {
            LaunchedEffect(it) {
                delay(2200)
                message = null
            }
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.pink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    editingRole?.let { role ->
        CookieDialog(
            role = role,
            onDismiss = { editingRole = null },
            onImported = { name ->
                editingRole = null
                message = "已导入：$name"
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val colors = AppColors.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text3,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .padding(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.current.text3,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppColors.current
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) colors.pink else colors.text2,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.pinkSoft else colors.fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = AppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text2,
            modifier = Modifier.width(64.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.pink,
                activeTrackColor = colors.pink,
            ),
        )
        Text(
            text = String.format("%.1f", value),
            style = MaterialTheme.typography.bodySmall,
            color = colors.text3,
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun AccountRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    val colors = AppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.text1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.text3)
        }
        Text(
            action,
            style = MaterialTheme.typography.bodySmall,
            color = colors.pink,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.pinkSoft)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CookieDialog(
    role: Identity,
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (role == Identity.PERSONAL) "导入个人 Cookie" else "导入会员 Cookie") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("SESSDATA=...; bili_jct=...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                if (error != null) {
                    Text(error ?: "", color = AppColors.current.pink, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (role == Identity.PERSONAL) {
                        "个人 Cookie 用于历史、收藏、评论和弹幕。"
                    } else {
                        "会员 Cookie 仅用于获取会员画质，不会用于社交操作。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.current.text3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val account = BiliApp.client.importCookie(text, role)
                            onImported(account.name)
                        } catch (e: Exception) {
                            error = e.message ?: "导入失败"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = text.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.current.pink),
            ) { Text(if (busy) "验证中…" else "导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
