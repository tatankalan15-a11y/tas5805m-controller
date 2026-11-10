package com.tas5805m.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.SubVoice
import com.tas5805m.tuner.data.TunerState
import java.util.Locale

/** Preset awal (konservatif) untuk driver 6,5" di box bawaan ATP3 (tuning port ≈ 45 Hz, perkiraan). */
private val PRESET_NATURAL = SubVoice(on = true, hpf = 30, pkHz = 55, pkDb10 = 30, pkQ10 = 10, harm = 0, limit = 90)
private val PRESET_BERBOBOT = SubVoice(on = true, hpf = 32, pkHz = 55, pkDb10 = 40, pkQ10 = 10, harm = 40, limit = 85)
private val PRESET_MEGAH = SubVoice(on = true, hpf = 35, pkHz = 50, pkDb10 = 50, pkQ10 = 9, harm = 70, limit = 80)

/** Voicing sub wireless: HPF subsonic, boost parametrik, harmonik psikoakustik, limiter. Firmware ≥ 2.9.14. */
@Composable
fun SubVoiceCard(s: TunerState, vm: TunerViewModel) {
    if (s.wsub == null) return
    val v = s.subVoice
    SectionCard("Karakter sub (voicing)") {
        if (v == null) {
            Text(
                "Menunggu status dari master… Butuh firmware master & slave 2.9.14.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Aktifkan voicing", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = v.on, onCheckedChange = { vm.setSubVoice(v.copy(on = it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = { vm.setSubVoice(PRESET_NATURAL) }, label = { Text("Natural") })
            FilterChip(selected = false, onClick = { vm.setSubVoice(PRESET_BERBOBOT) }, label = { Text("Berbobot") })
            FilterChip(selected = false, onClick = { vm.setSubVoice(PRESET_MEGAH) }, label = { Text("Megah") })
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledSlider("Subsonic HPF (lindungi driver)", "${v.hpf} Hz", v.hpf.toFloat(), 20f..60f, enabled = v.on) {
                vm.setSubVoice(v.copy(hpf = it.toInt()))
            }
            LabeledSlider("Boost bobot", String.format(Locale.US, "%+.1f dB", v.pkDb10 / 10f), v.pkDb10.toFloat(), -60f..90f, enabled = v.on) {
                vm.setSubVoice(v.copy(pkDb10 = (Math.round(it / 5f) * 5)))
            }
            LabeledSlider("Frekuensi boost", "${v.pkHz} Hz", v.pkHz.toFloat(), 30f..120f, enabled = v.on) {
                vm.setSubVoice(v.copy(pkHz = it.toInt()))
            }
            LabeledSlider("Harmonik (bass terasa lebih dalam)", "${v.harm}%", v.harm.toFloat(), 0f..100f, enabled = v.on) {
                vm.setSubVoice(v.copy(harm = it.toInt()))
            }
            LabeledSlider("Limiter (batas puncak)", "${v.limit}%", v.limit.toFloat(), 50f..100f, enabled = v.on) {
                vm.setSubVoice(v.copy(limit = it.toInt()))
            }
        }
        Text(
            "Naikkan pelan-pelan sambil memutar musik bass berat. Kalau terdengar bunyi ketukan, desis dari port, atau distorsi, " +
                "turunkan boost atau naikkan HPF. HPF membuang frekuensi di bawah kemampuan driver; harmonik menggantikannya " +
                "agar telinga tetap mendengar bass dalam (ilusi psikoakustik, bukan output 30 Hz sungguhan).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
