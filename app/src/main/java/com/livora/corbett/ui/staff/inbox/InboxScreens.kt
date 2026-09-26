@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)

package com.livora.corbett.ui.staff.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.MsgThread
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.MessageRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.guest.messages.Bubble
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUi(
    val status: String = "all",
    val query: String = "",
    val list: Load<List<MsgThread>> = Load.Loading,
    val unread: Int = 0,
    val refreshing: Boolean = false,
)

@HiltViewModel
class StaffInboxViewModel @Inject constructor(
    private val messages: MessageRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(InboxUi())
    val ui: StateFlow<InboxUi> = _ui.asStateFlow()
    private val queryFlow = MutableStateFlow("")
    private val poller = Poller(viewModelScope, 20_000) { load(silent = true) }

    init {
        viewModelScope.launch {
            queryFlow.debounce(350).distinctUntilChanged().collect {
                _ui.update { s -> s.copy(list = Load.Loading) }
                load(silent = false)
            }
        }
        viewModelScope.launch { socket.events.collect { if (it.name.startsWith("message")) load(silent = true) } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        queryFlow.value = q
    }

    fun setStatus(status: String) {
        _ui.update { it.copy(status = status, list = Load.Loading) }
        viewModelScope.launch { load(silent = false) }
    }

    fun refresh(initial: Boolean = false) {
        _ui.update { if (initial) it.copy(list = Load.Loading) else it.copy(refreshing = true) }
        viewModelScope.launch { load(silent = false) }
    }

    /** Reloads without flashing the skeleton; used on resume so read state is fresh after returning from a thread. */
    fun reload() = viewModelScope.launch { load(silent = true) }

    private suspend fun load(silent: Boolean) {
        val s = _ui.value
        val status = if (s.status == "all") "" else s.status
        when (val r = messages.inbox(status, s.query, 1)) {
            is ApiResult.Success -> _ui.update { it.copy(list = Load.Ready(r.data.threads), unread = r.data.unread, refreshing = false) }
            is ApiResult.Failure -> _ui.update {
                it.copy(list = if (silent && it.list is Load.Ready) it.list else Load.Failed(r.message), refreshing = false)
            }
        }
    }
}

private val filters = listOf("all" to "All", "new" to "New", "open" to "Open", "resolved" to "Resolved")

@Composable
fun StaffInboxScreen(onOpen: (String) -> Unit, vm: StaffInboxViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.reload()
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Inbox", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            if (ui.unread > 0) StatusPill("${ui.unread} unread", MaterialTheme.colorScheme.tertiary)
        }
        AppTextField(
            ui.query, vm::setQuery, "Search name, email, subject", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            imeAction = ImeAction.Search, leading = Icons.Filled.Search,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (k, label) -> SelectChip(label, ui.status == k, { vm.setStatus(k) }) }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            LoadBox(ui.list, onRetry = { vm.refresh(initial = true) }, skeleton = { ListSkeleton(6, 84.dp) }) { threads ->
                if (threads.isEmpty()) {
                    EmptyState(Icons.Filled.Chat, "No conversations", "Guest messages will show up here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(threads, key = { _, t -> t.id }) { i, t -> ThreadRow(t, Modifier.staggerIn(i)) { onOpen(t.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(t: MsgThread, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    GlassCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 6.dp, end = 10.dp).size(10.dp).clip(CircleShape)
                    .background(if (t.unreadByStaff) cs.tertiary else cs.onSurface.copy(alpha = 0.0f)),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.name.ifBlank { "Guest" }, style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (t.unreadByStaff) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(Fmt.smartTime(t.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Text(
                    t.subject.ifBlank { Fmt.titleCase(t.topic) }, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val preview = t.preview ?: t.messages.lastOrNull()?.body
                if (!preview.isNullOrBlank()) {
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(Fmt.titleCase(t.topic), cs.secondary)
                    StatusPill(Fmt.titleCase(t.status), statusColor(t.status))
                }
            }
        }
    }
}

// ───────────────────────── thread ─────────────────────────

@HiltViewModel
class StaffThreadViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val messages: MessageRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val id: String = checkNotNull(handle["id"])
    private val _thread = MutableStateFlow<Load<MsgThread>>(Load.Loading)
    val thread: StateFlow<Load<MsgThread>> = _thread.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _msgs = Channel<String>(Channel.BUFFERED)
    val toasts = _msgs.receiveAsFlow()
    private val poller = Poller(viewModelScope, 10_000) { load(silent = true) }

    init {
        refresh()
        viewModelScope.launch { socket.events.collect { if (it.name.startsWith("message")) load(silent = true) } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun refresh() {
        _thread.value = Load.Loading
        viewModelScope.launch { load(silent = false) }
    }

    private suspend fun load(silent: Boolean) {
        when (val r = messages.staffThread(id)) {
            is ApiResult.Success -> _thread.value = Load.Ready(r.data)
            is ApiResult.Failure -> if (!(silent && _thread.value is Load.Ready)) _thread.value = Load.Failed(r.message)
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        if (text.isBlank() || _sending.value) return
        _sending.value = true
        viewModelScope.launch {
            when (val r = messages.staffReply(id, text)) {
                is ApiResult.Success -> { onSent(); load(silent = true) }
                is ApiResult.Failure -> _msgs.trySend(r.message)
            }
            _sending.value = false
        }
    }

    fun setStatus(status: String) {
        viewModelScope.launch {
            when (val r = messages.setStatus(id, status)) {
                is ApiResult.Success -> { _msgs.trySend(if (status == "resolved") "Marked as resolved" else "Conversation reopened"); load(silent = true) }
                is ApiResult.Failure -> _msgs.trySend(r.message)
            }
        }
    }
}

private val quickReplies = listOf(
    "Thank you for reaching out. We will get back to you shortly.",
    "Your room is confirmed. We look forward to welcoming you.",
    "Check-in is from 2 PM and check-out is by 11 AM.",
    "Could you please share your preferred dates and number of guests?",
    "We have arranged this for you. Please let us know if you need anything else.",
)

@Composable
fun StaffThreadScreen(onBack: () -> Unit, vm: StaffThreadViewModel = hiltViewModel()) {
    val thread by vm.thread.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    LaunchedEffect(vm) { vm.toasts.collect { snack.showSnackbar(it) } }
    val count = (thread as? Load.Ready)?.data?.messages?.size ?: 0
    LaunchedEffect(count) { if (count > 0) listState.animateScrollToItem(count - 1) }

    val t = (thread as? Load.Ready)?.data
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        AppTopBar(t?.name?.ifBlank { null } ?: "Conversation", onBack) {
            val phone = t?.phone
            if (!phone.isNullOrBlank()) {
                IconButton(onClick = { Intents.call(ctx, phone) }) { Icon(Icons.Filled.Call, "Call guest") }
                IconButton(onClick = { Intents.whatsapp(ctx, phone) }) { Icon(Icons.Filled.Chat, "WhatsApp guest") }
            }
            if (t != null) {
                if (t.status == "resolved") {
                    IconButton(onClick = { vm.setStatus("open") }) { Icon(Icons.Filled.Replay, "Reopen") }
                } else {
                    IconButton(onClick = { vm.setStatus("resolved") }) { Icon(Icons.Filled.CheckCircle, "Mark resolved") }
                }
            }
        }
        if (t != null) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(Fmt.titleCase(t.status), statusColor(t.status))
                Text(
                    t.subject.ifBlank { Fmt.titleCase(t.topic) } + " · " + t.email,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            LoadBox(thread, onRetry = { vm.refresh() }, skeleton = { ListSkeleton(4, 70.dp) }) { th ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(th.messages, key = { i, m -> m.id.ifBlank { "m$i" } }) { _, m -> Bubble(m, mine = m.sender == "staff") }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickReplies.forEach { q -> SelectChip(q.substringBefore('.').take(34), false, { text = q }) }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(text, { text = it }, "Reply to guest", Modifier.weight(1f), singleLine = false, imeAction = ImeAction.Default)
            IconButton(onClick = { vm.send(text) { text = "" } }, enabled = text.isNotBlank() && !sending, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send reply", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
