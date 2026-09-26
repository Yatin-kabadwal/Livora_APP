@file:OptIn(ExperimentalMaterial3Api::class)

package com.livora.corbett.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.livora.corbett.data.repo.Criteria
import com.livora.corbett.util.Fmt
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate

/** Bottom sheet with the range calendar and guest counters. */
@Composable
fun DateGuestSheet(
    initial: Criteria,
    onApply: (LocalDate?, LocalDate?, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    maxAdults: Int = 6,
    maxChildren: Int = 4,
    requireDates: Boolean = false,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var inDate by remember { mutableStateOf(initial.checkIn) }
    var outDate by remember { mutableStateOf(initial.checkOut) }
    var adults by remember { mutableIntStateOf(initial.adults) }
    var children by remember { mutableIntStateOf(initial.children) }
    val nights = Fmt.nights(inDate, outDate)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Choose your stay", style = MaterialTheme.typography.headlineSmall)
            DateRangeCalendar(inDate, outDate, { a, b -> inDate = a; outDate = b })
            Text(
                when {
                    inDate == null -> "Select your check-in date"
                    outDate == null -> "Now select your check-out date"
                    else -> "${Fmt.dayShort(inDate)}  →  ${Fmt.dayShort(outDate)}  ·  ${Fmt.plural(nights, "night")}"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Adults", style = MaterialTheme.typography.titleSmall)
                    Text("Age 13+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QtyStepper(adults, { adults = it }, min = 1, max = maxAdults)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Children", style = MaterialTheme.typography.titleSmall)
                    Text("Under 13", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                QtyStepper(children, { children = it }, min = 0, max = maxChildren)
            }
            Spacer(Modifier.height(4.dp))
            GradientButton(
                "Apply",
                onClick = {
                    scope.launch { state.hide() }.invokeOnCompletion { onApply(inDate, outDate, adults, children) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !requireDates || (inDate != null && outDate != null),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
