@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.rooms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.MealPlan
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.api.Quote
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.repo.Criteria
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.data.repo.SearchState
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.CircleIconButton
import com.livora.corbett.ui.components.DateGuestSheet
import com.livora.corbett.ui.components.DetailRow
import com.livora.corbett.ui.components.EmptyState
import com.livora.corbett.ui.components.ErrorState
import com.livora.corbett.ui.components.GhostButton
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.ListSkeleton
import com.livora.corbett.ui.components.LoadBox
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.components.ShimmerBox
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.ui.theme.Cream
import com.livora.corbett.ui.theme.Gold
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ───────────────────────── rooms list ─────────────────────────

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val resort: ResortRepository,
    private val search: SearchState,
) : ViewModel() {
    val criteria: StateFlow<Criteria> = search.criteria
    private val _type = MutableStateFlow<String?>(null)
    val type: StateFlow<String?> = _type.asStateFlow()
    private val _rooms = MutableStateFlow<Load<List<Room>>>(Load.Loading)
    val rooms: StateFlow<Load<List<Room>>> = _rooms.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(_type, search.criteria, reload) { t, c, _ -> t to c }.collectLatest { (t, c) -> load(t, c) }
        }
    }

    private suspend fun load(t: String?, c: Criteria) {
        if (_rooms.value !is Load.Ready) _rooms.value = Load.Loading
        val ci = if (c.hasDates) Fmt.iso(c.checkIn!!) else null
        val co = if (c.hasDates) Fmt.iso(c.checkOut!!) else null
        when (val r = resort.rooms(t, c.adults, c.children, ci, co)) {
            is ApiResult.Success -> _rooms.value = Load.Ready(r.data)
            is ApiResult.Failure -> if (_rooms.value !is Load.Ready) _rooms.value = Load.Failed(r.message)
        }
        _refreshing.value = false
    }

    fun setType(t: String?) { _type.value = t }
    fun refresh() { _refreshing.value = true; reload.update { it + 1 } }
    fun retry() { _rooms.value = Load.Loading; reload.update { it + 1 } }
    fun apply(a: java.time.LocalDate?, b: java.time.LocalDate?, adults: Int, children: Int) {
        search.update { it.copy(checkIn = a, checkOut = b, adults = adults, children = children) }
    }
    fun clearDates() = search.setDates(null, null)
}

private val roomTypes = listOf(null to "All", "deluxe" to "Deluxe", "premium" to "Premium", "suite" to "Suite", "family" to "Family", "villa" to "Villa")

@Composable
fun RoomsScreen(onOpenRoom: (String) -> Unit, vm: RoomsViewModel = hiltViewModel()) {
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val criteria by vm.criteria.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Rooms & Suites", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        Text(
            "Ten rooms in five categories, each with its own character.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        StaySummaryChip(criteria, onClick = { showSheet = true }, onClear = { vm.clearDates() }, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(roomTypes) { _, (code, label) -> SelectChip(label, type == code, { vm.setType(code) }) }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
            LoadBox(rooms, onRetry = { vm.retry() }, skeleton = { ListSkeleton(3, 330.dp) }) { list ->
                if (list.isEmpty()) {
                    EmptyState(
                        Icons.Filled.Hotel, "No rooms match",
                        "Try different dates, fewer guests or another room type.",
                        actionLabel = "Show all rooms",
                        onAction = { vm.setType(null); vm.clearDates() },
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(list, key = { _, r -> r.id }) { i, room ->
                            RoomCard(room, criteria, { onOpenRoom(room.slug.ifBlank { room.id }) }, Modifier.staggerIn(i))
                        }
                    }
                }
            }
        }
    }
    if (showSheet) {
        DateGuestSheet(
            initial = criteria,
            onApply = { a, b, ad, ch -> vm.apply(a, b, ad, ch); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}

/** "12 Oct → 14 Oct · 2 guests" pill that opens the date/guest sheet. */
@Composable
fun StaySummaryChip(criteria: Criteria, onClick: () -> Unit, onClear: (() -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.onSurface.copy(alpha = 0.06f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Change dates and guests", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = cs.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (criteria.hasDates) "${Fmt.dayMonth(criteria.checkIn)}  →  ${Fmt.dayMonth(criteria.checkOut)}" else "Add your dates",
                style = MaterialTheme.typography.titleSmall,
            )
            val g = criteria.adults + criteria.children
            Text(Fmt.plural(g, "guest"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (criteria.hasDates && onClear != null) {
            Text("Clear", color = cs.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = onClear).padding(8.dp))
        }
    }
}

// ───────────────────────── room detail ─────────────────────────

data class RoomDetailUi(
    val room: Load<Room> = Load.Loading,
    val mealPlan: String = "ep",
    val promoInput: String = "",
    val promoApplied: String? = null,
    val promoMessage: String? = null,
    val promoOk: Boolean = false,
    val promoBusy: Boolean = false,
    val quote: Quote? = null,
    val quoteBusy: Boolean = false,
    val quoteError: String? = null,
)

@HiltViewModel
class RoomDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val resort: ResortRepository,
    private val search: SearchState,
    private val config: ResortConfig,
) : ViewModel() {
    private val slug: String = handle.get<String>("slug") ?: ""
    private val _ui = MutableStateFlow(RoomDetailUi())
    val ui: StateFlow<RoomDetailUi> = _ui.asStateFlow()
    val criteria: StateFlow<Criteria> = search.criteria
    val settings: StateFlow<PublicSettings> = config.settings
    private var quoteJob: Job? = null

    init {
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
                    _ui.update { it.copy(room = Load.Ready(r.data), mealPlan = def) }
                    requote()
                }
                is ApiResult.Failure -> _ui.update { it.copy(room = Load.Failed(r.message)) }
            }
        }
    }

    fun setMealPlan(code: String) { _ui.update { it.copy(mealPlan = code) }; requote() }
    fun setPromoInput(s: String) { _ui.update { it.copy(promoInput = s.uppercase().trim(), promoMessage = null) } }

    fun applyGuests(a: java.time.LocalDate?, b: java.time.LocalDate?, adults: Int, children: Int) {
        search.update { it.copy(checkIn = a, checkOut = b, adults = adults, children = children) }
    }

    fun applyPromo() {
        val code = _ui.value.promoInput
        val quote = _ui.value.quote
        val room = _ui.value.room.let { (it as? Load.Ready)?.data } ?: return
        if (code.isBlank()) return
        val c = search.criteria.value
        if (!c.hasDates || quote == null) {
            _ui.update { it.copy(promoMessage = "Select your dates first, then apply the code.", promoOk = false) }
            return
        }
        _ui.update { it.copy(promoBusy = true, promoMessage = null) }
        viewModelScope.launch {
            when (val r = resort.validatePromo(code, quote.nights, quote.roomAmount + quote.mealAmount, room.type)) {
                is ApiResult.Success -> {
                    if (r.data.valid) {
                        _ui.update { it.copy(promoBusy = false, promoApplied = code, promoOk = true, promoMessage = r.data.message ?: "Offer applied") }
                        requote()
                    } else {
                        _ui.update { it.copy(promoBusy = false, promoOk = false, promoApplied = null, promoMessage = r.data.message ?: "This code can't be used for your stay.") }
                        requote()
                    }
                }
                is ApiResult.Failure -> _ui.update { it.copy(promoBusy = false, promoOk = false, promoMessage = r.message) }
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
            val adults = c.adults.coerceIn(1, room.maxAdults.coerceAtLeast(1))
            val children = c.children.coerceIn(0, room.maxChildren.coerceAtLeast(0))
            when (val r = resort.quote(room.id, Fmt.iso(c.checkIn!!), Fmt.iso(c.checkOut!!), adults, children, st.mealPlan, st.promoApplied)) {
                is ApiResult.Success -> _ui.update { it.copy(quote = r.data, quoteBusy = false) }
                is ApiResult.Failure -> _ui.update { it.copy(quote = null, quoteBusy = false, quoteError = r.message) }
            }
        }
    }
}

@Composable
fun RoomDetailScreen(
    onBack: () -> Unit,
    onBook: (String) -> Unit,
    vm: RoomDetailViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val criteria by vm.criteria.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(cs.background)) {
        when (val st = ui.room) {
            is Load.Loading -> Column(Modifier.fillMaxSize()) {
                ShimmerBox(Modifier.fillMaxWidth().height(340.dp), RoundedCornerShape(0.dp))
                Spacer(Modifier.height(16.dp))
                ListSkeleton(3, 90.dp)
            }
            is Load.Failed -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(4.dp)) { CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) }
                ErrorState(st.message, { vm.load() })
            }
            is Load.Ready -> {
                val room = st.data
                val scroll = rememberScrollState()
                val images = room.imageUrls
                val pager = rememberPagerState(pageCount = { images.size.coerceAtLeast(1) })
                val collapsed by remember { derivedStateOf { scroll.value > 520 } }

                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    // parallax image header
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .graphicsLayer {
                                translationY = scroll.value * 0.55f
                                val s = 1f + (scroll.value / 3000f)
                                scaleX = s; scaleY = s
                                alpha = (1f - scroll.value / 900f).coerceIn(0f, 1f)
                            },
                    ) {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                            RemoteImage(
                                images.getOrNull(page), room.name,
                                Modifier.fillMaxSize().graphicsLayer { translationX = offset * size.width * 0.25f },
                            )
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to cs.background)))
                        if (images.size > 1) {
                            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(images.size) { i ->
                                    Box(
                                        Modifier.size(if (i == pager.currentPage) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                                            .background(if (i == pager.currentPage) Gold else Color.White.copy(alpha = 0.5f)),
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(Fmt.titleCase(room.type), Gold)
                                room.view?.takeIf { it.isNotBlank() }?.let { StatusPill(it, cs.secondary) }
                            }
                            Text(room.name, style = MaterialTheme.typography.displaySmall)
                            Text(roomMeta(room), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(Fmt.money(room.pricePerNight), style = MaterialTheme.typography.headlineMedium, color = cs.primary, fontWeight = FontWeight.Bold)
                                Text("  / night, incl. GST", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }

                        room.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface.copy(alpha = 0.9f))
                        }

                        if (room.highlights.isNotEmpty()) {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text("Highlights", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                room.highlights.forEach { h ->
                                    Row(Modifier.padding(vertical = 3.dp)) {
                                        Icon(Icons.Filled.Check, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(h, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        if (room.amenities.isNotEmpty()) {
                            Text("Amenities", style = MaterialTheme.typography.headlineSmall)
                            AmenityChips(room.amenities)
                        }

                        Text("Your stay", style = MaterialTheme.typography.headlineSmall)
                        StaySummaryChip(criteria, { showSheet = true }, null)
                        if (criteria.adults > room.maxAdults || criteria.children > room.maxChildren) {
                            Text(
                                "This room hosts up to ${room.maxAdults} adults and ${room.maxChildren} children. Guest numbers will be adjusted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Brand.warning,
                            )
                        }

                        if (settings.mealPlans.isNotEmpty()) {
                            Text("Meal plan", style = MaterialTheme.typography.headlineSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                settings.mealPlans.forEach { p ->
                                    SelectChip(mealLabel(p), ui.mealPlan == p.code, { vm.setMealPlan(p.code) })
                                }
                            }
                            settings.mealPlans.firstOrNull { it.code == ui.mealPlan }?.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }

                        // promo
                        Text("Promo code", style = MaterialTheme.typography.headlineSmall)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppTextField(
                                ui.promoInput, { vm.setPromoInput(it) }, "Have a code?",
                                Modifier.weight(1f), imeAction = ImeAction.Done,
                                enabled = ui.promoApplied == null,
                            )
                            if (ui.promoApplied == null) {
                                OutlinedButton(onClick = { vm.applyPromo() }, enabled = ui.promoInput.isNotBlank() && !ui.promoBusy, modifier = Modifier.height(56.dp)) {
                                    Text(if (ui.promoBusy) "…" else "Apply")
                                }
                            } else {
                                OutlinedButton(onClick = { vm.clearPromo() }, modifier = Modifier.height(56.dp)) { Text("Remove") }
                            }
                        }
                        ui.promoMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = if (ui.promoOk) Brand.success else cs.error)
                        }

                        // price breakdown
                        AnimatedVisibility(ui.quote != null, enter = fadeIn(), exit = fadeOut()) {
                            ui.quote?.let { q -> QuoteCard(q, ui.promoApplied) }
                        }
                        ui.quoteError?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }

                        GlassCard(Modifier.fillMaxWidth()) {
                            Text("Good to know", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            DetailRow("Check-in", settings.checkInTime)
                            DetailRow("Check-out", settings.checkOutTime)
                            DetailRow("Payment", "Pay at property")
                            DetailRow("Cancellation", "Free until ${settings.cancellationHours} hours before check-in")
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }

                // collapsing top bar
                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    AnimatedVisibility(collapsed, enter = fadeIn(), exit = fadeOut()) {
                        Box(Modifier.fillMaxWidth().background(cs.surface.copy(alpha = 0.96f)).statusBarsPadding().height(56.dp), contentAlignment = Alignment.CenterStart) {
                            Text(room.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 64.dp, end = 64.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                        CircleIconButton(Icons.Filled.Share, "Share room", {
                            Intents.share(ctx, "${room.name} at ${settings.resortName}: ${Fmt.money(room.pricePerNight)} per night. ${settings.phone}")
                        })
                    }
                }

                // sticky booking bar
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(cs.surfaceContainer.copy(alpha = 0.98f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val q = ui.quote
                        if (q != null) {
                            Text(Fmt.money(q.total), style = MaterialTheme.typography.headlineSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                            Text("${Fmt.plural(q.nights, "night")} · incl. GST", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        } else if (ui.quoteBusy) {
                            ShimmerBox(Modifier.width(110.dp).height(28.dp))
                        } else {
                            Text(Fmt.money(room.pricePerNight), style = MaterialTheme.typography.headlineSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                            Text("per night · select dates for a quote", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    when {
                        !settings.bookingsOpen -> GhostButton("Bookings paused", { Intents.call(ctx, settings.phone) })
                        !criteria.hasDates -> GradientButton("Select dates", { showSheet = true })
                        else -> GradientButton("Book now", { onBook(room.slug.ifBlank { room.id }) })
                    }
                }

                if (showSheet) {
                    DateGuestSheet(
                        initial = criteria,
                        onApply = { a, b, ad, ch -> vm.applyGuests(a, b, ad, ch); showSheet = false },
                        onDismiss = { showSheet = false },
                        maxAdults = room.maxAdults.coerceAtLeast(1),
                        maxChildren = room.maxChildren.coerceAtLeast(0),
                    )
                }
            }
        }
    }
}

fun mealLabel(p: MealPlan): String = if (p.adultPrice > 0) "${p.label} · +${Fmt.money(p.adultPrice)}/adult" else p.label

@Composable
fun QuoteCard(q: Quote, promo: String?) {
    val cs = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Price breakdown", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        DetailRow("${Fmt.money(q.pricePerNight)} × ${Fmt.plural(q.nights, "night")}", Fmt.money(q.roomAmount))
        if (q.mealAmount > 0) DetailRow("Meals", Fmt.money(q.mealAmount))
        if (q.discountAmount > 0) DetailRow("Offer ${promo ?: q.promoCode ?: ""}".trim(), "− " + Fmt.money(q.discountAmount), valueColor = Brand.success)
        q.promoMessage?.takeIf { it.isNotBlank() && q.discountAmount <= 0 }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        DetailRow("Taxable value", Fmt.money(q.baseAmount))
        DetailRow("GST @ ${(q.gstRate * 100).toInt()}%", Fmt.money(q.gstAmount))
        Spacer(Modifier.height(4.dp))
        DetailRow("Total (incl. GST)", Fmt.money(q.total), bold = true, valueColor = cs.primary)
    }
}
