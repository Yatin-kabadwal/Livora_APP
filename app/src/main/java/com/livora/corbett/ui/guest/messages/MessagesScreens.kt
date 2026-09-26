@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.messages

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.CreateMessageBody
import com.livora.corbett.data.api.Msg
import com.livora.corbett.data.api.MsgThread
import com.livora.corbett.data.local.AppPrefs
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.data.repo.MessageRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.PulsingBadge
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.navigation.GuestActions
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ───────────────────────── list ─────────────────────────

@HiltViewModel
class GuestMessagesViewModel @Inject constructor(
    private val messages: MessageRepository,
    private val auth: AuthRepository,
    private val prefs: AppPrefs,
    private val socket: SocketManager,
) : ViewModel() {
    private val _threads = MutableStateFlow<Load<List<MsgThread>>>(Load.Loading)
    val threads: StateFlow<Load<List<MsgThread>>> = _threads.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        refresh(initial = true)
        viewModelScope.launch { socket.events.collect { if (it.name == "message:update") refresh(silent = true) } }
    }

    fun refresh(initial: Boolean = false, silent: Boolean = false) {
        if (!initial && !silent) _refreshing.value = true
        viewModelScope.launch {
            val tokens = prefs.threadTokens.first()
            when (val r = messages.myThreads(auth.session.value != null, tokens)) {
                is ApiResult.Success -> _threads.value = Load.Ready(r.data)
                is ApiResult.Failure -> if (_threads.value !is Load.Ready) _threads.value = Load.Failed(r.message)
            }
            _refreshing.value = false
        }
    }
}

@Composable
fun MessagesScreen(actions: GuestActions, vm: GuestMessagesViewModel = hiltViewModel()) {
    val threads by vm.threads.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        vm.refresh(silent = true)
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Messages", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Text(
            "Questions, requests or feedback? Write to us and we'll reply here.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        GradientButton("Message the resort", actions.compose, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), icon = Icons.Filled.Edit)
        Spacer(Modifier.height(8.dp))
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            LoadBox(threads, onRetry = { vm.refresh(initial = true) }, skeleton = { ListSkeleton(3, 90.dp) }) { list ->
                if (list.isEmpty()) {
                    EmptyState(Icons.Filled.Chat, "No conversations yet", "Start one with the button above. Replies from the resort appear here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(list, key = { _, t -> t.token ?: t.id }) { i, t ->
                            ThreadRow(t, { actions.openChat(t.token ?: t.id) }, Modifier.staggerIn(i))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(t: MsgThread, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val last = t.messages.lastOrNull()
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.subject.ifBlank { Fmt.titleCase(t.topic) },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    fontWeight = if (t.unreadByGuest) FontWeight.Bold else FontWeight.Medium,
                )
                Text(
                    (if (last?.sender == "staff") "Resort: " else "") + (last?.body ?: t.preview ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Fmt.smartTime(t.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t.unreadByGuest) PulsingBadge(1) else StatusPill(Fmt.titleCase(t.status), statusColor(t.status))
            }
        }
    }
}

// ───────────────────────── compose ─────────────────────────

private val topics = listOf("general" to "General", "booking" to "Booking", "event" to "Event", "feedback" to "Feedback", "complaint" to "Complaint", "other" to "Other")

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val messages: MessageRepository,
    auth: AuthRepository,
) : ViewModel() {
    data class Ui(
        val name: String = "", val email: String = "", val phone: String = "",
        val topic: String = "general", val subject: String = "", val message: String = "",
        val sending: Boolean = false, val error: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        auth.session.value?.user?.let { u -> _ui.update { it.copy(name = u.fullName, email = u.email, phone = u.phone) } }
    }

    fun edit(block: (Ui) -> Ui) = _ui.update { block(it).copy(error = null) }

    fun send(onDone: (String) -> Unit) {
        val s = _ui.value
        val err = when {
            s.name.isBlank() -> "Please tell us your name."
            !Patterns.EMAIL_ADDRESS.matcher(s.email.trim()).matches() -> "Please enter a valid email so we can reply."
            s.message.trim().length < 5 -> "Please write a little more (at least 5 characters)."
            else -> null
        }
        if (err != null) { _ui.update { it.copy(error = err) }; return }
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val body = CreateMessageBody(
                name = s.name.trim(), email = s.email.trim(), phone = s.phone.trim().ifBlank { null },
                subject = s.subject.trim().ifBlank { null }, topic = s.topic, message = s.message.trim(),
                source = "app", website = "",
            )
            when (val r = messages.create(body)) {
                is ApiResult.Success -> { _ui.update { it.copy(sending = false) }; onDone(r.data.token) }
                is ApiResult.Failure -> _ui.update { it.copy(sending = false, error = r.message) }
            }
        }
    }
}

@Composable
fun ComposeMessageScreen(onBack: () -> Unit, onSent: (String) -> Unit, vm: ComposeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        AppTopBar("Message the resort", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What is this about?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { (code, label) -> SelectChip(label, ui.topic == code, { vm.edit { it.copy(topic = code) } }) }
            }
            AppTextField(ui.subject, { v -> vm.edit { it.copy(subject = v) } }, "Subject (optional)")
            AppTextField(ui.message, { v -> vm.edit { it.copy(message = v) } }, "Your message", singleLine = false, minLines = 5, imeAction = ImeAction.Default)
            Text("Your details", style = MaterialTheme.typography.titleMedium)
            AppTextField(ui.name, { v -> vm.edit { it.copy(name = v) } }, "Name")
            AppTextField(ui.email, { v -> vm.edit { it.copy(email = v) } }, "Email (we'll reply here and by email)", keyboardType = KeyboardType.Email)
            AppTextField(ui.phone, { v -> vm.edit { it.copy(phone = v) } }, "Phone (optional)", keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
            InlineError(ui.error)
            GradientButton("Send message", { vm.send(onSent) }, Modifier.fillMaxWidth(), loading = ui.sending)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ───────────────────────── chat ─────────────────────────

@HiltViewModel
class ChatViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val messages: MessageRepository,
    private val socket: SocketManager,
) : ViewModel() {
    val token: String = handle.get<String>("token") ?: ""
    private val _thread = MutableStateFlow<Load<MsgThread>>(Load.Loading)
    val thread: StateFlow<Load<MsgThread>> = _thread.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val poller = Poller(viewModelScope, 10_000) { refresh(silent = true) }

    init {
        refresh()
        viewModelScope.launch { socket.events.collect { if (it.name == "message:update") refresh(silent = true) } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            when (val r = messages.thread(token)) {
                is ApiResult.Success -> _thread.value = Load.Ready(r.data)
                is ApiResult.Failure -> if (!silent || _thread.value !is Load.Ready) _thread.value = Load.Failed(r.message)
            }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        if (text.isBlank() || _sending.value) return
        _sending.value = true
        _error.value = null
        viewModelScope.launch {
            when (val r = messages.reply(token, text)) {
                is ApiResult.Success -> {
                    // optimistic append so the bubble shows immediately, then sync
                    val cur = (_thread.value as? Load.Ready)?.data
                    if (cur != null) {
                        _thread.value = Load.Ready(cur.copy(messages = cur.messages + Msg(sender = "guest", body = text.trim(), at = java.time.Instant.now().toString())))
                    }
                    onSent()
                    refresh(silent = true)
                }
                is ApiResult.Failure -> _error.value = r.message
            }
            _sending.value = false
        }
    }
}

@Composable
fun ChatScreen(onBack: () -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val thread by vm.thread.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        vm.refresh(silent = true)
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    val count = (thread as? Load.Ready)?.data?.messages?.size ?: 0
    LaunchedEffect(count) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        val title = (thread as? Load.Ready)?.data?.let { it.subject.ifBlank { Fmt.titleCase(it.topic) } } ?: "Conversation"
        AppTopBar(title, onBack)
        Box(Modifier.weight(1f)) {
            LoadBox(thread, onRetry = { vm.refresh() }, skeleton = { ListSkeleton(4, 70.dp) }) { t ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(t.messages, key = { i, m -> m.id.ifBlank { "m$i" } }) { _, m -> Bubble(m, mine = m.sender != "staff") }
                }
            }
        }
        InlineError(error, Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(text, { text = it }, "Write a message", Modifier.weight(1f), singleLine = false, imeAction = ImeAction.Default)
            IconButton(onClick = { vm.send(text) { text = "" } }, enabled = text.isNotBlank() && !sending, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun Bubble(m: Msg, mine: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp))
                .background(if (mine) cs.primary.copy(alpha = 0.22f) else cs.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!mine) Text(m.staffName?.takeIf { it.isNotBlank() } ?: "Resort team", style = MaterialTheme.typography.labelSmall, color = cs.primary)
            Text(m.body, style = MaterialTheme.typography.bodyMedium)
        }
        Text(Fmt.dateTime(m.at), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
