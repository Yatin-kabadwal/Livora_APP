package com.livora.corbett.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavItem(val label: String, val icon: ImageVector, val badge: Int = 0)

/** Bottom navigation with a spring-animated sliding indicator, animated tint/scale and pulsing badges. */
@Composable
fun AnimatedBottomBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer.copy(alpha = 0.97f))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f))
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            val itemW = maxWidth / items.size
            val indicatorX by animateDpAsState(
                itemW * selected,
                spring(dampingRatio = 0.62f, stiffness = 380f),
                label = "nav-indicator",
            )
            Box(
                Modifier
                    .offset(x = indicatorX + (itemW - 60.dp) / 2, y = 8.dp)
                    .size(60.dp, 32.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.20f)),
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
                    val active = i == selected
                    val tint by animateColorAsState(if (active) cs.primary else cs.onSurfaceVariant, label = "nav-tint")
                    val scale by animateFloatAsState(
                        if (active) 1.14f else 1f,
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "nav-scale",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .semantics {
                                role = Role.Tab
                                this.selected = active
                                contentDescription = item.label + if (item.badge > 0) ", ${item.badge} new" else ""
                            }
                            .clickable {
                                if (!active) haptics.tap()
                                onSelect(i)
                            }
                            .padding(top = 8.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(width = 60.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                            )
                            if (item.badge > 0) {
                                PulsingBadge(item.badge, Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 4.dp))
                            }
                        }
                        Text(
                            item.label,
                            color = tint,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
