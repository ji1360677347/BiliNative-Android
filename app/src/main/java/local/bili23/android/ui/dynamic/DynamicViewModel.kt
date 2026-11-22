package local.bili23.android.ui.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.DynamicPost

class DynamicViewModel : ViewModel() {

    data class UiState(
        val items: List<DynamicPost> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val offset: String? = null,
        val end: Boolean = false,
        val loggedIn: Boolean = BiliApp.store.personalCookie.isNotBlank(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val loggedIn = BiliApp.store.personalCookie.isNotBlank()
        if (!loggedIn) {
            _state.value = UiState(loggedIn = false, error = "登录后查看关注动态")
            return
        }
        _state.update { it.copy(loading = true, error = null, loggedIn = true, offset = null, end = false) }
        viewModelScope.launch {
            try {
                val page = BiliApp.client.dynamicFeed(null)
                _state.update {
                    it.copy(
                        items = page.items,
                        loading = false,
                        offset = page.nextOffset,
                        end = page.nextOffset == null,
                        loggedIn = true,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "动态加载失败") }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val offset = current.offset ?: return
        if (current.loading || current.loadingMore || current.end) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = BiliApp.client.dynamicFeed(offset)
                _state.update {
                    it.copy(
                        items = (it.items + page.items).distinctBy { post -> post.id },
                        loadingMore = false,
                        offset = page.nextOffset,
                        end = page.nextOffset == null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
