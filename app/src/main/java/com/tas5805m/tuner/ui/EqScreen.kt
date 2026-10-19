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
import com.tas5805m.tuner.data.TunerState
import com.tas5805m.tuner.data.freqLabel

@Composable
fun EqScreen(s: TunerState, vm: TunerViewModel) {
    var chIdx by rememberSaveable { mutableStateOf(0) } // 0 = L+R, 1 = L, 2 = R
    var saveDialog by remember { mutableStateOf(false) }

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

    SectionCard(
        "Equalizer 15 band",
        trailing = { Switch(checked = s.eq.enabled, onCheckedChange = { vm.setEqEnabled(it) }) },
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !biamp, onClick = { vm.setEqBiamp(false) }, enabled = s.hasExt, label = { Text("Linked  L = R") })
            FilterChip(selected = biamp, onClick = { vm.setEqBiamp(true) }, enabled = s.hasExt, label = { Text("Biamp  L / R terpisah") })
        }
        if (biamp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("L + R", "L", "R").forEachIndexed { i, t ->
                    FilterChip(selected = chIdx == i, onClick = { chIdx = i }, label = { Text(t) })
                }
            }
        }
        EqCurve(shown, enabled = s.eq.enabled)
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
                        enabled = s.eq.enabled,
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
