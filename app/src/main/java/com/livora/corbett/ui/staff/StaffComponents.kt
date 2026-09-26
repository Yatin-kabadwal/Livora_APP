@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.staff

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.livora.corbett.ui.components.AnimatedCount
import com.livora.corbett.ui.components.AppTextField
import com.livora.corbett.ui.components.GlassCard
import com.livora.corbett.ui.components.GradientButton
import com.livora.corbett.ui.components.InlineError
import com.livora.corbett.ui.components.SelectChip
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.util.Fmt

val paymentMethods = listOf("cash" to "Cash", "upi" to "UPI", "card" to "Card", "bank_transfer" to "Bank transfer")

/** Collect-payment bottom sheet: amount defaults to the balance; method chips; optional reference. */
@Composable
fun PaymentSheet(
    guestName: String,
    balance: Double,
    busy: Boolean,
    error: String?,
    onSubmit: (amount: Double, method: String, reference: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf(if (balance > 0) balance.toLong().toString() else "") }
    var method by remember { mutableStateOf("cash") }
    var reference by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Collect payment", style = MaterialTheme.typography.headlineSmall)
            Text("$guestName · balance ${Fmt.money(balance)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == '-' }; localError = null }, "Amount", keyboardType = KeyboardType.Number, prefix = "₹")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                paymentMethods.forEach { (code, label) -> SelectChip(label, method == code, { method = code }) }
            }
            AppTextField(reference, { reference = it }, "Reference / transaction id (optional)", imeAction = ImeAction.Done)
            InlineError(localError ?: error)
            GradientButton(
                "Record payment",
                {
                    val a = amount.toDoubleOrNull()
                    if (a == null || a == 0.0) localError = "Enter a valid amount." else onSubmit(a, method, reference)
                },
                Modifier.fillMaxWidth(), loading = busy,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ChargeDialog(busy: Boolean, error: String?, onSubmit: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add charge", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(desc, { desc = it }, "Description (e.g. Extra bed)")
                AppTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Amount (incl. GST)", keyboardType = KeyboardType.Number, prefix = "₹", imeAction = ImeAction.Done)
                InlineError(error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount.toDoubleOrNull()?.let { if (desc.isNotBlank()) onSubmit(desc, it) } },
                enabled = !busy && desc.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
            ) { Text(if (busy) "Adding…" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Animated occupancy ring. */
@Composable
fun OccupancyRing(fraction: Float, label: String, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "occ")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val arc = MaterialTheme.colorScheme.primary
    Box(modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.dp)) {
            val sw = 10.dp.toPx()
            val inset = sw / 2f
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(arc, -90f, 360f * progress, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun KpiCard(
    title: String,
    value: Double,
    modifier: Modifier = Modifier,
    money: Boolean = false,
    caption: String? = null,
    accent: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    GlassCard(modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        AnimatedCount(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = if (accent == Color.Unspecified) MaterialTheme.colorScheme.onSurface else accent,
            format = { if (money) Fmt.money(it) else Math.round(it).toString() },
        )
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

fun roomStatusColor(status: String): Color = when (status) {
    "available" -> Brand.success
    "occupied" -> Brand.warning
    "housekeeping" -> Brand.info
    "maintenance" -> Brand.danger
    else -> Color(0xFF9AA5A0)
}
