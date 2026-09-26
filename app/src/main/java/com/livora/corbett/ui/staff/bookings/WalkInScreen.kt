@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.staff.bookings

import android.util.Patterns
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.Booking
import com.livora.corbett.data.api.CreateBookingBody
import com.livora.corbett.data.api.GuestInfo
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.api.Quote
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.repo.BookingRepository
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.AppTopBar
import com.livora.corbett.ui.components.DateRangeCalendar
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.QtyStepper
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.guest.rooms.QuoteCard
import com.livora.corbett.ui.guest.rooms.mealLabel
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
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

data class WalkInUi(
    val step: Int = 0,
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val adults: Int = 2,
    val children: Int = 0,
    val rooms: List<Room> = emptyList(),
    val roomsBusy: Boolean = false,
    val room: Room? = null,
    val mealPlan: String = "ep",
    val source: String = "walk_in",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val requests: String = "",
    val quote: Quote? = null,
    val quoteBusy: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WalkInViewModel @Inject constructor(
    private val resort: ResortRepository,
    private val bookings: BookingRepository,
    private val config: ResortConfig,
) : ViewModel() {
    private val _ui = MutableStateFlow(WalkInUi(checkIn = Fmt.today(), checkOut = Fmt.today().plusDays(1)))
    val ui: StateFlow<WalkInUi> = _ui.asStateFlow()
    val settings: StateFlow<PublicSettings> = config.settings
    private var quoteJob: Job? = null

    fun setDates(a: LocalDate?, b: LocalDate?) = _ui.update { it.copy(checkIn = a, checkOut = b, error = null) }
    fun setAdults(n: Int) = _ui.update { it.copy(adults = n) }
    fun setChildren(n: Int) = _ui.update { it.copy(children = n) }
    fun setSource(s: String) = _ui.update { it.copy(source = s) }
    fun setName(s: String) = _ui.update { it.copy(name = s, error = null) }
    fun setPhone(s: String) = _ui.update { it.copy(phone = s, error = null) }
    fun setEmail(s: String) = _ui.update { it.copy(email = s, error = null) }
    fun setRequests(s: String) = _ui.update { it.copy(requests = s) }
    fun setMeal(code: String) { _ui.update { it.copy(mealPlan = code) }; requote() }

    fun back(): Boolean {
        if (_ui.value.step == 0) return false
        _ui.update { it.copy(step = it.step - 1, error = null) }
        return true
    }

    fun findRooms() {
        val st = _ui.value
        val a = st.checkIn
        val b = st.checkOut
        if (a == null || b == null || !b.isAfter(a)) {
            _ui.update { it.copy(error = "Select check-in and check-out dates.") }
            return
        }
        _ui.update { it.copy(roomsBusy = true, error = null) }
        viewModelScope.launch {
            when (val r = resort.rooms(null, st.adults, st.children, Fmt.iso(a), Fmt.iso(b))) {
                is ApiResult.Success -> _ui.update { it.copy(rooms = r.data, roomsBusy = false, step = 1, room = null, quote = null) }
                is ApiResult.Failure -> _ui.update { it.copy(roomsBusy = false, error = r.message) }
            }
        }
    }

    fun pickRoom(room: Room) {
        val plans = config.settings.value.mealPlans
        val def = plans.firstOrNull { it.code == "ep" }?.code ?: plans.firstOrNull()?.code ?: "ep"
        _ui.update { it.copy(room = room, step = 2, mealPlan = def, error = null) }
        requote()
    }

    private fun requote() {
        quoteJob?.cancel()
        val st = _ui.value
        val room = st.room ?: return
        val a = st.checkIn ?: return
        val b = st.checkOut ?: return
        quoteJob = viewModelScope.launch {
            _ui.update { it.copy(quoteBusy = true) }
            delay(200)
            when (val r = resort.quote(room.id, Fmt.iso(a), Fmt.iso(b), st.adults, st.children, st.mealPlan, null)) {
                is ApiResult.Success -> _ui.update { it.copy(quote = r.data, quoteBusy = false) }
                is ApiResult.Failure -> _ui.update { it.copy(quote = null, quoteBusy = false, error = r.message) }
            }
        }
    }

    fun create(onCreated: (Booking) -> Unit) {
        val st = _ui.value
        val room = st.room ?: return
        val err = when {
            st.name.isBlank() -> "Enter the guest's name."
            st.phone.filter { it.isDigit() }.length < 10 -> "Enter a valid phone number."
            !Patterns.EMAIL_ADDRESS.matcher(st.email.trim()).matches() -> "An email address is required for the booking confirmation."
            else -> null
        }
        if (err != null) { _ui.update { it.copy(error = err) }; return }
        _ui.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val body = CreateBookingBody(
                roomId = room.id,
                checkIn = Fmt.iso(st.checkIn!!),
                checkOut = Fmt.iso(st.checkOut!!),
                adults = st.adults,
                children = st.children,
                mealPlan = st.mealPlan,
                specialRequests = st.requests.trim().ifBlank { null },
                guest = GuestInfo(st.name.trim(), st.email.trim(), st.phone.trim()),
                source = st.source,
            )
            when (val r = bookings.create(body)) {
                is ApiResult.Success -> { _ui.update { it.copy(submitting = false) }; onCreated(r.data) }
                is ApiResult.Failure -> _ui.update { it.copy(submitting = false, error = r.message) }
            }
        }
    }
}

private val sources = listOf("walk_in" to "Walk-in", "phone" to "Phone", "ota" to "OTA")

@Composable
fun WalkInScreen(onBack: () -> Unit, onCreated: (String) -> Unit, vm: WalkInViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val titles = listOf("Dates & guests", "Choose a room", "Guest & confirm")

    androidx.activity.compose.BackHandler(enabled = ui.step > 0) { vm.back() }

    Column(Modifier.fillMaxSize().background(cs.background).imePadding()) {
        AppTopBar("New booking", { if (!vm.back()) onBack() })
        Text("Step ${ui.step + 1} of 3 · ${titles[ui.step]}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Spacer(Modifier.height(8.dp))
        Crossfade(ui.step, Modifier.weight(1f), label = "walkin-step") { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    0 -> {
                        GlassCard(Modifier.fillMaxWidth()) {
                            DateRangeCalendar(ui.checkIn, ui.checkOut, { a, b -> vm.setDates(a, b) }, minDate = Fmt.today().minusDays(7))
                            Text(
                                if (ui.checkIn != null && ui.checkOut != null) "${Fmt.plural(Fmt.nights(ui.checkIn, ui.checkOut), "night")}" else "Select check-in, then check-out",
                                style = MaterialTheme.typography.titleSmall, color = cs.primary,
                            )
                        }
                        GlassCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Adults", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                QtyStepper(ui.adults, { vm.setAdults(it) }, min = 1, max = 8)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Children", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                QtyStepper(ui.children, { vm.setChildren(it) }, min = 0, max = 6)
                            }
                        }
                        InlineError(ui.error)
                        GradientButton("Find available rooms", { vm.findRooms() }, Modifier.fillMaxWidth(), loading = ui.roomsBusy)
                    }
                    1 -> {
                        val avail = ui.rooms.filter { it.available != false }
                        if (avail.isEmpty()) {
                            EmptyState(Icons.Filled.Person, "No rooms free", "Nothing is available for those dates and guests. Go back and try other dates.")
                        }
                        ui.rooms.forEach { room ->
                            val free = room.available != false
                            GlassCard(Modifier.fillMaxWidth(), onClick = if (free) ({ vm.pickRoom(room) }) else null, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RemoteImage(room.imageUrls.firstOrNull(), room.name, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${room.name} · ${room.roomNumber}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text("${Fmt.money(room.pricePerNight)} / night · up to ${room.maxAdults} adults", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                    }
                                    if (free) StatusPill("Available", Brand.success) else StatusPill("Booked", Brand.danger)
                                }
                            }
                        }
                    }
                    else -> {
                        val room = ui.room
                        if (room != null) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text(room.name, style = MaterialTheme.typography.titleLarge)
                                Text("${Fmt.dayShort(ui.checkIn)} → ${Fmt.dayShort(ui.checkOut)} · ${Fmt.plural(ui.adults, "adult")}${if (ui.children > 0) ", ${Fmt.plural(ui.children, "child", "children")}" else ""}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        Text("Booking source", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEach { (c, l) -> SelectChip(l, ui.source == c, { vm.setSource(c) }) } }
                        if (settings.mealPlans.isNotEmpty()) {
                            Text("Meal plan", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { settings.mealPlans.forEach { p -> SelectChip(mealLabel(p), ui.mealPlan == p.code, { vm.setMeal(p.code) }) } }
                        }
                        AppTextField(ui.name, { vm.setName(it) }, "Guest name", leading = Icons.Filled.Person)
                        AppTextField(ui.phone, { vm.setPhone(it) }, "Phone", keyboardType = KeyboardType.Phone, leading = Icons.Filled.Phone)
                        AppTextField(ui.email, { vm.setEmail(it) }, "Email", keyboardType = KeyboardType.Email, leading = Icons.Filled.Email)
                        AppTextField(ui.requests, { vm.setRequests(it) }, "Notes / requests (optional)", singleLine = false, minLines = 2)
                        ui.quote?.let { QuoteCard(it, null) }
                        InlineError(ui.error)
                        GradientButton("Create booking", { vm.create { b -> onCreated(b.id) } }, Modifier.fillMaxWidth(), loading = ui.submitting, enabled = ui.room != null)
                        Text("Payment is collected at the property. You can record it from the booking screen.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}
