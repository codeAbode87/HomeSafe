package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.meticulouscreations.homesafe.navigation.TopLevelRoute
import com.meticulouscreations.homesafe.ui.WithTooltip
import com.meticulouscreations.homesafe.ui.handCursor
import com.meticulouscreations.homesafe.ui.isCompactLandscape
import com.meticulouscreations.homesafe.ui.preview.FrigatePreview
import com.meticulouscreations.homesafe.ui.rememberPredictiveBack
import com.meticulouscreations.homesafe.ui.theme.FrigateTheme
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.shell_apps_button
import homesafe.shared.generated.resources.shell_drawer_section_apps
import homesafe.shared.generated.resources.shell_quick_apps_all_cards
import homesafe.shared.generated.resources.shell_quick_apps_close
import homesafe.shared.generated.resources.shell_quick_apps_open_app
import org.jetbrains.compose.resources.stringResource

/** The apps shown in the thumb-zone quick switcher, everyday ones on the first row. */
internal val QUICK_SWITCHER_APPS = listOf(
    InnerApp.WEATHER,
    InnerApp.FITNESS,
    InnerApp.FINANCE,
    InnerApp.PACKAGES,
    InnerApp.ATLAS,
    InnerApp.CHARGING,
)

/** Each app's signature badge tint for quick-launch tiles and pills, matching its drawer card. */
internal val InnerApp.quickAccent: Color
    get() = when (this) {
        InnerApp.WEATHER -> Color(0xFF1F6BD6)
        InnerApp.FITNESS -> Color(0xFFD9441A)
        InnerApp.FINANCE -> Color(0xFF1B8A5A)
        InnerApp.PACKAGES -> Color(0xFFC27815)
        InnerApp.ATLAS -> Color(0xFF1A7F8A)
        InnerApp.CHARGING -> Color(0xFF2E7D6F)
    }

/** Test tag on the circular Apps button floating beside the bottom nav. */
const val BOTTOM_NAV_APPS_TEST_TAG = "bottom_nav_apps"

/** Test tag on the thumb-zone quick app switcher sheet. */
const val QUICK_APP_SWITCHER_TEST_TAG = "quick_app_switcher"

/** Test tag on one app's tile inside [QuickAppSwitcher]. */
fun quickAppTileTestTag(app: InnerApp): String = "quick_app_${app.name.lowercase()}"

/** Test tag on one app's direct pill on the sign-in screen. */
fun signInQuickAppTestTag(app: InnerApp): String = "sign_in_app_${app.name.lowercase()}"

/**
 * The circular tonal button floating beside the bottom nav pill (or under the nav rail on a phone
 * on its side), putting Weather, Fitness and the other apps under the user's thumb without a reach
 * for the top-left menu button.
 */
@Composable
internal fun AppsNavButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.shell_apps_button)
    val interactionSource = remember { MutableInteractionSource() }
    WithTooltip(label, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                )
                .handCursor()
                .testTag(BOTTOM_NAV_APPS_TEST_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A compact 3×2 app launcher rising right above the bottom nav so every inner app (Weather,
 * Fitness, Finance, Packages, Atlas, Charging) is reachable in one thumb tap without scrolling a
 * drawer. Tapping a tile grows the app from that tile's position; "All cards" opens the full
 * animated drawer for glanceable cards.
 */
// Overlay that fills the scaffold's layer itself.
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
internal fun QuickAppSwitcher(
    open: Boolean,
    onOpenApp: (InnerApp, Offset) -> Unit,
    onOpenDrawer: () -> Unit,
    onClose: () -> Unit,
) {
    val back = rememberPredictiveBack(enabled = open) { onClose() }
    val pane = stringResource(Res.string.shell_drawer_section_apps)
    val closeLabel = stringResource(Res.string.shell_quick_apps_close)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(NAV_TRANSITION_MS, easing = LinearEasing)),
            exit = fadeOut(tween(NAV_TRANSITION_MS / 2, easing = LinearEasing)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - back.progress * 0.45f).coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = closeLabel,
                        onClick = onClose,
                    )
                    .testTag("quick_app_switcher_scrim"),
            )
        }
        val lift = if (isCompactLandscape()) 16.dp else 20.dp
        AnimatedVisibility(
            visible = open,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = lift),
            enter = fadeIn(tween(NAV_TRANSITION_MS, easing = LinearEasing)) +
                slideInVertically(tween(NAV_TRANSITION_MS, easing = NavEnterEasing)) { it / 3 },
            exit = fadeOut(tween(NAV_TRANSITION_MS / 2, easing = LinearEasing)) +
                slideOutVertically(tween(NAV_TRANSITION_MS / 2)) { it / 3 },
        ) {
            QuickAppSwitcherCard(
                paneTitle = pane,
                closeLabel = closeLabel,
                backProgress = back.progress,
                onOpenApp = onOpenApp,
                onOpenDrawer = onOpenDrawer,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun QuickAppSwitcherCard(
    paneTitle: String,
    closeLabel: String,
    backProgress: Float,
    onOpenApp: (InnerApp, Offset) -> Unit,
    onOpenDrawer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val scale = 1f - backProgress * 0.06f
                scaleX = scale
                scaleY = scale
                translationY = backProgress * 28f
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape)
            .semantics {
                this.paneTitle = paneTitle
                isTraversalGroup = true
            }
            .testTag(QUICK_APP_SWITCHER_TEST_TAG)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = paneTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.12.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenDrawer)
                    .handCursor()
                    .testTag("quick_switcher_drawer")
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.shell_quick_apps_all_cards),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onClose)
                    .handCursor()
                    .testTag("quick_switcher_close"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        QUICK_SWITCHER_APPS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { app ->
                    QuickAppTile(
                        app = app,
                        onOpen = { origin -> onOpenApp(app, origin) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAppTile(
    app: InnerApp,
    onOpen: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val name = stringResource(app.title)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.shell_quick_apps_open_app, name),
                onClick = { onOpen(center) },
            )
            .handCursor()
            .testTag(quickAppTileTestTag(app))
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(app.quickAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One-tap app chips shown on the sign-in screen so Weather, Fitness, Finance and the rest can be
 * opened straight away without going through the drawer or waiting on the camera server.
 */
@Composable
internal fun SignInQuickAppsGrid(
    onOpenApp: (InnerApp, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QUICK_SWITCHER_APPS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { app ->
                    SignInQuickAppChip(
                        app = app,
                        onOpen = { origin -> onOpenApp(app, origin) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInQuickAppChip(
    app: InnerApp,
    onOpen: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val name = stringResource(app.title)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.shell_quick_apps_open_app, name),
                onClick = { onOpen(center) },
            )
            .handCursor()
            .testTag(signInQuickAppTestTag(app))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(app.quickAccent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Quick app switcher", widthDp = 412, heightDp = 420)
@Preview(name = "Quick app switcher, in Spanish", widthDp = 412, heightDp = 420, locale = "es")
@Composable
private fun QuickAppSwitcherPreview() {
    FrigatePreview {
        QuickAppSwitcher(open = true, onOpenApp = { _, _ -> }, onOpenDrawer = {}, onClose = {})
    }
}

@Preview(name = "Sign-in quick apps", widthDp = 380)
@Preview(name = "Sign-in quick apps, in Spanish", widthDp = 380, locale = "es")
@Composable
private fun SignInQuickAppsGridPreview() {
    FrigateTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            SignInQuickAppsGrid(onOpenApp = { _, _ -> })
        }
    }
}

@Preview(name = "Bottom nav with Apps button", widthDp = 390, heightDp = 120)
@Preview(name = "Bottom nav with Apps button, in Spanish", widthDp = 390, heightDp = 120, locale = "es")
@Composable
private fun BottomNavWithAppsPreview() {
    FrigatePreview {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            BottomNavBar(selected = TopLevelRoute.Home, onSelect = {}, onOpenApps = {})
        }
    }
}
