package com.programmersbox.wordsolver

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canopas.lib.showcase.IntroShowCaseScaffold
import com.canopas.lib.showcase.IntroShowCaseScope
import com.programmersbox.chatfunctionality.ChatIcon
import com.programmersbox.chatfunctionality.ChatUi
import com.programmersbox.chatfunctionality.ChatViewModel
import com.programmersbox.wordsolver.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val context = LocalContext.current
            val settingsVm = viewModel { SettingsViewModel(context) }
            val defaultTheme = remember {
                runBlocking {
                    Theme.values().find { it.ordinal == settingsVm.themeIndex.firstOrNull()?.ordinal } ?: Theme.Default
                }
            }

            val defaultMode = remember {
                runBlocking { settingsVm.systemThemeMode.firstOrNull() ?: SystemThemeMode.FollowSystem }
            }
            val darkTheme by settingsVm.systemThemeMode.collectAsState(defaultMode)

            val isSystemInDarkMode = isSystemInDarkTheme()
            val isDarkTheme by remember {
                derivedStateOf {
                    darkTheme == SystemThemeMode.Night || (isSystemInDarkMode && darkTheme == SystemThemeMode.FollowSystem)
                }
            }

            WordSolverTheme(
                darkTheme = isDarkTheme,
                colorScheme = settingsVm.themeIndex.collectAsState(initial = defaultTheme).value
            ) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { WordUi(settingsVm = settingsVm) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WordUi(
    context: Context = LocalContext.current,
    vm: WordViewModel = viewModel { WordViewModel(context) },
    settingsVm: SettingsViewModel = viewModel { SettingsViewModel(context) },
    chatViewModel: ChatViewModel = viewModel { ChatViewModel(BuildConfig.IP4_ADDRESS_NO_PORT, BuildConfig.IP4_ADDRESS) }
) {
    LoadingDialog(
        showLoadingDialog = vm.isLoading,
        onDismissRequest = {}
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val settingsDrawerState = rememberDrawerState(DrawerValue.Closed)
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val chatSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()

    SnackbarHandler(vm = vm, snackbarHostState = snackbarHostState)
    BackButtonHandler(drawerState = drawerState, settingsDrawerState = settingsDrawerState)

    WordDialogs(vm)

    IncludeChat(chatSheetState = chatSheetState, chatViewModel = chatViewModel) {
        ModalBottomSheetLayout(
            sheetState = bottomSheetState,
            sheetContent = { ThemeChooser(settingsVm) },
            sheetBackgroundColor = MaterialTheme.colorScheme.background,
            sheetContentColor = MaterialTheme.colorScheme.onBackground
        ) {
            ModalNavigationDrawer(
                drawerContent = {
                    SettingsDrawer(
                        vm = settingsVm,
                        wordViewModel = vm,
                        drawerState = settingsDrawerState,
                        bottomSheetState = bottomSheetState
                    )
                },
                drawerState = settingsDrawerState,
                gesturesEnabled = settingsDrawerState.isOpen
            ) {
                ModalNavigationDrawer(
                    drawerContent = { DefinitionDrawer(vm) },
                    drawerState = drawerState,
                    gesturesEnabled = vm.definition != null
                ) {
                    IntroShowCaseScaffold(
                        showIntroShowCase = settingsVm.showcase.collectAsState(initial = false).value,
                        onShowCaseCompleted = { settingsVm.finishShowcase() },
                    ) {
                        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Guess the Words") },
                                    actions = {
                                        Text("${vm.wordGuesses.size}/${vm.anagramWords.size}")
                                        TextButton(
                                            onClick = { vm.finishGame = true },
                                            enabled = !vm.finishedGame
                                        ) { Text("Finish") }
                                        TextButton(onClick = { vm.shouldStartNewGame = true }) { Text("New Game") }
                                    },
                                    scrollBehavior = scrollBehavior
                                )
                            },
                            bottomBar = {
                                BottomBar(
                                    vm = vm,
                                    settingsVm = settingsVm,
                                    chatViewModel = chatViewModel,
                                    gridState = gridState,
                                    snackbarHostState = snackbarHostState,
                                    chatSheetState = chatSheetState
                                )
                            },
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                        ) { padding ->
                            WordContent(
                                padding = padding,
                                vm = vm,
                                settingsVm = settingsVm,
                                gridState = gridState,
                                settingsDrawerState = settingsDrawerState,
                                drawerState = drawerState
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun IncludeChat(
    chatSheetState: ModalBottomSheetState,
    chatViewModel: ChatViewModel,
    content: @Composable () -> Unit
) {
    if (BuildConfig.BUILD_TYPE == "lanVersion") {
        ModalBottomSheetLayout(
            sheetState = chatSheetState,
            sheetContent = {
                ChatUi(
                    BuildConfig.IP4_ADDRESS_NO_PORT,
                    BuildConfig.IP4_ADDRESS,
                    chatViewModel,
                    chatSheetState
                )
            },
            sheetBackgroundColor = MaterialTheme.colorScheme.background,
            sheetContentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    } else {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroShowCaseScope.WordContent(
    padding: PaddingValues,
    vm: WordViewModel,
    settingsVm: SettingsViewModel,
    gridState: LazyGridState,
    settingsDrawerState: DrawerState,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.padding(padding)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalButton(
                onClick = vm::useHint,
                enabled = vm.hintCount > 0,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .introShowCaseTarget(4, style = introShowCaseStyle()) {
                        Text("Use a hint")
                    }
            ) {
                Icon(Icons.Default.QuestionMark, null)
                Text(vm.hintCount.toString())
            }

            FilledTonalButton(
                onClick = { vm.showScoreInfo = true },
                enabled = vm.score > 0,
                modifier = Modifier
                    .align(Alignment.Center)
                    .introShowCaseTarget(5, style = introShowCaseStyle()) {
                        Text("Click here to see your points breakdown")
                    }
            ) { Text("${animateIntAsState(vm.score).value} points") }

            FilledTonalIconButton(
                onClick = { scope.launch { settingsDrawerState.open() } },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { Icon(Icons.Default.Settings, null) }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(settingsVm.columnCount),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp)
        ) {
            items(vm.anagramWords.sortedByDescending { it.length }) { anagrams ->
                Crossfade(targetState = vm.wordGuesses.any { it.equals(anagrams, true) }) { state ->
                    if (state) {
                        OutlinedCard(
                            onClick = { vm.getDefinition(anagrams) { scope.launch { drawerState.open() } } },
                        ) {
                            CustomListItem {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically)
                                ) { Text(anagrams, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    } else {
                        ElevatedCard(onClick = {}, enabled = false) {
                            CustomListItem {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        anagrams
                                            .uppercase()
                                            .replace(
                                                if (vm.hintList.isNotEmpty()) {
                                                    Regex("[^${vm.hintList.joinToString("")}]")
                                                } else {
                                                    Regex("\\w")
                                                },
                                                " _"
                                            ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${anagrams.length}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun IntroShowCaseScope.BottomBar(
    vm: WordViewModel,
    settingsVm: SettingsViewModel,
    chatViewModel: ChatViewModel,
    gridState: LazyGridState,
    snackbarHostState: SnackbarHostState,
    chatSheetState: ModalBottomSheetState
) {
    val scope = rememberCoroutineScope()
    CustomBottomAppBar {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .animateContentSize()
                ) {
                    vm.wordGuess.forEachIndexed { index, c ->
                        OutlinedIconButton(
                            onClick = { vm.updateGuess(vm.wordGuess.removeRange(index, index + 1)) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) { Text(c.uppercase()) }
                    }
                }

                FilledTonalIconButton(
                    onClick = { vm.wordGuess = "" },
                    modifier = Modifier.introShowCaseTarget(1, style = introShowCaseStyle()) {
                        Text("Clear Current Hand")
                    }
                ) { Icon(Icons.Default.Clear, null, tint = Alizarin) }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .height(48.dp)
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                val cornerSize = 16.dp
                vm.mainLetters.forEachIndexed { index, it ->
                    OutlinedIconButton(
                        onClick = { vm.updateGuess("${vm.wordGuess}$it") },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = when (index) {
                            0 -> RoundedCornerShape(topStart = cornerSize, bottomStart = cornerSize)
                            vm.mainLetters.lastIndex -> RoundedCornerShape(topEnd = cornerSize, bottomEnd = cornerSize)
                            else -> RectangleShape
                        }
                    ) { Text(it.uppercase()) }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = vm::bringBackWord,
                    modifier = Modifier.introShowCaseTarget(2, style = introShowCaseStyle()) {
                        Text("Bring back the last correctly guessed word")
                    }
                ) { Icon(Icons.Default.Undo, null) }

                FilledTonalIconButton(
                    onClick = vm::shuffle,
                    modifier = Modifier.introShowCaseTarget(0, style = introShowCaseStyle()) {
                        Text("Shuffle Letters")
                    }
                ) { Icon(Icons.Default.Shuffle, null) }

                ChatIcon(chatViewModel = chatViewModel, chatState = chatSheetState, scope = scope)

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val message = vm.guess {
                                if (settingsVm.scrollToItem) scope.launch { gridState.animateScrollToItem(it) }
                            }
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = vm.wordGuess.isNotEmpty(),
                    modifier = Modifier.introShowCaseTarget(3, style = introShowCaseStyle()) {
                        Text("Guess the word")
                    }
                ) {
                    Text(
                        "ENTER",
                        color = if (vm.wordGuess.isNotEmpty()) Emerald else LocalContentColor.current
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionDrawer(vm: WordViewModel) {
    ModalDrawerSheet {
        vm.definition?.let { definition ->
            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                definition.word.orEmpty()
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                            )
                        },
                        scrollBehavior = scrollBehavior
                    )
                },
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            ) { padding ->
                LazyColumn(
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(definition.meanings.orEmpty()) {
                        ElevatedCard {
                            ListItem(
                                headlineText = { Text(it.partOfSpeech.orEmpty()) },
                                supportingText = {
                                    Column {
                                        it.definitions?.forEach { d ->
                                            Text(d.definition.orEmpty())
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WordSolverTheme {
        WordUi()
    }
}

@Composable
fun CustomBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        // TODO(b/209583788): Consider adding a shape parameter if updated design guidance allows
        shape = RectangleShape,
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .windowInsetsPadding(windowInsets)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun LoadingDialog(
    showLoadingDialog: Boolean,
    dismissOnClickOutside: Boolean = false,
    onDismissRequest: () -> Unit
) {
    if (showLoadingDialog) {
        Dialog(
            onDismissRequest = onDismissRequest,
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = dismissOnClickOutside)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(28.0.dp)
                    )
            ) {
                Column {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(text = "Loading", Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDialogs(vm: WordViewModel) {
    val isFinished by remember { derivedStateOf { vm.wordGuesses.size == vm.anagramWords.size } }

    if (vm.shouldStartNewGame) {
        AlertDialog(
            onDismissRequest = { vm.shouldStartNewGame = false },
            title = { Text("New Game?") },
            text = { Text("Are you sure?${if (!isFinished) " You will lose all your progress." else ""}") },
            confirmButton = { TextButton(onClick = vm::getWord) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.shouldStartNewGame = false }) { Text("No") } }
        )
    }

    if (vm.finishGame) {
        AlertDialog(
            onDismissRequest = { vm.finishGame = false },
            title = { Text("Finish Game?") },
            text = { Text("Are you sure?${if (!isFinished) " You will lose all your progress." else ""}") },
            confirmButton = { TextButton(onClick = vm::endGame) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.finishGame = false }) { Text("No") } }
        )
    }

    if (vm.showScoreInfo) {
        AlertDialog(
            onDismissRequest = { vm.showScoreInfo = false },
            title = { Text("Score Info") },
            text = {
                LazyColumn {
                    items(vm.scoreInfo.entries.toList()) {
                        ListItem(headlineText = { Text("${it.key} = ${it.value} points") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showScoreInfo = false }) { Text("Done") } },
        )
    }
}

@Composable
fun SnackbarHandler(vm: WordViewModel, snackbarHostState: SnackbarHostState) {
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                vm.error!!,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            when (result) {
                SnackbarResult.Dismissed -> vm.error = null
                SnackbarResult.ActionPerformed -> vm.error = null
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    LaunchedEffect(vm.gotNewHint) {
        if (vm.gotNewHint) {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                "Got enough words for a new hint!",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            vm.gotNewHint = when (result) {
                SnackbarResult.Dismissed -> false
                SnackbarResult.ActionPerformed -> false
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackButtonHandler(drawerState: DrawerState, settingsDrawerState: DrawerState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goingBack by remember { mutableStateOf(true) }

    BackHandler(goingBack) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            settingsDrawerState.isOpen -> scope.launch { settingsDrawerState.close() }
            else -> {
                goingBack = false
                Toast.makeText(context, "Go back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(!goingBack) {
        if (!goingBack) {
            delay(5000)
            goingBack = true
        }
    }

    LifecycleHandle(
        onDestroy = { goingBack = true },
        onStop = { goingBack = true },
        onCreate = { goingBack = true },
        onResume = { goingBack = true },
        onPause = { goingBack = true },
        onStart = { goingBack = true },
        onAny = { goingBack = true }
    )
}

@Composable
fun LifecycleHandle(
    onCreate: () -> Unit = {},
    onStart: () -> Unit = {},
    onResume: () -> Unit = {},
    onPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onDestroy: () -> Unit = {},
    onAny: () -> Unit = {},
    vararg keys: Any
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // If `lifecycleOwner` changes, dispose and reset the effect
    DisposableEffect(lifecycleOwner, *keys) {
        // Create an observer that triggers our remembered callbacks
        // for sending analytics events
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> onCreate
                Lifecycle.Event.ON_START -> onStart
                Lifecycle.Event.ON_RESUME -> onResume
                Lifecycle.Event.ON_PAUSE -> onPause
                Lifecycle.Event.ON_STOP -> onStop
                Lifecycle.Event.ON_DESTROY -> onDestroy
                Lifecycle.Event.ON_ANY -> onAny
            }()
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
@ExperimentalMaterial3Api
private fun CustomListItem(
    modifier: Modifier = Modifier,
    shape: Shape = ListItemDefaults.shape,
    containerColor: Color = ListItemDefaults.containerColor,
    contentColor: Color = ListItemDefaults.contentColor,
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 8.dp)
                .padding(PaddingValues(vertical = 16.dp, horizontal = 16.dp)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}