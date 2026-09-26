@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)

package com.livora.corbett.ui.staff.bookings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.Booking
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.BookingRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.ConfirmDialog
import com.livora.corbett.ui.components.DetailRow
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.staff.ChargeDialog
import com.livora.corbett.ui.staff.PaymentSheet
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ───────────────────────── list ─────────────────────────

data class BookingsUi(
    val items: Load<List<Booking>> = Load.Loading,
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
)

@HiltViewModel
class StaffBookingsViewModel @Inject constructor(
    private val bookings: BookingRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val _view = MutableStateFlow<String?>(null)
    val view: StateFlow<String?> = _view.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val reload = MutableStateFlow(0)
    private val _ui = MutableStateFlow(BookingsUi())
    val ui: StateFlow<BookingsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_view, _query.debounce(350), reload) { v, q, _ -> v to q }.collectLatest { (v, q) -> load(1, v, q) }
        }
        viewModelScope.launch { socket.events.collect { if (it.name.startsWith("booking")) reload.update { n -> n + 1 } } }
    }

    fun setView(v: String?) { _view.value = v }
    fun setQuery(q: String) { _query.value = q }
    fun refresh() { _ui.update { it.copy(refreshing = true) }; reload.update { it + 1 } }
    fun retry() { _ui.update { it.copy(items = Load.Loading) }; reload.update { it + 1 } }

    fun loadMore() {
        val st = _ui.value
        if (st.loadingMore || st.page >= st.pages) return
        viewModelScope.launch { load(st.page + 1, _view.value, _query.value) }
    }

    private suspend fun load(page: Int, v: String?, q: String) {
        val cur = _ui.value
        if (page == 1 && cur.items !is Load.Ready) _ui.update { it.copy(items = Load.Loading) }
        if (page > 1) _ui.update { it.copy(loadingMore = true) }
        when (val r = bookings.list(null, q, v, page)) {
            is ApiResult.Success -> {
                val existing = if (page > 1) ((_ui.value.items as? Load.Ready)?.data ?: emptyList()) else emptyList()
                val merged = (existing + r.data.bookings).distinctBy { it.id }
                _ui.update { it.copy(items = Load.Ready(merged), page = r.data.page, pages = r.data.pages, total = r.data.total, loadingMore = false, refreshing = false) }
            }
            is ApiResult.Failure -> _ui.update {
                it.copy(items = if (it.items is Load.Ready) it.items else Load.Failed(r.message), loadingMore = false, refreshing = false)
            }
        }
    }
}

private val bookingViews = listOf(null to "All", "arrivals" to "Arrivals", "inhouse" to "In house", "departures" to "Departures", "upcoming" to "Upcoming")

@Composable
fun StaffBookingsScreen(onOpen: (String) -> Unit, onWalkIn: () -> Unit, vm: StaffBookingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Bookings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
            Spacer(Modifier.height(8.dp))
            AppTextField(
                query, { vm.setQuery(it) }, "Search name, phone, email or reference",
                Modifier.padding(horizontal = 16.dp), leading = Icons.Filled.Search, imeAction = ImeAction.Search,
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(bookingViews) { _, (code, label) -> SelectChip(label, view == code, { vm.setView(code) }) }
            }
            PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                LoadBox(ui.items, onRetry = { vm.retry() }, skeleton = { ListSkeleton(5, 100.dp) }) { list ->
                    if (list.isEmpty()) {
                        EmptyState(Icons.Filled.Search, "No bookings found", "Try another filter or search term.")
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(), state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(list, key = { _, b -> b.id }) { i, b ->
                                BookingRow(b, { onOpen(b.id) }, Modifier.staggerIn(i))
                                if (i >= list.size - 4) LaunchedEffect(list.size) { vm.loadMore() }
                            }
                            if (ui.loadingMore) item { Text("Loading more…", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onWalkIn,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Walk-in") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun BookingRow(b: Booking, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (b.isBlock) "Blocked dates" else b.guestName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${b.roomName.ifBlank { "Room ${b.roomNumber}" }} · ${b.bookingRef}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
            StatusPill(Fmt.titleCase(b.status), statusColor(b.status))
        }
        Spacer(Modifier.height(6.dp))
        Text("${Fmt.dayShort(b.checkIn)} → ${Fmt.dayShort(b.checkOut)}  ·  ${Fmt.plural(b.nights, "night")}", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.money(b.totalAmount), style = MaterialTheme.typography.titleSmall, color = cs.primary)
            Spacer(Modifier.width(10.dp))
            if (b.balanceDue > 0 && b.status != "cancelled") {
                StatusPill("Due ${Fmt.money(b.balanceDue)}", Brand.warning)
            } else if (b.paymentStatus == "paid") {
                StatusPill("Paid", Brand.success)
            }
            Spacer(Modifier.weight(1f))
            Text(Fmt.titleCase(b.source), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

// ───────────────────────── detail ─────────────────────────

sealed interface SbEvent {
    data class Toast(val text: String) : SbEvent
    data class Pdf(val file: File) : SbEvent
}

data class SbUi(
    val booking: Load<Booking> = Load.Loading,
    val busy: Boolean = false,
    val payOpen: Boolean = false,
    val chargeOpen: Boolean = false,
    val sheetBusy: Boolean = false,
    val sheetError: String? = null,
    val invoiceBusy: Boolean = false,
)

@HiltViewModel
class StaffBookingViewModel @Inject constructor(
    handle: SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val bookings: BookingRepository,
    private val socket: SocketManager,
) : ViewModel() {
    val id: String = handle.get<String>("id") ?: ""
    private val _ui = MutableStateFlow(SbUi())
    val ui: StateFlow<SbUi> = _ui.asStateFlow()
    private val _events = Channel<SbEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { socket.events.collect { if (it.name == "booking:update") load(silent = true) } }
    }

    fun load(silent: Boolean = false) {
        if (!silent) _ui.update { it.copy(booking = Load.Loading) }
        viewModelScope.launch {
            when (val r = bookings.get(id)) {
                is ApiResult.Success -> _ui.update { it.copy(booking = Load.Ready(r.data)) }
                is ApiResult.Failure -> if (!silent || _ui.value.booking !is Load.Ready) _ui.update { it.copy(booking = Load.Failed(r.message)) }
            }
        }
    }

    private fun applyResult(r: ApiResult<Booking>, done: String) {
        when (r) {
            is ApiResult.Success -> {
                _ui.update { it.copy(booking = Load.Ready(r.data), busy = false, sheetBusy = false, payOpen = false, chargeOpen = false, sheetError = null) }
                _events.trySend(SbEvent.Toast(done))
            }
            is ApiResult.Failure -> {
                _ui.update { it.copy(busy = false, sheetBusy = false, sheetError = r.message) }
                _events.trySend(SbEvent.Toast(r.message))
            }
        }
    }

    fun setStatus(status: String, done: String) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch { applyResult(bookings.setStatus(id, status), done) }
    }

    fun cancel() {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch { applyResult(bookings.cancel(id, "Cancelled by staff"), "Booking cancelled.") }
    }

    fun openPay(open: Boolean) = _ui.update { it.copy(payOpen = open, sheetError = null) }
    fun openCharge(open: Boolean) = _ui.update { it.copy(chargeOpen = open, sheetError = null) }

    fun pay(amount: Double, method: String, ref: String) {
        _ui.update { it.copy(sheetBusy = true, sheetError = null) }
        viewModelScope.launch { applyResult(bookings.addPayment(id, amount, method, ref, null), "Payment of ${Fmt.money(amount)} recorded.") }
    }

    fun charge(desc: String, amount: Double) {
        _ui.update { it.copy(sheetBusy = true, sheetError = null) }
        viewModelScope.launch { applyResult(bookings.addExtra(id, desc, amount), "Charge added.") }
    }

    fun saveNotes(notes: String) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch { applyResult(bookings.patch(id, notes, null), "Notes saved.") }
    }

    fun invoice() {
        val b = (_ui.value.booking as? Load.Ready)?.data ?: return
        _ui.update { it.copy(invoiceBusy = true) }
        viewModelScope.launch {
            when (val r = bookings.downloadInvoice(b.id, b.bookingRef, File(appContext.cacheDir, "invoices"))) {
                is ApiResult.Success -> { _ui.update { it.copy(invoiceBusy = false) }; _events.trySend(SbEvent.Pdf(r.data)) }
                is ApiResult.Failure -> { _ui.update { it.copy(invoiceBusy = false) }; _events.trySend(SbEvent.Toast(r.message)) }
            }
        }
    }
}

private enum class Pending { CANCEL, NO_SHOW, CHECK_OUT_DUE }

@Composable
fun StaffBookingScreen(onBack: () -> Unit, vm: StaffBookingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = LocalSnackbar.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf<Pending?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is SbEvent.Toast -> snack.showSnackbar(e.text)
                is SbEvent.Pdf -> Intents.openPdf(ctx, e.file)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        AppTopBar("Booking", onBack)
        LoadBox(ui.booking, onRetry = { vm.load() }, skeleton = { ListSkeleton(3, 140.dp) }) { b ->
            var notes by remember(b.id, b.staffNotes) { mutableStateOf(b.staffNotes.orEmpty()) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.guestName, style = MaterialTheme.typography.headlineSmall)
                            Text(b.bookingRef, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusPill(Fmt.titleCase(b.status), statusColor(b.status))
                            StatusPill(Fmt.titleCase(b.paymentStatus), statusColor(b.paymentStatus))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (b.guestPhone.isNotBlank()) {
                            GhostButton("Call", { Intents.call(ctx, b.guestPhone) }, Modifier.weight(1f), icon = Icons.Filled.Call)
                            GhostButton("WhatsApp", { Intents.whatsapp(ctx, b.guestPhone, "Hello ${b.guestName}, regarding your booking ${b.bookingRef}") }, Modifier.weight(1f), icon = Icons.Filled.Chat)
                        }
                        if (b.guestEmail.isNotBlank()) GhostButton("Email", { Intents.email(ctx, b.guestEmail, "Booking ${b.bookingRef}") }, Modifier.weight(1f), icon = Icons.Filled.Email)
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    DetailRow("Room", "${b.roomName} (${b.roomNumber})")
                    DetailRow("Check-in", Fmt.dayShort(b.checkIn))
                    DetailRow("Check-out", Fmt.dayShort(b.checkOut))
                    DetailRow("Nights", b.nights.toString())
                    DetailRow("Guests", Fmt.plural(b.adults, "adult") + if (b.children > 0) ", ${Fmt.plural(b.children, "child", "children")}" else "")
                    DetailRow("Meal plan", b.mealPlan.uppercase())
                    DetailRow("Source", Fmt.titleCase(b.source))
                    DetailRow("Contact", listOf(b.guestPhone, b.guestEmail).filter { it.isNotBlank() }.joinToString("\n"))
                    b.specialRequests?.takeIf { it.isNotBlank() }?.let { DetailRow("Requests", it) }
                    if (b.actualCheckIn != null) DetailRow("Checked in", Fmt.dateTime(b.actualCheckIn))
                    if (b.actualCheckOut != null) DetailRow("Checked out", Fmt.dateTime(b.actualCheckOut))
                    b.cancelReason?.takeIf { it.isNotBlank() }?.let { DetailRow("Cancel reason", it) }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Billing", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    DetailRow("Room (${Fmt.money(b.pricePerNight)} × ${b.nights})", Fmt.money(b.roomAmount))
                    if (b.mealAmount > 0) DetailRow("Meals", Fmt.money(b.mealAmount))
                    if (b.discountAmount > 0) DetailRow("Discount ${b.promoCode ?: ""}".trim(), "− " + Fmt.money(b.discountAmount), valueColor = Brand.success)
                    b.extras.forEach { DetailRow(it.description, Fmt.money(it.amount)) }
                    DetailRow("GST @ ${(b.gstRate * 100).toInt()}%", Fmt.money(b.gstAmount))
                    DetailRow("Total", Fmt.money(b.totalAmount), bold = true, valueColor = cs.primary)
                    b.payments.forEach { p -> DetailRow("${Fmt.titleCase(p.method)} · ${Fmt.dayMedium(p.at)}${p.reference?.let { " · $it" } ?: ""}", Fmt.money(p.amount)) }
                    DetailRow("Paid", Fmt.money(b.paidAmount))
                    DetailRow("Balance due", Fmt.money(b.balanceDue), bold = true, valueColor = if (b.balanceDue > 0) Brand.warning else Brand.success)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Staff notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    AppTextField(notes, { notes = it }, "Internal notes", singleLine = false, minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Save notes", { vm.saveNotes(notes) }, Modifier.fillMaxWidth(), enabled = notes != b.staffNotes.orEmpty() && !ui.busy)
                }

                if (b.status == "confirmed") {
                    GradientButton("Check in", { vm.setStatus("checked_in", "Guest checked in.") }, Modifier.fillMaxWidth(), loading = ui.busy)
                }
                if (b.status == "checked_in") {
                    GradientButton("Check out", { if (b.balanceDue > 0) confirm = Pending.CHECK_OUT_DUE else vm.setStatus("checked_out", "Guest checked out.") }, Modifier.fillMaxWidth(), loading = ui.busy)
                }
                if (b.status != "cancelled" && b.status != "no_show") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Collect payment", { vm.openPay(true) }, Modifier.weight(1f), icon = Icons.Filled.Payments)
                        GhostButton("Add charge", { vm.openCharge(true) }, Modifier.weight(1f), icon = Icons.Filled.Add)
                    }
                    GhostButton(if (ui.invoiceBusy) "Preparing invoice…" else "Invoice (PDF)", { vm.invoice() }, Modifier.fillMaxWidth(), enabled = !ui.invoiceBusy, icon = Icons.Filled.Download)
                }
                if (b.status == "confirmed") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Mark no-show", { confirm = Pending.NO_SHOW }, Modifier.weight(1f))
                        GhostButton("Cancel booking", { confirm = Pending.CANCEL }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (ui.payOpen) {
                PaymentSheet(b.guestName, b.balanceDue, ui.sheetBusy, ui.sheetError, { a, m, r -> vm.pay(a, m, r) }, { vm.openPay(false) })
            }
            if (ui.chargeOpen) {
                ChargeDialog(ui.sheetBusy, ui.sheetError, { d, a -> vm.charge(d, a) }, { vm.openCharge(false) })
            }
        }
    }

    when (confirm) {
        Pending.CANCEL -> ConfirmDialog("Cancel this booking?", "The room will be released. This can't be undone.", "Cancel booking", { confirm = null; vm.cancel() }, { confirm = null }, destructive = true)
        Pending.NO_SHOW -> ConfirmDialog("Mark as no-show?", "Use this when the guest did not arrive.", "Mark no-show", { confirm = null; vm.setStatus("no_show", "Marked as no-show.") }, { confirm = null }, destructive = true)
        Pending.CHECK_OUT_DUE -> ConfirmDialog("Balance still due", "This guest still owes a balance. Check out anyway? You can also record the payment first.", "Check out anyway", { confirm = null; vm.setStatus("checked_out", "Guest checked out.") }, { confirm = null })
        null -> Unit
    }
}
