package com.livora.corbett.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.local.Session
import com.livora.corbett.data.realtime.RealtimeEvent
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.data.repo.LastBooking
import com.livora.corbett.data.repo.MessageRepository
import com.livora.corbett.data.repo.OpsRepository
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.AppState
import com.livora.corbett.work.StaffPollWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Badges(val messages: Int = 0, val requests: Int = 0)

/** Activity-scoped state: session, theme, settings, live badges and realtime toasts. */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: AuthRepository,
    private val prefs: AppPrefs,
    private val config: ResortConfig,
    private val socket: SocketManager,
    private val messages: MessageRepository,
    private val ops: OpsRepository,
    val lastBooking: LastBooking,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session
    val settings: StateFlow<PublicSettings> = config.settings
    val themeMode: StateFlow<String> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "dark")
    val onboardingSeen: StateFlow<Boolean?> =
        prefs.onboardingSeen.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val staffPromptShown: StateFlow<Boolean?> =
        prefs.staffPromptShown.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _badges = MutableStateFlow(Badges())
    val badges: StateFlow<Badges> = _badges.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    val realtime: SharedFlow<RealtimeEvent> = socket.events

    private var lastBadgeRefresh = 0L

    init {
        viewModelScope.launch { config.refresh() }
        viewModelScope.launch {
            auth.session.collect { s ->
                if (s == null) {
                    socket.stop()
                    StaffPollWorker.cancel(appContext)
                    _badges.value = Badges()
                } else {
                    if (AppState.isForeground) socket.start()
                    if (s.user.isStaff) StaffPollWorker.schedule(appContext) else StaffPollWorker.cancel(appContext)
                    refreshBadges(force = true)
                }
            }
        }
        viewModelScope.launch { socket.events.collect { onEvent(it) } }
    }

    fun onForeground() {
        AppState.isForeground = true
        if (auth.session.value != null) {
            socket.start()
            refreshBadges(force = true)
        }
        if (!config.loaded.value) viewModelScope.launch { config.refresh() }
    }

    fun onBackground() {
        AppState.isForeground = false
        socket.stop()
    }

    fun refreshBadges(force: Boolean = false) {
        val s = auth.session.value ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastBadgeRefresh < 3_000) return
        lastBadgeRefresh = now
        viewModelScope.launch {
            if (s.user.isStaff) {
                val unread = (messages.unread() as? ApiResult.Success)?.data ?: _badges.value.messages
                val pending = (ops.roomService("pending") as? ApiResult.Success)?.data?.size ?: _badges.value.requests
                _badges.value = Badges(unread, pending)
            } else {
                val r = messages.myThreads(true, emptySet())
                if (r is ApiResult.Success) {
                    _badges.value = Badges(messages = r.data.count { it.unreadByGuest }, requests = 0)
                }
            }
        }
    }

    private fun onEvent(e: RealtimeEvent) {
        val isStaff = auth.session.value?.user?.isStaff == true
        when (e.name) {
            "booking:new" -> if (isStaff) _toasts.tryEmit("New booking received")
            "message:new" -> if (isStaff) _toasts.tryEmit("New guest message")
            "roomservice:new" -> if (isStaff) _toasts.tryEmit("New room service request")
            "roomservice:update" -> if (!isStaff) _toasts.tryEmit("Room service update")
            "booking:update" -> if (!isStaff) _toasts.tryEmit("Your booking was updated")
        }
        if (e.name.startsWith("message") || e.name.startsWith("roomservice")) refreshBadges()
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun markStaffPromptShown() {
        viewModelScope.launch { prefs.setStaffPromptShown() }
    }

    fun markOnboardingSeen() {
        viewModelScope.launch { prefs.setOnboardingSeen() }
    }

    fun signOut() {
        viewModelScope.launch { auth.logout() }
    }
}
