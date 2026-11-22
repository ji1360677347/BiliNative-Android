package local.bili23.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 少量需要跨页面即时生效的全局状态（主题模式）。 */
object AppRuntime {
    var themeMode by mutableIntStateOf(0)
        private set

    fun init() {
        themeMode = BiliApp.store.themeMode
    }

    fun applyTheme(mode: Int) {
        themeMode = mode
        BiliApp.store.themeMode = mode
    }
}
