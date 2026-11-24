package local.bili23.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.Video

class HomeViewModel : ViewModel() {

    data class UiState(
        val tab: Int = 0,
        val items: List<Video> = emptyList(),
        val followedMids: Set<Int> = emptySet(),
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
        val end: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(loading = true))
    val state = _state.asStateFlow()

    private var job: Job? = null
    private var followJob: Job? = null
    private var page = 1

    init {
        load(reset = true)
        viewModelScope.launch {
            BiliApp.store.personalCookieFlow
                .collect { cookie ->
                    if (cookie.isBlank()) {
                        followJob?.cancel()
                        _state.update { it.copy(followedMids = emptySet()) }
                    } else {
                        loadFollowedMids()
                    }
                }
        }
    }

    fun selectTab(tab: Int) {
        if (tab == _state.value.tab) return
        _state.value = UiState(
            tab = tab,
            followedMids = _state.value.followedMids,
            loading = true,
        )
        page = 1
        load(reset = true)
    }

    fun refresh() {
        load(reset = true)
        loadFollowedMids()
    }

    private fun loadFollowedMids() {
        if (BiliApp.store.personalCookie.isBlank()) {
            _state.update { it.copy(followedMids = emptySet()) }
            return
        }
        followJob?.cancel()
        followJob = viewModelScope.launch {
            val mids = BiliApp.client.followingMids()
            if (mids != null) {
                _state.update { it.copy(followedMids = mids) }
            }
        }
    }

    fun retry() = load(reset = true)

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.refreshing || current.loadingMore || current.end) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val snapshot = _state.value
        val tab = snapshot.tab
        if (reset) page = 1 else page += 1
        val target = page
        job?.cancel()
        _state.update {
            it.copy(
                loading = reset && it.items.isEmpty(),
                refreshing = reset && it.items.isNotEmpty(),
                loadingMore = !reset,
                error = null,
            )
        }
        job = viewModelScope.launch {
            try {
                val result = when (tab) {
                    0 -> BiliApp.client.recommended(target)
                    1 -> BiliApp.client.popular(target)
                    else -> if (target == 1) BiliApp.client.ranking() else emptyList()
                }
                _state.update { state ->
                    if (state.tab != tab) state
                    else {
                        val merged = if (reset) result else (state.items + result).distinctBy { it.routeId }
                        state.copy(
                            items = merged,
                            loading = false,
                            refreshing = false,
                            loadingMore = false,
                            end = when {
                                tab == 2 -> true
                                result.isEmpty() -> true
                                else -> result.size < 20
                            },
                            error = if (result.isEmpty() && reset) "没有拉到内容，请稍后重试" else null,
                        )
                    }
                }
            } catch (e: Exception) {
                if (!reset) page = (page - 1).coerceAtLeast(1)
                _state.update {
                    if (it.tab != tab) it
                    else it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = if (reset || it.items.isEmpty()) (e.message ?: "加载失败") else it.error,
                    )
                }
            }
        }
    }
}
