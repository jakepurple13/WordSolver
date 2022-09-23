package com.programmersbox.wordsolver

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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
