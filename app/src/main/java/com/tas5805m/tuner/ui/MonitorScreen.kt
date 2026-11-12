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
import androidx.compose.material3.Switch
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
import com.tas5805m.tuner.data.FaultIncidents
import com.tas5805m.tuner.data.TunerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(s: TunerState, vm: TunerViewModel) {
    val twoChip = s.mode.isTwoChip && s.hasLvl2
    val wsubMeters = s.mode == AudioMode.WIRELESS && s.wsub != null
    val clipping = s.levelL > -30 || s.levelR > -30 || (twoChip && (s.level2L > -30 || s.level2R > -30)) ||
        (wsubMeters && s.wsubLvl.values.any { it > -30 })

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
            wsubMeters -> {
                LevelBar("Sat L", s.levelL, s.peakL, onResetPeak = { vm.clearPeaks() })
                LevelBar("Sat R", s.levelR, s.peakR, onResetPeak = { vm.clearPeaks() })
                val peers = s.wsub?.peers.orEmpty()
                if (peers.isEmpty()) {
                    Text(
                        "Belum ada sub wireless terpasang (tab Output → Sub wireless).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                peers.forEach { p ->
                    val lv = s.wsubLvl[p.slot]
                    val name = if (peers.size > 1) "Sub · slot ${p.slot}" else "Sub"
                    when {
                        lv != null -> LevelBar(name, lv, s.wsubPeak[p.slot] ?: lv, onResetPeak = { vm.clearPeaks() })
                        !p.link -> Text("$name: tidak terhubung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.hiz -> Text("$name: standby (Hi-Z)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Text(
                            if (s.hasWlvl) "$name: menunggu level dari slave… (butuh firmware slave 2.9.15)"
                            else "$name: meter butuh firmware master & slave 2.9.15",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            if (wsubMeters) "Meter dari DSP TAS5805M (setelah EQ/mixer). Sub wireless: dibaca di slave dan dikirim ke master ±4×/detik (peak antar-paket ditahan). Geser horizontal pada bar = reset peak."
            else "Meter dari DSP TAS5805M (setelah EQ/mixer), ±6×/detik. Geser horizontal pada bar = reset peak.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (wsubMeters) WsubHealthCard(s, vm)

    SectionCard(
        "Diagnostik fault",
        trailing = {
            val nowMs = System.currentTimeMillis()
            val satKnown = s.faultsReadMs > 0
            val subKnown = s.wsub?.peers?.any { it.flt >= 0 } == true
            val ageMs = nowMs - s.faultsReadMs
            // "OK" hanya bila ada pembacaan nyata; belum pernah dibaca / sudah lama = bukan hijau
            val (txt, col) = when {
                s.anyFault -> "ADA FAULT" to BadRed
                !satKnown && !subKnown -> "BELUM DIBACA" to MaterialTheme.colorScheme.onSurfaceVariant
                satKnown && !s.faultAuto && ageMs > 90_000 && !subKnown -> "OK · ${FaultIncidents.formatAgo(ageMs)}" to WarnAmber
                else -> "OK" to OkGreen
            }
            Text(txt, color = col, fontWeight = FontWeight.Bold)
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
        // Amp sub wireless (dibaca slave tiap 0,5 dtk, dikirim lewat STATUS; firmware slave ≥ 2.9.15)
        s.wsub?.peers?.filter { it.link }?.forEach { p ->
            Text("Sub wireless (slot ${p.slot})", style = MaterialTheme.typography.labelLarge)
            if (p.flt < 0) {
                Text(
                    "Fault amp sub tidak dilaporkan — firmware slave lama (butuh 2.9.15).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val on = p.fltKeys
                FaultGrid { it in on }
                if (p.fcnt > 0) {
                    Text(
                        "Kejadian fault sejak boot slave: ${p.fcnt}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (s.faultsReadMs == 0L) {
            Text(
                "Fault chip satelit belum pernah dibaca — ketuk \"Baca fault\" atau aktifkan pantau otomatis. Grid di atas belum menunjukkan kondisi nyata.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnAmber,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.readFaults() }) { Text("Baca fault") }
            OutlinedButton(onClick = { vm.ping() }) { Text(if (s.rttMs >= 0) "Ping ${s.rttMs} ms" else "Ping") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pantau fault otomatis (tiap 5 dtk)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Mencatat durasi fault satelit di riwayat. Aktif selama tab Monitor terbuka.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.faultAuto, onCheckedChange = { vm.setFaultAuto(it) })
        }
        Text(
            "Membaca fault akan menghapus register fault di chip satelit (perilaku firmware). Amp sub wireless dibaca & di-clear otomatis oleh slave. Kejadian dicatat di riwayat di bawah.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(
        "Riwayat fault",
        trailing = { OutlinedButton(onClick = { vm.clearFaultHistory() }) { Text("Bersihkan") } },
    ) {
        if (s.faultIncidents.isEmpty()) {
            Text("Belum ada fault tercatat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val nowMs = System.currentTimeMillis()
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val dayFmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)
            val sorted = s.faultIncidents.sortedByDescending { it.lastSeenMs }
            val activeN = sorted.count { it.active }
            val last = sorted.first()
            Text(
                "${sorted.size} kejadian · " + (if (activeN > 0) "$activeN masih aktif · " else "") +
                    "terakhir ${FaultIncidents.formatAgo(nowMs - last.lastSeenMs)}",
                style = MaterialTheme.typography.labelLarge,
            )
            sorted.take(30).forEach { e ->
                val sameDay = nowMs - e.startMs < 12 * 3_600_000L
                val f = if (sameDay) fmt else dayFmt
                val color = when {
                    e.active -> BadRed
                    FaultIncidents.isCritical(e.key) -> WarnAmber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "${FaultIncidents.keyLabel(e.key)} · ${FaultIncidents.sourceLabel(e.source)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                    val range = if (e.active) "${f.format(Date(e.startMs))} → masih aktif"
                    else "${f.format(Date(e.startMs))} → ${f.format(Date(e.endMs ?: e.lastSeenMs))}"
                    Text(
                        range + " · ≈ ${FaultIncidents.formatDuration(e.durationMs(nowMs))}" +
                            (if (e.count > 1) " · kambuh ${e.count - 1}×" else "") +
                            (if (e.truncated) " · data terputus" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sorted.size > 30) {
                Text("+${sorted.size - 30} kejadian lebih lama (ada di laporan diagnosa).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Durasi = selisih pembacaan pertama dan terakhir yang melaporkan fault (perkiraan; resolusi ≈ 2 dtk untuk sub wireless, 5 dtk untuk satelit dengan pantau otomatis). " +
                "Fault yang kambuh dalam 6 dtk digabung. Riwayat disimpan di perangkat.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
