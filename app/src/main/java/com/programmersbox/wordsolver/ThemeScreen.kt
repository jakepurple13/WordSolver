package com.programmersbox.wordsolver

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.programmersbox.wordsolver.ui.theme.Emerald
import com.programmersbox.wordsolver.ui.theme.Theme
import com.programmersbox.wordsolver.ui.theme.animate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeChooser(settingsVm: SettingsViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(4.dp))
                    .size(width = 100.dp, height = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopAppBar(title = { Text("Choose Theme") }, scrollBehavior = null)

            val currentTheme by settingsVm.themeIndexes.collectAsState(0)
            val state by settingsVm.systemThemeMode.collectAsState(SystemThemeMode.FollowSystem)

            ListItem(
                headlineContent = { Text("Select Theme Mode") },
                trailingContent = {
                    GroupButton(
                        selected = state,
                        options = listOf(
                            GroupButtonModel(SystemThemeMode.Day) { Text("Day") },
                            GroupButtonModel(SystemThemeMode.Night) { Text("Night") },
                            GroupButtonModel(SystemThemeMode.FollowSystem) { Text("Follow System") },
                        ),
                        onClick = settingsVm::setSystemThemeMode
                    )
                }
            )

            ListItem(headlineContent = { Text("Select Theme") })

            val context = LocalContext.current
            val darkTheme by settingsVm.systemThemeMode.collectAsState(SystemThemeMode.FollowSystem)
            val isSystemInDarkMode = isSystemInDarkTheme()
            val isDarkTheme by remember {
                derivedStateOf {
                    darkTheme == SystemThemeMode.Night || (isSystemInDarkMode && darkTheme == SystemThemeMode.FollowSystem)
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(Theme.values()) { theme ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { settingsVm.setTheme(theme.ordinal) }
                            .width(80.dp)
                            .border(
                                4.dp,
                                animateColorAsState(
                                    if (currentTheme == theme.ordinal) Emerald
                                    else MaterialTheme.colorScheme.onBackground
                                ).value,
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        theme
                            .getTheme(isDarkTheme, context, true)
                            .let { c ->
                                Row {
                                    ColorBox(color = c.primary.animate().value)
                                    ColorBox(color = c.primaryContainer.animate().value)
                                }
                                Row {
                                    ColorBox(color = c.secondary.animate().value)
                                    ColorBox(color = c.secondaryContainer.animate().value)
                                }
                                Row {
                                    ColorBox(color = c.tertiary.animate().value)
                                    ColorBox(color = c.tertiaryContainer.animate().value)
                                }
                                Row {
                                    ColorBox(color = c.background.animate().value)
                                    ColorBox(color = c.surface.animate().value)
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorBox(color: Color) {
    Box(
        Modifier
            .background(color)
            .size(40.dp)
    )
}

class GroupButtonModel<T>(val item: T, val iconContent: @Composable () -> Unit)

@Composable
fun <T> GroupButton(
    selected: T,
    options: List<GroupButtonModel<T>>,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    onClick: (T) -> Unit
) {
    Row(modifier) {
        val noCorner = CornerSize(0.dp)

        options.forEachIndexed { i, option ->
            OutlinedButton(
                modifier = Modifier,
                onClick = { onClick(option.item) },
                shape = shape.copy(
                    topStart = if (i == 0) shape.topStart else noCorner,
                    topEnd = if (i == options.size - 1) shape.topEnd else noCorner,
                    bottomStart = if (i == 0) shape.bottomStart else noCorner,
                    bottomEnd = if (i == options.size - 1) shape.bottomEnd else noCorner
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = animateColorAsState(if (selected == option.item) selectedColor else unselectedColor).value,
                    contentColor = animateColorAsState(if (selected == option.item) selectedContentColor else unselectedContentColor).value
                )
            ) { option.iconContent() }
        }
    }
}