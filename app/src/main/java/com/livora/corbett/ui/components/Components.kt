@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.livora.corbett.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.ui.theme.Forest950
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.ui.theme.GoldLight
import com.livora.corbett.ui.theme.LocalAssetBase
import com.livora.corbett.ui.theme.LocalReduceMotion
import com.livora.corbett.util.Fmt
import com.livora.corbett.util.Load
import com.livora.corbett.util.resolveImageUrl
import kotlinx.coroutines.delay

// ───────────────────────── haptics & snackbars ─────────────────────────

class Haptics(private val fb: HapticFeedback) {
    fun tap() = fb.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun success() = fb.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val fb = LocalHapticFeedback.current
    return remember(fb) { Haptics(fb) }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

// ───────────────────────── shimmer / skeleton ─────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val reduce = LocalReduceMotion.current
    val cs = MaterialTheme.colorScheme
    val base = cs.onSurface.copy(alpha = 0.07f)
    val hi = cs.onSurface.copy(alpha = 0.16f)
    val t: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer-t",
        )
    }
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val w = size.width
                val shift = (t.value * 2f - 0.5f) * w
                drawRect(
                    Brush.linearGradient(
                        listOf(base, hi, base),
                        start = Offset(shift - w * 0.4f, 0f),
                        end = Offset(shift + w * 0.4f, size.height),
                    ),
                )
            },
    )
}

@Composable
fun ListSkeleton(rows: Int = 4, rowHeight: Dp = 120.dp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(rows) { ShimmerBox(Modifier.fillMaxWidth().height(rowHeight), RoundedCornerShape(22.dp)) }
    }
}

/** Shown under skeletons: after ~6s of loading we explain the (Render) cold start. */
@Composable
fun WakeHint(modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(6_000)
        show = true
    }
    AnimatedVisibility(show, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Waking up the server, this can take up to a minute the first time…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Standard loading / error / content switch. */
@Composable
fun <T> LoadBox(
    state: Load<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit = { ListSkeleton() },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is Load.Loading -> Column(modifier) {
            skeleton()
            WakeHint()
        }
        is Load.Failed -> ErrorState(state.message, onRetry, modifier)
        is Load.Ready -> content(state.data)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GhostButton("Try again", onRetry)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            GradientButton(actionLabel, onAction, Modifier.fillMaxWidth(0.7f))
        }
    }
}

// ───────────────────────── motion helpers ─────────────────────────

/** Fades + slides an item in with a small stagger based on [index]. */
@Composable
fun Modifier.staggerIn(index: Int, perItemMs: Int = 55, distance: Dp = 26.dp): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            progress.animateTo(1f, tween(480, delayMillis = index.coerceIn(0, 6) * perItemMs, easing = FastOutSlowInEasing))
        }
    }
    val px = with(LocalDensity.current) { distance.toPx() }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * px
    }
}

@Composable
fun AnimatedCount(
    target: Double,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    format: (Double) -> String = { Fmt.money(it) },
) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) { shown = target.toFloat() }
    val v by animateFloatAsState(shown, tween(if (reduce) 0 else 900, easing = FastOutSlowInEasing), label = "count")
    Text(format(v.toDouble()), modifier, style = style, color = color, maxLines = 1)
}

/** Pulsing red-ish dot/number used for new messages / requests. */
@Composable
fun PulsingBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val reduce = LocalReduceMotion.current
    val s: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "badge").animateFloat(
            1f, 1.18f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "badge-s",
        )
    }
    Box(
        modifier
            .heightIn(min = 18.dp)
            .graphicsLayer { scaleX = s.value; scaleY = s.value }
            .clip(CircleShape)
            .background(Brand.danger)
            .padding(horizontal = 5.dp)
            .semantics { contentDescription = "$count new" },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ───────────────────────── surfaces ─────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.onSurface
    val fill = Brush.linearGradient(listOf(base.copy(alpha = 0.10f), base.copy(alpha = 0.03f)))
    val border = Brush.linearGradient(listOf(base.copy(alpha = 0.28f), base.copy(alpha = 0.06f)))
    var m = modifier.clip(shape).background(fill).border(1.dp, border, shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/**
 * Card that tilts in 3D following the touch position (rotationX/Y + cameraDistance) with a specular
 * glare gradient. Observes touches in the Initial pass without consuming them, so scrolling and
 * clicks keep working.
 */
@Composable
fun Tilt3DBox(
    modifier: Modifier = Modifier,
    maxDegrees: Float = 9f,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    var target by remember { mutableStateOf(Offset.Zero) }
    val ax by animateFloatAsState(target.x, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "tilt-x")
    val ay by animateFloatAsState(target.y, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "tilt-y")
    Box(
        modifier
            .graphicsLayer {
                rotationY = ax * maxDegrees
                rotationX = -ay * maxDegrees
                cameraDistance = 14f * density
            }
            .clip(shape)
            .pointerInput(reduce) {
                if (reduce) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    target = Offset(((down.position.x / w) * 2f - 1f).coerceIn(-1f, 1f), ((down.position.y / h) * 2f - 1f).coerceIn(-1f, 1f))
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val c = event.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        target = Offset(((c.position.x / w) * 2f - 1f).coerceIn(-1f, 1f), ((c.position.y / h) * 2f - 1f).coerceIn(-1f, 1f))
                    }
                    target = Offset.Zero
                }
            }
            .drawWithContent {
                drawContent()
                val strength = (kotlin.math.abs(ax) + kotlin.math.abs(ay)).coerceIn(0f, 1f)
                if (strength > 0.02f) {
                    val c = Offset(size.width * (0.5f + ax * 0.5f), size.height * (0.5f + ay * 0.5f))
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.20f * strength), Color.Transparent),
                            center = c,
                            radius = size.maxDimension * 0.7f,
                        ),
                    )
                }
            },
        content = content,
    )
}

/** Flips between [front] and [back] around the Y axis with a spring. */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val rot by animateFloatAsState(
        if (flipped) 180f else 0f,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
        label = "flip",
    )
    Box(
        modifier.graphicsLayer {
            rotationY = rot
            cameraDistance = 16f * density
        },
    ) {
        if (rot <= 90f) {
            front()
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
        }
    }
}

// ───────────────────────── buttons ─────────────────────────

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "btn-scale")
    val t: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "btn-grad").animateFloat(
            0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "btn-grad-t",
        )
    }
    val active = enabled && !loading
    Box(
        modifier
            .heightIn(min = 52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                val w = size.width
                val x0 = -w * 0.5f + t.value * w
                drawRect(
                    Brush.linearGradient(
                        listOf(Gold, GoldLight, Gold),
                        start = Offset(x0, 0f),
                        end = Offset(x0 + w * 1.4f, size.height),
                    ),
                    alpha = if (enabled) 1f else 0.4f,
                )
            }
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Forest950, strokeWidth = 2.5.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, null, tint = Forest950, modifier = Modifier.size(20.dp))
                Text(text, color = Forest950, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptics = rememberHaptics()
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.primary.copy(alpha = if (enabled) 0.6f else 0.2f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        Text(text, color = c.primary.copy(alpha = if (enabled) 1f else 0.4f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Black.copy(alpha = 0.38f),
    tint: Color = Color.White,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

// ───────────────────────── text field ─────────────────────────

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    error: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    prefix: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        leadingIcon = if (leading != null) ({ Icon(leading, null) }) else null,
        prefix = if (prefix != null) ({ Text(prefix) }) else null,
        trailingIcon = if (password) ({
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide password" else "Show password")
            }
        }) else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.outline,
            focusedLabelColor = cs.primary,
            cursorColor = cs.primary,
            focusedContainerColor = cs.onSurface.copy(alpha = 0.04f),
            unfocusedContainerColor = cs.onSurface.copy(alpha = 0.03f),
        ),
    )
}

// ───────────────────────── small pieces ─────────────────────────

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val haptics = rememberHaptics()
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = { haptics.tap(); onClick() },
        label = { Text(label, maxLines = 1) },
        modifier = modifier.heightIn(min = 36.dp),
        leadingIcon = if (leading != null) ({ Icon(leading, null, Modifier.size(16.dp)) }) else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primary.copy(alpha = 0.22f),
            selectedLabelColor = cs.primary,
            selectedLeadingIconColor = cs.primary,
            containerColor = cs.onSurface.copy(alpha = 0.05f),
            labelColor = cs.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = cs.outline.copy(alpha = 0.6f),
            selectedBorderColor = cs.primary,
        ),
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Colour for booking/thread/request/task statuses. */
fun statusColor(status: String): Color = when (status) {
    "confirmed", "accepted", "open", "in_progress", "partial" -> Brand.info
    "checked_in", "delivered", "completed", "paid", "resolved", "available" -> Brand.success
    "pending", "new", "housekeeping" -> Gold
    "checked_out", "skipped" -> Color(0xFF9AA5A0)
    "cancelled", "no_show", "maintenance" -> Brand.danger
    "occupied" -> Brand.warning
    else -> Color(0xFF9AA5A0)
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
        Text(message ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun QtyStepper(qty: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = 20) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (qty > min) onChange(qty - 1) }, enabled = qty > min) {
            Icon(Icons.Filled.Remove, "Decrease")
        }
        Text(qty.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 22.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { if (qty < max) onChange(qty + 1) }, enabled = qty < max) {
            Icon(Icons.Filled.Add, "Increase")
        }
    }
}

@Composable
fun RatingStars(rating: Int, onRate: ((Int) -> Unit)? = null, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Row(modifier) {
        for (i in 1..5) {
            val icon = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder
            val m = if (onRate != null) Modifier.size(48.dp).clickable(role = Role.Button) { onRate(i) } else Modifier.size(size)
            Box(m, contentAlignment = Alignment.Center) {
                Icon(icon, "$i star", tint = Gold, modifier = Modifier.size(size))
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

// ───────────────────────── images ─────────────────────────

@Composable
fun RemoteImage(
    url: String?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
) {
    val base = LocalAssetBase.current
    val resolved = resolveImageUrl(url, base)
    val ctx = LocalContext.current
    if (resolved == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        }
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(ctx).data(resolved).crossfade(true).build(),
        contentDescription = description,
        modifier = modifier,
        contentScale = contentScale,
        loading = { ShimmerBox(Modifier.fillMaxSize(), RoundedCornerShape(0.dp)) },
        error = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
fun CloseIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Close, "Close") }
}
