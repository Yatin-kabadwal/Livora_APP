@file:OptIn(ExperimentalMaterial3Api::class)

package com.livora.corbett.ui.guest.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.livora.corbett.data.api.MenuItem
import com.livora.corbett.data.api.Promotion
import com.livora.corbett.data.api.PublicSettings
import com.livora.corbett.data.api.Review
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.repo.Criteria
import com.livora.corbett.data.repo.ResortConfig
import com.livora.corbett.data.repo.ResortRepository
import com.livora.corbett.data.repo.SearchState
import com.livora.corbett.ui.components.DateGuestSheet
import com.livora.corbett.ui.components.ForestBackdrop
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.LocalSnackbar
import com.livora.corbett.ui.components.RatingStars
import com.livora.corbett.ui.components.SectionTitle
import com.livora.corbett.ui.components.ShimmerBox
import com.livora.corbett.ui.components.staggerIn
import com.livora.corbett.ui.guest.rooms.RoomMiniCard
import com.livora.corbett.ui.guest.rooms.StaySummaryChip
import com.livora.corbett.ui.navigation.GuestActions
import com.livora.corbett.ui.components.defaultSkyMood
import com.livora.corbett.ui.theme.Cream
import com.livora.corbett.ui.theme.ForceDark
import com.livora.corbett.ui.theme.Forest950
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.ui.theme.GoldLight
import com.livora.corbett.ui.theme.LocalReduceMotion
import com.livora.corbett.util.ApiResult
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Intents
import com.livora.corbett.util.Load
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUi(
    val rooms: Load<List<Room>> = Load.Loading,
    val promos: List<Promotion> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val menu: List<MenuItem> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val resort: ResortRepository,
    private val search: SearchState,
    config: ResortConfig,
) : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()
    val criteria: StateFlow<Criteria> = search.criteria
    val settings: StateFlow<PublicSettings> = config.settings

    init { load() }

    fun load() {
        viewModelScope.launch {
            when (val r = resort.rooms(null, null, null, null, null)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(rooms = Load.Ready(r.data))
                is ApiResult.Failure -> _ui.value = _ui.value.copy(rooms = Load.Failed(r.message))
            }
        }
        viewModelScope.launch {
            val r = resort.promotions()
            if (r is ApiResult.Success) _ui.value = _ui.value.copy(promos = r.data)
        }
        viewModelScope.launch {
            val r = resort.reviews()
            if (r is ApiResult.Success) _ui.value = _ui.value.copy(reviews = r.data.take(8))
        }
        viewModelScope.launch {
            val r = resort.menu()
            if (r is ApiResult.Success) _ui.value = _ui.value.copy(menu = r.data)
        }
    }

    fun apply(a: java.time.LocalDate?, b: java.time.LocalDate?, adults: Int, children: Int) {
        search.update { it.copy(checkIn = a, checkOut = b, adults = adults, children = children) }
    }
}

private class Experience(val title: String, val body: String, val icon: ImageVector)

private val experiences = listOf(
    Experience("Jeep safari", "We can help you plan a jeep safari at Jim Corbett. Permits and availability apply.", Icons.Filled.DirectionsCar),
    Experience("Nature walks", "Unhurried walks through the forest edge and surrounding countryside.", Icons.Filled.Hiking),
    Experience("Birdwatching", "Early mornings here are alive with birdsong. Bring binoculars.", Icons.Filled.Visibility),
    Experience("Riverside picnic", "A relaxed picnic by the river, arranged on request.", Icons.Filled.Restaurant),
    Experience("Bonfire evenings", "Wind down under the stars with a warm bonfire.", Icons.Filled.LocalFireDepartment),
)

@Composable
fun HomeScreen(
    userName: String?,
    actions: GuestActions,
    onSearch: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val criteria by vm.criteria.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    var mood by remember { mutableFloatStateOf(defaultSkyMood().let { if (it < 0.1f) 0.5f else it }) }

    Box(Modifier.fillMaxSize().background(Forest950)) {
        ForestBackdrop(
            Modifier.fillMaxWidth().height(560.dp).graphicsLayer { translationY = scroll.value * 0.45f },
            mood = mood,
            scroll = { (scroll.value / 1400f).coerceIn(0f, 1f) },
        )
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // hero
            ForceDark {
                Box(Modifier.fillMaxWidth().height(400.dp).statusBarsPadding()) {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 56.dp)) {
                        Text(
                            if (userName.isNullOrBlank()) "WELCOME TO" else "WELCOME BACK, ${userName.uppercase()}",
                            style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 8f)),
                            color = GoldLight,
                        )
                        Text(
                            settings.resortName,
                            style = MaterialTheme.typography.displayMedium.copy(shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 16f)),
                            color = Cream,
                        )
                        Text(
                            settings.tagline ?: "A quiet luxury retreat at the edge of Jim Corbett",
                            style = MaterialTheme.typography.bodyLarge.copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 8f)),
                            color = Cream.copy(alpha = 0.92f),
                        )
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Change time of day") {
                                mood = when {
                                    mood >= 0.9f -> 0.5f
                                    mood >= 0.4f -> 0f
                                    else -> 1f
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.WbTwilight, "Change time of day", tint = Color.White)
                        }
                    }
                }
            }

            // content sheet
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(cs.background)
                    .padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // availability
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(0), shape = RoundedCornerShape(26.dp)) {
                    Text("Plan your stay", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    StaySummaryChip(criteria, { showSheet = true }, null)
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Check availability", onSearch, Modifier.fillMaxWidth())
                }

                if (!settings.announcement.isNullOrBlank()) {
                    AnnouncementBanner(settings.announcement.orEmpty(), Modifier.padding(horizontal = 16.dp))
                }

                if (ui.promos.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Offers")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ui.promos, key = { it.id.ifBlank { it.code } }) { p -> PromoCard(p) }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Rooms & suites", action = "See all", onAction = onSearch)
                    when (val r = ui.rooms) {
                        is Load.Loading -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(3) { ShimmerBox(Modifier.width(250.dp).height(300.dp), RoundedCornerShape(22.dp)) }
                        }
                        is Load.Failed -> Text(r.message, Modifier.padding(horizontal = 16.dp), color = cs.error, style = MaterialTheme.typography.bodyMedium)
                        is Load.Ready -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(r.data.take(6), key = { it.id }) { room -> RoomMiniCard(room, { actions.openRoom(room.slug.ifBlank { room.id }) }) }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Experiences")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(experiences) { e -> ExperienceCard(e) }
                    }
                }

                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = actions.dining) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dining", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (ui.menu.isNotEmpty()) "${ui.menu.size} dishes on our in-room and restaurant menu" else "Fresh, comforting food served at the resort",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.Restaurant, null, tint = cs.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("View menu  →", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                }

                if (ui.reviews.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("Guest reviews")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(ui.reviews, key = { it.id }) { r -> ReviewCard(r) }
                        }
                    }
                }

                // contact
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Get in touch", style = MaterialTheme.typography.headlineSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("Front Desk", Icons.Filled.Call, Modifier.weight(1f)) {
                            Intents.call(ctx, settings.phone)
                        }
                        QuickAction("Reservations", Icons.Filled.Phone, Modifier.weight(1f)) {
                            Intents.call(ctx, settings.phoneSecondary)
                        }
                        QuickAction("WhatsApp", Icons.Filled.Chat, Modifier.weight(1f)) { Intents.whatsapp(ctx, settings.whatsapp, "Hello! I'd like to know more about ${settings.resortName}.") }
                        QuickAction("Directions", Icons.Filled.Directions, Modifier.weight(1f)) { Intents.openUrl(ctx, settings.mapsUrl) }
                    }
                    settings.address?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Text(
                        "Check-in ${settings.checkInTime}  ·  Check-out ${settings.checkOutTime}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
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

@Composable
private fun AnnouncementBanner(text: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val pulse: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "announce").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "announce-a")
    }
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(Gold))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PromoCard(p: Promotion) {
    val clipboard = LocalClipboardManager.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF17382A), Color(0xFF2F6B4F), Color(0xFF17382A)))),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (p.discountType.startsWith("percent")) "${p.discountValue.toInt()}% OFF" else "${Fmt.money(p.discountValue)} OFF",
                style = MaterialTheme.typography.labelLarge, color = GoldLight,
            )
            Text(p.name, style = MaterialTheme.typography.headlineSmall, color = Cream, maxLines = 2)
            p.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.85f), maxLines = 3) }
            Row(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(role = Role.Button, onClickLabel = "Copy code ${p.code}") {
                        clipboard.setText(AnnotatedString(p.code))
                        scope.launch { snack.showSnackbar("Code ${p.code} copied. Apply it when you book.") }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.code, style = MaterialTheme.typography.labelLarge, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.ContentCopy, null, tint = Gold, modifier = Modifier.size(16.dp))
            }
            p.validTo?.let { Text("Valid till ${Fmt.dayMedium(it)}", style = MaterialTheme.typography.labelSmall, color = Cream.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun ExperienceCard(e: Experience) {
    GlassCard(Modifier.width(230.dp), shape = RoundedCornerShape(22.dp)) {
        Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(e.title, style = MaterialTheme.typography.titleLarge)
        Text(e.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewCard(r: Review) {
    GlassCard(Modifier.width(280.dp), shape = RoundedCornerShape(22.dp)) {
        RatingStars(r.rating, size = 18.dp)
        r.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(r.comment, style = MaterialTheme.typography.bodySmall, maxLines = 5)
        Spacer(Modifier.height(6.dp))
        Text(listOfNotNull(r.name.takeIf { it.isNotBlank() }, r.location).joinToString(", "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
