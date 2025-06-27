package com.programmersbox.wordsolver

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import java.io.File
/*
class CurrentLettersWidget : GlanceAppWidget() {
    override var stateDefinition: GlanceStateDefinition<*> = LetterInfoStateDefinition

    @Composable
    override fun Content() {
        val letters = currentState(SavedDataHandling.MAIN_LETTERS)
        val context = LocalContext.current
        val day = androidx.glance.unit.ColorProvider(
            resId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_200
            else android.R.color.background_light
        )
        val night = androidx.glance.unit.ColorProvider(
            resId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.R.color.system_accent1_900
            else android.R.color.background_dark
        )

        val textStyle = TextStyle(
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = ColorProvider(
                day = Color.Black.compositeOver(day.getColor(context)),
                night = Color.White.compositeOver(night.getColor(context))
            )
        )

        *//*Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ColorProvider(
                        day = day.getColor(context),
                        night = night.getColor(context)
                    ),
                )
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .clickable(onClick = actionRunCallback<CurrentLettersClickAction>()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Anagramer",
                style = textStyle,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(onClick = actionRunCallback<CurrentLettersClickAction>())
            )
            if (letters != null) {
                Text(
                    text = letters,
                    style = textStyle,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(onClick = actionRunCallback<CurrentLettersClickAction>()),
                )
            } else {
                Text(
                    text = "Need a bit. Please wait",
                    style = textStyle,
                )
            }
            if (BuildConfig.BUILD_TYPE == "lanVersion") {
                Text(
                    "LAN Version",
                    style = textStyle.copy(fontSize = 12.sp),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(onClick = actionRunCallback<CurrentLettersClickAction>())
                )
            }
        }*//*
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
}*/
