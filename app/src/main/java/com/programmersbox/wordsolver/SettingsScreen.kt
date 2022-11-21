package com.programmersbox.wordsolver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun SettingsDrawer(
    vm: SettingsViewModel,
    wordViewModel: WordViewModel,
    drawerState: DrawerState,
    bottomSheetState: ModalBottomSheetState
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ModalDrawerSheet {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } }
                        ) { Icon(Icons.Default.Close, null) }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            bottomBar = {
                BottomAppBar {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(id = R.string.app_name))
                        Text(appVersion())
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    var columnCount by remember(vm.columnCount) { mutableStateOf(vm.columnCount.toFloat()) }
                    NavigationDrawerItem(
                        label = {
                            ListItem(
                                headlineText = { Text("Column Count") },
                                supportingText = { Text("Choose how many columns there are. Click here to reset to default (3).") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        },
                        selected = true,
                        onClick = { vm.updateColumnCount(3) },
                        badge = { Text(columnCount.roundToInt().toString()) },
                    )

                    Slider(
                        value = columnCount,
                        onValueChange = { columnCount = it },
                        onValueChangeFinished = { vm.updateColumnCount(columnCount.roundToInt()) },
                        valueRange = 1f..5f
                    )
                }

                item { Divider() }

                item {
                    NavigationDrawerItem(
                        label = {
                            ListItem(
                                headlineText = { Text("Show Tutorial") },
                                supportingText = { Text("It might only come up on next app open") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        },
                        selected = true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            vm.showShowcase()
                        },
                    )
                }

                item { Divider() }

                item {
                    NavigationDrawerItem(
                        label = {
                            ListItem(
                                headlineText = { Text("Change Theme") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        },
                        selected = true,
                        onClick = {
                            scope.launch { drawerState.close() }
                            scope.launch { bottomSheetState.show() }
                        },
                    )
                }

                item {
                    val type by vm.letterType.collectAsState(initial = LetterUiType.Circle)
                    var showDialog by remember { mutableStateOf(false) }

                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("Choose a Letter Ui Type") },
                            text = {
                                Column {
                                    LetterUiType.values().dropLast(1).forEach {
                                        ListItem(
                                            modifier = Modifier.clickable { vm.setLetterType(it) },
                                            leadingContent = {
                                                RadioButton(
                                                    selected = it == type,
                                                    onClick = { vm.setLetterType(it) }
                                                )
                                            },
                                            headlineText = { Text(it.name) }
                                        )
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Done") } }
                        )
                    }

                    NavigationDrawerItem(
                        label = {
                            ListItem(
                                headlineText = { Text("Letters UI") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                trailingContent = { Text(type.name) }
                            )
                        },
                        selected = true,
                        onClick = { showDialog = true },
                    )
                }

                if (BuildConfig.DEBUG) {
                    item { Divider() }

                    item {
                        NavigationDrawerItem(
                            label = {
                                ListItem(
                                    headlineText = { Text("Scroll to Item on Already Guessed") },
                                    supportingText = {
                                        Text("Enable if you want to be scrolled to where in the grid the already guessed word is.")
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            },
                            selected = true,
                            onClick = { vm.updateScrollToItem(!vm.scrollToItem) },
                            badge = {
                                Switch(
                                    checked = vm.scrollToItem,
                                    onCheckedChange = { vm.updateScrollToItem(it) }
                                )
                            },
                            modifier = Modifier.height(150.dp),
                            shape = RoundedCornerShape(25)
                        )
                    }
                }

                if (BuildConfig.DEBUG) {
                    item {
                        NavigationDrawerItem(
                            label = {
                                ListItem(
                                    headlineText = { Text("Win Game") },
                                    overlineText = { Text("Cheat") },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            },
                            selected = true,
                            onClick = wordViewModel::cheatGame
                        )
                    }
                }
            }
        }
    }
}