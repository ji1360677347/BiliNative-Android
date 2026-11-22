package local.bili23.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.Video

class SearchViewModel : ViewModel() {

    data class UiState(
        val query: String = "",
        val items: List<Video> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val page: Int = 1,
        val end: Boolean = false,
        val order: String = "totalrank",
        val searched: Boolean = false,
        val history: List<String> = emptyList(),
        val hot: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState(history = BiliApp.store.searchHistory))
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val hot = runCatching { BiliApp.client.hotSearches() }.getOrDefault(emptyList())
            _state.update { it.copy(hot = hot) }
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun setOrder(order: String) {
        if (_state.value.order == order) return
        _state.update { it.copy(order = order) }
        if (_state.value.searched) search(reset = true)
    }

    fun choose(keyword: String) {
        _state.update { it.copy(query = keyword) }
        search(reset = true)
    }

    fun search(reset: Boolean = true) {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        if (!reset && (_state.value.loading || _state.value.loadingMore || _state.value.end)) return
        val page = if (reset) 1 else _state.value.page + 1
        job?.cancel()
        _state.update {
            it.copy(
                loading = reset,
                loadingMore = !reset,
                error = null,
                searched = true,
                page = page,
            )
        }
        if (reset) {
            val history = (listOf(query) + BiliApp.store.searchHistory).distinct().take(12)
            BiliApp.store.searchHistory = history
            _state.update { it.copy(history = history) }
        }
        job = viewModelScope.launch {
            try {
                val result = BiliApp.client.search(query, page, _state.value.order)
                _state.update { current ->
                    if (current.query.trim() != query) current
                    else current.copy(
                        items = if (reset) result else (current.items + result).distinctBy { it.bvid },
                        loading = false,
                        loadingMore = false,
                        end = result.isEmpty() || result.size < 24,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = e.message ?: "搜索失败",
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (_state.value.items.isEmpty()) return
        search(reset = false)
    }

    fun clearHistory() {
        BiliApp.store.searchHistory = emptyList()
        _state.update { it.copy(history = emptyList()) }
    }
}
