package com.t2sstudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val T2SColorScheme = lightColorScheme(
    primary = T2SOrange,
    onPrimary = T2SBackground,
    primaryContainer = T2SCream,
    onPrimaryContainer = T2SText,
    secondary = T2SPeach,
    onSecondary = T2SText,
    background = T2SBackground,
    onBackground = T2SText,
    surface = T2SSurface,
    onSurface = T2SText,
    surfaceVariant = T2SCream,
    onSurfaceVariant = T2SSecondaryText,
    outline = T2SBorder
)

@Composable
fun T2SStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = T2SColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
