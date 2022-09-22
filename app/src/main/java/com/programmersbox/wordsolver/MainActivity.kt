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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
fun WordUi(
    context: Context = LocalContext.current,
    vm: WordViewModel = viewModel { WordViewModel(context) }
) {

    LoadingDialog(
        showLoadingDialog = vm.isLoading,
        onDismissRequest = {}
    )

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var goingBack by remember { mutableStateOf(true) }

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
                                    IconButton(onClick = vm::bringBackWord) { Icon(Icons.Default.Undo, null) }
                                    IconButton(onClick = vm::useHint) {
                                        BadgedBox(
                                            badge = { Badge { Text(vm.hints.toString()) } }
                                        ) { Icon(Icons.Default.QuestionMark, null) }
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
                columns = GridCells.Fixed(3), // Maybe put an option to have either 2 or 3 columns?
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

class WordViewModel(context: Context) : ViewModel() {

    private val savedDataHandling = SavedDataHandling(context)

    var shouldStartNewGame by mutableStateOf(false)
    var finishGame by mutableStateOf(false)
    private var usedFinishGame = false
    var isLoading by mutableStateOf(false)

    var mainLetters by mutableStateOf("")

    private var anagrams by mutableStateOf(emptyList<Anagrams>())
    val anagramWords by derivedStateOf {
        val size = if (anagrams.size > 50) 4 else 3
        anagrams
            .mapNotNull { it.word }
            .filterNot { it.length < size }
    }

    var wordGuesses by mutableStateOf<List<String>>(emptyList())
    var wordGuess by mutableStateOf("")
    private var prevGuess = ""

    var definition by mutableStateOf<BaseDefinition?>(null)
    private val definitionMap = mutableMapOf<String, BaseDefinition>()

    var error: String? by mutableStateOf(null)

    var hints by mutableStateOf(5)
    var hintList by mutableStateOf(emptySet<String>())
    var gotNewHint by mutableStateOf(false)

    init {
        viewModelScope.launch {
            savedDataHandling.mainLetters
                .onEach { mainLetters = it }
                .collect()
        }
        viewModelScope.launch {
            savedDataHandling.anagrams
                .onEach { anagrams = it }
                .collect()
        }
        viewModelScope.launch {
            savedDataHandling.wordGuesses
                .onEach { wordGuesses = it }
                .collect()
        }
        viewModelScope.launch {
            savedDataHandling.hints
                .onEach { hints = it }
                .collect()
        }
        viewModelScope.launch {
            savedDataHandling.hintList
                .onEach { hintList = it }
                .collect()
        }
        viewModelScope.launch {
            if (!savedDataHandling.hasSavedData()) {
                getWord()
            }
        }
    }

    fun getWord() {
        viewModelScope.launch {
            shouldStartNewGame = false
            isLoading = true
            definitionMap.clear()
            savedDataHandling.updateWordGuesses(emptyList())
            savedDataHandling.updateHints(hints + 1)
            savedDataHandling.updateHintList(emptySet())
            if (wordGuesses.size >= anagrams.size / 2 && !usedFinishGame) {
                gotNewHint = true
                savedDataHandling.updateHints(hints + 1)
            }
            usedFinishGame = false
            wordGuess = ""
            withContext(Dispatchers.IO) {
                getLetters().fold(
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
                ).let { savedDataHandling.updateMainLetters(it) }
            }
            withContext(Dispatchers.IO) {
                getAnagram(mainLetters).fold(
                    onSuccess = {
                        withContext(Dispatchers.Main) { error = null }
                        it.orEmpty()
                    },
                    onFailure = {
                        withContext(Dispatchers.Main) { error = "Something went Wrong" }
                        emptyList()
                    }
                ).let { savedDataHandling.updateAnagrams(it) }
            }
            isLoading = false
        }
    }

    fun endGame() {
        wordGuesses = anagramWords
        finishGame = false
        usedFinishGame = true
    }

    fun shuffle() {
        viewModelScope.launch { savedDataHandling.updateMainLetters(mainLetters.toList().shuffled().joinToString("")) }
    }

    fun updateGuess(word: String) {
        //TODO: Final thing is to make sure only the letters chosen can be pressed
        if (word.toList().all { mainLetters.contains(it) }) {
            wordGuess = word
        }
    }

    fun useHint() {
        if (hints > 0) {
            viewModelScope.launch { savedDataHandling.updateHints(hints - 1) }
            mainLetters
                .uppercase()
                .filterNot { hintList.contains(it.toString()) }
                .randomOrNull()
                ?.uppercase()
                ?.let {
                    val list = hintList.toMutableSet()
                    list.add(it)
                    viewModelScope.launch { savedDataHandling.updateHintList(list) }
                }
        }
    }

    fun bringBackWord() {
        wordGuess = prevGuess
    }

    fun guess(): String {
        return when {
            wordGuesses.contains(wordGuess) -> "Already Guessed"
            anagramWords.any { it.equals(wordGuess, ignoreCase = true) } -> {
                val list = wordGuesses.toMutableList()
                list.add(wordGuess)
                viewModelScope.launch { savedDataHandling.updateWordGuesses(list) }
                prevGuess = wordGuess
                wordGuess = ""
                "Got it!"
            }
            else -> "Not in List"
        }
    }

    fun getDefinition(word: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (definitionMap.contains(word) && definitionMap[word] != null) {
                onComplete()
                definition = definitionMap[word]
            } else {
                isLoading = true
                withContext(Dispatchers.IO) {
                    definition = withTimeoutOrNull(5000) { getWordDefinition(word) }?.fold(
                        onSuccess = { definition ->
                            error = null
                            definition.firstOrNull()?.also {
                                onComplete()
                                definitionMap[word] = it
                            }
                        },
                        onFailure = { null }
                    )
                    if (definition == null) error = "Something went Wrong"
                }
                isLoading = false
            }
        }
    }
}

suspend fun getLetters() = runCatching {
    getApi<List<String>>("https://random-word-api.herokuapp.com/word?length=7").orEmpty()
}

suspend fun getAnagram(letters: String) = runCatching {
    getApiResponse("https://danielthepope-countdown-v1.p.rapidapi.com/solve/$letters?variance=-1") {
        append("X-RapidAPI-Host", "danielthepope-countdown-v1.p.rapidapi.com")
        append("X-RapidAPI-Key", "cefe1904a6msh94a1484f93d57dbp16f734jsn098d9ecefd68")
    }.bodyAsText().fromJson<List<Anagrams>>()
}

suspend fun getWordDefinition(word: String) = runCatching {
    getApiResponse("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
        .bodyAsText()
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

suspend inline fun getApiResponse(
    url: String,
    noinline headers: HeadersBuilder.() -> Unit = {}
): HttpResponse = HttpClient().get(url) { headers(headers) }

fun Any?.toJson() = Gson().toJson(this)

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
        ) { floatingActionButton() }
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

class SavedDataHandling(context: Context) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    private val dataStore by lazy { context.dataStore }

    companion object {
        private val MAIN_LETTERS = stringPreferencesKey("main_word")
        private val WORD_GUESSES = stringSetPreferencesKey("word_guesses")
        private val ANAGRAMS = stringPreferencesKey("anagrams")
        private val HINTS = intPreferencesKey("hints")
        private val HINT_LIST = stringSetPreferencesKey("hint_list")
    }

    val mainLetters = dataStore.data.map { it[MAIN_LETTERS] ?: "" }
    suspend fun updateMainLetters(letters: String) {
        dataStore.edit { it[MAIN_LETTERS] = letters }
    }

    val wordGuesses = dataStore.data.map { it[WORD_GUESSES].orEmpty().toList() }
    suspend fun updateWordGuesses(words: List<String>) {
        dataStore.edit { it[WORD_GUESSES] = words.toSet() }
    }

    val anagrams = dataStore.data.map { it[ANAGRAMS].fromJson<List<Anagrams>>().orEmpty() }
    suspend fun updateAnagrams(anagrams: List<Anagrams>) {
        dataStore.edit { it[ANAGRAMS] = anagrams.toJson() }
    }

    val hints = dataStore.data.map { it[HINTS] ?: 4 }
    suspend fun updateHints(hintCount: Int) {
        dataStore.edit { it[HINTS] = hintCount }
    }

    val hintList = dataStore.data.map { it[HINT_LIST] ?: emptySet() }
    suspend fun updateHintList(hintList: Set<String>) {
        dataStore.edit { it[HINT_LIST] = hintList }
    }

    suspend fun hasSavedData(): Boolean {
        return dataStore.data.map {
            it[MAIN_LETTERS] != null && it[WORD_GUESSES] != null && it[ANAGRAMS] != null
        }.firstOrNull() ?: false
    }
}