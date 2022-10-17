package com.programmersbox.chatfunctionality

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatUi(host: String, url: String, vm: ChatViewModel = viewModel { ChatViewModel(host, url) }) {
}

class ChatViewModel(host: String, url: String) : ViewModel() {
    val hasMessages = false
}