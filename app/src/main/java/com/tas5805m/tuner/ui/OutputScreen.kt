package com.tas5805m.tuner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.bassBalanceOf
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.TunerState
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
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
                "Crossover memakai biquad band EQ 20/32/50 Hz. Di mode 2.1 band itu dikunci agar tidak tertimpa EQ.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Level output") {
            LabeledSlider("Satelit", "${s.sat}%", s.sat.toFloat(), 0f..100f) { vm.setSat(it.roundToInt()) }
            LabeledSlider("Subwoofer", "${s.sub}%", s.sub.toFloat(), 0f..100f) { vm.setSub(it.roundToInt()) }

            val balance = bassBalanceOf(s.sat, s.sub)
            LabeledSlider(
                "Bass Balance",
                "Sat ${s.sat}% · Sub ${s.sub}%",
                balance,
                0f..1f,
            ) { vm.setBassBalance(it) }
            Text(
                "Geser ke kiri = lebih satelit · ke kanan = lebih sub. Cocok untuk menyeimbangkan Samsung + ATP3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (s.mode == AudioMode.TWO_CHIP_21) "Mode 2-chip: level = volume digital tiap chip × master."
                else "Mode 1-chip: level diatur lewat penguat mixer (mono −6 dB × level).",
                style = MaterialTheme.typography.labelSmall,
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

    // Mode 2 (1-chip 2.1): mixer dipakai routing sat/sub — UI crossfeed dinonaktifkan.
    // Mode 1 (2-chip): crossfeed hanya ke chip satelit (firmware). Mode 0: stereo penuh.
    SectionCard("Mixer matrix / Crossfeed") {
        if (s.mode == AudioMode.ONE_CHIP_21) {
            Text(
                "Mode 2 (1-chip 2.1): crossfeed & matrix mixer dinonaktifkan. " +
                    "Matriks dipakai firmware untuk routing satelit (L) / sub (R).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // amount 0..0.5 → main = 1 − amount  → 50% = mono, loudness tengah konstan
            val crossfeed = ((s.mix.getOrElse(1) { 0f } + s.mix.getOrElse(2) { 0f }) / 2f).coerceIn(0f, 0.5f)

            LabeledSlider(
                "Crossfeed",
                "${(crossfeed * 100).roundToInt()}%",
                crossfeed,
                0f..0.5f,
            ) { amount ->
                val a = amount.coerceIn(0f, 0.5f)
                val main = 1f - a
                vm.setMixerAll(listOf(main, a, a, main))
            }
            Text(
                "0% = stereo · 25% ringan · 40% sedang · 50% = mono. " +
                    "main = 1 − amount → volume sinyal tengah konstan. " +
                    if (s.mode == AudioMode.TWO_CHIP_21)
                        "Mode 1: hanya chip satelit (0x2D); sub tetap mono LPF."
                    else
                        "Mode stereo: matriks ke kedua channel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Preset (ketuk = apply · tahan = apply + save NVS)", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                data class Mx(val label: String, val m: List<Float>)
                // main = 1 − amount, amount ≤ 0.5
                listOf(
                    Mx("Stereo", listOf(1f, 0f, 0f, 1f)),
                    Mx("Ringan", listOf(0.75f, 0.25f, 0.25f, 0.75f)),
                    Mx("Sedang", listOf(0.60f, 0.40f, 0.40f, 0.60f)),
                    Mx("Kuat", listOf(0.50f, 0.50f, 0.50f, 0.50f)),
                    Mx("Asimetris", listOf(0.70f, 0.35f, 0.25f, 0.70f)),
                    Mx("Mono", listOf(0.50f, 0.50f, 0.50f, 0.50f)),
                ).forEach { p ->
                    AssistChip(
                        onClick = { vm.setMixerAll(p.m) },
                        modifier = Modifier.combinedClickable(
                            onClick = { vm.setMixerAll(p.m) },
                            onLongClick = { vm.setMixerAllAndSave(p.m) },
                        ),
                        label = { Text(p.label) },
                    )
                }
            }

            var showManual by remember { mutableStateOf(false) }
            TextButton(onClick = { showManual = !showManual }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showManual) "Sembunyikan matrix manual ▲" else "Tampilkan matrix manual ▼")
            }
            AnimatedVisibility(visible = showManual) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val names = listOf("L → L", "L → R", "R → L", "R → R")
                    for (i in 0 until 4) {
                        LabeledSlider(
                            names[i],
                            "%.2f".format(s.mix.getOrElse(i) { 0f }),
                            s.mix.getOrElse(i) { 0f },
                            0f..1f,
                            enabled = true,
                        ) { vm.setMixer(i, it.coerceIn(0f, 1f)) }
                    }
                }
            }

            if (s.hasV3 && s.mode == AudioMode.STEREO) {
                Text("Mode mixer (bawaan chip)", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "stereo" to "Stereo",
                        "inv" to "Inverse",
                        "mono" to "Mono",
                        "left" to "Left",
                        "right" to "Right",
                    ).forEach { (k, t) ->
                        AssistChip(onClick = { vm.setMixerMode(k) }, label = { Text(t) })
                    }
                }
            }

            Text(
                if (s.mode == AudioMode.TWO_CHIP_21)
                    "Mode 1 (2-chip): crossfeed → chip 0x2D saja. Crossover band 0–2 tetap dikunci."
                else
                    "main + cross = 1 → loudness tengah konstan. 50% = mono sempurna.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
