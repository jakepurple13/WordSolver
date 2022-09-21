package com.programmersbox.wordsolver

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.programmersbox.wordsolver.ui.theme.WordSolverTheme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
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
fun WordUi(vm: WordViewModel = viewModel()) {

    LoadingDialog(
        showLoadingDialog = vm.isLoading,
        onDismissRequest = {}
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var goingBack by remember { mutableStateOf(true) }
    val context = LocalContext.current

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

    BackHandler(goingBack) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            goingBack = false
            Toast.makeText(context, "Go back again to exit", Toast.LENGTH_SHORT).show()
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
        drawerContent = {
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
        },
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
                        TextButton(onClick = { vm.finishGame = true }) { Text("Finish") }
                        TextButton(onClick = { vm.shouldStartNewGame = true }) { Text("New Game") }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            bottomBar = {
                CustomBottomAppBar(
                    actions = {
                        Row {
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
                                    modifier = Modifier.animateContentSize()
                                ) {
                                    vm.wordGuess.forEachIndexed { index, c ->
                                        OutlinedIconButton(
                                            onClick = { vm.updateGuess(vm.wordGuess.removeRange(index, index + 1)) },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                        ) { Text(c.uppercase()) }
                                    }
                                }
                            }
                            Column {
                                IconButton(onClick = vm::shuffle) { Icon(Icons.Default.Shuffle, null) }
                                IconButton(onClick = { vm.wordGuess = "" }) { Icon(Icons.Default.Clear, null) }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val message = vm.guess()
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                message,
                                                withDismissAction = true,
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                ) { Icon(Icons.Default.Send, null) }
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
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
                            ) { ListItem(headlineText = { Text(anagrams) }) }
                        } else {
                            ElevatedCard {
                                ListItem(
                                    headlineText = { Text("") },
                                    trailingContent = { Text("${anagrams.length} letters") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class WordViewModel : ViewModel() {

    var shouldStartNewGame by mutableStateOf(false)
    var finishGame by mutableStateOf(false)
    var isLoading by mutableStateOf(false)

    var mainLetters by mutableStateOf("")

    private var anagrams by mutableStateOf(emptyList<Anagrams>())
    val anagramWords by derivedStateOf {
        val size = if (anagrams.size > 50) 4 else 3
        anagrams
            .mapNotNull { it.word }
            .filterNot { it.length < size }
    }

    val wordGuesses = mutableStateListOf<String>()
    var wordGuess by mutableStateOf("")

    var definition by mutableStateOf<BaseDefinition?>(null)
    private val definitionMap = mutableMapOf<String, BaseDefinition>()

    var error: String? by mutableStateOf(null)

    init {
        getWord()
    }

    fun getWord() {
        viewModelScope.launch {
            shouldStartNewGame = false
            isLoading = true
            definitionMap.clear()
            wordGuesses.clear()
            wordGuess = ""
            mainLetters = withContext(Dispatchers.IO) {
                getLetters()
                    .fold(
                        onSuccess = {
                            withContext(Dispatchers.Main) { error = null }
                            it.firstOrNull()
                                .orEmpty()
                                .toList()
                                .shuffled()
                                .joinToString("")
                        },
                        onFailure = {
                            withContext(Dispatchers.Main) { error = "Something went Wrong" }
                            ""
                        }
                    )

            }
            anagrams = withContext(Dispatchers.IO) {
                getAnagram(mainLetters)
                    .fold(
                        onSuccess = {
                            withContext(Dispatchers.Main) { error = null }
                            it.orEmpty()
                        },
                        onFailure = {
                            withContext(Dispatchers.Main) { error = "Something went Wrong" }
                            emptyList()
                        }
                    )
            }
            isLoading = false
        }
    }

    fun endGame() {
        wordGuesses.clear()
        wordGuesses.addAll(anagramWords)
        finishGame = false
    }

    fun shuffle() {
        mainLetters = mainLetters.toList().shuffled().joinToString("")
    }

    fun updateGuess(word: String) {
        //TODO: Final thing is to make sure only the letters chosen can be pressed
        if (word.toList().all { mainLetters.contains(it) }) {
            wordGuess = word
        }
    }

    fun guess(): String {
        return when {
            wordGuesses.contains(wordGuess) -> "Already Guessed"
            anagramWords.any { it.equals(wordGuess, ignoreCase = true) } -> {
                wordGuesses += wordGuess
                wordGuess = ""
                "Got it!"
            }
            else -> "Not in List"
        }
    }

    fun getDefinition(word: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            definition = if (definitionMap.contains(word)) {
                onComplete()
                definitionMap[word]
            } else {
                isLoading = true
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(10000) { getWordDefinition(word) }
                        ?.fold(
                            onSuccess = { definition ->
                                error = null
                                definition.firstOrNull()
                                    ?.also {
                                        isLoading = false
                                        definitionMap[word] = it
                                        onComplete()
                                    }
                            },
                            onFailure = {
                                isLoading = false
                                error = "Something went Wrong"
                                null
                            }
                        )
                }
            }
        }
    }
}

suspend fun getLetters() = runCatching {
    getApi<List<String>>("https://random-word-api.herokuapp.com/word?length=7").orEmpty()
}

suspend fun getAnagram(letters: String) =
    runCatching {
        getApi<HttpResponse>("https://danielthepope-countdown-v1.p.rapidapi.com/solve/$letters?variance=-1") {
            append("X-RapidAPI-Host", "danielthepope-countdown-v1.p.rapidapi.com")
            append("X-RapidAPI-Key", "cefe1904a6msh94a1484f93d57dbp16f734jsn098d9ecefd68")
        }?.bodyAsText().fromJson<List<Anagrams>>()
    }

suspend fun getWordDefinition(word: String) =
    runCatching {
        getApi<HttpResponse>("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
            ?.bodyAsText()
            .fromJson<List<BaseDefinition>>()
            .orEmpty()
    }

suspend inline fun <reified T> getApi(
    url: String,
    noinline headers: HeadersBuilder.() -> Unit = {}
): T? {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    isLenient = true
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
            )
        }
    }
    val response: HttpResponse = client.get(url) { headers(headers) }
    return response.body<T>()
}

inline fun <reified T> String?.fromJson(): T? = try {
    GsonBuilder()
        .setLenient()
        .create()
        .fromJson(this, object : TypeToken<T>() {}.type)
} catch (e: Exception) {
    e.printStackTrace()
    null
}

data class Anagrams(val word: String?, val length: Number?, val conundrum: Boolean?)

data class BaseDefinition(
    val word: String?,
    val phonetic: String?,
    val phonetics: List<Phonetics>?,
    val origin: String?,
    val meanings: List<Meanings>?
)

data class Definitions(
    val definition: String?,
    val example: String?,
    val synonyms: List<Any>?,
    val antonyms: List<Any>?
)

data class Meanings(val partOfSpeech: String?, val definitions: List<Definitions>?)

data class Phonetics(val text: String?, val audio: String?)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WordSolverTheme {
        WordUi()
    }
}

@Composable
fun CustomBottomAppBar(
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable (() -> Unit)? = null,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
    contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
) = CustomBottomAppBar(
    modifier = modifier,
    containerColor = containerColor,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    windowInsets = windowInsets,
    contentPadding = contentPadding
) {
    actions()
    if (floatingActionButton != null) {
        Spacer(Modifier.weight(1f, true))
        Box(
            Modifier.padding(
                top = FABVerticalPadding,
                end = FABHorizontalPadding
            ),
            contentAlignment = Alignment.TopStart
        ) {
            floatingActionButton()
        }
    }
}

// Padding minus IconButton's min touch target expansion
private val BottomAppBarHorizontalPadding = 16.dp - 12.dp
internal val BottomAppBarVerticalPadding = 16.dp - 12.dp

// Padding minus content padding
private val FABHorizontalPadding = 16.dp - BottomAppBarHorizontalPadding
private val FABVerticalPadding = 12.dp - BottomAppBarVerticalPadding

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
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun LoadingDialog(
    showLoadingDialog: Boolean,
    onDismissRequest: () -> Unit
) {
    if (showLoadingDialog) {
        Dialog(
            onDismissRequest = onDismissRequest,
            DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
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