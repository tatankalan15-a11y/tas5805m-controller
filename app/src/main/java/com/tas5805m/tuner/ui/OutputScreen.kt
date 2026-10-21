package com.tas5805m.tuner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.TunerState
import kotlin.math.roundToInt

@Composable
fun OutputScreen(s: TunerState, vm: TunerViewModel) {
    if (s.mode.isTwoOne) {
        val where = if (s.mode == AudioMode.TWO_CHIP_21)
            "Chip 0x2D: satelit L/R dengan HPF · Chip 0x2E: sub dengan LPF mono."
        else
            "OUT L = satelit (mono + HPF) · OUT R = sub (mono + LPF)."

        SectionCard("Crossover") {
            CrossoverPlot(s.xover)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("━ HPF satelit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                Text("━ LPF sub", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            LabeledSlider(
                "Frekuensi crossover", "${s.xover} Hz",
                s.xover.toFloat(), 60f..150f, steps = 8,
            ) { vm.setXover((it / 10f).roundToInt() * 10) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 80, 100, 120, 150).forEach { hz ->
                    FilterChip(selected = s.xover == hz, onClick = { vm.setXover(hz) }, label = { Text("$hz Hz") })
                }
            }
            Text(
                "$where Firmware memakai profil biquad 60–150 Hz dengan langkah 10 Hz. Grafik hanya ilustrasi orde-4.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { vm.reapplyMode() }) { Text("Terapkan ulang crossover") }
            if (s.eqpL != null && s.eqpR != null) {
                Text(
                    if (s.mode == AudioMode.TWO_CHIP_21 && s.eqp2L != null && s.eqp2R != null)
                        "Chip 1 (satelit): L=${profileName(s.eqpL)} · R=${profileName(s.eqpR)}\nChip 2 (sub): L=${profileName(s.eqp2L)} · R=${profileName(s.eqp2R)}"
                    else
                        "Profil filter aktif di chip: L=${profileName(s.eqpL)} · R=${profileName(s.eqpR)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Crossover memakai biquad band EQ 20/32/50 Hz. Di mode 2.1 band itu dikunci agar tidak tertimpa EQ; tombol di atas menulis ulang filter bila perlu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Level output") {
            LabeledSlider("Satelit", "${s.sat}%", s.sat.toFloat(), 0f..100f) { vm.setSat(it.roundToInt()) }
            LabeledSlider("Subwoofer", "${s.sub}%", s.sub.toFloat(), 0f..100f) { vm.setSub(it.roundToInt()) }
            Text(
                if (s.mode == AudioMode.TWO_CHIP_21) "Mode 2-chip: level = volume digital tiap chip × master."
                else "Mode 1-chip: level diatur lewat penguat mixer (mono −6 dB × level).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SectionCard("Mode stereo") {
            Text(
                "Mode stereo 1-chip tidak memakai crossover. Pilih mode 2.1 di tab Tune bila memakai subwoofer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionCard("Mixer matrix") {
        val names = listOf("L → L", "L → R", "R → L", "R → R")
        for (i in 0 until 4) {
            LabeledSlider(
                names[i], "%.2f".format(s.mix[i]), s.mix[i], 0f..2f,
                enabled = !s.locked21,
            ) { vm.setMixer(i, it) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.setMixerAll(listOf(1f, 0f, 0f, 1f)) }, enabled = !s.locked21, label = { Text("Stereo") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.5f, 0.5f, 0.5f, 0.5f)) }, enabled = !s.locked21, label = { Text("Mono") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0f, 1f, 1f, 0f)) }, enabled = !s.locked21, label = { Text("Tukar L/R") })
            AssistChip(onClick = { vm.setMixerAll(listOf(1f, 1f, 0f, 0f)) }, enabled = !s.locked21, label = { Text("Kiri saja") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0f, 0f, 1f, 1f)) }, enabled = !s.locked21, label = { Text("Kanan saja") })
        }
        if (s.hasV3 && !s.locked21) {
            Text("Mode mixer (bawaan chip)", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("stereo" to "Stereo", "inv" to "Inverse", "mono" to "Mono", "left" to "Left", "right" to "Right").forEach { (k, t) ->
                    AssistChip(onClick = { vm.setMixerMode(k) }, label = { Text(t) })
                }
            }
        }
        Text(
            if (s.mode.isTwoOne) "Di mode 2.1 mixer dikunci: routing satelit/sub diatur firmware lewat level satelit/sub. Ganti ke mode Stereo bila ingin mengubah mixer manual."
            else "Nilai 1,00 = unity. Maksimum 2,00 (+6 dB) — hati-hati clipping.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun profileName(n: Int?): String {
    val v = n ?: return "?"
    return when {
        v == 0 -> "FLAT"
        v in 1..10 -> "LPF ${50 + v * 10} Hz"
        v in 11..20 -> "HPF ${50 + (v - 10) * 10} Hz"
        else -> "#$v"
    }
}
