package com.meticulouscreations.homesafe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.camera_frame_button
import homesafe.shared.generated.resources.camera_hide
import homesafe.shared.generated.resources.camera_show
import homesafe.shared.generated.resources.camera_turn_left
import homesafe.shared.generated.resources.camera_turn_right
import org.jetbrains.compose.resources.stringResource

/**
 * A camera card's controls in Arrange mode, in a floating Material 3 Expressive capsule along its
 * bottom edge over the video: a quarter turn either way, the framing editor ([onFrame]), and the
 * hide toggle ([onHiddenChange]).
 *
 * Spread across the capsule's width. On a card too narrow for all four (two or three abreast in a
 * landscape window, from 200dp) the labelled Frame button is left out, whatever length its label
 * has in the language: tapping the card frames it as well, and says so to a screen reader. The
 * three icon buttons keep their full touch targets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CameraArrangeControls(
    onTurn: (clockwise: Boolean) -> Unit,
    onFrame: () -> Unit,
    hidden: Boolean,
    onHiddenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
        ) {
            Layout(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                content = {
                    IconButton(onClick = { onTurn(false) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.RotateLeft, contentDescription = stringResource(Res.string.camera_turn_left), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    FilledTonalButton(
                        onClick = onFrame,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag(CAMERA_FRAME_BUTTON_TEST_TAG),
                    ) {
                        Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(Res.string.camera_frame_button))
                    }
                    IconButton(onClick = { onTurn(true) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.RotateRight, contentDescription = stringResource(Res.string.camera_turn_right), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    FilledTonalIconToggleButton(
                        checked = hidden,
                        onCheckedChange = onHiddenChange,
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.testTag(CAMERA_HIDE_BUTTON_TEST_TAG),
                    ) {
                        Icon(
                            imageVector = if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (hidden) Res.string.camera_show else Res.string.camera_hide),
                        )
                    }
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val buttons = measurables.map { it.measure(loose) }
                val gapPx = 4.dp.roundToPx()
                val allWidth = buttons.sumOf { it.width } + gapPx * (buttons.size - 1)
                // All four where they fit; otherwise the three icon buttons, the card's tap framing it.
                val shown = if (allWidth <= constraints.maxWidth) buttons else buttons.filterIndexed { index, _ -> index != FRAME_BUTTON_INDEX }
                val height = shown.maxOf { it.height }
                val totalWidth = (shown.sumOf { it.width } + gapPx * (shown.size - 1)).coerceAtMost(constraints.maxWidth)
                val gap = if (shown.size > 1) ((totalWidth - shown.sumOf { it.width }).coerceAtLeast(0)) / (shown.size - 1) else 0
                layout(totalWidth, height) {
                    var x = 0
                    for (button in shown) {
                        button.placeRelative(x, (height - button.height) / 2)
                        x += button.width + gap
                    }
                }
            }
        }
    }
}

/** Where the labelled Frame button comes among the controls: the one left out when they don't all fit. */
private const val FRAME_BUTTON_INDEX = 1

/** The card's way into the framing editor in Arrange mode. */
internal const val CAMERA_FRAME_BUTTON_TEST_TAG = "camera_frame_button"

/** The card's hide / show toggle in Arrange mode. */
internal const val CAMERA_HIDE_BUTTON_TEST_TAG = "camera_hide_button"
