package com.programmersbox.wordsolver

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WordViewModel(context: Context) : ViewModel() {

    private val savedDataHandling = SavedDataHandling(context)

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

    var hints by mutableStateOf(0)
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
            finishedGame = false
            isLoading = true
            definitionMap.clear()
            val hintUpdates = if (
                (wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 }) && !usedFinishGame
            ) {
                gotNewHint = true
                2
            } else {
                1
            }
            savedDataHandling.updateHints(hints + hintUpdates)
            savedDataHandling.updateWordGuesses(emptyList())
            savedDataHandling.updateHintList(emptySet())
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
        usedFinishGame = !(wordGuesses.size >= anagramWords.size / 2 || wordGuesses.any { it.length == 7 })
        wordGuesses = anagramWords
        finishGame = false
        finishedGame = true
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

class SettingsViewModel(context: Context) : ViewModel() {

    private val settingsHandling = SettingsHandling(context)

    var scrollToItem by mutableStateOf(false)
    var columnCount by mutableStateOf(3)

    init {
        viewModelScope.launch {
            settingsHandling.columnAmount
                .onEach { columnCount = it }
                .collect()
        }
        viewModelScope.launch {
            settingsHandling.scrollToItem
                .onEach { scrollToItem = it }
                .collect()
        }
    }

    fun updateScrollToItem(scrollToItem: Boolean) {
        viewModelScope.launch { settingsHandling.updateItemScroll(scrollToItem) }
    }

    fun updateColumnCount(count: Int) {
        viewModelScope.launch { settingsHandling.updateColumnAmount(count) }
    }
}


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SavedDataHandling(context: Context) {

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

class SettingsHandling(context: Context) {
    private val dataStore by lazy { context.dataStore }

    companion object {
        private val SCROLL_TO_ITEM_ON_ALREADY_GUESSED = booleanPreferencesKey("scroll_to_item_on_already_guessed")
        private val COLUMN_AMOUNT = intPreferencesKey("column_amount")
    }

    val scrollToItem = dataStore.data.map { it[SCROLL_TO_ITEM_ON_ALREADY_GUESSED] ?: false }
    suspend fun updateItemScroll(scroll: Boolean) {
        dataStore.edit { it[SCROLL_TO_ITEM_ON_ALREADY_GUESSED] = scroll }
    }

    val columnAmount = dataStore.data.map { it[COLUMN_AMOUNT] ?: 3 }
    suspend fun updateColumnAmount(columnCount: Int) {
        dataStore.edit { it[COLUMN_AMOUNT] = columnCount }
    }
}