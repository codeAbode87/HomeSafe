package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.meticulouscreations.homesafe.ui.handCursor
import com.meticulouscreations.homesafe.ui.theme.FrigateTheme
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.settings_shortcuts_add_label
import homesafe.shared.generated.resources.settings_shortcuts_caption
import homesafe.shared.generated.resources.settings_shortcuts_requested
import org.jetbrains.compose.resources.stringResource

/** The apps offered for pinning to the home screen, everyday ones first. */
internal val PINNABLE_APPS = listOf(
    InnerApp.WEATHER,
    InnerApp.FITNESS,
    InnerApp.FINANCE,
    InnerApp.PACKAGES,
    InnerApp.ATLAS,
    InnerApp.CHARGING,
)

/**
 * Lets the user pin any of PercySafe's apps directly onto the Android home screen as a one-tap
 * icon, shown inside Settings' "Home screen shortcuts" fold. [pinnedApp] is the app whose pin
 * sheet was just requested, shown briefly as a confirmation check.
 */
@Composable
internal fun HomeShortcutsOptions(
    pinnedApp: InnerApp?,
    onPin: (InnerApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsCaption(stringResource(Res.string.settings_shortcuts_caption))
        PINNABLE_APPS.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { app ->
                    ShortcutPinTile(
                        app = app,
                        requested = app == pinnedApp,
                        onClick = { onPin(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (pinnedApp != null) {
            SettingsCaption(
                stringResource(Res.string.settings_shortcuts_requested, stringResource(pinnedApp.title)),
            )
        }
    }
}

@Composable
private fun ShortcutPinTile(
    app: InnerApp,
    requested: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(app.title)
    val shape = RoundedCornerShape(14.dp)
    val accent = app.shortcutAccent
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (requested) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = shape,
            )
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.settings_shortcuts_add_label, name),
                onClick = onClick,
            )
            .handCursor()
            .testTag("settings_pin_${app.name.lowercase()}")
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(app.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (requested) Icons.Filled.Check else Icons.Filled.Add,
            contentDescription = null,
            tint = if (requested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Each app's signature badge tint for its home-screen shortcut icon, matching its drawer card. */
internal val InnerApp.shortcutAccent: Color
    get() = when (this) {
        InnerApp.WEATHER -> Color(0xFF1F6BD6)
        InnerApp.FITNESS -> Color(0xFFD9441A)
        InnerApp.FINANCE -> Color(0xFF1B8A5A)
        InnerApp.PACKAGES -> Color(0xFFC27815)
        InnerApp.ATLAS -> Color(0xFF1A7F8A)
        InnerApp.CHARGING -> Color(0xFF2E7D6F)
    }

@Preview(name = "Home shortcuts options", widthDp = 380)
@Preview(name = "Home shortcuts options, in Spanish", widthDp = 380, locale = "es")
@Composable
private fun HomeShortcutsOptionsPreview() {
    FrigateTheme {
        Surface {
            Box(Modifier.padding(16.dp)) {
                HomeShortcutsOptions(pinnedApp = InnerApp.FITNESS, onPin = {})
            }
        }
    }
}
