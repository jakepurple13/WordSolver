package com.programmersbox.wordsolver

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.programmersbox.wordsolver.ui.theme.WordSolverTheme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
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

    init {
        getWord()
    }

    fun getWord() {
        viewModelScope.launch {
            isLoading = true
            definitionMap.clear()
            wordGuesses.clear()
            wordGuess = ""
            mainLetters = withContext(Dispatchers.IO) {
                getLetters()
                    .firstOrNull()
                    .orEmpty()
                    .toList()
                    .shuffled()
                    .joinToString("")
            }
            anagrams = withContext(Dispatchers.IO) { getAnagram(mainLetters).orEmpty() }
            isLoading = false
            shouldStartNewGame = false
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
                definitionMap[word]
            } else {
                isLoading = true
                withContext(Dispatchers.IO) { getWordDefinition(word) }
                    .firstOrNull()
                    ?.also {
                        isLoading = false
                        definitionMap[word] = it
                    }
            }
            onComplete()
        }
    }
}

suspend fun getLetters() =
    getApi<List<String>>("https://random-word-api.herokuapp.com/word?length=7").orEmpty()

suspend fun getAnagram(letters: String) =
    getApi<HttpResponse>("https://danielthepope-countdown-v1.p.rapidapi.com/solve/$letters?variance=-1") {
        append("X-RapidAPI-Host", "danielthepope-countdown-v1.p.rapidapi.com")
        append("X-RapidAPI-Key", "cefe1904a6msh94a1484f93d57dbp16f734jsn098d9ecefd68")
    }?.bodyAsText().fromJson<List<Anagrams>>()

suspend fun getWordDefinition(word: String) =
    getApi<HttpResponse>("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
        ?.bodyAsText()
        .fromJson<List<BaseDefinition>>()
        .orEmpty()

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
    Gson().fromJson(this, object : TypeToken<T>() {}.type)
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