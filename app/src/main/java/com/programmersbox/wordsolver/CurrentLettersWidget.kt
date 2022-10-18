package com.programmersbox.wordsolver

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.Text
import java.io.File

class CurrentLettersWidget : GlanceAppWidget() {
    override var stateDefinition: GlanceStateDefinition<*> = LetterInfoStateDefinition

    @Composable
    override fun Content() {
        val letters = currentState(SavedDataHandling.MAIN_LETTERS)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(day = Color.White, night = Color.DarkGray)
                .appWidgetBackground()
                .cornerRadius(4.dp)
        ) {
            Text(
                text = letters ?: "Need a bit. Please wait",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clickable(onClick = actionRunCallback<CurrentLettersClickAction>())
            )
        }
    }
}

class CurrentLettersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentLettersWidget()
}

class CurrentLettersClickAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, LetterInfoStateDefinition, glanceId) {
            it.toMutablePreferences().apply {
                this[SavedDataHandling.MAIN_LETTERS]
                    ?.toList()
                    ?.shuffled()
                    ?.joinToString("")
                    ?.let { l -> this[SavedDataHandling.MAIN_LETTERS] = l }
            }
        }
        CurrentLettersWidget().update(context, glanceId)
    }
}

object LetterInfoStateDefinition : GlanceStateDefinition<Preferences> {
    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> = context.dataStore
    override fun getLocation(context: Context, fileKey: String): File = context.dataStoreFile("settings")
}
