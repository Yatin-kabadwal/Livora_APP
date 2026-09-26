package com.livora.corbett.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.livora.corbett.ui.theme.LocalReduceMotion
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2.0 * PI).toFloat()
private const val LAYERS = 5

/** Sky mood: 0 = day, 0.5 = dusk, 1 = night. */
fun defaultSkyMood(): Float {
    val h = LocalTime.now().hour
    return when {
        h in 7..16 -> 0f
        h in 17..18 || h in 5..6 -> 0.5f
        else -> 1f
    }
}

/**
 * Canvas-drawn parallax forest valley: layered ridges, pine silhouettes, drifting mist, twinkling
 * fireflies and a sun/moon glow. Reacts to device tilt (rotation-vector sensor, low-pass filtered)
 * and to [scroll] (e.g. pager position, in "pages"). Everything is drawn in one Canvas.
 */
@Composable
fun ForestBackdrop(
    modifier: Modifier = Modifier,
    mood: Float = 0.75f,
    scroll: () -> Float = { 0f },
    tiltEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val moodState = animateFloatAsState(mood, tween(if (reduce) 0 else 1800), label = "sky-mood")
    val tilt = rememberTilt(enabled = tiltEnabled && !reduce)
    val clock: State<Float> = if (reduce) {
        remember { mutableFloatStateOf(0.3f) }
    } else {
        rememberInfiniteTransition(label = "forest-clock").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
            label = "forest-clock-value",
        )
    }
    val seeds = remember { ForestSeeds() }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawForest(moodState.value, clock.value, tilt.value, scroll(), seeds)
        }
        content()
    }
}

// ───────────────────────── tilt sensor ─────────────────────────

/** Low-pass filtered device tilt in [-1, 1] on both axes; recentres itself slowly. */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    LifecycleResumeEffect(enabled) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (enabled) sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) else null
        val listener: SensorEventListener? = if (sm != null && sensor != null) {
            val rot = FloatArray(9)
            val ori = FloatArray(3)
            val base = FloatArray(2)
            var haveBase = false
            var fx = 0f
            var fy = 0f
            val l = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, e.values)
                    SensorManager.getOrientation(rot, ori)
                    val pitch = ori[1]
                    val roll = ori[2]
                    if (!haveBase) {
                        base[0] = pitch
                        base[1] = roll
                        haveBase = true
                    }
                    val tx = ((roll - base[1]) / 0.5f).coerceIn(-1f, 1f)
                    val ty = ((pitch - base[0]) / 0.5f).coerceIn(-1f, 1f)
                    fx += (tx - fx) * 0.08f
                    fy += (ty - fy) * 0.08f
                    base[0] += (pitch - base[0]) * 0.004f
                    base[1] += (roll - base[1]) * 0.004f
                    state.value = Offset(fx, fy)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
            l
        } else {
            null
        }
        onPauseOrDispose {
            if (listener != null) sm?.unregisterListener(listener)
        }
    }
    return state
}

// ───────────────────────── palette ─────────────────────────

private class Sky(
    val top: Color,
    val mid: Color,
    val horizon: Color,
    val glow: Color,
    val body: Color,
    val ridgeFar: Color,
    val ridgeNear: Color,
    val mist: Color,
    val stars: Float,
)

private val DAY = Sky(
    top = Color(0xFF4F8F86), mid = Color(0xFF9CCBB2), horizon = Color(0xFFEFE6C5),
    glow = Color(0xFFFFF2C0), body = Color(0xFFFFF6D6),
    ridgeFar = Color(0xFF86B49C), ridgeNear = Color(0xFF17382A),
    mist = Color(0xFFFFFFFF), stars = 0f,
)
private val DUSK = Sky(
    top = Color(0xFF24304A), mid = Color(0xFF7A5A6A), horizon = Color(0xFFE8935A),
    glow = Color(0xFFFFB36A), body = Color(0xFFFFD9A0),
    ridgeFar = Color(0xFF5B4A5C), ridgeNear = Color(0xFF0B1A13),
    mist = Color(0xFFE8B090), stars = 0.25f,
)
private val NIGHT = Sky(
    top = Color(0xFF050B12), mid = Color(0xFF0B1A26), horizon = Color(0xFF1A3B30),
    glow = Color(0xFFDFE8F5), body = Color(0xFFF1F4FA),
    ridgeFar = Color(0xFF17382A), ridgeNear = Color(0xFF07110C),
    mist = Color(0xFF8FB9A0), stars = 1f,
)

private fun lerpSky(a: Sky, b: Sky, t: Float) = Sky(
    lerp(a.top, b.top, t), lerp(a.mid, b.mid, t), lerp(a.horizon, b.horizon, t),
    lerp(a.glow, b.glow, t), lerp(a.body, b.body, t),
    lerp(a.ridgeFar, b.ridgeFar, t), lerp(a.ridgeNear, b.ridgeNear, t),
    lerp(a.mist, b.mist, t), a.stars + (b.stars - a.stars) * t,
)

private fun skyAt(m: Float): Sky =
    if (m < 0.5f) lerpSky(DAY, DUSK, (m * 2f).coerceIn(0f, 1f)) else lerpSky(DUSK, NIGHT, ((m - 0.5f) * 2f).coerceIn(0f, 1f))

// ───────────────────────── seeds ─────────────────────────

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val k: Int)
private class Fly(val x: Float, val y: Float, val phase: Float, val k: Int, val drift: Int)
private class PinePos(val x: Float, val scale: Float)

private class ForestSeeds {
    private val rnd = Random(20260926)
    val stars = List(48) { Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.2f, rnd.nextFloat() * TAU, 20 + rnd.nextInt(30)) }
    val flies = List(20) { Fly(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * TAU, 25 + rnd.nextInt(30), 6 + rnd.nextInt(8)) }
    val phases = FloatArray(LAYERS) { rnd.nextFloat() * TAU }
    val pines: List<List<PinePos>> = List(LAYERS) { layer ->
        val count = if (layer >= 2) 10 + layer * 4 else 0
        List(count) { PinePos(rnd.nextFloat(), 0.7f + rnd.nextFloat() * 0.7f) }
    }
}

// ───────────────────────── drawing ─────────────────────────

private fun ridgeY(x: Float, layer: Int, w: Float, h: Float, phase: Float): Float {
    val base = h * (0.46f + 0.085f * layer)
    val amp = h * (0.05f + 0.012f * layer)
    val nx = x / max(w, 1f) * TAU
    val v = sin(nx * (1.1f + 0.3f * layer) + phase) * 0.6f +
        sin(nx * (2.7f + 0.4f * layer) + phase * 1.7f) * 0.3f +
        sin(nx * 6.1f + phase * 2.3f) * 0.1f
    return base - amp * v
}

private fun DrawScope.drawPine(cx: Float, baseY: Float, ph: Float, color: Color) {
    val pw = ph * 0.34f
    val p = Path()
    p.moveTo(cx, baseY - ph)
    p.lineTo(cx + pw * 0.55f, baseY - ph * 0.58f)
    p.lineTo(cx + pw * 0.28f, baseY - ph * 0.58f)
    p.lineTo(cx + pw * 0.8f, baseY - ph * 0.26f)
    p.lineTo(cx + pw * 0.36f, baseY - ph * 0.26f)
    p.lineTo(cx + pw, baseY)
    p.lineTo(cx - pw, baseY)
    p.lineTo(cx - pw * 0.36f, baseY - ph * 0.26f)
    p.lineTo(cx - pw * 0.8f, baseY - ph * 0.26f)
    p.lineTo(cx - pw * 0.28f, baseY - ph * 0.58f)
    p.lineTo(cx - pw * 0.55f, baseY - ph * 0.58f)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.drawForest(mood: Float, t: Float, tilt: Offset, scroll: Float, seeds: ForestSeeds) {
    val w = size.width
    val h = size.height
    val d = density
    val sky = skyAt(mood)

    // sky
    drawRect(Brush.verticalGradient(0f to sky.top, 0.55f to sky.mid, 1f to sky.horizon))

    // stars
    if (sky.stars > 0.02f) {
        seeds.stars.forEach { s ->
            val tw = 0.55f + 0.45f * sin(t * TAU * s.k + s.phase)
            drawCircle(
                Color.White.copy(alpha = (sky.stars * tw).coerceIn(0f, 1f)),
                radius = s.r * d,
                center = Offset(s.x * w - tilt.x * w * 0.01f, s.y * h * 0.5f),
            )
        }
    }

    // sun / moon glow
    val cx = w * 0.72f - tilt.x * w * 0.03f - scroll * w * 0.06f
    val cy = h * 0.24f + tilt.y * h * 0.02f
    val glowR = min(w, h) * 0.6f
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(sky.glow.copy(alpha = 0.55f), sky.glow.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = glowR,
        ),
        radius = glowR,
        center = center,
    )
    drawCircle(sky.body, radius = w * 0.045f, center = center)

    // ridges, pines and mist
    for (i in 0 until LAYERS) {
        val depth = i / (LAYERS - 1f)
        val off = (tilt.x * 0.045f * w + scroll * 0.16f * w) * (0.25f + depth) * -1f
        val yShift = tilt.y * h * 0.012f * (0.25f + depth)
        val fade = 1f - depth
        val color = lerp(lerp(sky.ridgeFar, sky.mid, 0.25f * fade), sky.ridgeNear, depth.coerceIn(0f, 1f) * 0.98f + 0.02f)

        val path = Path()
        val step = max(w / 56f, 6f)
        val phase = seeds.phases[i]
        path.moveTo(-40f, h + 4f)
        var x = -40f
        while (x <= w + 40f) {
            path.lineTo(x, ridgeY(x - off, i, w, h, phase) + yShift)
            x += step
        }
        path.lineTo(w + 40f, h + 4f)
        path.close()
        drawPath(path, color)

        // pines on the nearer ridges
        seeds.pines[i].forEach { p ->
            val px = p.x * (w + 80f) - 40f
            val py = ridgeY(px - off, i, w, h, phase) + yShift + 2f * d
            val ph = h * (0.035f + 0.03f * depth) * p.scale
            drawPine(px, py, ph, color)
        }

        // mist band drifting in front of mid layers
        if (i == 1 || i == 3) {
            val k = if (i == 1) 3 else 5
            val drift = ((t * k + i * 0.37f) % 1f)
            val mx = drift * w * 1.8f - w * 0.4f
            val my = ridgeY(w * 0.5f, i, w, h, phase) + h * 0.03f
            val rad = w * 0.6f
            val c = Offset(mx, my)
            val a = if (i == 1) 0.20f else 0.16f
            val sx = 1.8f
            val sy = 0.28f
            withTransform({ scale(sx, sy, c) }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(sky.mist.copy(alpha = a), Color.Transparent),
                        center = c,
                        radius = rad,
                    ),
                    radius = rad,
                    center = c,
                )
            }
        }
    }

    // fireflies
    val vis = 0.2f + 0.8f * mood
    seeds.flies.forEach { f ->
        val ang = t * TAU * f.drift
        val fx = f.x * w + sin(ang + f.phase) * w * 0.03f - tilt.x * w * 0.02f
        val fy = h * (0.55f + f.y * 0.4f) + cos(ang * 1.3f + f.phase) * h * 0.012f
        val tw = sin(t * TAU * f.k + f.phase)
        val a = (tw * tw) * vis
        if (a > 0.03f) {
            val r = 5f * d
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFF0D9A0).copy(alpha = a), Color(0xFFD9B76A).copy(alpha = a * 0.35f), Color.Transparent),
                    center = Offset(fx, fy),
                    radius = r * 3.2f,
                ),
                radius = r * 3.2f,
                center = Offset(fx, fy),
            )
            drawCircle(Color(0xFFFFF6D0).copy(alpha = a), radius = r * 0.32f, center = Offset(fx, fy))
        }
    }

    // bottom scrim so foreground UI stays legible
    drawRect(
        Brush.verticalGradient(
            0.55f to Color.Transparent,
            1f to Color(0xFF07110C).copy(alpha = 0.55f),
        ),
    )
}
