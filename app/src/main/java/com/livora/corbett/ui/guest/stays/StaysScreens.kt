@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.stays

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.Booking
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.realtime.SocketManager
import com.livora.corbett.data.repo.BookingRepository
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.ConfirmDialog
import com.livora.corbett.ui.components.DetailRow
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.RatingStars
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.statusColor
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.navigation.GuestActions
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

// ───────────────────────── list (Trips tab) ─────────────────────────

@HiltViewModel
class StaysViewModel @Inject constructor(
    private val bookings: BookingRepository,
    private val socket: SocketManager,
) : ViewModel() {
    private val _stays = MutableStateFlow<Load<List<Booking>>>(Load.Loading)
    val stays: StateFlow<Load<List<Booking>>> = _stays.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        refresh(initial = true)
        viewModelScope.launch { socket.events.collect { if (it.name == "booking:update") refresh(silent = true) } }
    }

    fun refresh(initial: Boolean = false, silent: Boolean = false) {
        if (!initial && !silent) _refreshing.value = true
        if (initial || _stays.value !is Load.Ready && !silent) _stays.value = Load.Loading
        viewModelScope.launch {
            when (val r = bookings.mine()) {
                is ApiResult.Success -> _stays.value = Load.Ready(r.data.filter { !it.isBlock })
                is ApiResult.Failure -> if (_stays.value !is Load.Ready) _stays.value = Load.Failed(r.message)
            }
            _refreshing.value = false
        }
    }
}

fun isUpcoming(b: Booking): Boolean {
    if (b.status != "confirmed" && b.status != "checked_in") return false
    val out = Fmt.parseDay(b.checkOut) ?: return true
    return !out.isBefore(Fmt.today())
}

@Composable
fun StaysScreen(
    loggedIn: Boolean,
    actions: GuestActions,
    onBrowseRooms: () -> Unit,
    vm: StaysViewModel = hiltViewModel(),
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("My Stays", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        if (!loggedIn) {
            AnonymousStays(actions)
        } else {
            val stays by vm.stays.collectAsStateWithLifecycle()
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            var tab by remember { mutableIntStateOf(0) }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Upcoming", tab == 0, { tab = 0 })
                SelectChip("Past & cancelled", tab == 1, { tab = 1 })
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                LoadBox(stays, onRetry = { vm.refresh(initial = true) }, skeleton = { ListSkeleton(3, 150.dp) }) { all ->
                    val shown = if (tab == 0) all.filter { isUpcoming(it) }.sortedBy { it.checkIn } else all.filter { !isUpcoming(it) }.sortedByDescending { it.checkIn }
                    if (shown.isEmpty()) {
                        EmptyState(
                            Icons.Filled.Luggage,
                            if (tab == 0) "No upcoming stays" else "No past stays yet",
                            if (tab == 0) "When you book a room it will appear here." else "Completed and cancelled stays show up here.",
                            actionLabel = if (tab == 0) "Browse rooms" else null,
                            onAction = if (tab == 0) onBrowseRooms else null,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(shown, key = { _, b -> b.id }) { i, b ->
                                StayCard(b, { actions.openStay(b.id) }, Modifier.staggerIn(i))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnonymousStays(actions: GuestActions) {
    var ref by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Find your booking", style = MaterialTheme.typography.titleLarge)
            Text("Enter the reference from your confirmation and the email you booked with.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            AppTextField(ref, { ref = it.uppercase() }, "Booking reference (e.g. CVL-2026-00001)")
            Spacer(Modifier.height(8.dp))
            AppTextField(email, { email = it }, "Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Done)
            Spacer(Modifier.height(12.dp))
            GradientButton("Find booking", { actions.openLookup(ref.trim(), email.trim()) }, Modifier.fillMaxWidth(), enabled = ref.isNotBlank() && email.contains("@"))
        }
        EmptyState(Icons.Filled.Luggage, "Sign in for all your stays", "Create an account or sign in to see every booking, message the resort and order to your room.", actionLabel = "Sign in", onAction = actions.login)
    }
}

@Composable
fun StayCard(b: Booking, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            RemoteImage(b.roomImage, b.roomName, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.3f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color(0xCC050B08))))
            StatusPill(Fmt.titleCase(b.status), statusColor(b.status), Modifier.align(Alignment.TopEnd).padding(10.dp))
            Text(
                b.roomName, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp), maxLines = 1,
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${Fmt.dayShort(b.checkIn)} → ${Fmt.dayShort(b.checkOut)}  ·  ${Fmt.plural(b.nights, "night")}", style = MaterialTheme.typography.titleSmall)
            Row {
                Text(b.bookingRef, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(Fmt.money(b.totalAmount), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ───────────────────────── detail ─────────────────────────

sealed interface StayEvent {
    data class Toast(val text: String) : StayEvent
    data class OpenPdf(val file: File) : StayEvent
}

data class StayUi(
    val booking: Load<Booking> = Load.Loading,
    val busy: Boolean = false,
    val invoiceBusy: Boolean = false,
    val reviewed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class StayDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val bookings: BookingRepository,
    private val resort: ResortRepository,
    config: ResortConfig,
    private val socket: SocketManager,
) : ViewModel() {
    val id: String = handle.get<String>("id") ?: ""
    val ref: String = handle.get<String>("ref") ?: ""
    val email: String = handle.get<String>("email") ?: ""
    val lookupMode: Boolean get() = id.isBlank()

    private val _ui = MutableStateFlow(StayUi())
    val ui: StateFlow<StayUi> = _ui.asStateFlow()
    val settings: StateFlow<PublicSettings> = config.settings
    private val _events = Channel<StayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch { socket.events.collect { if (it.name == "booking:update") load(silent = true) } }
    }

    fun load(silent: Boolean = false) {
        if (!silent) _ui.update { it.copy(booking = Load.Loading) }
        viewModelScope.launch {
            val r = if (lookupMode) bookings.lookup(ref, email).let { res ->
                when (res) {
                    is ApiResult.Success -> ApiResult.Success(res.data.booking)
                    is ApiResult.Failure -> res
                }
            } else bookings.get(id)
            when (r) {
                is ApiResult.Success -> _ui.update { it.copy(booking = Load.Ready(r.data)) }
                is ApiResult.Failure -> if (!silent || _ui.value.booking !is Load.Ready) _ui.update { it.copy(booking = Load.Failed(r.message)) }
            }
        }
    }

    fun cancel(reason: String) {
        val b = (_ui.value.booking as? Load.Ready)?.data ?: return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = if (lookupMode) bookings.lookupCancel(b.bookingRef, email, reason).let { res ->
                when (res) {
                    is ApiResult.Success -> ApiResult.Success(b.copy(status = "cancelled"))
                    is ApiResult.Failure -> res
                }
            } else bookings.cancel(b.id, reason)
            when (r) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(busy = false, booking = Load.Ready(r.data)) }
                    _events.trySend(StayEvent.Toast("Your booking has been cancelled."))
                    if (lookupMode) load(silent = true)
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(busy = false, error = r.message) }
                    _events.trySend(StayEvent.Toast(r.message))
                }
            }
        }
    }

    fun downloadInvoice() {
        val b = (_ui.value.booking as? Load.Ready)?.data ?: return
        if (b.id.isBlank()) return
        _ui.update { it.copy(invoiceBusy = true) }
        viewModelScope.launch {
            when (val r = bookings.downloadInvoice(b.id, b.bookingRef, File(appContext.cacheDir, "invoices"))) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(invoiceBusy = false) }
                    _events.trySend(StayEvent.OpenPdf(r.data))
                }
                is ApiResult.Failure -> {
                    _ui.update { it.copy(invoiceBusy = false) }
                    _events.trySend(StayEvent.Toast(r.message))
                }
            }
        }
    }

    fun review(rating: Int, title: String, comment: String, onDone: () -> Unit) {
        val b = (_ui.value.booking as? Load.Ready)?.data ?: return
        viewModelScope.launch {
            when (val r = resort.postReview(b.bookingRef, b.guestEmail.ifBlank { email }, rating, title, comment)) {
                is ApiResult.Success -> {
                    _ui.update { it.copy(reviewed = true) }
                    _events.trySend(StayEvent.Toast("Thank you for your review!"))
                    onDone()
                }
                is ApiResult.Failure -> _events.trySend(StayEvent.Toast(r.message))
            }
        }
    }
}

/** Free-cancellation deadline: check-in day at check-in time minus N hours (IST). */
fun cancellationDeadline(b: Booking, settings: PublicSettings): LocalDateTime? {
    val day = Fmt.parseDay(b.checkIn) ?: return null
    val t = try { LocalTime.parse(settings.checkInTime) } catch (e: Exception) { LocalTime.of(14, 0) }
    return LocalDateTime.of(day, t).minusHours(settings.cancellationHours.toLong())
}

@Composable
fun StayDetailScreen(
    onBack: () -> Unit,
    actions: GuestActions,
    vm: StayDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    var confirmCancel by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is StayEvent.Toast -> snack.showSnackbar(e.text)
                is StayEvent.OpenPdf -> Intents.openPdf(ctx, e.file)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        AppTopBar(if (vm.lookupMode) "Your booking" else "Stay details", onBack)
        LoadBox(ui.booking, onRetry = { vm.load() }, skeleton = { ListSkeleton(3, 140.dp) }) { b ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // header
                Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))) {
                    RemoteImage(b.roomImage, b.roomName, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.3f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color(0xDD050B08))))
                    StatusPill(Fmt.titleCase(b.status), statusColor(b.status), Modifier.align(Alignment.TopEnd).padding(12.dp))
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(b.roomName, style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                        Text(b.bookingRef, style = MaterialTheme.typography.labelLarge, color = com.livora.corbett.ui.theme.GoldLight)
                    }
                }

                StatusTimeline(b)

                GlassCard(Modifier.fillMaxWidth()) {
                    DetailRow("Check-in", "${Fmt.dayShort(b.checkIn)}, from ${settings.checkInTime}")
                    DetailRow("Check-out", "${Fmt.dayShort(b.checkOut)}, by ${settings.checkOutTime}")
                    DetailRow("Nights", b.nights.toString())
                    DetailRow("Guests", Fmt.plural(b.adults, "adult") + if (b.children > 0) ", ${Fmt.plural(b.children, "child", "children")}" else "")
                    DetailRow("Meal plan", settings.mealPlans.firstOrNull { it.code == b.mealPlan }?.label ?: b.mealPlan.uppercase())
                    DetailRow("Guest", b.guestName)
                    b.specialRequests?.takeIf { it.isNotBlank() }?.let { DetailRow("Requests", it) }
                    b.cancelReason?.takeIf { it.isNotBlank() && b.status == "cancelled" }?.let { DetailRow("Cancel reason", it) }
                }

                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Price", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    DetailRow("Room (${Fmt.money(b.pricePerNight)} × ${b.nights})", Fmt.money(b.roomAmount))
                    if (b.mealAmount > 0) DetailRow("Meals", Fmt.money(b.mealAmount))
                    if (b.discountAmount > 0) DetailRow("Offer ${b.promoCode ?: ""}".trim(), "− " + Fmt.money(b.discountAmount), valueColor = Brand.success)
                    b.extras.forEach { DetailRow(it.description, Fmt.money(it.amount)) }
                    DetailRow("Taxable value", Fmt.money(b.baseAmount))
                    DetailRow("GST @ ${(b.gstRate * 100).toInt()}%", Fmt.money(b.gstAmount))
                    DetailRow("Total (incl. GST)", Fmt.money(b.totalAmount), bold = true, valueColor = cs.primary)
                    if (b.payments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Payments", style = MaterialTheme.typography.titleSmall)
                        b.payments.forEach { p ->
                            DetailRow("${Fmt.titleCase(p.method)}  ·  ${Fmt.dayMedium(p.at)}", Fmt.money(p.amount))
                        }
                    }
                    DetailRow("Paid", Fmt.money(b.paidAmount))
                    DetailRow("Balance due at property", Fmt.money(b.balanceDue), bold = true)
                }

                // actions
                if (b.status == "checked_in" && !vm.lookupMode) {
                    GradientButton("Room service & requests", { actions.roomService(b.id) }, Modifier.fillMaxWidth(), icon = Icons.Filled.RoomService)
                }
                if (!vm.lookupMode && b.status != "cancelled" && b.status != "no_show") {
                    GhostButton(
                        if (ui.invoiceBusy) "Preparing invoice…" else "Download invoice (PDF)",
                        { vm.downloadInvoice() }, Modifier.fillMaxWidth(), enabled = !ui.invoiceBusy, icon = Icons.Filled.Download,
                    )
                }
                if (b.status == "checked_out" && !ui.reviewed && !vm.lookupMode) {
                    GhostButton("Rate your stay", { showReview = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Star)
                }
                if (!vm.lookupMode) {
                    GhostButton("Message the resort", { actions.compose() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Chat)
                }
                if (b.status == "confirmed") {
                    val deadline = cancellationDeadline(b, settings)
                    val open = deadline == null || LocalDateTime.now(Fmt.IST).isBefore(deadline)
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            if (open) "Free cancellation until ${deadline?.let { Fmt.dayShort(it.toLocalDate()) + ", " + it.toLocalTime() } ?: "${settings.cancellationHours} hours before check-in"}."
                            else "The free cancellation window (${settings.cancellationHours} hours before check-in) has passed. Please contact the resort to change or cancel.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (open) {
                            GhostButton(if (ui.busy) "Cancelling…" else "Cancel booking", { confirmCancel = true }, Modifier.fillMaxWidth(), enabled = !ui.busy)
                        } else {
                            GhostButton("Call the resort", { Intents.call(ctx, settings.phone) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Call)
                        }
                        InlineError(ui.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancel this booking?",
            text = "This releases your room. Payment is at the property, so nothing will be charged.",
            confirmLabel = "Yes, cancel",
            onConfirm = { confirmCancel = false; vm.cancel("Cancelled by guest via app") },
            onDismiss = { confirmCancel = false },
            destructive = true,
        )
    }
    if (showReview) {
        ReviewDialog(onDismiss = { showReview = false }, onSubmit = { r, t, c -> vm.review(r, t, c) { showReview = false } })
    }
}

@Composable
private fun ReviewDialog(onDismiss: () -> Unit, onSubmit: (Int, String, String) -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate your stay", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RatingStars(rating, { rating = it })
                AppTextField(title, { title = it }, "Title (optional)")
                AppTextField(comment, { comment = it }, "Tell us about your stay (min 10 characters)", singleLine = false, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(rating, title, comment) }, enabled = comment.trim().length >= 10) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Vertical status timeline: Booked → Checked in → Checked out (or Cancelled). */
@Composable
fun StatusTimeline(b: Booking) {
    val cancelled = b.status == "cancelled" || b.status == "no_show"
    val stage = when (b.status) {
        "confirmed" -> 0
        "checked_in" -> 1
        "checked_out" -> 2
        else -> 0
    }
    data class Step(val label: String, val sub: String, val done: Boolean)
    val steps = if (cancelled) listOf(
        Step("Booked", Fmt.dateTime(b.createdAt), true),
        Step(if (b.status == "no_show") "No show" else "Cancelled", Fmt.dateTime(b.cancelledAt), true),
    ) else listOf(
        Step("Booked", Fmt.dateTime(b.createdAt).ifBlank { "Confirmed" }, true),
        Step("Checked in", if (stage >= 1) Fmt.dateTime(b.actualCheckIn) else "Arrival ${Fmt.dayShort(b.checkIn)}", stage >= 1),
        Step("Checked out", if (stage >= 2) Fmt.dateTime(b.actualCheckOut) else "Departure ${Fmt.dayShort(b.checkOut)}", stage >= 2),
    )
    GlassCard(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, s ->
            val danger = cancelled && i == steps.lastIndex
            val target = if (s.done) 1f else 0f
            val fill by animateFloatAsState(target, tween(600), label = "tl-fill")
            val dot by animateColorAsState(
                when {
                    danger -> Brand.danger
                    s.done -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "tl-dot",
            )
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(dot.copy(alpha = 0.25f + 0.75f * fill)))
                    if (i < steps.lastIndex) {
                        Box(Modifier.width(2.dp).height(34.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 10.dp)) {
                    Text(s.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (s.done) FontWeight.Bold else FontWeight.Medium)
                    if (s.sub.isNotBlank()) Text(s.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
