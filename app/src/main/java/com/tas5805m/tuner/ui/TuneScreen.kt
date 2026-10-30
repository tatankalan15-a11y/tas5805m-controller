package com.tas5805m.tuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.AmpState
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.TunerState
import kotlin.math.roundToInt

@Composable
fun TuneScreen(s: TunerState, vm: TunerViewModel) {
    var pending by remember { mutableStateOf<AudioMode?>(null) }

    // Ringkasan status
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("Amp: ${s.amp.label}") })
        AssistChip(onClick = {}, label = { Text(if (s.eq.enabled) "EQ aktif" else "EQ mati") })
        AssistChip(onClick = {}, label = { Text("Gain %.1f dB".format(s.gainDb)) })
        if (s.mode.isTwoOne) AssistChip(onClick = {}, label = { Text("Xover ${s.xover} Hz") })
        AssistChip(onClick = { vm.ping() }, label = { Text(if (s.rttMs >= 0) "Ping ${s.rttMs} ms" else "Ping") })
    }

    SectionCard("Mode audio") {
        AudioMode.entries.forEach { m ->
            val sel = m == s.mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { if (!sel) pending = m },
                shape = RoundedCornerShape(18.dp),
                border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(selected = sel, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(m.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text("env: ${m.env}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    SectionCard("Volume") {
        LabeledSlider("Master", "${s.master}%", s.master.toFloat(), 0f..100f) { vm.setMaster(it.roundToInt()) }
        LabeledSlider(
            "Gain analog (headroom)", "%.1f dB".format(s.gainDb),
            s.gainCode.toFloat(), 0f..31f, steps = 30,
        ) { vm.setGainCode(it.roundToInt()) }
        Text(
            "Gain analog 0 = 0 dB (paling keras), 31 = −15,5 dB. Naikkan gain hanya jika speaker/PSU kuat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Status amplifier") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmpState.entries.forEach { a ->
                FilterChip(selected = s.amp == a, onClick = { vm.setAmp(a) }, label = { Text(a.label) })
            }
        }
        Text(
            "Play = normal · Mute = senyap cepat · Hi-Z = output dilepas · Sleep/Deep sleep = hemat daya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Auto-standby") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Mati", 5 to "5 mnt", 10 to "10 mnt", 30 to "30 mnt", 60 to "60 mnt").forEach { (m, t) ->
                FilterChip(selected = s.standbyMin == m, onClick = { vm.setStandby(m) }, label = { Text(t) })
            }
        }
        Text(
            if (s.standbyActive) "Sedang standby (Hi-Z) — audio berikutnya membangunkan amp otomatis."
            else "Bila tidak ada audio Bluetooth sebesar waktu ini, amp pindah ke Hi-Z. " +
                "Audio berikutnya membangunkannya kembali (awal lagu bisa tertunda sepersekian detik).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (s.hasV3) {
        SectionCard("Simpan di perangkat") {
            Text(
                if (s.saved == true) "Setelan tersimpan di ESP32 dan dipulihkan otomatis saat boot."
                else "Belum ada setelan tersimpan. Setelan sekarang hilang saat ESP32 restart.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { vm.saveToDevice() }) { Text("Simpan sekarang") }
                OutlinedButton(onClick = { vm.eraseSaved() }, enabled = s.saved == true) { Text("Hapus") }
                OutlinedButton(onClick = { vm.rebootDevice() }) { Text("Reboot ESP32") }
            }
            Text(
                "Yang disimpan: volume, gain, crossover, level sat/sub, EQ, mixer, clipper. Hanya dipulihkan bila env firmware sama.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (!s.hasExt) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Firmware versi dasar terdeteksi", fontWeight = FontWeight.SemiBold)
                Text(
                    "Kontrol dasar tetap berfungsi. Untuk EQ per-channel, meter level, readback DAC/modulasi/clipper, dan simpan setelan, flash firmware dari folder firmware/ di repo ini (env yang sama dengan mode Anda).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    pending?.let { m ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Ganti ke ${m.title}?") },
            text = {
                Text(
                    "Mode mengubah routing DSP (mixer, filter, level). Wiring dan PDN tetap ditentukan firmware env \"${m.env}\".\n\n" +
                        if (m.isTwoChip) "Mode ini butuh TAS5805M kedua di alamat 0x2E dan PDN2 (GPIO25)." +
                            (if (m == AudioMode.THREE_ONE) " Chip 2 harus BTL (OUT L = center, OUT R = sub)." else "")
                        else if (m == AudioMode.WIRELESS) "Butuh firmware env wireless_master dan board slave (esp32dev_sub_slave). Pasangkan di tab Output → Sub wireless."
                        else "Pastikan speaker terpasang sesuai mode ini.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.setMode(m); pending = null }) { Text("Ganti") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } },
        )
    }
}
