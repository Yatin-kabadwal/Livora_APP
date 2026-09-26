@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.roomservice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.CreateRoomServiceBody
import com.livora.corbett.data.api.MenuItem
import com.livora.corbett.data.api.RoomServiceReq
import com.livora.corbett.data.api.RsItemBody
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.OpsRepository
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.QtyStepper
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import com.livora.corbett.util.Poller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomServiceUi(
    val menu: Load<List<MenuItem>> = Load.Loading,
    val cart: Map<String, Int> = emptyMap(),
    val requests: Load<List<RoomServiceReq>> = Load.Loading,
    val submitting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class RoomServiceViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val resort: ResortRepository,
    private val ops: OpsRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val bookingId: String = handle.get<String>("bookingId") ?: ""
    private val _ui = MutableStateFlow(RoomServiceUi())
    val ui: StateFlow<RoomServiceUi> = _ui.asStateFlow()
    private val poller = Poller(viewModelScope, 15_000) { loadRequests() }

    init {
        loadMenu()
        loadRequests()
        viewModelScope.launch { socket.events.collect { if (it.name.startsWith("roomservice")) loadRequests() } }
    }

    fun startPolling() = poller.start()
    fun stopPolling() = poller.stop()
    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun loadMenu() {
        _ui.update { it.copy(menu = Load.Loading) }
        viewModelScope.launch {
            when (val r = resort.menu()) {
                is ApiResult.Success -> _ui.update { it.copy(menu = Load.Ready(r.data)) }
                is ApiResult.Failure -> _ui.update { it.copy(menu = Load.Failed(r.message)) }
            }
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            when (val r = ops.roomService(null)) {
                is ApiResult.Success -> {
                    val mine = r.data.filter { it.bookingId?.id == bookingId || it.bookingId?.id.isNullOrBlank() }
                        .sortedByDescending { it.createdAt ?: "" }
                    _ui.update { it.copy(requests = Load.Ready(mine)) }
                }
                is ApiResult.Failure -> _ui.update { if (it.requests is Load.Ready) it else it.copy(requests = Load.Failed(r.message)) }
            }
        }
    }

    fun setQty(itemId: String, qty: Int) {
        _ui.update {
            val c = it.cart.toMutableMap()
            if (qty <= 0) c.remove(itemId) else c[itemId] = qty
            it.copy(cart = c)
        }
    }

    fun placeOrder(notes: String, onDone: () -> Unit) {
        val st = _ui.value
        if (st.cart.isEmpty() || st.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val body = CreateRoomServiceBody(
                bookingId = bookingId,
                category = "food",
                title = "Food order",
                guestNotes = notes.trim().ifBlank { null },
                items = st.cart.map { (id, q) -> RsItemBody(id, q) },
            )
            when (val r = ops.createRoomService(body)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(submitting = false, cart = emptyMap(), message = "Order placed. We'll confirm it shortly.") }
                    loadRequests()
                    onDone()
                }
                is ApiResult.Failure -> _ui.update { it.copy(submitting = false, message = r.message) }
            }
        }
    }

    fun request(category: String, title: String, notes: String, onDone: () -> Unit) {
        if (_ui.value.submitting) return
        _ui.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val body = CreateRoomServiceBody(bookingId, category, title, notes.trim().ifBlank { null })
            when (val r = ops.createRoomService(body)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(submitting = false, message = "Request sent.") }
                    loadRequests()
                    onDone()
                }
                is ApiResult.Failure -> _ui.update { it.copy(submitting = false, message = r.message) }
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            when (val r = ops.cancelRoomService(id)) {
                is ApiResult.Success -> { _ui.update { it.copy(message = "Request cancelled.") }; loadRequests() }
                is ApiResult.Failure -> _ui.update { it.copy(message = r.message) }
            }
        }
    }
}

private data class ServiceKind(val category: String, val title: String, val icon: ImageVector, val hint: String)

private val serviceKinds = listOf(
    ServiceKind("laundry", "Laundry", Icons.Filled.LocalLaundryService, "What would you like washed or pressed?"),
    ServiceKind("amenities", "Extra amenities", Icons.Filled.Spa, "Towels, pillows, toiletries, blankets…"),
    ServiceKind("maintenance", "Maintenance", Icons.Filled.Build, "What needs attention in your room?"),
    ServiceKind("transport", "Transport", Icons.Filled.DirectionsCar, "Where and when do you need a ride?"),
    ServiceKind("other", "Something else", Icons.Filled.RoomService, "Tell us what you need."),
)

@Composable
fun RoomServiceScreen(onBack: () -> Unit, vm: RoomServiceViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val snack = LocalSnackbar.current
    val cs = MaterialTheme.colorScheme

    LifecycleResumeEffect(Unit) {
        vm.loadRequests()
        vm.startPolling()
        onPauseOrDispose { vm.stopPolling() }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        AppTopBar("Room service", onBack)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip("Order food", tab == 0, { tab = 0 })
            SelectChip("Requests", tab == 1, { tab = 1 })
            val active = (ui.requests as? Load.Ready)?.data?.count { it.status != "delivered" && it.status != "cancelled" } ?: 0
            SelectChip(if (active > 0) "Track ($active)" else "Track", tab == 2, { tab = 2 })
        }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "rs-tab", modifier = Modifier.weight(1f)) { t ->
            when (t) {
                0 -> FoodTab(ui, vm) { tab = 2 }
                1 -> RequestTab(ui, vm) { tab = 2 }
                else -> TrackTab(ui, vm)
            }
        }
    }
}

@Composable
private fun FoodTab(ui: RoomServiceUi, vm: RoomServiceViewModel, onPlaced: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        LoadBox(ui.menu, onRetry = { vm.loadMenu() }, skeleton = { ListSkeleton(5, 84.dp) }) { items ->
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.RoomService, "Menu unavailable", "The in-room menu isn't available right now. You can still send a request from the Requests tab.")
            } else {
                val cats = items.map { it.category }.filter { it.isNotBlank() }.distinct()
                val shown = if (category == null) items else items.filter { it.category == category }
                val total = items.sumOf { (ui.cart[it.id] ?: 0) * it.price }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = if (ui.cart.isEmpty()) 24.dp else 240.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectChip("All", category == null, { category = null })
                            cats.forEach { c -> SelectChip(Fmt.titleCase(c), category == c, { category = c }) }
                        }
                    }
                    itemsIndexed(shown, key = { _, m -> m.id }) { i, m ->
                        MenuRow(m, ui.cart[m.id] ?: 0, { vm.setQty(m.id, it) }, Modifier.staggerIn(i))
                    }
                }
                if (ui.cart.isNotEmpty()) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppTextField(notes, { notes = it }, "Notes for the kitchen (optional)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.money(total), style = MaterialTheme.typography.headlineSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                                Text("${ui.cart.values.sum()} items · billed to your room", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            GradientButton("Place order", { vm.placeOrder(notes) { notes = ""; onPlaced() } }, loading = ui.submitting, icon = Icons.Filled.ShoppingCart)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(m: MenuItem, qty: Int, onQty: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(if (m.isVeg) Brand.success else Brand.danger),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleSmall)
                m.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Text(Fmt.money(m.price), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            QtyStepper(qty, onQty)
        }
    }
}

@Composable
private fun RequestTab(ui: RoomServiceUi, vm: RoomServiceViewModel, onSent: () -> Unit) {
    var selected by remember { mutableStateOf<ServiceKind?>(null) }
    var notes by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What do you need?", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            serviceKinds.forEach { k -> SelectChip(k.title, selected == k, { selected = k }, leading = k.icon) }
        }
        val k = selected
        if (k != null) {
            AppTextField(notes, { notes = it }, k.hint, singleLine = false, minLines = 3)
            GradientButton(
                "Send request", { vm.request(k.category, k.title, notes) { notes = ""; selected = null; onSent() } },
                Modifier.fillMaxWidth(), loading = ui.submitting, enabled = notes.isNotBlank() || k.category != "other",
            )
        } else {
            Text("Pick a request type above. Our team will confirm it and keep you updated here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackTab(ui: RoomServiceUi, vm: RoomServiceViewModel) {
    LoadBox(ui.requests, onRetry = { vm.loadRequests() }, skeleton = { ListSkeleton(3, 130.dp) }) { list ->
        if (list.isEmpty()) {
            EmptyState(Icons.Filled.RoomService, "Nothing yet", "Orders and requests you make during your stay appear here with live status.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(list, key = { _, r -> r.id }) { i, r -> RequestCard(r, { vm.cancel(r.id) }, Modifier.staggerIn(i)) }
            }
        }
    }
}

@Composable
private fun RequestCard(r: RoomServiceReq, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title.ifBlank { Fmt.titleCase(r.category) }, style = MaterialTheme.typography.titleMedium)
                Text(Fmt.dateTime(r.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(Fmt.titleCase(r.status), statusColor(r.status))
        }
        if (r.items.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            r.items.forEach { Text("${it.qty} × ${it.name}", style = MaterialTheme.typography.bodyMedium) }
        }
        r.guestNotes?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (r.totalAmount > 0) Text(Fmt.money(r.totalAmount), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (r.status != "cancelled") {
            Spacer(Modifier.height(10.dp))
            StatusTracker(r.status)
            if (r.estimatedMinutes != null && r.status != "delivered") {
                Text("Estimated ${r.estimatedMinutes} min", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        r.staffNotes?.takeIf { it.isNotBlank() }?.let { Text("Resort: $it", style = MaterialTheme.typography.bodySmall) }
        if (r.status == "pending") {
            Spacer(Modifier.height(8.dp))
            GhostButton("Cancel request", onCancel)
        }
    }
}

private val trackSteps = listOf("pending" to "Received", "accepted" to "Accepted", "in_progress" to "On the way", "delivered" to "Delivered")

/** Four-step live tracker with animated connectors. */
@Composable
fun StatusTracker(status: String) {
    val idx = trackSteps.indexOfFirst { it.first == status }.coerceAtLeast(0)
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        trackSteps.forEachIndexed { i, (_, label) ->
            val done = i <= idx
            val dot by animateColorAsState(if (done) cs.primary else cs.outline, tween(500), label = "trk-dot")
            val leftFill by animateFloatAsState(if (i <= idx) 1f else 0f, tween(500), label = "trk-l")
            val rightFill by animateFloatAsState(if (i < idx) 1f else 0f, tween(500), label = "trk-r")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) androidx.compose.ui.graphics.Color.Transparent else cs.primary.copy(alpha = 0.15f + 0.85f * leftFill)))
                    Box(Modifier.size(if (i == idx) 18.dp else 14.dp).clip(CircleShape).background(dot))
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == trackSteps.lastIndex) androidx.compose.ui.graphics.Color.Transparent else cs.primary.copy(alpha = 0.15f + 0.85f * rightFill)))
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (done) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
