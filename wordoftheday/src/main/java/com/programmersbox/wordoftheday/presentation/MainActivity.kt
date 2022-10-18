/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.programmersbox.wordoftheday.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.*
import com.programmersbox.wordoftheday.BuildConfig
import com.programmersbox.wordoftheday.R
import com.programmersbox.wordoftheday.presentation.theme.WordSolverTheme
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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

@Composable
fun WearApp() {
    WordSolverTheme {
        /* If you have enough items in your list, use [ScalingLazyColumn] which is an optimized
         * version of LazyColumn for wear devices with some added features. For more information,
         * see d.android.com/wear/compose.
         */
        var key by remember { mutableStateOf(0) }
        val info by getWordOfTheDay(key)
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
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

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}

@Composable
fun getWordOfTheDay(vararg key: Any?) = produceState<Results<Definition>>(Results.Loading, keys = key) {
    value = Results.Loading
    value = runCatching { getApi<Definition>("${BuildConfig.IP4_ADDRESS}/wordOfTheDay")!! }
        .fold(
            onSuccess = { Results.Success(it) },
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