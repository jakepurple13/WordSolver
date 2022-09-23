package com.programmersbox.wordsolver

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
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
import com.programmersbox.wordsolver.ui.theme.WordSolverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

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

    if (vm.shouldStartNewGame) {
        AlertDialog(
            onDismissRequest = { vm.shouldStartNewGame = false },
            title = { Text("New Game?") },
            text = { Text("Are you sure? You will lose all your progress.") },
            confirmButton = { TextButton(onClick = vm::getWord) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.shouldStartNewGame = false }) { Text("No") } }
        )
    }

    if (vm.finishGame) {
        AlertDialog(
            onDismissRequest = { vm.finishGame = false },
            title = { Text("Finish Game?") },
            text = { Text("Are you sure? You will lose all your progress.") },
            confirmButton = { TextButton(onClick = vm::endGame) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { vm.finishGame = false }) { Text("No") } }
        )
    }

    ModalNavigationDrawer(
        drawerContent = { SettingsDrawer(vm = settingsVm, drawerState = settingsDrawerState) },
        drawerState = settingsDrawerState,
        gesturesEnabled = settingsDrawerState.isOpen
    ) {
        ModalNavigationDrawer(
            drawerContent = { DefinitionDrawer(vm) },
            drawerState = drawerState,
            gesturesEnabled = vm.definition != null
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
                        settingsDrawerState = settingsDrawerState,
                        snackbarHostState = snackbarHostState
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            ) { padding ->
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(settingsVm.columnCount),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = padding,
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
                                        overlineText = {},
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
                                        overlineText = { Text("${anagrams.length} letters") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBar(
    vm: WordViewModel,
    settingsVm: SettingsViewModel,
    gridState: LazyGridState,
    settingsDrawerState: DrawerState,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    CustomBottomAppBar {
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.animateContentSize()
            ) {
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

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .animateContentSize()
                    .height(48.dp)
            ) {
                vm.wordGuess.forEachIndexed { index, c ->
                    OutlinedIconButton(
                        onClick = { vm.updateGuess(vm.wordGuess.removeRange(index, index + 1)) },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) { Text(c.uppercase()) }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize()
            ) {
                FilledTonalIconButton(onClick = vm::bringBackWord) { Icon(Icons.Default.Undo, null) }
                Spacer(Modifier.size(48.dp))
                FilledTonalButton(
                    onClick = vm::useHint,
                    enabled = vm.hints > 0
                ) {
                    Icon(Icons.Default.QuestionMark, null)
                    Text(vm.hints.toString())
                }
                Spacer(Modifier.size(48.dp))
                FilledTonalIconButton(
                    onClick = { scope.launch { settingsDrawerState.open() } }
                ) { Icon(Icons.Default.Settings, null) }
            }
        }
        Column {
            FilledTonalIconButton(onClick = vm::shuffle) { Icon(Icons.Default.Shuffle, null) }
            FilledTonalIconButton(onClick = { vm.wordGuess = "" }) { Icon(Icons.Default.Clear, null) }
            FilledTonalIconButton(
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
                enabled = vm.wordGuess.isNotEmpty()
            ) { Icon(Icons.Default.Send, null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(vm: SettingsViewModel, drawerState: DrawerState) {
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
                    NavigationDrawerItem(
                        label = { Text("Column Count") },
                        selected = false,
                        onClick = { vm.updateColumnCount(if (vm.columnCount == 2) 3 else 2) },
                        badge = { Text(vm.columnCount.toString()) },
                    )
                    Text(
                        "Choose between having 2 or 3 columns.",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                item { Divider() }

                item {
                    NavigationDrawerItem(
                        label = { Text("Scroll to Item on Already Guessed") },
                        selected = false,
                        onClick = { vm.updateScrollToItem(!vm.scrollToItem) },
                        badge = { Switch(checked = vm.scrollToItem, onCheckedChange = { vm.updateScrollToItem(it) }) },
                    )
                    Text(
                        "Enable if you want to be scrolled to where in the grid the already guessed word is.",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.bodySmall
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
