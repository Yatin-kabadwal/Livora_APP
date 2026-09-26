@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.booking

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.Booking
import com.livora.corbett.data.api.CreateBookingBody
import com.livora.corbett.data.api.GuestInfo
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.api.Quote
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.repo.AuthRepository
import com.livora.corbett.data.repo.BookingRepository
import com.livora.corbett.data.repo.Criteria
import com.livora.corbett.data.repo.LastBooking
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.data.repo.SearchState
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.BookingCelebration
import com.livora.corbett.ui.components.DateRangeCalendar
import com.livora.corbett.ui.components.DetailRow
import com.livora.corbett.ui.components.ErrorState
import com.livora.corbett.ui.components.ForestBackdrop
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.QtyStepper
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.guest.rooms.QuoteCard
import com.livora.corbett.ui.guest.rooms.mealLabel
import com.livora.corbett.ui.theme.ForceDark
import com.livora.corbett.ui.theme.Forest950
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BookingUi(
    val room: Load<Room> = Load.Loading,
    val step: Int = 0,
    val forward: Boolean = true,
    val mealPlan: String = "ep",
    val promoInput: String = "",
    val promoApplied: String? = null,
    val promoMessage: String? = null,
    val promoOk: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val requests: String = "",
    val quote: Quote? = null,
    val quoteBusy: Boolean = false,
    val quoteError: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class BookingViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val resort: ResortRepository,
    private val bookings: BookingRepository,
    private val search: SearchState,
    private val config: ResortConfig,
    private val auth: AuthRepository,
    private val last: LastBooking,
) : ViewModel() {
    private val slug: String = handle.get<String>("slug") ?: ""
    private val _ui = MutableStateFlow(BookingUi())
    val ui: StateFlow<BookingUi> = _ui.asStateFlow()
    val criteria: StateFlow<Criteria> = search.criteria
    val settings: StateFlow<PublicSettings> = config.settings
    private var quoteJob: Job? = null

    init {
        val u = auth.session.value?.user
        if (u != null) {
            _ui.update { it.copy(name = u.fullName, email = u.email, phone = u.phone, loggedIn = true) }
        }
        load()
        viewModelScope.launch { search.criteria.collect { requote() } }
    }

    fun load() {
        _ui.update { it.copy(room = Load.Loading) }
        viewModelScope.launch {
            when (val r = resort.room(slug)) {
                is ApiResult.Success -> {
                    val plans = config.settings.value.mealPlans
                    val def = plans.firstOrNull { it.code == "ep" }?.code ?: plans.firstOrNull()?.code ?: "ep"
                    // keep guest counts inside the room's limits
                    search.update { c ->
                        c.copy(
                            adults = c.adults.coerceIn(1, r.data.maxAdults.coerceAtLeast(1)),
                            children = c.children.coerceIn(0, r.data.maxChildren.coerceAtLeast(0)),
                        )
                    }
                    _ui.update { it.copy(room = Load.Ready(r.data), mealPlan = def) }
                    requote()
                }
                is ApiResult.Failure -> _ui.update { it.copy(room = Load.Failed(r.message)) }
            }
        }
    }

    fun setDates(a: LocalDate?, b: LocalDate?) = search.setDates(a, b)
    fun setAdults(n: Int) = search.update { it.copy(adults = n) }
    fun setChildren(n: Int) = search.update { it.copy(children = n) }
    fun setMeal(code: String) { _ui.update { it.copy(mealPlan = code) }; requote() }
    fun setName(s: String) = _ui.update { it.copy(name = s, error = null) }
    fun setEmail(s: String) = _ui.update { it.copy(email = s, error = null) }
    fun setPhone(s: String) = _ui.update { it.copy(phone = s, error = null) }
    fun setRequests(s: String) = _ui.update { it.copy(requests = s) }
    fun setPromoInput(s: String) = _ui.update { it.copy(promoInput = s.uppercase().trim(), promoMessage = null) }

    fun applyPromo() {
        val st = _ui.value
        val room = (st.room as? Load.Ready)?.data ?: return
        val q = st.quote
        if (st.promoInput.isBlank()) return
        if (q == null) {
            _ui.update { it.copy(promoMessage = "Select your dates first.", promoOk = false) }
            return
        }
        viewModelScope.launch {
            when (val r = resort.validatePromo(st.promoInput, q.nights, q.roomAmount + q.mealAmount, room.type)) {
                is ApiResult.Success -> {
                    _ui.update {
                        it.copy(
                            promoApplied = if (r.data.valid) st.promoInput else null,
                            promoOk = r.data.valid,
                            promoMessage = r.data.message ?: if (r.data.valid) "Offer applied" else "This code can't be used for your stay.",
                        )
                    }
                    requote()
                }
                is ApiResult.Failure -> _ui.update { it.copy(promoOk = false, promoMessage = r.message) }
            }
        }
    }

    fun clearPromo() {
        _ui.update { it.copy(promoApplied = null, promoInput = "", promoMessage = null, promoOk = false) }
        requote()
    }

    private fun requote() {
        quoteJob?.cancel()
        val room = (_ui.value.room as? Load.Ready)?.data ?: return
        val c = search.criteria.value
        if (!c.hasDates) {
            _ui.update { it.copy(quote = null, quoteBusy = false, quoteError = null) }
            return
        }
        val st = _ui.value
        quoteJob = viewModelScope.launch {
            _ui.update { it.copy(quoteBusy = true, quoteError = null) }
            delay(250)
            when (val r = resort.quote(room.id, Fmt.iso(c.checkIn!!), Fmt.iso(c.checkOut!!), c.adults, c.children, st.mealPlan, st.promoApplied)) {
                is ApiResult.Success -> _ui.update { it.copy(quote = r.data, quoteBusy = false) }
                is ApiResult.Failure -> _ui.update { it.copy(quote = null, quoteBusy = false, quoteError = r.message) }
            }
        }
    }

    /** Returns true when the step's inputs are valid (otherwise sets an error). */
    fun next(): Boolean {
        val st = _ui.value
        val c = search.criteria.value
        when (st.step) {
            0 -> if (!c.hasDates) { _ui.update { it.copy(error = "Please select your check-in and check-out dates.") }; return false }
            1 -> {
                val err = when {
                    st.name.isBlank() -> "Please enter the guest's name."
                    !Patterns.EMAIL_ADDRESS.matcher(st.email.trim()).matches() -> "Please enter a valid email address."
                    st.phone.filter { it.isDigit() }.length < 10 -> "Please enter a valid phone number."
                    else -> null
                }
                if (err != null) { _ui.update { it.copy(error = err) }; return false }
            }
        }
        _ui.update { it.copy(step = (it.step + 1).coerceAtMost(2), forward = true, error = null) }
        return true
    }

    fun back(): Boolean {
        if (_ui.value.step == 0) return false
        _ui.update { it.copy(step = it.step - 1, forward = false, error = null) }
        return true
    }

    fun submit(onDone: () -> Unit) {
        val st = _ui.value
        val room = (st.room as? Load.Ready)?.data ?: return
        val c = search.criteria.value
        if (!c.hasDates || st.submitting) return
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val body = CreateBookingBody(
                roomId = room.id,
                checkIn = Fmt.iso(c.checkIn!!),
                checkOut = Fmt.iso(c.checkOut!!),
                adults = c.adults,
                children = c.children,
                mealPlan = st.mealPlan,
                promoCode = st.promoApplied,
                specialRequests = st.requests.trim().ifBlank { null },
                guest = GuestInfo(st.name.trim(), st.email.trim(), st.phone.trim()),
                source = "app",
            )
            when (val r = bookings.create(body)) {
                is ApiResult.Success -> {
                    last.set(r.data)
                    _ui.update { it.copy(submitting = false) }
                    onDone()
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(submitting = false, error = r.message, step = if (r.code == 409) 0 else it.step, forward = false)
                }
            }
        }
    }
}

private val stepTitles = listOf("Dates & guests", "Your details", "Review")

@Composable
fun BookingScreen(onBack: () -> Unit, onConfirmed: () -> Unit, vm: BookingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val criteria by vm.criteria.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    val goBack = { if (!vm.back()) onBack() }
    androidx.activity.compose.BackHandler(enabled = ui.step > 0) { vm.back() }

    Column(Modifier.fillMaxSize().background(cs.background).imePadding()) {
        AppTopBar("Book your stay", goBack)
        StepIndicator(ui.step)
        when (val st = ui.room) {
            is Load.Loading -> ListSkeleton(3, 140.dp)
            is Load.Failed -> ErrorState(st.message, { vm.load() })
            is Load.Ready -> {
                val room = st.data
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = ui.step,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 3 } + fadeOut(tween(200)))
                            } else {
                                (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(320))) togetherWith
                                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
                            }
                        },
                        label = "booking-step",
                    ) { step ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            RoomSummary(room, criteria)
                            when (step) {
                                0 -> StepDates(vm, ui, criteria, room, settings)
                                1 -> StepDetails(vm, ui)
                                else -> StepReview(ui, criteria, room, settings)
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
                // bottom action bar
                Column(
                    Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InlineError(ui.error)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val q = ui.quote
                            Text(if (q != null) Fmt.money(q.total) else "—", style = MaterialTheme.typography.headlineSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                            Text(
                                if (q != null) "${Fmt.plural(q.nights, "night")} · incl. GST" else "Select dates for a quote",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (ui.step < 2) {
                            GradientButton("Continue", { vm.next() }, enabled = true)
                        } else {
                            GradientButton("Confirm booking", { vm.submit(onConfirmed) }, loading = ui.submitting)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stepTitles.forEachIndexed { i, t ->
            val fill by animateFloatAsState(if (i <= step) 1f else 0f, tween(400), label = "step-fill")
            val c by animateColorAsState(if (i <= step) cs.primary else cs.onSurfaceVariant, label = "step-color")
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(fill).height(4.dp).clip(CircleShape).background(cs.primary))
                }
                Spacer(Modifier.height(4.dp))
                Text("${i + 1}. $t", style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoomSummary(room: Room, criteria: Criteria) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(room.imageUrls.firstOrNull(), room.name, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(
                    if (criteria.hasDates) "${Fmt.dayShort(criteria.checkIn)} → ${Fmt.dayShort(criteria.checkOut)}" else "Choose your dates below",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepDates(vm: BookingViewModel, ui: BookingUi, criteria: Criteria, room: Room, settings: PublicSettings) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        DateRangeCalendar(criteria.checkIn, criteria.checkOut, { a, b -> vm.setDates(a, b) })
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                criteria.checkIn == null -> "Select your check-in date"
                criteria.checkOut == null -> "Now select your check-out date"
                else -> "${Fmt.plural(Fmt.nights(criteria.checkIn, criteria.checkOut), "night")} · check-in ${settings.checkInTime}, check-out ${settings.checkOutTime}"
            },
            style = MaterialTheme.typography.titleSmall, color = cs.primary,
        )
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Adults", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            QtyStepper(criteria.adults, { vm.setAdults(it) }, min = 1, max = room.maxAdults.coerceAtLeast(1))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Children", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            QtyStepper(criteria.children, { vm.setChildren(it) }, min = 0, max = room.maxChildren.coerceAtLeast(0))
        }
    }
    if (settings.mealPlans.isNotEmpty()) {
        Text("Meal plan", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.mealPlans.forEach { p -> SelectChip(mealLabel(p), ui.mealPlan == p.code, { vm.setMeal(p.code) }) }
        }
    }
    Text("Promo code", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(ui.promoInput, { vm.setPromoInput(it) }, "Have a code?", Modifier.weight(1f), enabled = ui.promoApplied == null, imeAction = ImeAction.Done)
        if (ui.promoApplied == null) {
            OutlinedButton(onClick = { vm.applyPromo() }, enabled = ui.promoInput.isNotBlank(), modifier = Modifier.height(56.dp)) { Text("Apply") }
        } else {
            OutlinedButton(onClick = { vm.clearPromo() }, modifier = Modifier.height(56.dp)) { Text("Remove") }
        }
    }
    ui.promoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (ui.promoOk) com.livora.corbett.ui.theme.Brand.success else cs.error) }
    ui.quoteError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
}

@Composable
private fun StepDetails(vm: BookingViewModel, ui: BookingUi) {
    Text("Who's staying?", style = MaterialTheme.typography.headlineSmall)
    AppTextField(ui.name, { vm.setName(it) }, "Guest name", leading = Icons.Filled.Person)
    AppTextField(ui.email, { vm.setEmail(it) }, "Email (for your confirmation)", keyboardType = KeyboardType.Email, leading = Icons.Filled.Email)
    AppTextField(ui.phone, { vm.setPhone(it) }, "Phone", keyboardType = KeyboardType.Phone, leading = Icons.Filled.Phone)
    AppTextField(
        ui.requests, { vm.setRequests(it) }, "Special requests (optional)",
        singleLine = false, minLines = 3, imeAction = ImeAction.Default,
    )
    if (ui.loggedIn) {
        Text("Prefilled from your profile. Edit if you're booking for someone else.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepReview(ui: BookingUi, criteria: Criteria, room: Room, settings: PublicSettings) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Your stay", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        DetailRow("Check-in", "${Fmt.dayShort(criteria.checkIn)}, from ${settings.checkInTime}")
        DetailRow("Check-out", "${Fmt.dayShort(criteria.checkOut)}, by ${settings.checkOutTime}")
        DetailRow("Guests", "${Fmt.plural(criteria.adults, "adult")}" + if (criteria.children > 0) ", ${Fmt.plural(criteria.children, "child", "children")}" else "")
        DetailRow("Meal plan", settings.mealPlans.firstOrNull { it.code == ui.mealPlan }?.label ?: ui.mealPlan.uppercase())
        DetailRow("Guest", ui.name)
        DetailRow("Contact", "${ui.email}\n${ui.phone}")
        if (ui.requests.isNotBlank()) DetailRow("Requests", ui.requests)
    }
    ui.quote?.let { QuoteCard(it, ui.promoApplied) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(
            "Pay at property. Free cancellation until ${settings.cancellationHours} hours before check-in.",
            style = MaterialTheme.typography.titleSmall, color = cs.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text("All prices are in INR and inclusive of GST (${((ui.quote?.gstRate ?: 0.12) * 100).toInt()}%). No online payment is taken.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

// ───────────────────────── confirmation ─────────────────────────

@HiltViewModel
class ConfirmationViewModel @Inject constructor(
    last: LastBooking,
    config: ResortConfig,
    auth: AuthRepository,
) : ViewModel() {
    val booking: StateFlow<Booking?> = last.booking
    val settings: StateFlow<PublicSettings> = config.settings
    val loggedIn: Boolean = auth.session.value != null
}

@Composable
fun ConfirmationScreen(onDone: () -> Unit, onViewStays: () -> Unit, vm: ConfirmationViewModel = hiltViewModel()) {
    val booking by vm.booking.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    ForceDark {
        Box(Modifier.fillMaxSize().background(Forest950)) {
            ForestBackdrop(Modifier.fillMaxSize(), mood = 0.6f)
            val b = booking
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BookingCelebration(Modifier.fillMaxWidth().height(260.dp))
                Text("Booking confirmed", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                if (b == null) {
                    Text("Thank you! Your booking has been placed.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                } else {
                    Text("Reference", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(b.bookingRef, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        DetailRow("Room", b.roomName)
                        DetailRow("Check-in", "${Fmt.dayShort(b.checkIn)}, from ${settings.checkInTime}")
                        DetailRow("Check-out", "${Fmt.dayShort(b.checkOut)}, by ${settings.checkOutTime}")
                        DetailRow("Guests", Fmt.plural(b.adults, "adult") + if (b.children > 0) ", ${Fmt.plural(b.children, "child", "children")}" else "")
                        DetailRow("Total (incl. GST)", Fmt.money(b.totalAmount), bold = true, valueColor = MaterialTheme.colorScheme.primary)
                        DetailRow("Payment", "Pay at property")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (vm.loggedIn) "You can find this booking under My Stays."
                        else "Keep your reference and the email you used. You can look up or cancel your booking under My Stays.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Calendar", {
                            val a = Fmt.parseDay(b.checkIn)
                            val z = Fmt.parseDay(b.checkOut)
                            if (a != null && z != null) {
                                Intents.addToCalendar(ctx, "Stay at ${settings.resortName}", a, z, settings.address ?: settings.resortName, "Booking ${b.bookingRef}. Pay at property.")
                            }
                        }, Modifier.weight(1f), icon = Icons.Filled.CalendarMonth)
                        GhostButton("Share", {
                            Intents.share(ctx, "My stay at ${settings.resortName}\nBooking ${b.bookingRef}\n${Fmt.dayShort(b.checkIn)} → ${Fmt.dayShort(b.checkOut)}\n${settings.mapsUrl}")
                        }, Modifier.weight(1f), icon = Icons.Filled.Share)
                        GhostButton("Map", { Intents.openUrl(ctx, settings.mapsUrl) }, Modifier.weight(1f), icon = Icons.Filled.Directions)
                    }
                }
                Spacer(Modifier.height(16.dp))
                GradientButton(if (vm.loggedIn) "View my stays" else "Look up my booking", onViewStays, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onDone) { Text("Back to home", color = MaterialTheme.colorScheme.onBackground) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
