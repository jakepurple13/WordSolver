package com.programmersbox.wordsolver

import android.graphics.Paint
import android.util.Range
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> PatternInput(
    options: List<T>,
    modifier: Modifier = Modifier,
    optionToString: (T) -> String = { it.toString() },
    colors: PatternColors = PatternInputDefaults.defaultColors(),
    dotsSize: Float = 50f,
    sensitivity: Float = dotsSize,
    linesStroke: Float,
    circleStroke: Stroke = Stroke(width = linesStroke),
    animationDuration: Int = 200,
    animationDelay: Long = 100,
    onStart: (Dot<T>) -> Unit = {},
    onDotRemoved: (Dot<T>) -> Unit = {},
    onDotConnected: (Dot<T>) -> Unit = {},
    onResult: (List<Dot<T>>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val dotsList = remember(options) { mutableListOf<Dot<T>>() }
    var previewLine by remember { mutableStateOf(Line(Offset(0f, 0f), Offset(0f, 0f))) }
    val connectedLines = remember { mutableListOf<Line>() }
    val connectedDots = remember { mutableListOf<Dot<T>>() }
    var removableDot: Dot<T>? = remember { null }

    Canvas(
        modifier.pointerInteropFilter {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    dotsList.find { dots ->
                        it.x in Range(
                            dots.offset.x - sensitivity,
                            dots.offset.x + sensitivity
                        ) && it.y in Range(
                            dots.offset.y - sensitivity,
                            dots.offset.y + sensitivity
                        )
                    }?.let { dots ->
                        connectedDots.add(dots)
                        onStart(dots)
                        scope.launch {
                            dots.size.animateTo(
                                (dotsSize * 1.8).toFloat(),
                                tween(animationDuration)
                            )
                            delay(animationDelay)
                            dots.size.animateTo(dotsSize, tween(animationDuration))
                        }
                        previewLine = previewLine.copy(start = Offset(dots.offset.x, dots.offset.y))
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    previewLine = previewLine.copy(end = Offset(it.x, it.y))
                    //Connecting and adding
                    dotsList.find { dots ->
                        it.x in Range(
                            dots.offset.x - sensitivity,
                            dots.offset.x + sensitivity
                        ) && it.y in Range(
                            dots.offset.y - sensitivity,
                            dots.offset.y + sensitivity
                        )
                    }
                        ?.let { dots ->
                            if (dots !in connectedDots) {
                                if (previewLine.start != Offset(0f, 0f)) {
                                    connectedLines.add(
                                        Line(
                                            start = previewLine.start,
                                            end = dots.offset
                                        )
                                    )
                                }
                                connectedDots.add(dots)
                                onDotConnected(dots)
                                scope.launch {
                                    dots.size.animateTo(
                                        (dotsSize * 1.8).toFloat(),
                                        tween(animationDuration)
                                    )
                                    delay(animationDelay)
                                    dots.size.animateTo(dotsSize, tween(animationDuration))
                                }
                                previewLine = previewLine.copy(start = dots.offset)
                            } else if (dots in connectedDots) {
                                if (removableDot == null)
                                    removableDot = connectedDots.getOrNull(connectedDots.indexOf(dots) - 1)
                            }
                        }

                    //Checking if the last one should be removed
                    if (removableDot != null && connectedDots.size >= 2) {
                        if (
                            it.x in Range(
                                removableDot!!.offset.x - sensitivity,
                                removableDot!!.offset.x + sensitivity
                            ) &&
                            it.y in Range(
                                removableDot!!.offset.y - sensitivity,
                                removableDot!!.offset.y + sensitivity
                            )
                        ) {
                            connectedLines.removeLastOrNull()
                            connectedDots.removeLastOrNull()?.let(onDotRemoved)
                            removableDot = null
                            connectedDots.lastOrNull()?.let { previewLine = previewLine.copy(start = it.offset) }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    previewLine = previewLine.copy(start = Offset(0f, 0f), end = Offset(0f, 0f))
                    onResult(connectedDots)
                    connectedLines.clear()
                    connectedDots.clear()
                }
            }
            true
        }
    ) {
        drawCircle(
            color = colors.dotsColor,
            radius = size.width / 2 - circleStroke.width,
            style = circleStroke,
            center = center
        )

        val radius = (size.width / 2) - (dotsSize * 2) - circleStroke.width

        if (dotsList.size < options.size) {
            options.forEachIndexed { index, t ->
                val angleInDegrees = ((index.toFloat() / options.size.toFloat()) * 360.0) + 50.0
                val x = -(radius * sin(Math.toRadians(angleInDegrees))).toFloat() + (size.width / 2)
                val y = (radius * cos(Math.toRadians(angleInDegrees))).toFloat() + (size.height / 2)

                dotsList.add(
                    Dot(
                        id = t,
                        offset = Offset(x = x, y = y),
                        size = Animatable(dotsSize),
                    )
                )
            }
        }
        if (previewLine.start != Offset(0f, 0f) && previewLine.end != Offset(0f, 0f)) {
            drawLine(
                color = colors.linesColor,
                start = previewLine.start,
                end = previewLine.end,
                strokeWidth = linesStroke,
                cap = StrokeCap.Round
            )
        }
        for (dots in dotsList) {
            drawCircle(
                color = colors.dotsColor,
                radius = dotsSize * 2,
                style = Stroke(width = 2.dp.value),
                center = dots.offset
            )
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    optionToString(dots.id),
                    dots.offset.x,
                    dots.offset.y + (dots.size.value / 3),
                    Paint().apply {
                        color = colors.letterColor.toArgb()
                        textSize = dots.size.value
                        textAlign = Paint.Align.CENTER
                    }
                )
            }
        }
        for (line in connectedLines) {
            drawLine(
                color = colors.linesColor,
                start = line.start,
                end = line.end,
                strokeWidth = linesStroke,
                cap = StrokeCap.Round
            )
        }

    }
}

data class Dot<T : Any>(
    val id: T,
    val offset: Offset,
    val size: Animatable<Float, AnimationVector1D>
)

data class Line(
    val start: Offset,
    val end: Offset
)

@Immutable
data class PatternColors(
    val dotsColor: Color,
    val linesColor: Color,
    val letterColor: Color,
)

object PatternInputDefaults {
    @Composable
    fun defaultColors(
        dotsColor: Color = Color.White,
        linesColor: Color = Color.White,
        letterColor: Color = Color.White,
    ) = PatternColors(
        dotsColor = dotsColor,
        linesColor = linesColor,
        letterColor = letterColor,
    )
}

@Preview
@Composable
fun PatternInputPreview() {
    var wordGuess by remember { mutableStateOf("") }
    Column {
        Text(wordGuess)
        PatternInput(
            options = listOf("h", "e", "l", "l", "o", "!", "!"),
            modifier = Modifier
                .width(500.dp)
                .height(1000.dp)
                .background(Color.Black),
            optionToString = { it },
            colors = PatternInputDefaults.defaultColors(Color.White, Color.White, Color.White),
            dotsSize = 100f,
            sensitivity = 50.sp.value,
            linesStroke = 30f,
            circleStroke = Stroke(width = 30f),
            animationDuration = 200,
            animationDelay = 100,
            onStart = {
                wordGuess = ""
                wordGuess = it.id
            },
            onDotConnected = { wordGuess = "$wordGuess${it.id}" },
            onResult = { /*Does a final thing*/ }
        )
    }
}