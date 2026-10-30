package com.tas5805m.tuner.ui

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
    val twoChip = s.mode.isTwoChip && s.hasLvl2
    val clipping = s.levelL > -30 || s.levelR > -30 || (twoChip && (s.level2L > -30 || s.level2R > -30))

    SectionCard(
        "Level meter",
        trailing = {
            Text(
                if (clipping) "CLIP" else if (s.hasExt) "LIVE" else "—",
                color = if (clipping) BadRed else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
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
                LevelBar("Satelit (L)", s.levelL, s.peakL, onResetPeak = { vm.clearPeaks() })
                LevelBar("Sub (R)", s.levelR, s.peakR, onResetPeak = { vm.clearPeaks() })
            }
            s.mode.isTwoChip -> {
                LevelBar("Sat L", s.levelL, s.peakL, onResetPeak = { vm.clearPeaks() })
                LevelBar("Sat R", s.levelR, s.peakR, onResetPeak = { vm.clearPeaks() })
                if (twoChip) {
                    LevelBar(if (s.mode == AudioMode.THREE_ONE) "Chip 2 (center + sub)" else "Sub", maxOf(s.level2L, s.level2R), maxOf(s.peak2L, s.peak2R), onResetPeak = { vm.clearPeaks() })
                } else {
                    Text(
                        "Meter sub (chip 0x2E) butuh firmware v3.1 env 2-chip.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LevelBar("L", s.levelL, s.peakL, onResetPeak = { vm.clearPeaks() })
                LevelBar("R", s.levelR, s.peakR, onResetPeak = { vm.clearPeaks() })
            }
        }
        if (clipping) {
            Text(
                "⚠ Level mendekati clipping (> −3 dB). Turunkan master/sat/sub atau naikkan analog gain (kurang sensitif).",
                style = MaterialTheme.typography.bodySmall,
                color = BadRed,
            )
        }
        Text(
            "Meter dari DSP TAS5805M (setelah EQ/mixer), ±6×/detik. Geser horizontal pada bar = reset peak.",
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
