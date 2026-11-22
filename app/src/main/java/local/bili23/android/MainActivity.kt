package local.bili23.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import local.bili23.android.ui.RootNav
import local.bili23.android.ui.theme.BiliTheme

class MainActivity : ComponentActivity() {

    private var pendingVideoId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppRuntime.init()
        pendingVideoId = videoIdFrom(intent)
        setContent {
            val dark = when (AppRuntime.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            BiliTheme(darkTheme = dark) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                RootNav(
                    pendingVideoId = pendingVideoId,
                    onPendingConsumed = { pendingVideoId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        videoIdFrom(intent)?.let { pendingVideoId = it }
    }

    private fun videoIdFrom(intent: Intent?): String? {
        val text = intent?.data?.toString() ?: return null
        return Regex("BV[0-9A-Za-z]{10}").find(text)?.value
    }
}
