package com.livora.corbett.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livora.corbett.ui.theme.Brand
import com.livora.corbett.ui.theme.Cream
import com.livora.corbett.ui.theme.Ember
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.ui.theme.GoldLight
import com.livora.corbett.ui.theme.LocalReduceMotion
import com.livora.corbett.ui.theme.Sage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val rect: Boolean,
)

/** Lottie-free celebration: Canvas confetti burst + animated ring and check mark. */
@Composable
fun BookingCelebration(modifier: Modifier = Modifier, checkSize: Dp = 132.dp) {
    val reduce = LocalReduceMotion.current
    val burst = remember { Animatable(if (reduce) 1f else 0f) }
    val ring = remember { Animatable(if (reduce) 1f else 0f) }
    val check = remember { Animatable(if (reduce) 1f else 0f) }
    val particles = remember {
        val rnd = Random(42)
        val palette = listOf(Gold, GoldLight, Sage, Ember, Cream, Brand.success)
        List(72) {
            Particle(
                angle = (rnd.nextFloat() * 2f * PI).toFloat(),
                speed = 0.35f + rnd.nextFloat() * 0.9f,
                size = 4f + rnd.nextFloat() * 6f,
                color = palette[rnd.nextInt(palette.size)],
                spin = rnd.nextFloat() * 720f - 360f,
                rect = rnd.nextBoolean(),
            )
        }
    }
    LaunchedEffect(Unit) {
        if (!reduce) {
            ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            check.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            burst.animateTo(1f, tween(2600, easing = LinearEasing))
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val reach = size.minDimension * 0.75f
            val t = burst.value
            if (t > 0f && t < 1f) {
                val alpha = (1f - t * t).coerceIn(0f, 1f)
                particles.forEach { p ->
                    val dist = p.speed * reach * (1f - (1f - t) * (1f - t))
                    val gravity = 0.55f * reach * t * t
                    val pos = Offset(c.x + cos(p.angle) * dist, c.y + sin(p.angle) * dist + gravity)
                    if (p.rect) {
                        rotate(p.spin * t, pos) {
                            drawRect(p.color.copy(alpha = alpha), topLeft = Offset(pos.x - p.size, pos.y - p.size / 2f), size = Size(p.size * 2f, p.size))
                        }
                    } else {
                        drawCircle(p.color.copy(alpha = alpha), radius = p.size / 2f, center = pos)
                    }
                }
            }
            val r = checkSize.toPx() / 2f
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Gold,
                startAngle = -90f,
                sweepAngle = 360f * ring.value,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = stroke,
            )
            // check mark: two segments drawn progressively
            val a = Offset(c.x - r * 0.42f, c.y + r * 0.02f)
            val b = Offset(c.x - r * 0.10f, c.y + r * 0.34f)
            val d = Offset(c.x + r * 0.46f, c.y - r * 0.30f)
            val p = check.value
            val firstFrac = (p / 0.4f).coerceIn(0f, 1f)
            val secondFrac = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (firstFrac > 0f) {
                drawLine(GoldLight, a, Offset(a.x + (b.x - a.x) * firstFrac, a.y + (b.y - a.y) * firstFrac), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            }
            if (secondFrac > 0f) {
                drawLine(GoldLight, b, Offset(b.x + (d.x - b.x) * secondFrac, b.y + (d.y - b.y) * secondFrac), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}
