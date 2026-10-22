package com.tas5805m.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.tuner.data.EQ_FREQS
import com.tas5805m.tuner.data.EQ_MAX
import com.tas5805m.tuner.data.EQ_MIN
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

/** Slider vertikal buatan sendiri (untuk band EQ). */
@Composable
fun VerticalGain(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = EQ_MIN,
    max: Int = EQ_MAX,
    enabled: Boolean = true,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val active = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val mid = MaterialTheme.colorScheme.outline
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val pad = with(density) { 12.dp.toPx() }
    var h by remember { mutableFloatStateOf(1f) }

    fun yToValue(y: Float): Int {
        val span = (h - 2 * pad).coerceAtLeast(1f)
        val f = 1f - ((y - pad) / span).coerceIn(0f, 1f)
        return (min + f * (max - min)).roundToInt().coerceIn(min, max)
    }

    Canvas(
        modifier
            .onSizeChanged { h = it.height.toFloat() }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onChange(yToValue(it.y)) }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectVerticalDragGestures(
                        onDragStart = { onChange(yToValue(it.y)) },
                    ) { change, _ ->
                        change.consume()
                        onChange(yToValue(change.position.y))
                    }
                }
            },
    ) {
        val cx = size.width / 2f
        val span = size.height - 2 * pad
        fun yOf(v: Int): Float = pad + span * (1f - (v - min).toFloat() / (max - min))
        val tw = 6.dp.toPx()
        drawRoundRect(track, Offset(cx - tw / 2, pad), Size(tw, span), CornerRadius(tw / 2))
        val y0 = yOf(0)
        val yv = yOf(value)
        val top = minOf(y0, yv)
        val bot = maxOf(y0, yv)
        if (bot - top > 0.5f) drawRoundRect(active, Offset(cx - tw / 2, top), Size(tw, bot - top), CornerRadius(tw / 2))
        drawLine(mid, Offset(cx - 11.dp.toPx(), y0), Offset(cx + 11.dp.toPx(), y0), strokeWidth = 1.5.dp.toPx())
        drawCircle(active, radius = 10.dp.toPx(), center = Offset(cx, yv))
        drawCircle(bg, radius = 4.dp.toPx(), center = Offset(cx, yv))
    }
}

/** Perkiraan kurva respons dari 15 band (puncak gaussian pada skala log). */
@Composable
fun EqCurve(gains: List<Int>, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val line = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width
            val h = size.height
            val range = 18f
            fun yOfDb(db: Float): Float = h / 2f - (db / range) * (h / 2f)
            fun xOfHz(hz: Float): Float = (ln(hz / 20f) / ln(1000f)) * w

            for (db in listOf(-12f, -6f, 6f, 12f)) drawLine(grid, Offset(0f, yOfDb(db)), Offset(w, yOfDb(db)), 1.dp.toPx())
            for (hz in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) {
                drawLine(grid, Offset(xOfHz(hz), 0f), Offset(xOfHz(hz), h), 1.dp.toPx())
            }
            drawLine(zero, Offset(0f, yOfDb(0f)), Offset(w, yOfDb(0f)), 1.5.dp.toPx())

            val n = 160
            val path = Path()
            val fill = Path()
            for (i in 0..n) {
                val f = 20f * 1000f.pow(i.toFloat() / n)
                var db = 0f
                for (b in EQ_FREQS.indices) {
                    val d = ln(f / EQ_FREQS[b])
                    db += gains[b] * exp(-(d * d) / (2f * 0.42f * 0.42f))
                }
                val x = xOfHz(f)
                val y = yOfDb(db.coerceIn(-range, range))
                if (i == 0) { path.moveTo(x, y); fill.moveTo(x, yOfDb(0f)); fill.lineTo(x, y) }
                else { path.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(w, yOfDb(0f))
            fill.close()
            drawPath(fill, line.copy(alpha = 0.14f))
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val w = maxWidth
            listOf(50f to "50", 100f to "100", 500f to "500", 1000f to "1k", 5000f to "5k", 10000f to "10k").forEach { (hz, t) ->
                val frac = ln(hz / 20f) / ln(1000f)
                Text(t, fontSize = 10.sp, color = labelColor, modifier = Modifier.offset(x = w * frac - 8.dp))
            }
        }
    }
}

/** Ilustrasi filter crossover (Butterworth orde-4) untuk HPF satelit dan LPF sub. */
@Composable
fun CrossoverPlot(fc: Int, modifier: Modifier = Modifier) {
    val hp = MaterialTheme.colorScheme.primary
    val lp = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        val dbMin = -36f
        val dbMax = 3f
        fun y(db: Float) = h * (1f - (db.coerceIn(dbMin, dbMax) - dbMin) / (dbMax - dbMin))
        fun x(hz: Float) = (ln(hz / 20f) / ln(1000f)) * w
        for (db in listOf(0f, -12f, -24f)) drawLine(grid, Offset(0f, y(db)), Offset(w, y(db)), 1.dp.toPx())
        for (hz in listOf(50f, 100f, 200f, 500f, 1000f, 5000f)) drawLine(grid, Offset(x(hz), 0f), Offset(x(hz), h), 1.dp.toPx())
        drawLine(marker, Offset(x(fc.toFloat()), 0f), Offset(x(fc.toFloat()), h), 1.5.dp.toPx())
        val pLp = Path()
        val pHp = Path()
        val n = 140
        for (i in 0..n) {
            val f = 20f * 1000f.pow(i.toFloat() / n)
            val r = (f / fc).toDouble()
            val lpDb = (-10.0 * log10(1.0 + r.pow(8.0))).toFloat()
            val hpDb = (-10.0 * log10(1.0 + (1.0 / r).pow(8.0))).toFloat()
            val px = x(f)
            if (i == 0) { pLp.moveTo(px, y(lpDb)); pHp.moveTo(px, y(hpDb)) }
            else { pLp.lineTo(px, y(lpDb)); pHp.lineTo(px, y(hpDb)) }
        }
        drawPath(pLp, lp, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(pHp, hp, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Meter level gaya studio.
 *  [levelDeci]/[peakDeci] = deci-dB (−1200..0). Rentang tampil −60..0 dB.
 *  Gradien hijau→kuning→merah, segmen, skala, peak hold.
 *  Swipe horizontal → [onResetPeak]. */
@Composable
fun LevelBar(
    label: String,
    levelDeci: Int,
    peakDeci: Int,
    modifier: Modifier = Modifier,
    onResetPeak: (() -> Unit)? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    fun frac(d: Int) = (((d / 10f) + 60f) / 60f).coerceIn(0f, 1f)
    val db = levelDeci / 10f
    val peakDb = peakDeci / 10f
    val clipping = db > -3f
    val hot = db > -12f
    val valueColor = when {
        clipping -> BadRed
        hot -> WarnAmber
        else -> OkGreen
    }
    Column(
        modifier.then(
            if (onResetPeak != null) Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures { _, _ -> onResetPeak() }
            } else Modifier
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (clipping) {
                Text(
                    "CLIP",
                    color = BadRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                if (levelDeci <= -1190) "−∞" else "%.1f".format(db),
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
                fontWeight = if (clipping) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(" dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "  · pk %.0f".format(peakDb.coerceAtLeast(-60f)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(5.dp.toPx())
            // Track
            drawRoundRect(track, Offset.Zero, size, r)

            // Filled level with studio gradient (green → amber → red along the bar)
            val wFill = w * frac(levelDeci)
            if (wFill > 1.5f) {
                val brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to OkGreen,
                        0.70f to OkGreen,
                        0.82f to WarnAmber,
                        0.92f to WarnAmber,
                        1.0f to BadRed,
                    ),
                    endX = w,
                )
                drawRoundRect(brush, Offset.Zero, Size(wFill, h), r)
            }

            // Segment gaps (VU-style)
            val segments = 24
            val gap = 1.5.dp.toPx()
            val segW = (w - gap * (segments - 1)) / segments
            for (i in 0 until segments) {
                val x = i * (segW + gap) + segW
                if (i < segments - 1) {
                    drawRect(track.copy(alpha = 0.55f), Offset(x, 0f), Size(gap, h))
                }
            }

            // Scale ticks: -60, -40, -20, -12, -6, -3, 0
            for (mark in listOf(-60f, -40f, -20f, -12f, -6f, -3f, 0f)) {
                val x = w * ((mark + 60f) / 60f)
                val tall = mark in listOf(-20f, -12f, -6f, -3f, 0f)
                val th = if (tall) h * 0.45f else h * 0.28f
                drawLine(tickColor, Offset(x, h - th), Offset(x, h), 1.2.dp.toPx())
            }

            // Peak hold marker (bright line + small cap)
            val px = w * frac(peakDeci)
            val peakCol = if (peakDb > -3f) BadRed else Color.White
            drawLine(peakCol, Offset(px, 0f), Offset(px, h), 2.5.dp.toPx())
            drawRoundRect(
                peakCol,
                Offset(px - 2.dp.toPx(), 0f),
                Size(4.dp.toPx(), 5.dp.toPx()),
                CornerRadius(1.dp.toPx()),
            )
        }
        // Mini scale labels
        Row(Modifier.fillMaxWidth()) {
            listOf("-60" to 0f, "-40" to 0.33f, "-20" to 0.67f, "-12" to 0.80f, "0" to 1f).forEach { (lab, pos) ->
                // approximate positions via weights is hard; use fixed small labels at ends + mid
            }
            Text("−60", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("−40", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("−20", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("−12", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
