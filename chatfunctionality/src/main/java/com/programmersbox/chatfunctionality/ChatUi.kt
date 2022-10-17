package com.programmersbox.chatfunctionality

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChatUi(
    host: String,
    url: String,
    vm: ChatViewModel = viewModel { ChatViewModel(host, url) },
    bottomSheetState: ModalBottomSheetState
) {
    LaunchedEffect(bottomSheetState) {
        snapshotFlow { bottomSheetState.currentValue }
            .filter { it == ModalBottomSheetValue.Expanded }
            .onEach { vm.hasMessages = false }
            .collect()
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                actions = {
                    IconButton(onClick = { scope.launch { bottomSheetState.hide() } }) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BottomAppBar {
                OutlinedTextField(
                    value = vm.text,
                    onValueChange = { vm.text = it },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = vm::send) { Icon(Icons.Default.Send, null) } },
                    singleLine = true,
                    label = { Text("You are: ${vm.name}") }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(vm.messages) {
                ElevatedCard {
                    ListItem(
                        headlineText = { Text(it.message) },
                        overlineText = { Text(it.user.name) },
                        supportingText = { Text(it.time) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChatIcon(
    chatViewModel: ChatViewModel,
    chatState: ModalBottomSheetState,
    scope: CoroutineScope
) {
    FilledTonalIconButton(
        onClick = { scope.launch { chatState.show() } },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = animateColorAsState(
                if (chatViewModel.hasMessages) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondaryContainer
            ).value
        )
    ) { Icon(Icons.Default.Chat, null) }
}