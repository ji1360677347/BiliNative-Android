package local.bili23.android.ui.profile

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.Account
import local.bili23.android.core.Identity
import local.bili23.android.core.QRLogin
import local.bili23.android.core.QRPoll
import local.bili23.android.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    onDismiss: () -> Unit,
    onLoggedIn: (Account) -> Unit,
) {
    val colors = AppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<QRLogin?>(null) }
    var qrStatus by remember { mutableStateOf("正在生成二维码…") }
    var qrLoading by remember { mutableStateOf(true) }
    var generateKey by remember { mutableIntStateOf(0) }
    var showWebLogin by remember { mutableStateOf(false) }

    LaunchedEffect(generateKey) {
        qrLoading = true
        qrStatus = "正在生成二维码…"
        try {
            qr = BiliApp.client.qrGenerate()
            qrStatus = "请使用 B 站 App 扫码登录"
        } catch (e: Exception) {
            qrStatus = e.message ?: "二维码生成失败"
        } finally {
            qrLoading = false
        }
    }

    LaunchedEffect(qr?.key) {
        val key = qr?.key ?: return@LaunchedEffect
        while (true) {
            delay(2000)
            try {
                when (val result = BiliApp.client.qrPoll(key)) {
                    is QRPoll.Success -> {
                        onLoggedIn(result.account)
                        return@LaunchedEffect
                    }
                    QRPoll.Expired -> {
                        qrStatus = "二维码已过期，点击刷新"
                        return@LaunchedEffect
                    }
                    QRPoll.Confirmation -> qrStatus = "已扫码，请在手机上确认登录"
                    QRPoll.Waiting -> qrStatus = "请使用 B 站 App 扫码登录"
                }
            } catch (e: Exception) {
                qrStatus = e.message ?: "登录状态查询失败"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("登录哔哩哔哩", style = MaterialTheme.typography.titleMedium, color = colors.text1)
            Text(
                "登录后可同步历史、收藏、动态，并可发表评论与弹幕。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text3,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf("扫码登录", "Cookie 导入", "网页登录").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (mode == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (mode == index) colors.pink else colors.text2,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (mode == index) colors.pinkSoft else colors.fill)
                            .clickable { mode = index }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }

            if (mode == 0) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bitmap = remember(qr?.url) {
                            qr?.url?.let { runCatching { qrBitmap(it) }.getOrNull() }
                        }
                        when {
                            bitmap != null -> Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "登录二维码",
                                modifier = Modifier.size(184.dp),
                            )
                            qrLoading -> CircularProgressIndicator(color = colors.pink, strokeWidth = 2.dp)
                            else -> Text(qrStatus, color = androidx.compose.ui.graphics.Color(0xFF61666D), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(qrStatus, style = MaterialTheme.typography.bodySmall, color = colors.text2)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "刷新二维码",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.pink,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { generateKey++ },
                        )
                        Text(
                            "改用网页登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.blue,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { mode = 2 },
                        )
                    }
                    Text(
                        "扫码后长时间无反应，通常是网页登录态或网络问题，可改用网页登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else if (mode == 1) {
                var cookieText by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        placeholder = { Text("粘贴包含 SESSDATA / bili_jct 的 Cookie", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    if (error != null) {
                        Text(error ?: "", color = colors.pink, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "获取方式：浏览器登录 bilibili.com → 开发者工具 → Network → 任意请求的 Cookie 请求头。Cookie 仅保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "也可以直接用官方网页登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.blue,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { mode = 2 },
                    )
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val account = BiliApp.client.importCookie(cookieText, Identity.PERSONAL)
                                    onLoggedIn(account)
                                } catch (e: Exception) {
                                    error = e.message ?: "导入失败"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = cookieText.isNotBlank() && !busy,
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.pink),
                    ) {
                        Text(if (busy) "验证中…" else "导入并验证")
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Text(
                        "使用 B 站官方网页登录",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text1,
                    )
                    Text(
                        "支持账号密码、短信验证码，账号信息只在 B 站官方页面内填写，客户端只读取登录后的 Cookie。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(
                        onClick = { showWebLogin = true },
                        modifier = Modifier.padding(top = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.pink),
                    ) {
                        Text("打开官方登录页")
                    }
                    Text(
                        "如果页面提示环境异常，点右上角刷新重试；仍不行可在电脑浏览器登录后使用 Cookie 导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text3,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }

    if (showWebLogin) {
        WebLoginDialog(
            onDismiss = { showWebLogin = false },
            onLoggedIn = { account ->
                showWebLogin = false
                onLoggedIn(account)
            },
        )
    }
}

private fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
