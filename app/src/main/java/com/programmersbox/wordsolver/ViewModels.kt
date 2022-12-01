package com.programmersbox.wordsolver

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.wordsolver.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WordViewModel(context: Context) : ViewModel() {

    private val savedDataHandling = SavedDataHandling(context)

    private val networkRetrieving: NetworkRetrieving by lazy {
        if (BuildConfig.BUILD_TYPE == "lanVersion") LanVersion()
        else APIVersion(
            context.resources.openRawResource(R.raw.words)
                .bufferedReader()
                .readLines()
                .filter { it.all(Char::isLetter) }
        )
    }

    var shouldStartNewGame by mutableStateOf(false)
    var finishGame by mutableStateOf(false)
    var finishedGame by mutableStateOf(false)
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

    var usedHint by mutableStateOf(false)
    var hints by mutableStateOf(0)
    var hintList by mutableStateOf(emptySet<String>())
    var gotNewHint by mutableStateOf(false)
    val hintCount by derivedStateOf { hints + if (usedHint) 0 else 1 }

    var showScoreInfo by mutableStateOf(false)
    private var internalScore = 0
    val score by derivedStateOf {
        if (finishedGame) {
            internalScore
        } else {
            wordGuesses
                .groupBy { it.length }
                .map { it.key * (it.value.size + it.key) }
                .ifEmpty { listOf(0) }
                .reduce { acc, i -> acc + i }
        }
    }

    val scoreInfo by derivedStateOf {
        wordGuesses
            .sortedByDescending { it.length }
            .groupBy { it.length }
            .mapValues { (it.value.size + it.key) * it.key }
    }

    init {
        savedDataHandling.mainLetters
            .onEach { mainLetters = it }
            .launchIn(viewModelScope)
        savedDataHandling.anagrams
            .onEach { anagrams = it }
            .launchIn(viewModelScope)
        savedDataHandling.wordGuesses
            .onEach { wordGuesses = it }
            .launchIn(viewModelScope)
        savedDataHandling.hints
            .onEach { hints = it }
            .launchIn(viewModelScope)
        savedDataHandling.hintList
            .onEach { hintList = it }
            .launchIn(viewModelScope)
        savedDataHandling.usedHint
            .onEach { usedHint = it }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            if (!savedDataHandling.hasSavedData()) {
                getWord()
            }
        }
    }

    fun getWord() {
        viewModelScope.launch {
            shouldStartNewGame = false
            finishedGame = false
            isLoading = true
            internalScore = 0
            definitionMap.clear()
            if (
                (wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 }) && !usedFinishGame
            ) {
                gotNewHint = true
                savedDataHandling.updateHints(hints + 1)
            }
            savedDataHandling.updateUsedHint(false)
            savedDataHandling.updateWordGuesses(emptyList())
            savedDataHandling.updateHintList(emptySet())
            usedFinishGame = false
            wordGuess = ""
            networkRetrieving.getLettersAndAnagrams(savedDataHandling) {
                withContext(Dispatchers.Main) { error = if (it != null) "Something went Wrong" else null }
            }
            isLoading = false
        }
    }

    fun endGame() {
        internalScore = score
        usedFinishGame = !(wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 })
        wordGuesses = anagramWords
        finishGame = false
        finishedGame = true
    }

    fun cheatGame() {
        wordGuesses = anagramWords
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
            if (usedHint) {
                viewModelScope.launch { savedDataHandling.updateHints(hints - 1) }
            }
            viewModelScope.launch { savedDataHandling.updateUsedHint(true) }
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

    fun guess(onAlreadyGuessed: (Int) -> Unit): String {
        return when {
            wordGuesses.contains(wordGuess) -> {
                onAlreadyGuessed(wordGuesses.indexOf(wordGuess))
                "Already Guessed"
            }
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
                    definition = withTimeoutOrNull(5000) { networkRetrieving.getDefinition(word) }?.fold(
                        onSuccess = { definition ->
                            error = null
                            definition.firstOrNull()?.also {
                                onComplete()
                                definitionMap[word] = it
                            }
                        },
                        onFailure = {
                            it.printStackTrace()
                            null
                        }
                    )
                    if (definition == null) error = "Something went Wrong"
                }
                isLoading = false
            }
        }
    }
}

class SettingsViewModel(context: Context) : ViewModel() {

    private val settingsHandling = SettingsHandling(context)

    val showcase by lazy { settingsHandling.showShowcase }
    var scrollToItem by mutableStateOf(false)
    var columnCount by mutableStateOf(3)
    val systemThemeMode by lazy { settingsHandling.systemThemeMode }
    val letterType by lazy { settingsHandling.lettersUiType }
    val themeIndex by lazy {
        settingsHandling.themeIndex.map { index -> Theme.values().find { it.ordinal == index } ?: Theme.Default }
    }

    val themeIndexes by lazy { settingsHandling.themeIndex }

    init {
        settingsHandling.columnAmount
            .onEach { columnCount = it }
            .launchIn(viewModelScope)

        settingsHandling.scrollToItem
            .onEach { scrollToItem = it }
            .launchIn(viewModelScope)
    }

    fun updateScrollToItem(scrollToItem: Boolean) {
        viewModelScope.launch { settingsHandling.updateItemScroll(scrollToItem) }
    }

    fun updateColumnCount(count: Int) {
        viewModelScope.launch { settingsHandling.updateColumnAmount(count) }
    }

    fun finishShowcase() {
        viewModelScope.launch { settingsHandling.updateShowcase(false) }
    }

    fun showShowcase() {
        viewModelScope.launch { settingsHandling.updateShowcase(true) }
    }

    fun setTheme(index: Int) = viewModelScope.launch { settingsHandling.setThemeIndex(index) }
    fun setSystemThemeMode(mode: SystemThemeMode) = viewModelScope.launch { settingsHandling.setSystemThemeMode(mode) }
    fun setLetterType(type: LetterUiType) = viewModelScope.launch { settingsHandling.setLettersType(type) }
}

class SavedDataHandling(context: Context) {

    private val dataStore by lazy { context.dataStore }
    private val preferences by lazy { context.savedData }
    private val all: Flow<SavedData> get() = preferences.data

    companion object {
        val MAIN_LETTERS = stringPreferencesKey("main_word")
        private val WORD_GUESSES = stringSetPreferencesKey("word_guesses")
        private val HINTS = intPreferencesKey("hints")
        private val HINT_LIST = stringSetPreferencesKey("hint_list")
        private val USED_HINT = booleanPreferencesKey("used_hint")
    }

    val mainLetters = dataStore.data.map { it[MAIN_LETTERS] ?: "" }
    suspend fun updateMainLetters(letters: String) {
        dataStore.edit { it[MAIN_LETTERS] = letters }
    }

    val wordGuesses = dataStore.data.map { it[WORD_GUESSES].orEmpty().toList() }
    suspend fun updateWordGuesses(words: List<String>) {
        dataStore.edit { it[WORD_GUESSES] = words.toSet() }
    }

    val anagrams = all.map { p -> p.anagramsList.map { Anagrams(it.word, it.length, it.conundrum) } }
    suspend fun updateAnagrams(anagrams: List<Anagrams>) = preferences.update {
        clearAnagrams()
        val list = anagrams.map {
            anagramWrapper {
                word = it.word.orEmpty()
                length = it.length ?: 0
                conundrum = it.conundrum ?: false
            }
        }
        addAllAnagrams(list)
    }

    val hints = dataStore.data.map { it[HINTS] ?: 4 }
    suspend fun updateHints(hintCount: Int) {
        dataStore.edit { it[HINTS] = hintCount }
    }

    val hintList = dataStore.data.map { it[HINT_LIST] ?: emptySet() }
    suspend fun updateHintList(hintList: Set<String>) {
        dataStore.edit { it[HINT_LIST] = hintList }
    }

    val usedHint = dataStore.data.map { it[USED_HINT] ?: false }
    suspend fun updateUsedHint(usedHint: Boolean) {
        dataStore.edit { it[USED_HINT] = usedHint }
    }

    suspend fun hasSavedData(): Boolean {
        return dataStore.data.map {
            it[MAIN_LETTERS] != null && all.map { p -> p.anagramsList.isNotEmpty() }.firstOrNull() == true
        }.firstOrNull() ?: false
    }
}

class SettingsHandling(context: Context) {
    private val preferences by lazy { context.settings }
    private val all: Flow<Settings> get() = preferences.data

    val scrollToItem = all.map { it.scrollToItemOnAlreadyGuessed }
    suspend fun updateItemScroll(scroll: Boolean) = preferences.update { setScrollToItemOnAlreadyGuessed(scroll) }

    val columnAmount = all.map { it.columnAmount.coerceAtLeast(1) }
    suspend fun updateColumnAmount(columnCount: Int) = preferences.update { setColumnAmount(columnCount) }

    val showShowcase = all.map { it.showShowCase }
    suspend fun updateShowcase(showShowcase: Boolean) = preferences.update { setShowShowCase(showShowcase) }

    val themeIndex = all.map { it.themeIndex }
    suspend fun setThemeIndex(index: Int) = preferences.update { setThemeIndex(index) }

    val systemThemeMode = all.map { it.mode }
    suspend fun setSystemThemeMode(mode: SystemThemeMode) = preferences.update { setMode(mode) }

    val lettersMode = all.map { it.lettersMode }
    suspend fun setLettersMode(mode: Boolean) = preferences.update { setLettersMode(mode) }

    val lettersUiType = all.map { it.letterUiType }
    suspend fun setLettersType(type: LetterUiType) = preferences.update { setLetterUiType(type) }
}