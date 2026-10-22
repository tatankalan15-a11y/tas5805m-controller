package com.tas5805m.tuner.ui

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.FAULT_DEFS
import com.tas5805m.tuner.data.TunerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(s: TunerState, vm: TunerViewModel) {
    val twoChip = s.mode == AudioMode.TWO_CHIP_21 && s.hasLvl2
    val clipThreshold = -30   // -3.0 dB
    val warnThreshold = -120  // -12.0 dB
    val anyClip = s.levelL > clipThreshold || s.levelR > clipThreshold ||
            (twoChip && (s.level2L > clipThreshold || s.level2R > clipThreshold))
    val anyWarn = s.levelL > warnThreshold || s.levelR > warnThreshold ||
            (twoChip && (s.level2L > warnThreshold || s.level2R > warnThreshold))

    SectionCard(
        "Level meter",
        trailing = {
            val statusText = when {
                anyClip -> "CLIP !"
                anyWarn -> "HOT"
                s.hasExt -> "LIVE"
                else -> "—"
            }
            val statusColor = when {
                anyClip -> BadRed
                anyWarn -> WarnAmber
                else -> MaterialTheme.colorScheme.primary
            }
            Text(statusText, color = statusColor, fontWeight = FontWeight.Bold)
        },
    ) {
        if (!s.hasExt) {
            Text(
                "Meter level butuh firmware v2 (perintah lvl). Flash firmware/ dari repo ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            s.mode == AudioMode.ONE_CHIP_21 -> {
                LevelBarImproved("Satelit (L)", s.levelL, s.peakL)
                LevelBarImproved("Sub (R)", s.levelR, s.peakR)
            }
            s.mode == AudioMode.TWO_CHIP_21 -> {
                LevelBarImproved("Sat L", s.levelL, s.peakL)
                LevelBarImproved("Sat R", s.levelR, s.peakR)
                if (twoChip) {
                    LevelBarImproved("Sub", maxOf(s.level2L, s.level2R), maxOf(s.peak2L, s.peak2R))
                } else {
                    Text(
                        "Meter sub (chip 0x2E) butuh firmware v3.1 env 2-chip.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LevelBarImproved("L", s.levelL, s.peakL)
                LevelBarImproved("R", s.levelR, s.peakR)
            }
        }
        if (anyClip) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BadRed.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Text(
                    "⚠ CLIPPING terdeteksi! Turunkan Master / Sat / Sub segera agar speaker 4Ω aman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BadRed,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else if (anyWarn) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(WarnAmber.copy(alpha = 0.12f))
                    .padding(10.dp)
            ) {
                Text(
                    "Level mendekati clipping. Perhatikan volume agar tidak merusak speaker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnAmber,
                )
            }
        }
        Text(
            "Meter dibaca dari DSP TAS5805M (setelah EQ/mixer), diperbarui ±6×/detik.\nHijau = aman · Kuning = mendekati · Merah = clipping.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(
        "Diagnostik fault",
        trailing = {
            Text(
                if (s.anyFault) "ADA FAULT" else "OK",
                color = if (s.anyFault) BadRed else OkGreen,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        if (s.hasLvl2) {
            Text("Satelit (chip 1)", style = MaterialTheme.typography.labelLarge)
            FaultGrid { s.fault1(it) }
            Text("Subwoofer (chip 2)", style = MaterialTheme.typography.labelLarge)
            FaultGrid { s.fault2(it) }
        } else {
            FaultGrid { s.fault1(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.readFaults() }) { Text("Baca fault") }
            OutlinedButton(onClick = { vm.ping() }) { Text(if (s.rttMs >= 0) "Ping ${s.rttMs} ms" else "Ping") }
        }
        Text(
            "Membaca fault akan menghapus register fault di chip (perilaku firmware). Kejadian dicatat di riwayat di bawah.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(
        "Riwayat fault",
        trailing = { OutlinedButton(onClick = { vm.clearFaultHistory() }) { Text("Bersihkan") } },
    ) {
        if (s.faultHistory.isEmpty()) {
            Text("Belum ada fault tercatat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            s.faultHistory.asReversed().forEach { e ->
                val labels = e.keys.mapNotNull { k ->
                    val d = FAULT_DEFS.firstOrNull { it.key == k.removeSuffix("@2") }
                    d?.let { if (k.endsWith("@2")) it.label + " (sub)" else it.label }
                }
                Text("${fmt.format(Date(e.time))}  ·  ${labels.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (s.hasV3) {
        SectionCard("Status chip") {
            val i = s.info
            if (i.isEmpty()) {
                Text("Menunggu data…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                InfoLine("Sample rate terdeteksi", i["fs"] ?: "—")
                InfoLine("Rasio BCK", i["bck"] ?: "—")
                InfoLine("Power state", (i["pwr"] ?: "—").replace('_', ' '))
                InfoLine("Auto-mute L / R", "${if (i["mute_l"] == "1") "ya" else "tidak"} / ${if (i["mute_r"] == "1") "ya" else "tidak"}")
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FaultGrid(active: (String) -> Boolean) {
        FAULT_DEFS.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { d ->
                    val on = active(d.key)
                    val c = if (!on) OkGreen else if (d.critical) BadRed else WarnAmber
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.size(10.dp).background(c, CircleShape)) {}
                        Text(d.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
}


/**
 * Level bar visual: gradient hijau→kuning→merah, animasi, peak hold, zona clip -3 dB.
 */
@Composable
fun LevelBarImproved(
    label: String,
    levelDeci: Int,
    peakDeci: Int,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val peakColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    fun frac(d: Int) = (((d / 10f) + 60f) / 60f).coerceIn(0f, 1f)
    val db = levelDeci / 10f
    val targetFrac = frac(levelDeci)
    val peakFrac = frac(peakDeci)
    val animatedFrac by animateFloatAsState(targetValue = targetFrac, animationSpec = tween(80), label = "levelAnim")
    val color by animateColorAsState(
        targetValue = when {
            db > -3f -> BadRed
            db > -12f -> WarnAmber
            else -> OkGreen
        },
        animationSpec = tween(120),
        label = "colorAnim"
    )
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                if (levelDeci <= -1190) "−∞ dB" else "%.1f dB".format(db),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = if (db > -12f) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))) {
            val r = CornerRadius(9.dp.toPx())
            drawRoundRect(track, Offset.Zero, size, r)
            val fillW = size.width * animatedFrac
            if (fillW > 1f) {
                val brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to OkGreen,
                        0.70f to OkGreen,
                        0.85f to WarnAmber,
                        1.0f to BadRed,
                    ),
                    endX = size.width
                )
                drawRoundRect(brush = brush, topLeft = Offset.Zero, size = Size(fillW, size.height), cornerRadius = r)
            }
            val px = size.width * peakFrac
            if (peakFrac > 0.02f) {
                drawLine(peakColor, Offset(px, 0f), Offset(px, size.height), 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            val clipX = size.width * frac(-30)
            drawLine(BadRed.copy(alpha = 0.35f), Offset(clipX, 0f), Offset(clipX, size.height), 1.dp.toPx())
        }
    }
}
