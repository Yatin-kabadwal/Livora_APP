@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.staff.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.RoomServiceReq
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.OpsRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.PulsingBadge
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.guest.roomservice.StatusTracker
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RequestsUi(
    val list: Load<List<RoomServiceReq>> = Load.Loading,
    val refreshing: Boolean = false,
    val busyId: String? = null,
)

@HiltViewModel
class StaffRequestsViewModel @Inject constructor(
    private val ops: OpsRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(RequestsUi())
    val ui: StateFlow<RequestsUi> = _ui.asStateFlow()
    private val _msgs = Channel<String>(Channel.BUFFERED)
    val messages = _msgs.receiveAsFlow()
    private val poller = Poller(viewModelScope, 15_000) { load(silent = true) }

    init {
        refresh(initial = true)
        viewModelScope.launch { socket.events.collect { if (it.name.startsWith("roomservice")) load(silent = true) } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun refresh(initial: Boolean = false) {
        if (initial) _ui.update { it.copy(list = Load.Loading) } else _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { load(silent = false) }
    }

    private suspend fun load(silent: Boolean) {
        when (val r = ops.roomService(null)) {
            is ApiResult.Success -> _ui.update { it.copy(list = Load.Ready(r.data), refreshing = false) }
            is ApiResult.Failure -> _ui.update {
                it.copy(list = if (silent && it.list is Load.Ready) it.list else Load.Failed(r.message), refreshing = false)
            }
        }
    }

    fun setStatus(req: RoomServiceReq, status: String, eta: Int? = null, amount: Double? = null) {
        _ui.update { it.copy(busyId = req.id) }
        viewModelScope.launch {
            when (val r = ops.roomServiceStatus(req.id, status, eta, amount, null)) {
                is ApiResult.Success -> _msgs.trySend("Room ${req.roomNumber}: ${Fmt.titleCase(status)}")
                is ApiResult.Failure -> _msgs.trySend(r.message)
            }
            _ui.update { it.copy(busyId = null) }
            load(silent = true)
        }
    }
}

private fun rank(s: String) = when (s) { "pending" -> 0; "accepted" -> 1; "in_progress" -> 2; else -> 3 }

@Composable
fun RequestsScreen(vm: StaffRequestsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current
    var tab by remember { mutableStateOf(0) }
    var etaFor by remember { mutableStateOf<RoomServiceReq?>(null) }
    var amountFor by remember { mutableStateOf<RoomServiceReq?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Requests", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            val pending = (ui.list as? Load.Ready)?.data?.count { it.status == "pending" } ?: 0
            PulsingBadge(pending)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Active", tab == 0, { tab = 0 })
            SelectChip("Completed", tab == 1, { tab = 1 })
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            LoadBox(ui.list, onRetry = { vm.refresh(initial = true) }, skeleton = { ListSkeleton(4, 140.dp) }) { all ->
                val shown = if (tab == 0) all.filter { it.status in setOf("pending", "accepted", "in_progress") }.sortedWith(compareBy({ rank(it.status) }, { it.createdAt ?: "" }))
                else all.filter { it.status == "delivered" || it.status == "cancelled" }.sortedByDescending { it.createdAt ?: "" }.take(40)
                if (shown.isEmpty()) {
                    EmptyState(Icons.Filled.RoomService, if (tab == 0) "No active requests" else "Nothing completed yet", "New guest requests appear here instantly.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(shown, key = { _, r -> r.id }) { i, r ->
                            RequestCard(
                                r, ui.busyId == r.id, Modifier.staggerIn(i),
                                onAccept = { etaFor = r },
                                onStart = { vm.setStatus(r, "in_progress") },
                                onDeliver = { amountFor = r },
                                onDecline = { vm.setStatus(r, "cancelled") },
                            )
                        }
                    }
                }
            }
        }
    }

    etaFor?.let { req ->
        EtaDialog(onConfirm = { m -> vm.setStatus(req, "accepted", eta = m); etaFor = null }, onDismiss = { etaFor = null })
    }
    amountFor?.let { req ->
        AmountDialog(req.totalAmount, onConfirm = { a -> vm.setStatus(req, "delivered", amount = a); amountFor = null }, onDismiss = { amountFor = null })
    }
}

@Composable
private fun RequestCard(
    r: RoomServiceReq,
    busy: Boolean,
    modifier: Modifier,
    onAccept: () -> Unit,
    onStart: () -> Unit,
    onDeliver: () -> Unit,
    onDecline: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Room ${r.roomNumber.ifBlank { "?" }} · ${r.guestName}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${Fmt.titleCase(r.category)} · ${Fmt.smartTime(r.createdAt)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            StatusPill(Fmt.titleCase(r.status), statusColor(r.status))
        }
        Spacer(Modifier.height(6.dp))
        Text(r.title.ifBlank { Fmt.titleCase(r.category) }, style = MaterialTheme.typography.titleSmall)
        r.items.forEach { Text("${it.qty} × ${it.name}", style = MaterialTheme.typography.bodyMedium) }
        r.guestNotes?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        if (r.totalAmount > 0) Text(Fmt.money(r.totalAmount), style = MaterialTheme.typography.titleSmall, color = cs.primary)
        if (r.estimatedMinutes != null && r.status != "delivered" && r.status != "cancelled") {
            Text("ETA ${r.estimatedMinutes} min", style = MaterialTheme.typography.labelLarge, color = Brand.info)
        }
        if (r.status != "cancelled") {
            Spacer(Modifier.height(8.dp))
            StatusTracker(r.status)
        }
        Spacer(Modifier.height(10.dp))
        when (r.status) {
            "pending" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Accept", onAccept, Modifier.weight(1f), loading = busy)
                GhostButton("Decline", onDecline, Modifier.weight(1f), enabled = !busy)
            }
            "accepted" -> GradientButton("Start preparing", onStart, Modifier.fillMaxWidth(), loading = busy)
            "in_progress" -> GradientButton("Mark delivered", onDeliver, Modifier.fillMaxWidth(), loading = busy)
            else -> Unit
        }
    }
}

@Composable
private fun EtaDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf(20) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accept request", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estimated time to the guest")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30, 45, 60).forEach { m -> SelectChip("$m min", minutes == m, { minutes = m }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text("Accept") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AmountDialog(initial: Double, onConfirm: (Double?) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(if (initial > 0) initial.toLong().toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark delivered", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Amount to bill to the guest's room (leave empty for no charge).")
                AppTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount", keyboardType = KeyboardType.Number, prefix = "₹", imeAction = ImeAction.Done)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(amount.toDoubleOrNull()?.takeIf { it > 0 }) }) { Text("Deliver") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
