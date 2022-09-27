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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.programmersbox.wordsolver.ui.theme.Alizarin
import com.programmersbox.wordsolver.ui.theme.Emerald
import com.programmersbox.wordsolver.ui.theme.WordSolverTheme
import com.programmersbox.wordsolver.ui.theme.introShowCaseStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WordSolverTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { WordUi() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordUi(
    context: Context = LocalContext.current,
    vm: WordViewModel = viewModel { WordViewModel(context) },
    settingsVm: SettingsViewModel = viewModel { SettingsViewModel(context) }
) {

    LoadingDialog(
        showLoadingDialog = vm.isLoading,
        onDismissRequest = {}
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val settingsDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()

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

    WordDialogs(vm)

    ModalNavigationDrawer(
        drawerContent = { SettingsDrawer(vm = settingsVm, wordViewModel = vm, drawerState = settingsDrawerState) },
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
                            gridState = gridState,
                            snackbarHostState = snackbarHostState
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                ) { padding ->
                    Column(
                        modifier = Modifier.padding(padding)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = vm::useHint,
                                enabled = vm.hints > 0,
                                modifier = Modifier.introShowCaseTarget(4, style = introShowCaseStyle()) {
                                    Text("Use a hint")
                                }
                            ) {
                                Icon(Icons.Default.QuestionMark, null)
                                Text(vm.hints.toString())
                            }

                            FilledTonalButton(
                                onClick = { vm.showScoreInfo = true },
                                enabled = vm.score > 0,
                                modifier = Modifier.introShowCaseTarget(5, style = introShowCaseStyle()) {
                                    Text("Click here to see your points breakdown")
                                }
                            ) { Text("${animateIntAsState(vm.score).value} points") }

                            FilledTonalIconButton(
                                onClick = { scope.launch { settingsDrawerState.open() } }
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
                                            onClick = { vm.getDefinition(anagrams) { scope.launch { drawerState.open() } } }
                                        ) {
                                            ListItem(
                                                trailingContent = {},
                                                headlineText = { Text(anagrams) }
                                            )
                                        }
                                    } else {
                                        ElevatedCard {
                                            ListItem(
                                                headlineText = {
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
                                                            )
                                                    )
                                                },
                                                trailingContent = { Text("${anagrams.length}") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntroShowCaseScope.BottomBar(
    vm: WordViewModel,
    settingsVm: SettingsViewModel,
    gridState: LazyGridState,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    CustomBottomAppBar {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxWidth()
            ) {
                Row {
                    vm.mainLetters.forEach {
                        OutlinedIconButton(
                            onClick = { vm.updateGuess("${vm.wordGuess}$it") },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = .5f)
                            ),
                        ) { Text(it.uppercase()) }
                    }
                }

                FilledTonalIconButton(
                    onClick = vm::shuffle,
                    modifier = Modifier.introShowCaseTarget(0, style = introShowCaseStyle()) {
                        Text("Shuffle Letters")
                    }
                ) { Icon(Icons.Default.Shuffle, null) }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.animateContentSize()) {
                    vm.wordGuess.forEachIndexed { index, c ->
                        OutlinedIconButton(
                            onClick = { vm.updateGuess(vm.wordGuess.removeRange(index, index + 1)) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
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
fun SettingsDrawer(vm: SettingsViewModel, wordViewModel: WordViewModel, drawerState: DrawerState) {
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
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
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
                        MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(28.0.dp)
                    )
            ) {
                Column {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
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
                        ListItem(headlineText = { Text("${it.key} = ${it.value.size * it.key} points") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showScoreInfo = false }) { Text("Done") } },
        )
    }
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
