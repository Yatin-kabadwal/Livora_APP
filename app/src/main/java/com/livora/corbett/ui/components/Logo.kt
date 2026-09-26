package com.livora.corbett.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livora.corbett.ui.theme.Gold
import com.livora.corbett.ui.theme.GoldLight
import com.livora.corbett.ui.theme.Sage

/** The gold "V" + leaf mark (same geometry as the launcher icon). */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Canvas(modifier.size(size).semantics { contentDescription = "Corbett The Vedant logo" }) {
        val s = this.size.minDimension / 60f
        // Source geometry lives in a 108 viewport; the mark occupies x 33..75, y 22..78.
        fun px(x: Float) = (x - 24f) * s
        fun py(y: Float) = (y - 20f) * s
        val left = Path().apply {
            moveTo(px(33f), py(38f)); lineTo(px(45f), py(38f)); lineTo(px(56f), py(68f)); lineTo(px(51f), py(78f)); close()
        }
        val right = Path().apply {
            moveTo(px(75f), py(38f)); lineTo(px(63f), py(38f)); lineTo(px(52f), py(68f)); lineTo(px(57f), py(78f)); close()
        }
        val leaf = Path().apply {
            moveTo(px(54f), py(22f))
            cubicTo(px(60f), py(27f), px(60f), py(34f), px(54f), py(40f))
            cubicTo(px(48f), py(34f), px(48f), py(27f), px(54f), py(22f))
            close()
        }
        drawPath(left, Gold)
        drawPath(right, GoldLight)
        drawPath(leaf, Sage)
    }
}
