package com.tas5805m.tuner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.EQ_BANDS
import com.tas5805m.tuner.data.EQ_FREQS
import com.tas5805m.tuner.data.FactoryPresets
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.TunerState
import com.tas5805m.tuner.data.XOVER_BANDS
import com.tas5805m.tuner.data.freqLabel
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun EqScreen(s: TunerState, vm: TunerViewModel) {
    var chIdx by rememberSaveable { mutableStateOf(0) } // 0 = L+R, 1 = L, 2 = R
    var saveDialog by remember { mutableStateOf(false) }

    val locked = s.locked21
    val biamp = s.eq.biamp && s.hasExt
    val ch: Char = when {
        !biamp -> 'b'
        chIdx == 1 -> 'l'
        chIdx == 2 -> 'r'
        else -> 'b'
    }
    val shown = if (ch == 'r') s.eq.right else s.eq.left

    if (!s.hasExt) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text(
                "Firmware dasar: EQ hanya mode linked (diatur lewat kanal kiri) dan tidak bisa dibaca balik. " +
                    "Flash firmware/ dari repo ini untuk EQ per-channel.",
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (locked) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                "Mode 2.1: band 20/32/50 Hz dicadangkan untuk crossover (satelit HPF / sub LPF) dan EQ tidak bisa " +
                    "dimatikan. Preset dan Reset hanya mengubah band 80 Hz ke atas. Ini mencegah suara bocor " +
                    "(bass ke satelit atau treble ke sub).",
                Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    SectionCard(
        "Equalizer 15 band",
        trailing = { Switch(checked = s.eq.enabled || locked, enabled = !locked, onCheckedChange = { vm.setEqEnabled(it) }) },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !biamp, onClick = { vm.setEqBiamp(false) }, enabled = s.hasExt && !locked, label = { Text("Linked  L = R") })
            FilterChip(selected = biamp, onClick = { vm.setEqBiamp(true) }, enabled = s.hasExt && !locked, label = { Text("Biamp  L / R terpisah") })
        }
        if (biamp) {
            val names = when (s.mode) {
                AudioMode.ONE_CHIP_21 -> listOf("Sat + Sub", "Satelit (L)", "Sub (R)")
                AudioMode.THREE_ONE -> listOf("Semua satelit", "Sat L", "Sat R")
                else -> listOf("L + R", "L", "R")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, t ->
                    FilterChip(selected = chIdx == i, onClick = { chIdx = i }, label = { Text(t) })
                }
            }
        }
        EqCurve(shown, enabled = s.eq.enabled || locked)
        Text(
            "Kurva adalah perkiraan visual dari gain tiap band, bukan hasil pengukuran.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Band (−15 … +15 dB)") {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (i in 0 until EQ_BANDS) {
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val g = shown[i]
                    Text(
                        (if (g > 0) "+" else "") + g,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (g == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    VerticalGain(
                        value = g,
                        onChange = { vm.setEqBand(ch, i, it) },
                        modifier = Modifier.width(48.dp).height(210.dp),
                        enabled = s.eq.enabled && !(locked && i < XOVER_BANDS) && !(s.delayBandsBusy && i >= 12),
                    )
                    Text(freqLabel(EQ_FREQS[i]), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.resetEq() }) { Text("Reset flat") }
            Button(onClick = { saveDialog = true }) { Text("Simpan preset") }
        }
        Text(
            "Tip: boost besar di bass menaikkan risiko clipping. Turunkan master bila terdengar pecah.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (s.hasPeq) {
        var peqBand by rememberSaveable { mutableIntStateOf(if (locked) XOVER_BANDS else 0) }
        var peqChannel by rememberSaveable { mutableStateOf('b') }
        var logFreq by rememberSaveable { mutableStateOf(log10(1000f)) }
        var peqQ by rememberSaveable { mutableStateOf(1f) }
        var peqGain by rememberSaveable { mutableStateOf(0f) }
        LaunchedEffect(locked) { if (locked && peqBand < XOVER_BANDS) peqBand = XOVER_BANDS }
        val freqHz = 10f.pow(logFreq).coerceIn(20f, 20000f)
        val delayBandsReserved = s.delayBandsBusy
        val peqBandEnd = if (delayBandsReserved) 12 else EQ_BANDS
        LaunchedEffect(delayBandsReserved, locked) {
            val minBand = if (locked) XOVER_BANDS else 0
            if (peqBand < minBand || peqBand >= peqBandEnd) peqBand = minBand
        }
        SectionCard("Parametric EQ (PEQ)") {
            Text("Pilih band biquad yang akan diatur", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (locked) XOVER_BANDS until peqBandEnd else 0 until peqBandEnd).forEach { idx ->
                    FilterChip(selected = peqBand == idx, onClick = { peqBand = idx }, label = { Text("${idx + 1}") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = peqChannel == 'b', onClick = { peqChannel = 'b' }, label = { Text("L + R") })
                FilterChip(selected = peqChannel == 'l', onClick = { peqChannel = 'l' }, label = { Text("Kiri") })
                FilterChip(selected = peqChannel == 'r', onClick = { peqChannel = 'r' }, label = { Text("Kanan") })
            }
            LabeledSlider("Frekuensi tengah", "${freqHz.toInt()} Hz", logFreq, log10(20f)..log10(20000f), steps = 0) {
                logFreq = it
            }
            LabeledSlider("Q (lebar filter)", "${"%.2f".format(peqQ)}", peqQ, 0.1f..20f, steps = 0) { peqQ = it }
            LabeledSlider("Gain", "${"%+.1f".format(peqGain)} dB", peqGain, -15f..15f, steps = 59) { peqGain = it }
            Button(onClick = { vm.setPeq(peqChannel, peqBand, freqHz, peqQ, peqGain) }, enabled = s.synced) {
                Text("Terapkan PEQ band ${peqBand + 1}")
            }
            OutlinedButton(onClick = { vm.saveToDevice() }, enabled = s.synced) { Text("Simpan semua DSP ke perangkat") }
            Text(
                "PEQ disimpan ke perangkat melalui tombol Simpan di bawah / perintah Save. Parameter frekuensi, Q, dan gain akan dipulihkan saat boot. Band 1–3 dikunci ketika crossover 2.1 aktif; band 13–15 dipakai bersama oleh time alignment selama delay aktif. Menggeser band yang sama di EQ grafis akan menggantikan PEQ band itu (frekuensi dan Q-nya dibuang).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard("Preset EQ cepat") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactoryPresets.all.forEach { p ->
                AssistChip(onClick = { vm.applyPreset(p) }, label = { Text(p.name) })
            }
        }
    }

    if (saveDialog) {
        NameDialog(
            title = "Simpan preset",
            initial = "",
            onDismiss = { saveDialog = false },
            onOk = { vm.saveCurrentAs(it); saveDialog = false },
        )
    }
}
