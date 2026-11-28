package com.meticulouscreations.homesafe.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import homesafe.shared.generated.resources.Res
import homesafe.shared.generated.resources.albert_sans_medium
import homesafe.shared.generated.resources.albert_sans_regular
import homesafe.shared.generated.resources.albert_sans_semibold
import homesafe.shared.generated.resources.fraunces_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun frauncesFontFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_semibold, FontWeight.SemiBold),
)

@Composable
fun albertSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.albert_sans_regular, FontWeight.Normal),
    Font(Res.font.albert_sans_medium, FontWeight.Medium),
    Font(Res.font.albert_sans_semibold, FontWeight.SemiBold),
)

/**
 * Maps the Stitch design system's named text styles onto Material 3 Expressive's type scale,
 * including emphasized roles for prominent headers, camera names, and active controls.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun frigateTypography(): Typography {
    val fraunces = frauncesFontFamily()
    val albertSans = albertSansFontFamily()
    val base = Typography()
    val displayLarge = TextStyle(
        fontFamily = fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.4.sp,
        letterSpacing = (-0.02).em,
    )
    val headlineMedium = TextStyle(
        fontFamily = fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.4.sp,
    )
    val headlineSmall = TextStyle(
        fontFamily = fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.6.sp,
    )
    val titleLarge = base.titleLarge.copy(
        fontFamily = fraunces,
        fontWeight = FontWeight.SemiBold,
    )
    val titleMedium = base.titleMedium.copy(
        fontFamily = albertSans,
        fontWeight = FontWeight.SemiBold,
    )
    val bodyLarge = TextStyle(
        fontFamily = albertSans,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    )
    val bodyMedium = TextStyle(
        fontFamily = albertSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val labelLarge = TextStyle(
        fontFamily = albertSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.01.em,
    )
    val labelMedium = TextStyle(
        fontFamily = albertSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.2.sp,
        letterSpacing = 0.02.em,
    )
    val labelSmall = TextStyle(
        fontFamily = albertSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.2.sp,
        letterSpacing = 0.05.em,
    )
    return base.copy(
        displayLarge = displayLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
        displayLargeEmphasized = displayLarge.copy(letterSpacing = (-0.03).em),
        headlineMediumEmphasized = headlineMedium.copy(letterSpacing = (-0.015).em),
        headlineSmallEmphasized = headlineSmall.copy(letterSpacing = (-0.01).em),
        titleLargeEmphasized = titleLarge.copy(letterSpacing = (-0.01).em),
        titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
        labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
        labelMediumEmphasized = labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
        labelSmallEmphasized = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em),
    )
}
