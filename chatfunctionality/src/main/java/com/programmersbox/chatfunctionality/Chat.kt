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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat

enum class MessageType {
    MESSAGE, SERVER, INFO, TYPING_INDICATOR, SETUP
}

@Serializable
sealed class Message {
    abstract val user: ChatUser
    abstract val messageType: MessageType
    val time: String = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())
}

@Serializable
@SerialName("MessageMessage")
data class MessageMessage(
    override val user: ChatUser,
    val message: String,
    override val messageType: MessageType = MessageType.MESSAGE
) : Message()

@Serializable
@SerialName("SetupMessage")
data class SetupMessage(
    override val user: ChatUser,
    val userColor: Int,
    override val messageType: MessageType = MessageType.SETUP
) : Message()

@Serializable
@SerialName("UserListMessage")
data class UserListMessage(
    override val user: ChatUser,
    override val messageType: MessageType = MessageType.INFO,
    val userList: List<ChatUser>
) : Message()

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

    val messages = MutableSharedFlow<Message>()

    val name = MutableStateFlow<SetupMessage?>(null)

    suspend fun init(host: String) {
        try {
            client.ws(method = HttpMethod.Get, host = host, port = 8080, path = "/anagramerChat") {
                incoming
                    .consumeAsFlow()
                    .filterIsInstance<Frame.Text>()
                    .map { it.readText() }
                    .map { text ->
                        println(text)
                        try {
                            json.decodeFromString<Message>(text)
                        } catch (e: Exception) {
                            try {
                                json.decodeFromString<SetupMessage>(text)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .filterNotNull()
                    .onEach {
                        when (it) {
                            is MessageMessage -> messages.emit(it)
                            is SetupMessage -> name.emit(it)
                            is UserListMessage -> messages.emit(it)
                        }
                    }
                    .collect()
            }
        } catch (_: Exception) {

        }
    }

    suspend fun sendMessage(message: String) {
        client.post("$url/anagramerMessage") {
            setBody(PostMessage(name.value?.user?.name.orEmpty(), message))
            contentType(ContentType.Application.Json)
        }
    }
}