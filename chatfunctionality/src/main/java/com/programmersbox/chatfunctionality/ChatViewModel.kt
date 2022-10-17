package com.programmersbox.chatfunctionality

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(host: String, url: String) : ViewModel() {

    private val chat = Chat(url)

    var text by mutableStateOf("")
    val messages = mutableStateListOf<SendMessage>()
    var name by mutableStateOf("")

    val hasMessages by derivedStateOf { messages.isNotEmpty() }

    init {
        viewModelScope.launch { chat.init(host) }

        chat.messages
            .onEach { messages.add(it) }
            .launchIn(viewModelScope)

        chat.name
            .filterNotNull()
            .onEach { name = it }
            .launchIn(viewModelScope)
    }

    fun send() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { chat.sendMessage(text) }
            text = ""
        }
    }
}