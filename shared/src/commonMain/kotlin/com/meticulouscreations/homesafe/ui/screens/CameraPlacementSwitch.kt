package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meticulouscreations.homesafe.domain.model.CameraPlacement
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.home_placement_indoor
import homesafe.shared.generated.resources.home_placement_outdoor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Home page's quick swap between the outdoor cameras and the indoor ones: a Material 3
 * Expressive connected button group in a floating tonal capsule, with shape-morphing segments.
 * A pair of tabs to a screen reader, since that is what it does to the list under it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CameraPlacementSwitch(
    selected: CameraPlacement,
    onSelect: (CameraPlacement) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = CAMERA_PLACEMENT_SWITCH_TEST_TAG,
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            val entries = CameraPlacement.entries
            entries.forEachIndexed { index, placement ->
                val isSelected = placement == selected
                val shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onSelect(placement) },
                    shapes = shapes,
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    },
                ) {
                    Icon(
                        imageVector = placement.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(placement.label),
                        style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private val CameraPlacement.label: StringResource
    get() = when (this) {
        CameraPlacement.Outdoor -> Res.string.home_placement_outdoor
        CameraPlacement.Indoor -> Res.string.home_placement_indoor
    }

private val CameraPlacement.icon: ImageVector
    get() = when (this) {
        CameraPlacement.Outdoor -> Icons.Outlined.Park
        CameraPlacement.Indoor -> Icons.Outlined.Weekend
    }

internal const val CAMERA_PLACEMENT_SWITCH_TEST_TAG = "home-camera-placement-switch"

/** The same switch floating at the foot of the Home page, within reach of the thumb. */
internal const val FLOATING_PLACEMENT_SWITCH_TEST_TAG = "home-floating-placement-switch"
