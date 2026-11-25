package com.napy.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.napy.musicplayer.playback.SpectrumBus
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

private fun hz(f: Float) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "${f.roundToInt()} Hz"

/**
 * Analizzatore di spettro in tempo reale (FFT sull'audio in riproduzione).
 * Tocca o trascina il dito sul grafico per leggere frequenza e livello di una banda.
 * Il pulsante in alto cambia stile: barre con riflesso / area.
 */
@Composable
fun SpectrumView(playing: Boolean, modifier: Modifier = Modifier) {
    val n = SpectrumBus.BANDS
    val shown = remember { FloatArray(n) }
    val peaks = remember { FloatArray(n) }
    var frame by remember { mutableLongStateOf(0L) }
    var style by remember { mutableIntStateOf(0) }
    var touchX by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos { t ->
                val src = SpectrumBus.levels
                val fresh = playing && (System.nanoTime() - SpectrumBus.stamp) < 250_000_000L
                for (i in 0 until n) {
                    val target = if (fresh) src[i] else 0f
                    shown[i] = if (target > shown[i]) shown[i] + (target - shown[i]) * 0.65f else max(target, shown[i] * 0.90f)
                    peaks[i] = if (shown[i] >= peaks[i]) shown[i] else max(0f, peaks[i] - 0.006f)
                }
                frame = t
            }
        }
    }

    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(Surface1.copy(alpha = 0.7f))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent().changes.firstOrNull() ?: return@awaitEachGesture
                    touchX = if (down.pressed) down.position.x else null
                    while (true) {
                        val p = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!p.pressed) break
                        touchX = p.position.x
                    }
                    touchX = null
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            frame.let { }   // legge lo stato: ridisegna a ogni fotogramma
            widthPx = size.width.toInt().coerceAtLeast(1)
            val w = size.width
            val labelH = 16.dp.toPx()
            val h = size.height - labelH
            val mainH = h * 0.82f
            val refH = h - mainH
            val slot = w / n
            val gap = slot * 0.22f
            val brush = Brush.verticalGradient(listOf(Pink, Violet, Cyan), startY = 0f, endY = mainH)

            // griglia
            for (g in 1..3) {
                val y = mainH * g / 4f
                drawLine(Border.copy(alpha = 0.45f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
            }

            if (style == 0) {
                for (i in 0 until n) {
                    val x = i * slot + gap / 2
                    val bh = (shown[i] * mainH).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(brush, Offset(x, mainH - bh), Size(slot - gap, bh), CornerRadius(3.dp.toPx()))
                    // riflesso
                    val rh = shown[i] * refH
                    drawRect(Cyan.copy(alpha = 0.22f), Offset(x, mainH + 2.dp.toPx()), Size(slot - gap, rh))
                    // picco
                    val py = mainH - peaks[i] * mainH
                    drawRect(Color.White, Offset(x, (py - 4.dp.toPx()).coerceAtLeast(0f)), Size(slot - gap, 2.dp.toPx()))
                }
            } else {
                val line = Path(); val area = Path()
                area.moveTo(0f, mainH)
                for (i in 0 until n) {
                    val x = i * slot + slot / 2
                    val y = mainH - shown[i] * mainH
                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                    area.lineTo(x, y)
                }
                area.lineTo(w, mainH); area.close()
                drawPath(area, Brush.verticalGradient(listOf(Pink.copy(alpha = 0.85f), Violet.copy(alpha = 0.35f), Color.Transparent), 0f, mainH))
                drawPath(line, Cyan, style = Stroke(3.dp.toPx()))
            }

            // etichette di frequenza (asse logaritmico 40 Hz - 16 kHz)
            for ((label, f) in listOf("100" to 100f, "1k" to 1000f, "10k" to 10000f)) {
                val frac = ln(f / 40f) / ln(16000f / 40f)
                val t = measurer.measure(label, TextStyle(color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                drawText(t, topLeft = Offset(frac * w - t.size.width / 2, size.height - labelH + 2.dp.toPx()))
            }

            // evidenzia la banda toccata
            touchX?.let { tx ->
                val i = (tx / w * n).toInt().coerceIn(0, n - 1)
                drawRect(Color.White.copy(alpha = 0.18f), Offset(i * slot, 0f), Size(slot, mainH))
                drawRect(Color.White, Offset(i * slot, mainH - shown[i] * mainH), Size(slot, 2.dp.toPx()))
            }
        }

        // suggerimento con frequenza e livello
        touchX?.let { tx ->
            val i = (tx / widthPx * n).toInt().coerceIn(0, n - 1)
            val db = shown[i] * SpectrumBus.RANGE_DB + SpectrumBus.FLOOR_DB
            val c = SpectrumBus.centers
            Text(
                "${if (c[i] > 0) hz(c[i]) else "-"}  ·  ${"%.0f".format(db)} dB",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier
                    .offset { IntOffset((tx - 60.dp.toPx()).coerceIn(0f, (widthPx - 130.dp.toPx()).coerceAtLeast(0f)).roundToInt(), 6.dp.roundToPx()) }
                    .clip(RoundedCornerShape(10.dp)).background(Bg.copy(alpha = 0.92f))
                    .border(1.dp, Cyan, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Text(
            if (style == 0) "BARRE" else "AREA", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Surface2)
                .clickable { style = 1 - style }.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
