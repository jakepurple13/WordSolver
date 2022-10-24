/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.programmersbox.wordoftheday.presentation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.wear.compose.material.*
import com.programmersbox.wordoftheday.BuildConfig
import com.programmersbox.wordoftheday.presentation.theme.WordSolverTheme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val MAIN_WORD = stringPreferencesKey("main_word")
val DEFINITION = stringPreferencesKey("definition")

@Composable
fun WearApp() {
    WordSolverTheme {
        /* If you have enough items in your list, use [ScalingLazyColumn] which is an optimized
         * version of LazyColumn for wear devices with some added features. For more information,
         * see d.android.com/wear/compose.
         */
        var key by remember { mutableStateOf(0) }
        val context = LocalContext.current
        val dataStore = remember { context.dataStore }
        val info by getWordOfTheDay(dataStore, key)
        val state = rememberScalingLazyListState()
        Scaffold(
            timeText = { TimeText(modifier = Modifier.scrollAway(state)) },
            positionIndicator = { PositionIndicator(state) }
        ) {
            ScalingLazyColumn(
                state = state,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                if (info is Results.Success<Definition>) {
                    item {
                        ListHeader {
                            Text((info as Results.Success<Definition>).value.word)
                        }
                    }
                    item {
                        Text((info as Results.Success<Definition>).value.definition)
                    }
                } else {
                    item {
                        CircularProgressIndicator()
                    }
                }
                item { Button(onClick = { key++ }) { Text("Get New Word") } }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}

@Composable
fun getWordOfTheDay(
    dataStore: DataStore<Preferences>,
    vararg key: Any?
) = produceState<Results<Definition>>(Results.Loading, keys = key) {
    value = Results.Loading
    val getFromApi: suspend () -> Definition = {
        if (BuildConfig.BUILD_TYPE == "lanVersion") getApi<Definition>("${BuildConfig.IP4_ADDRESS}/wordOfTheDay")!!
        else getApi<List<RandomWordApi>>("https://random-words-api.vercel.app/word/")?.first()
            .let { Definition(it?.word.orEmpty(), it?.definition.orEmpty()) }
    }
    value = runCatching {
        if (key.any { if (it is Int) it == 0 else false }) {
            dataStore.data.map {
                val word = it[MAIN_WORD]
                val definition = it[DEFINITION]
                if (word == null && definition == null) null else Definition(word!!, definition!!)
            }.firstOrNull() ?: getFromApi()
        } else {
            getFromApi()
        }
    }
        .fold(
            onSuccess = {
                dataStore.edit { p ->
                    p[MAIN_WORD] = it.word
                    p[DEFINITION] = it.definition
                }
                Results.Success(it)
            },
            onFailure = { Results.Error }
        )
}

sealed class Results<out R> {
    class Success<out T>(val value: T) : Results<T>()
    object Error : Results<Nothing>()
    object Loading : Results<Nothing>()
}

@Serializable
data class Definition(val word: String, val definition: String)

@Serializable
data class RandomWordApi(val word: String?, val definition: String?, val pronunciation: String?)

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