package com.programmersbox.chatfunctionality

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatUi(
    host: String,
    url: String,
    vm: ChatViewModel = viewModel { ChatViewModel(host, url) },
    bottomSheetState: ModalBottomSheetState
) {
}

class ChatViewModel(host: String, url: String) : ViewModel() {
    val hasMessages = false
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatIcon(
    chatViewModel: ChatViewModel,
    chatState: ModalBottomSheetState,
    scope: CoroutineScope
) {
}