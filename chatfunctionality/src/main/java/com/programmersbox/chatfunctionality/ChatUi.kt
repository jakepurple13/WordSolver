package com.programmersbox.chatfunctionality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatUi(host: String, url: String, vm: ChatViewModel = viewModel { ChatViewModel(host, url) }) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
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