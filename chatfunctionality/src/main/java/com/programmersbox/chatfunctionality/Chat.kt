package com.programmersbox.chatfunctionality

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat

enum class MessageType {
    MESSAGE, SERVER, INFO, TYPING_INDICATOR
}

@Serializable
data class SendMessage(
    val user: ChatUser,
    val message: String,
    val type: MessageType?,
    val time: String = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())
)

@Serializable
data class ChatUser(val name: String)

@Serializable
data class PostMessage(val name: String, val message: String)

class Chat(private val url: String) {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(json) }
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

    val messages = MutableSharedFlow<SendMessage>()

    val name = MutableStateFlow<String?>(null)

    suspend fun init(host: String) {
        client.ws(method = HttpMethod.Get, host = host, port = 8080, path = "/anagramerChat") {
            incoming
                .consumeAsFlow()
                .filterIsInstance<Frame.Text>()
                .map { it.readText() }
                .map { text ->
                    if (name.value == null) name.emit(text)
                    println(text)
                    try {
                        json.decodeFromString<SendMessage>(text)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filterNotNull()
                .onEach { messages.emit(it) }
                .collect()
        }
    }

    suspend fun sendMessage(message: String) {
        client.post("$url/anagramerMessage") {
            setBody(PostMessage(name.value.orEmpty(), message))
            contentType(ContentType.Application.Json)
        }
    }
}