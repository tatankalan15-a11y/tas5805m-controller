package com.tas5805m.tuner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
                "Crossover memakai biquad band EQ 20/32/50 Hz. Di mode 2.1 band itu dikunci agar tidak tertimpa EQ.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ============================================================
        // LEVEL OUTPUT + BASS BALANCE
        // ============================================================
        SectionCard("Level output") {
            LabeledSlider("Satelit", "${s.sat}%", s.sat.toFloat(), 0f..100f) { vm.setSat(it.roundToInt()) }
            LabeledSlider("Subwoofer", "${s.sub}%", s.sub.toFloat(), 0f..100f) { vm.setSub(it.roundToInt()) }

            // ----- Bass Balance (sat vs sub) -----
            // 0 = lebih banyak satelit, 50 = seimbang, 100 = lebih banyak sub
            val balance = ((s.sub - s.sat + 100) / 2f).coerceIn(0f, 100f)
            LabeledSlider(
                "Bass Balance",
                when {
                    balance < 40 -> "Lebih satelit"
                    balance > 60 -> "Lebih sub"
                    else -> "Seimbang"
                } + " (${balance.roundToInt()})",
                balance,
                0f..100f,
            ) { v ->
                val center = 90
                val delta = ((v - 50f) / 50f * 20f).roundToInt()   // ±20
                val newSat = (center - delta).coerceIn(50, 100)
                val newSub = (center + delta).coerceIn(50, 100)
                vm.setSat(newSat)
                vm.setSub(newSub)
            }

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

    // ============================================================
    // MIXER MATRIX + CROSSFEED
    // ============================================================
    SectionCard("Mixer matrix / Crossfeed") {

        val crossfeed = ((s.mix.getOrElse(1) { 0f } + s.mix.getOrElse(2) { 0f }) / 2f).coerceIn(0f, 1f)

        LabeledSlider(
            "Crossfeed",
            "${(crossfeed * 100).roundToInt()}%",
            crossfeed,
            0f..1f,
        ) { amount ->
            val main = (1f - amount * 0.25f).coerceIn(0.75f, 1f)
            vm.setMixerAll(listOf(main, amount, amount, main))
        }

        Text(
            "0% = stereo murni · 25% = ringan · 40% = sedang · 60%+ = kuat. Volume otomatis dikompensasi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Preset cepat
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { vm.setMixerAll(listOf(1f, 0f, 0f, 1f)) }, label = { Text("Stereo") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.95f, 0.25f, 0.25f, 0.95f)) }, label = { Text("Ringan") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.90f, 0.40f, 0.40f, 0.90f)) }, label = { Text("Sedang") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.85f, 0.60f, 0.60f, 0.85f)) }, label = { Text("Kuat") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.92f, 0.45f, 0.30f, 0.92f)) }, label = { Text("Asimetris") })
            AssistChip(onClick = { vm.setMixerAll(listOf(0.5f, 0.5f, 0.5f, 0.5f)) }, label = { Text("Mono") })
        }

        // 4 slider manual
        val names = listOf("L → L", "L → R", "R → L", "R → R")
        for (i in 0 until 4) {
            LabeledSlider(
                names[i],
                "%.2f".format(s.mix.getOrElse(i) { 0f }),
                s.mix.getOrElse(i) { 0f },
                0f..2f,
                enabled = true,
            ) { vm.setMixer(i, it) }
        }

        if (s.hasV3) {
            Text("Mode mixer (bawaan chip)", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "stereo" to "Stereo",
                    "inv" to "Inverse",
                    "mono" to "Mono",
                    "left" to "Left",
                    "right" to "Right"
                ).forEach { (k, t) ->
                    AssistChip(onClick = { vm.setMixerMode(k) }, label = { Text(t) })
                }
            }
        }

        Text(
            if (s.mode.isTwoOne)
                "Mode 2.1: mixer terbuka untuk imaging/crossfeed. Crossover tetap dikunci."
            else
                "Nilai 1,00 = unity. Maksimum 2,00 (+6 dB).",
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
