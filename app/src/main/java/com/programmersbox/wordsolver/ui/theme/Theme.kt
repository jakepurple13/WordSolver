package com.programmersbox.wordsolver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.canopas.lib.showcase.ShowcaseStyle
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun WordSolverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    colorScheme: Theme = Theme.Default,
    content: @Composable () -> Unit
) {
    val uiController = rememberSystemUiController()
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect { uiController.setSystemBarsColor(Color.Transparent, !darkTheme) }
    }

    MaterialTheme(
        colorScheme = colorScheme.getTheme(darkTheme, context, dynamicColor).animateToNewScheme(),
        typography = Typography,
        content = content
    )
}

@Composable
private fun ColorScheme.animateToNewScheme() = copy(
    primary = primary.animate().value,
    onPrimary = onPrimary.animate().value,
    primaryContainer = primaryContainer.animate().value,
    onPrimaryContainer = onPrimaryContainer.animate().value,
    inversePrimary = inversePrimary.animate().value,
    secondary = secondary.animate().value,
    onSecondary = onSecondary.animate().value,
    secondaryContainer = secondaryContainer.animate().value,
    onSecondaryContainer = onSecondaryContainer.animate().value,
    tertiary = tertiary.animate().value,
    onTertiary = onTertiary.animate().value,
    tertiaryContainer = tertiaryContainer.animate().value,
    onTertiaryContainer = onTertiaryContainer.animate().value,
    background = background.animate().value,
    onBackground = onBackground.animate().value,
    surface = surface.animate().value,
    onSurface = onSurface.animate().value,
    surfaceVariant = surfaceVariant.animate().value,
    onSurfaceVariant = onSurfaceVariant.animate().value,
    surfaceTint = surfaceTint.animate().value,
    inverseSurface = inverseSurface.animate().value,
    inverseOnSurface = inverseOnSurface.animate().value,
    error = error.animate().value,
    onError = onError.animate().value,
    errorContainer = errorContainer.animate().value,
    onErrorContainer = onErrorContainer.animate().value,
    outline = outline.animate().value,
    outlineVariant = outlineVariant.animate().value,
    scrim = scrim.animate().value
)

@Composable
fun introShowCaseStyle() = ShowcaseStyle.Default.copy(
    backgroundColor = MaterialTheme.colorScheme.background,
    targetCircleColor = Color.White,
    backgroundAlpha = 0.98f
)