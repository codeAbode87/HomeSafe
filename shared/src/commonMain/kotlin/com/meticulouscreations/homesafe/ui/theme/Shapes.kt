package com.meticulouscreations.homesafe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale for HomeSafe: generous outer corners on media cards and
 * floating surfaces, paired with tight inner corners in grouped lists (see [segmentedCardShape]).
 */
val FrigateShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Corner shape for item [index] inside a Material 3 Expressive segmented card group of [count]
 * items: outer ends get generous [outer] corners while touching inner edges tighten to [inner]
 * so adjacent items read as one cohesive group without borders.
 */
fun segmentedCardShape(index: Int, count: Int, outer: Dp = 20.dp, inner: Dp = 4.dp): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(outer)
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}
