package com.meticulouscreations.homesafe.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrigateTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFrigateExtraColors provides FrigateDarkExtraColors) {
        MaterialExpressiveTheme(
            colorScheme = FrigateDarkColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = FrigateShapes,
            typography = frigateTypography(),
            content = content,
        )
    }
}
