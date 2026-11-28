package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meticulouscreations.homesafe.ui.components.PulsingDot
import com.meticulouscreations.homesafe.ui.handCursor
import com.meticulouscreations.homesafe.ui.theme.FrigateTheme
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.shell_drawer_quick_open
import homesafe.shared.generated.resources.shell_drawer_quick_workout_active
import org.jetbrains.compose.resources.stringResource

/** Everyday apps first, so Weather, Fitness and Finance sit on the top row of the drawer's quick strip. */
internal val DRAWER_QUICK_APPS = listOf(
    InnerApp.WEATHER,
    InnerApp.FITNESS,
    InnerApp.FINANCE,
    InnerApp.PACKAGES,
    InnerApp.ATLAS,
    InnerApp.CHARGING,
)

/** Test tag on one app's quick-jump tile at the top of [ShellDrawer]. */
fun drawerQuickAppTestTag(app: InnerApp): String = "drawer_quick_${app.name.lowercase()}"

/**
 * Compact 3×2 quick-jump strip at the top of [ShellDrawer] so every inner app (Weather, Fitness,
 * Finance, Packages, Atlas, Charging) is reachable in one tap the moment the drawer slides open,
 * without scrolling past the tall live-shader glance cards underneath. When [workoutActive] is
 * true, Fitness wears a live pulse so an in-progress workout stands out immediately.
 */
@Composable
fun DrawerQuickStrip(
    onOpen: (InnerApp, Offset) -> Unit,
    modifier: Modifier = Modifier,
    workoutActive: Boolean = false,
    animated: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DRAWER_QUICK_APPS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { app ->
                    DrawerQuickTile(
                        app = app,
                        highlighted = workoutActive && app == InnerApp.FITNESS,
                        animated = animated,
                        onOpen = { origin -> onOpen(app, origin) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerQuickTile(
    app: InnerApp,
    highlighted: Boolean,
    animated: Boolean,
    onOpen: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val name = stringResource(app.title)
    val accent = app.drawerQuickAccent
    val shape = RoundedCornerShape(16.dp)
    val openLabel = stringResource(Res.string.shell_drawer_quick_open, name)
    val description = if (highlighted) {
        stringResource(Res.string.shell_drawer_quick_workout_active, name)
    } else {
        null
    }
    Column(
        modifier = modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (highlighted) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                shape = shape,
            )
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = { onOpen(center) })
            .handCursor()
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .testTag(drawerQuickAppTestTag(app))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            if (highlighted) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    PulsingDot(color = Color(0xFFFF7A45), size = 7.dp, pulsing = animated)
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val InnerApp.drawerQuickAccent: Color
    get() = when (this) {
        InnerApp.WEATHER -> Color(0xFF1F6BD6)
        InnerApp.FITNESS -> Color(0xFFD9441A)
        InnerApp.FINANCE -> Color(0xFF1B8A5A)
        InnerApp.PACKAGES -> Color(0xFFC27815)
        InnerApp.ATLAS -> Color(0xFF1A7F8A)
        InnerApp.CHARGING -> Color(0xFF2E7D6F)
    }

@Preview(name = "Drawer quick strip", widthDp = 304)
@Preview(name = "Drawer quick strip, in Spanish", widthDp = 304, locale = "es")
@Composable
private fun DrawerQuickStripPreview() {
    FrigateTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp)) {
            DrawerQuickStrip(onOpen = { _, _ -> }, workoutActive = true, animated = false)
        }
    }
}
