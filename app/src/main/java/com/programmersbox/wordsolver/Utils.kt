package com.programmersbox.wordsolver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.protobuf.GeneratedMessageLite
import com.google.protobuf.InvalidProtocolBufferException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

suspend fun getLetters() = runCatching {
    getApi<List<String>>("https://random-word-api.herokuapp.com/word?length=7").orEmpty()
}

suspend fun getAnagram(letters: String) = runCatching {
    getApi<List<Anagrams>>("https://danielthepope-countdown-v1.p.rapidapi.com/solve/$letters?variance=-1") {
        append("X-RapidAPI-Host", "danielthepope-countdown-v1.p.rapidapi.com")
        append("X-RapidAPI-Key", "cefe1904a6msh94a1484f93d57dbp16f734jsn098d9ecefd68")
    }.orEmpty()
}

suspend fun getWordDefinition(word: String) = runCatching {
    getApi<List<BaseDefinition>>("https://api.dictionaryapi.dev/api/v2/entries/en/$word").orEmpty()
}

suspend inline fun <reified T> getApi(
    url: String,
    noinline headers: HeadersBuilder.() -> Unit = {}
): T? {
    val client = HttpClient {
        install(Logging)
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

@Serializable
data class Anagrams(val word: String?, val length: Int?, val conundrum: Boolean?)

@Serializable
data class BaseDefinition(
    val word: String?,
    val phonetic: String? = null,
    val phonetics: List<Phonetics>? = emptyList(),
    val origin: String? = null,
    val meanings: List<Meanings>?
)

@Serializable
data class Definitions(val definition: String?)

@Serializable
data class Meanings(val partOfSpeech: String?, val definitions: List<Definitions>?)

@Serializable
data class Phonetics(val text: String?, val audio: String?)

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val Context.savedData: DataStore<SavedData> by dataStore(
    fileName = "SavedData",
    serializer = SavedDataSerializer
)

val Context.settings: DataStore<Settings> by dataStore(
    fileName = "Settings",
    serializer = SettingsSerializer
)

object SavedDataSerializer : GenericSerializer<SavedData, SavedData.Builder> {
    override val defaultValue: SavedData get() = SavedData.getDefaultInstance()
    override val parseFrom: (input: InputStream) -> SavedData get() = SavedData::parseFrom
}

object SettingsSerializer : GenericSerializer<Settings, Settings.Builder> {
    override val defaultValue: Settings
        get() = Settings.getDefaultInstance()
            .toBuilder()
            .setColumnAmount(3)
            .setShowShowCase(true)
            .build()
    override val parseFrom: (input: InputStream) -> Settings get() = Settings::parseFrom
}

suspend fun <DS : DataStore<MessageType>, MessageType : GeneratedMessageLite<MessageType, BuilderType>, BuilderType : GeneratedMessageLite.Builder<MessageType, BuilderType>> DS.update(
    statsBuilder: suspend BuilderType.() -> BuilderType
) = updateData { statsBuilder(it.toBuilder()).build() }

interface GenericSerializer<MessageType, BuilderType> : Serializer<MessageType>
        where MessageType : GeneratedMessageLite<MessageType, BuilderType>,
              BuilderType : GeneratedMessageLite.Builder<MessageType, BuilderType> {

    /**
     * Call MessageType::parseFrom here!
     */
    val parseFrom: (input: InputStream) -> MessageType

    override suspend fun readFrom(input: InputStream): MessageType =
        withContext(Dispatchers.IO) {
            try {
                parseFrom(input)
            } catch (exception: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read proto.", exception)
            }
        }

    override suspend fun writeTo(t: MessageType, output: OutputStream) =
        withContext(Dispatchers.IO) { t.writeTo(output) }
}

interface NetworkRetrieving {
    suspend fun getLettersAndAnagrams(savedDataHandling: SavedDataHandling, onError: suspend (Throwable?) -> Unit = {})
    suspend fun getDefinition(word: String): Result<List<BaseDefinition>>
}

class LanVersion : NetworkRetrieving {

    override suspend fun getLettersAndAnagrams(
        savedDataHandling: SavedDataHandling,
        onError: suspend (Throwable?) -> Unit
    ) {
        @Serializable
        data class Word(val word: String, val anagrams: List<String>)
        runCatching { getApi<Word>("${BuildConfig.IP4_ADDRESS}/randomWord/7?minimumSize=4") }
            .fold(
                onSuccess = { word ->
                    println(word)
                    if (word != null) {
                        savedDataHandling.updateMainLetters(word.word)
                        savedDataHandling.updateAnagrams(word.anagrams.map { Anagrams(it, it.length, false) })
                    }
                },
                onFailure = {
                    it.printStackTrace()
                    onError(it)
                }
            )
    }

    override suspend fun getDefinition(word: String): Result<List<BaseDefinition>> = runCatching {
        @Serializable
        data class Definition(val word: String, val definition: String)

        getApi<Definition>("${BuildConfig.IP4_ADDRESS}/wordDefinition/$word")
            ?.let {
                listOf(
                    BaseDefinition(
                        it.word,
                        meanings = listOf(
                            Meanings(
                                partOfSpeech = null,
                                definitions = listOf(Definitions(it.definition))
                            )
                        ),
                        origin = null,
                        phonetic = null,
                        phonetics = emptyList()
                    )
                )
            }
            .orEmpty()
    }
}

class APIVersion : NetworkRetrieving {

    override suspend fun getLettersAndAnagrams(
        savedDataHandling: SavedDataHandling,
        onError: suspend (Throwable?) -> Unit
    ) {
        val newLetters = withContext(Dispatchers.IO) {
            getLetters().fold(
                onSuccess = {
                    onError(null)
                    it.firstOrNull()
                        .orEmpty()
                        .toList()
                        .shuffled()
                        .joinToString("")
                },
                onFailure = {
                    it.printStackTrace()
                    onError(it)
                    ""
                }
            ).also { savedDataHandling.updateMainLetters(it) }
        }
        withContext(Dispatchers.IO) {
            getAnagram(newLetters).fold(
                onSuccess = {
                    onError(null)
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    onError(it)
                    emptyList()
                }
            ).let { savedDataHandling.updateAnagrams(it) }
        }
    }

    override suspend fun getDefinition(word: String) = getWordDefinition(word)
}

val Context.appVersion: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0L)
        ).versionName
    } else {
        packageManager.getPackageInfo(packageName, 0)?.versionName
    }.orEmpty()

@Composable
fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) { context.appVersion }
}