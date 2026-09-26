@file:OptIn(ExperimentalLayoutApi::class)

package com.livora.corbett.ui.guest.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.livora.corbett.data.api.Room
import com.livora.corbett.data.repo.Criteria
import com.livora.corbett.ui.components.CircleIconButton
import com.livora.corbett.ui.components.FlipCard
import com.livora.corbett.ui.components.RemoteImage
import com.livora.corbett.ui.components.StatusPill
import com.livora.corbett.ui.components.Tilt3DBox
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.ui.theme.Cream
import com.livora.corbett.ui.theme.Forest800
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.ui.theme.GoldLight
import com.livora.corbett.util.Fmt

fun roomMeta(room: Room): String {
    val parts = mutableListOf<String>()
    room.size?.let { if (it > 0) parts += "${it.toInt()} sq ft" }
    room.bedType?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += "Up to ${room.maxAdults + room.maxChildren} guests"
    return parts.joinToString("  ·  ")
}

/** 3D-tilting room card with a flip side listing amenities. */
@Composable
fun RoomCard(room: Room, criteria: Criteria, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var flipped by remember { mutableStateOf(false) }
    val soldOut = room.available == false
    val nights = if (criteria.hasDates) Fmt.nights(criteria.checkIn, criteria.checkOut) else 0
    Tilt3DBox(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)) {
        FlipCard(
            flipped = flipped,
            modifier = Modifier.fillMaxWidth(),
            front = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clickable(onClickLabel = "Open ${room.name}", onClick = onClick),
                ) {
                    RemoteImage(room.imageUrls.firstOrNull(), room.name, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF2050B08)),
                        ),
                    )
                    Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(Fmt.titleCase(room.type), Gold)
                        if (soldOut) StatusPill("Sold out for your dates", Brand.danger)
                    }
                    CircleIconButton(
                        Icons.Filled.FlipToBack,
                        "Show amenities",
                        { flipped = true },
                        Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(room.name, style = MaterialTheme.typography.headlineMedium, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(roomMeta(room), style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.money(room.pricePerNight), style = MaterialTheme.typography.headlineSmall, color = GoldLight, fontWeight = FontWeight.Bold)
                            Text("  / night", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 3.dp))
                        }
                        if (nights > 0) {
                            Text(
                                "${Fmt.money(room.pricePerNight * nights)} for ${Fmt.plural(nights, "night")} (before meals)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cream.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            },
            back = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .background(Brush.linearGradient(listOf(Forest800, Color(0xFF0B1A13)))),
                ) {
                    Column(Modifier.fillMaxSize().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Amenities", style = MaterialTheme.typography.headlineSmall, color = Cream, modifier = Modifier.weight(1f))
                            CircleIconButton(Icons.Filled.FlipToFront, "Back to photo", { flipped = false })
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            AmenityChips(room.amenities.take(14), dark = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "View details  →",
                            color = Gold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun AmenityChips(items: List<String>, dark: Boolean = false, modifier: Modifier = Modifier) {
    val tint = if (dark) Cream else MaterialTheme.colorScheme.onSurface
    val bg = if (dark) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { a ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = Gold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(a, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

/** Compact card for horizontal carousels (Home). */
@Composable
fun RoomMiniCard(room: Room, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tilt3DBox(modifier.width(250.dp), maxDegrees = 8f, shape = RoundedCornerShape(22.dp)) {
        Box(Modifier.fillMaxWidth().height(300.dp).clickable(onClickLabel = "Open ${room.name}", onClick = onClick)) {
            RemoteImage(room.imageUrls.firstOrNull(), room.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xEE050B08))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Fmt.titleCase(room.type).uppercase(), style = MaterialTheme.typography.labelSmall, color = Gold)
                Text(room.name, style = MaterialTheme.typography.titleLarge, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${Fmt.money(room.pricePerNight)} / night", style = MaterialTheme.typography.bodySmall, color = GoldLight)
            }
        }
    }
}
