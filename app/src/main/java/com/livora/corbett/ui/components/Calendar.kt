package com.livora.corbett.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.livora.corbett.util.Fmt
import java.time.LocalDate
import java.time.YearMonth

/** Animated check-in / check-out range calendar (Monday-first). */
@Composable
fun DateRangeCalendar(
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    onPick: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate = Fmt.today(),
) {
    var month by remember { mutableStateOf(YearMonth.from(checkIn ?: minDate)) }
    val minMonth = YearMonth.from(minDate)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, enabled = month.isAfter(minMonth)) {
                Icon(Icons.Filled.ChevronLeft, "Previous month")
            }
            AnimatedContent(
                targetState = month,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "cal-title",
            ) { m ->
                Text(
                    Fmt.monthYear(m.atDay(1)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.Filled.ChevronRight, "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                if (targetState.isAfter(initialState)) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "cal-grid",
        ) { m ->
            MonthGrid(m, checkIn, checkOut, minDate, onPick)
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    minDate: LocalDate,
    onPick: (LocalDate?, LocalDate?) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7
    Column(Modifier.fillMaxWidth()) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - leading + 1
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            DayCell(date, checkIn, checkOut, date.isBefore(minDate)) {
                                when {
                                    checkIn == null || checkOut != null -> onPick(date, null)
                                    date.isAfter(checkIn) -> onPick(checkIn, date)
                                    else -> onPick(date, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, checkIn: LocalDate?, checkOut: LocalDate?, disabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isStart = date == checkIn
    val isEnd = date == checkOut
    val inRange = checkIn != null && checkOut != null && date.isAfter(checkIn) && date.isBefore(checkOut)
    val edge = isStart || isEnd
    val circle by animateColorAsState(if (edge) cs.primary else androidx.compose.ui.graphics.Color.Transparent, tween(220), label = "day-circle")
    val textColor by animateColorAsState(
        when {
            edge -> cs.onPrimary
            disabled -> cs.onSurface.copy(alpha = 0.28f)
            else -> cs.onSurface
        },
        tween(220),
        label = "day-text",
    )
    val band = cs.primary.copy(alpha = 0.16f)
    val bandShape = when {
        isStart && checkOut != null -> RoundedCornerShape(topStart = 23.dp, bottomStart = 23.dp)
        isEnd -> RoundedCornerShape(topEnd = 23.dp, bottomEnd = 23.dp)
        else -> RoundedCornerShape(0.dp)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(if (inRange || (isStart && checkOut != null) || isEnd) Modifier.background(band, bandShape) else Modifier)
            .semantics {
                contentDescription = Fmt.dayShort(date)
                selected = edge || inRange
            }
            .clickable(enabled = !disabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (edge || date == Fmt.today()) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
