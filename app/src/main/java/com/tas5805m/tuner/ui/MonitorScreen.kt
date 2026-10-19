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
import com.tas5805m.tuner.data.FAULT_DEFS
import com.tas5805m.tuner.data.TunerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(s: TunerState, vm: TunerViewModel) {
    val clipping = s.levelL > -10 || s.levelR > -10

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
        LevelBar("L", s.levelL, s.peakL)
        LevelBar("R", s.levelR, s.peakR)
        Text(
            "Meter dibaca dari DSP TAS5805M (setelah EQ/mixer), diperbarui ±6×/detik selama tab ini terbuka.",
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
        FAULT_DEFS.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { d ->
                    val on = s.faultActive(d.key)
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
                val labels = e.keys.mapNotNull { k -> FAULT_DEFS.firstOrNull { it.key == k }?.label }
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
