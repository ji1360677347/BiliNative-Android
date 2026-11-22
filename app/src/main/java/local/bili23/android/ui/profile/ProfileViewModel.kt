package local.bili23.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.bili23.android.BiliApp
import local.bili23.android.core.Account
import local.bili23.android.core.Identity

class ProfileViewModel : ViewModel() {

    data class UiState(
        val account: Account? = null,
        val member: Account? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val showLogin: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val account = BiliApp.client.account(Identity.PERSONAL)
                val member = runCatching { BiliApp.client.account(Identity.MEMBER) }.getOrNull()
                _state.update {
                    it.copy(account = account, member = member, loading = false, error = null)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(account = null, loading = false, error = e.message ?: "账号状态异常")
                }
            }
        }
    }

    fun openLogin() {
        _state.update { it.copy(showLogin = true) }
    }

    fun closeLogin() {
        _state.update { it.copy(showLogin = false) }
    }

    fun onLoggedIn(account: Account) {
        _state.update {
            it.copy(account = account, showLogin = false, message = "已登录：${account.name}")
        }
    }

    fun logout() {
        BiliApp.client.logout(Identity.PERSONAL)
        _state.update { it.copy(account = null, message = "已退出登录") }
    }

    fun toast(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
