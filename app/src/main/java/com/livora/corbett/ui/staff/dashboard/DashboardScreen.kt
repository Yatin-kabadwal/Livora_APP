@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.staff.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.BoardRoom
import com.livora.corbett.data.api.Dashboard
import com.livora.corbett.data.api.DashStay
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.data.repo.BookingRepository
import com.livora.corbett.data.repo.OpsRepository
import com.livora.corbett.ui.components.ErrorState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.SectionTitle
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.WakeHint
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.navigation.StaffActions
import com.livora.corbett.ui.staff.KpiCard
import com.livora.corbett.ui.staff.OccupancyRing
import com.livora.corbett.ui.staff.PaymentSheet
import com.livora.corbett.ui.staff.roomStatusColor
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class PayTarget(val bookingId: String, val name: String, val balance: Double)

data class DashUi(
    val dash: Load<Dashboard> = Load.Loading,
    val board: List<BoardRoom> = emptyList(),
    val refreshing: Boolean = false,
    val busyRef: String? = null,
    val pay: PayTarget? = null,
    val payBusy: Boolean = false,
    val payError: String? = null,
    val isManager: Boolean = false,
    val firstName: String = "",
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val ops: OpsRepository,
    private val bookings: BookingRepository,
    private val socket: SocketManager,
    auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        DashUi(
            isManager = auth.session.value?.user?.isManager == true,
            firstName = auth.session.value?.user?.firstName.orEmpty(),
        ),
    )
    val ui: StateFlow<DashUi> = _ui.asStateFlow()
    private val _msgs = Channel<String>(Channel.BUFFERED)
    val messages = _msgs.receiveAsFlow()
    private val poller = Poller(viewModelScope, 60_000) { load(silent = true) }

    init {
        refresh(initial = true)
        viewModelScope.launch {
            socket.events.collect { e ->
                if (e.name.startsWith("booking") || e.name.startsWith("housekeeping") || e.name.startsWith("roomservice") || e.name.startsWith("message")) {
                    load(silent = true)
                }
            }
        }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()

    fun refresh(initial: Boolean = false) {
        if (initial) _ui.update { it.copy(dash = Load.Loading) } else _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch { load(silent = false) }
    }

    private suspend fun load(silent: Boolean) {
        val (d, b) = kotlinx.coroutines.coroutineScope {
            val dj = async { ops.dashboard() }
            val bj = async { ops.board() }
            dj.await() to bj.await()
        }
        _ui.update { st ->
            val dash = when (d) {
                is ApiResult.Success -> Load.Ready(d.data)
                is ApiResult.Failure -> if (st.dash is Load.Ready && silent) st.dash else Load.Failed(d.message)
            }
            val board = (b as? ApiResult.Success)?.data ?: st.board
            st.copy(dash = dash, board = board, refreshing = false)
        }
    }

    private suspend fun resolveId(s: DashStay): String? = s.bid ?: bookings.idForRef(s.bookingRef)

    fun open(s: DashStay, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            val id = resolveId(s)
            if (id != null) onOpen(id) else _msgs.trySend("Couldn't open that booking.")
        }
    }

    fun transition(s: DashStay, status: String, doneText: String) {
        if (_ui.value.busyRef != null) return
        _ui.update { it.copy(busyRef = s.bookingRef) }
        viewModelScope.launch {
            val id = resolveId(s)
            if (id == null) {
                _msgs.trySend("Couldn't find that booking.")
            } else {
                when (val r = bookings.setStatus(id, status)) {
                    is ApiResult.Success -> _msgs.trySend("${s.guestName}: $doneText")
                    is ApiResult.Failure -> _msgs.trySend(r.message)
                }
            }
            _ui.update { it.copy(busyRef = null) }
            load(silent = true)
        }
    }

    fun openPayment(s: DashStay) {
        viewModelScope.launch {
            val id = resolveId(s)
            if (id == null) _msgs.trySend("Couldn't find that booking.")
            else _ui.update { it.copy(pay = PayTarget(id, s.guestName, s.balanceDue), payError = null) }
        }
    }

    fun closePayment() = _ui.update { it.copy(pay = null, payError = null, payBusy = false) }

    fun submitPayment(amount: Double, method: String, ref: String) {
        val t = _ui.value.pay ?: return
        _ui.update { it.copy(payBusy = true, payError = null) }
        viewModelScope.launch {
            when (val r = bookings.addPayment(t.bookingId, amount, method, ref, null)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(pay = null, payBusy = false) }
                    _msgs.trySend("Payment of ${Fmt.money(amount)} recorded.")
                    load(silent = true)
                }
                is ApiResult.Failure -> _ui.update { it.copy(payBusy = false, payError = r.message) }
            }
        }
    }

    fun setRoomStatus(roomId: String, status: String) {
        viewModelScope.launch {
            when (val r = ops.setRoomStatus(roomId, status)) {
                is ApiResult.Success -> _msgs.trySend("Room updated to ${Fmt.titleCase(status)}.")
                is ApiResult.Failure -> _msgs.trySend(r.message)
            }
            load(silent = true)
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now(Fmt.IST).hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun DashboardScreen(
    actions: StaffActions,
    onTab: (Int) -> Unit,
    onSignOut: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current
    var menuOpen by remember { mutableStateOf(false) }
    var roomSheet by remember { mutableStateOf<BoardRoom?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${greeting()}${if (ui.firstName.isBlank()) "" else ", ${ui.firstName}"}", style = MaterialTheme.typography.headlineMedium)
                Text(Fmt.dayShort(Fmt.today()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Profile & settings") }, onClick = { menuOpen = false; actions.profile() })
                    DropdownMenuItem(text = { Text("New walk-in booking") }, onClick = { menuOpen = false; actions.walkIn() })
                    DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                }
            }
        }
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            when (val d = ui.dash) {
                is Load.Loading -> Column { ListSkeleton(4, 110.dp, Modifier.padding(top = 12.dp)); WakeHint() }
                is Load.Failed -> ErrorState(d.message, { vm.refresh(initial = true) })
                is Load.Ready -> DashboardContent(d.data, ui, vm, actions, onTab, onRoom = { roomSheet = it })
            }
        }
    }

    val rs = roomSheet
    if (rs != null) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { roomSheet = null }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Room ${rs.roomNumber}  ·  ${rs.name}", style = MaterialTheme.typography.headlineSmall)
                rs.current?.let { Text("In house: ${it.guestName} (${it.bookingRef})", style = MaterialTheme.typography.bodyMedium) }
                rs.arrivingToday?.let { Text("Arriving today: ${it.guestName}", style = MaterialTheme.typography.bodyMedium) }
                rs.departingToday?.let { Text("Departing today: ${it.guestName}", style = MaterialTheme.typography.bodyMedium) }
                Text("Set status", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("available", "occupied", "housekeeping", "maintenance").forEach { st ->
                        SelectChip(Fmt.titleCase(st), rs.status == st, { vm.setRoomStatus(rs.id, st); roomSheet = null })
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    ui.pay?.let { t ->
        PaymentSheet(t.name, t.balance, ui.payBusy, ui.payError, { a, m, r -> vm.submitPayment(a, m, r) }, { vm.closePayment() })
    }
}

@Composable
private fun DashboardContent(
    d: Dashboard,
    ui: DashUi,
    vm: DashboardViewModel,
    actions: StaffActions,
    onTab: (Int) -> Unit,
    onRoom: (BoardRoom) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val total = d.occupancy.total.takeIf { it > 0 } ?: ui.board.size
    val occ = if (total > 0) d.occupancy.occupiedTonight.toFloat() / total else (d.occupancy.rate.toFloat().let { if (it > 1f) it / 100f else it })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggerIn(0)) {
                GlassCard(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                    Text("Occupancy tonight", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OccupancyRing(occ, "${Math.round(occ * 100)}%")
                    }
                    Text("${d.occupancy.occupiedTonight} of $total rooms", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("In house", d.occupancy.inHouse.toDouble(), Modifier.fillMaxWidth(), caption = "guests staying")
                    KpiCard("Pending payments", d.pendingPayments.total, Modifier.fillMaxWidth(), money = true, caption = "${d.pendingPayments.count} bookings", accent = Brand.warning, onClick = { onTab(1) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggerIn(1)) {
                KpiCard("New bookings (24h)", d.counters.newBookings24h.toDouble(), Modifier.weight(1f), onClick = { onTab(1) })
                KpiCard("Unread messages", d.counters.unreadMessages.toDouble(), Modifier.weight(1f), accent = if (d.counters.unreadMessages > 0) Brand.danger else Color0, onClick = { onTab(4) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggerIn(2)) {
                KpiCard("Pending requests", d.counters.pendingService.toDouble(), Modifier.weight(1f), accent = if (d.counters.pendingService > 0) Brand.warning else Color0, onClick = { onTab(3) })
                KpiCard("Housekeeping tasks", d.counters.pendingHousekeeping.toDouble(), Modifier.weight(1f), onClick = { onTab(2) })
            }
        }
        if (ui.isManager) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggerIn(3)) {
                    KpiCard("Revenue today", d.today.totalRevenue, Modifier.weight(1f), money = true)
                    KpiCard("Month to date", d.monthToDate.totalRevenue, Modifier.weight(1f), money = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Net profit (MTD)", d.monthToDate.netProfit, Modifier.weight(1f), money = true, accent = if (d.monthToDate.netProfit < 0) Brand.danger else Brand.success)
                    KpiCard("Expenses today", d.today.expenseTotal, Modifier.weight(1f), money = true)
                }
            }
        }

        item { SectionTitle("Arrivals today (${d.arrivals.size})", Modifier.padding(top = 8.dp)) }
        if (d.arrivals.isEmpty()) item { EmptyLine("No arrivals scheduled for today.") }
        items(d.arrivals.size) { i ->
            val s = d.arrivals[i]
            StayRow(s, "Check in", ui.busyRef == s.bookingRef,
                onPrimary = { vm.transition(s, "checked_in", "checked in") },
                onCollect = { vm.openPayment(s) },
                onOpen = { vm.open(s, actions.openBooking) },
                onCall = { Intents.call(ctx, s.guestPhone) })
        }

        item { SectionTitle("Departures today (${d.departures.size})", Modifier.padding(top = 8.dp)) }
        if (d.departures.isEmpty()) item { EmptyLine("No departures today.") }
        items(d.departures.size) { i ->
            val s = d.departures[i]
            StayRow(s, "Check out", ui.busyRef == s.bookingRef,
                onPrimary = { vm.transition(s, "checked_out", "checked out") },
                onCollect = { vm.openPayment(s) },
                onOpen = { vm.open(s, actions.openBooking) },
                onCall = { Intents.call(ctx, s.guestPhone) })
        }

        item { SectionTitle("Rooms", Modifier.padding(top = 8.dp)) }
        item {
            if (ui.board.isEmpty()) {
                EmptyLine("Room board unavailable.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ui.board.chunked(3).forEach { rowRooms ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowRooms.forEach { r -> RoomTile(r, Modifier.weight(1f)) { onRoom(r) } }
                            repeat(3 - rowRooms.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Unspecified

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
}

@Composable
private fun StayRow(
    s: DashStay,
    primaryLabel: String,
    busy: Boolean,
    onPrimary: () -> Unit,
    onCollect: () -> Unit,
    onOpen: () -> Unit,
    onCall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.guestName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "Room ${s.roomNumber}${if (s.roomName.isNotBlank()) " · ${s.roomName}" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                )
                Text(
                    Fmt.plural(s.adults, "adult") + (if (s.children > 0) ", ${Fmt.plural(s.children, "child", "children")}" else "") + "  ·  " + s.bookingRef,
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            if (s.guestPhone.isNotBlank()) {
                IconButton(onClick = onCall) { Icon(Icons.Filled.Call, "Call ${s.guestName}", tint = cs.primary) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientButton(primaryLabel, onPrimary, Modifier.weight(1f), loading = busy)
            if (s.balanceDue > 0) {
                GhostButton("Collect ${Fmt.money(s.balanceDue)}", onCollect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoomTile(r: BoardRoom, modifier: Modifier, onClick: () -> Unit) {
    val c = roomStatusColor(r.status)
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClickLabel = "Change status of room ${r.roomNumber}", onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(c))
            Spacer(Modifier.width(6.dp))
            Text(Fmt.titleCase(r.status), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
        }
        Text(r.roomNumber.ifBlank { "-" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(r.current?.guestName ?: r.arrivingToday?.guestName?.let { "→ $it" } ?: r.name, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}
