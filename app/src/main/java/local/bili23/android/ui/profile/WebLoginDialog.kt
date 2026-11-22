package local.bili23.android.ui.profile

import android.content.res.Configuration
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import local.bili23.android.BiliApp
import local.bili23.android.core.Account
import local.bili23.android.core.Identity
import local.bili23.android.ui.theme.AppColors

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * 官方网页登录：在 WebView 里走 B 站自己的账号密码 / 短信登录页，
 * 登录成功后从 CookieManager 读取 .bilibili.com 的 Cookie 并交给客户端验证。
 * 不注入脚本、不读取输入框，只读取 WebView 自身的 Cookie。
 */
@Composable
fun WebLoginDialog(
    onDismiss: () -> Unit,
    onLoggedIn: (Account) -> Unit,
) {
    val colors = AppColors.current
    val context = LocalContext.current
    var status by remember { mutableStateOf("正在加载官方登录页…") }
    var cookie by remember { mutableStateOf("") }
    var lastTried by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val webViewAvailable = remember {
        runCatching { WebView.getCurrentWebViewPackage() != null }.getOrDefault(false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "关闭", tint = colors.text1)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "B 站网页登录",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text1,
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text3,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = "刷新",
                        fontSize = 13.sp,
                        color = colors.text2,
                        modifier = Modifier
                            .clickable { webView?.reload() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "完成",
                        fontSize = 13.sp,
                        color = colors.pink,
                        modifier = Modifier
                            .clickable {
                                readBiliCookie { value ->
                                    if (value.isBlank()) {
                                        status = "未检测到登录 Cookie，请先在页面完成登录"
                                    } else {
                                        cookie = value
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (!webViewAvailable) {
                        Text(
                            "系统 WebView 不可用，请改用 Cookie 导入。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                    if (webViewAvailable) AndroidView(
                        factory = { ctx ->
                            // 官方登录页在深色模式下会把协议勾选框压得很暗，这里固定用浅色渲染。
                            val config = Configuration(ctx.resources.configuration).apply {
                                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                    Configuration.UI_MODE_NIGHT_NO
                            }
                            val lightContext = ctx.createConfigurationContext(config)
                            WebView(lightContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                settings.userAgentString = MOBILE_UA
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): WebResourceResponse? {
                                        request.requestHeaders["Cookie"]?.let { header ->
                                            if (header.contains("SESSDATA=")) cookie = header
                                        }
                                        return null
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        readBiliCookie { cookie = it }
                                        status = if (url?.contains("/login") == true) {
                                            "请完成登录（需勾选页面底部的同意协议）"
                                        } else {
                                            "登录成功后会自动返回"
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView,
                                        request: WebResourceRequest,
                                        error: WebResourceError,
                                    ) {
                                        if (request.isForMainFrame) {
                                            status = "网页加载失败：${error.description}"
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        if (newProgress in 1..100 && status.startsWith("正在加载")) {
                                            status = "正在加载 $newProgress%"
                                        }
                                    }
                                }
                                loadUrl("https://passport.bilibili.com/login")
                                webView = this
                            }
                        },
                        update = { webView = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.navigationBarsPadding().height(2.dp))
            }
        }
    }

    // CookieManager 的 getCookie 不触发回调，登录完成后主动轮询一次。
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            readBiliCookie { if (it.isNotBlank()) cookie = it }
        }
    }

    LaunchedEffect(cookie) {
        if (cookie.isBlank() || cookie == lastTried) return@LaunchedEffect
        lastTried = cookie
        status = "正在验证登录状态…"
        try {
            val account = BiliApp.client.importCookie(cookie, Identity.PERSONAL)
            onLoggedIn(account)
        } catch (e: Exception) {
            status = "验证失败：${e.message ?: "未知错误"}，可刷新页面后重试"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
        }
    }
}

private fun readBiliCookie(onCookie: (String) -> Unit) {
    val manager = CookieManager.getInstance()
    val hosts = listOf("https://www.bilibili.com", "https://passport.bilibili.com", "https://api.bilibili.com")
    val merged = LinkedHashMap<String, String>()
    hosts.forEach { host ->
        manager.getCookie(host)?.split(';')?.forEach { pair ->
            val index = pair.indexOf('=')
            if (index > 0) {
                val name = pair.substring(0, index).trim()
                val value = pair.substring(index + 1).trim()
                if (name.isNotBlank() && value.isNotBlank()) merged[name] = value
            }
        }
    }
    if (merged.containsKey("SESSDATA")) {
        onCookie(merged.entries.joinToString("; ") { "${it.key}=${it.value}" })
    } else {
        onCookie("")
    }
}
