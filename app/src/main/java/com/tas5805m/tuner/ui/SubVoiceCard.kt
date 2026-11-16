package com.tas5805m.tuner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.SubVoice
import com.tas5805m.tuner.data.TunerState
import java.util.Locale

/**
 * Voicing = ukuran driver × karakter. Angka 6,5" identik dengan preset lama (Natural/Berbobot/Megah, box ATP3, port ≈ 45 Hz).
 * Ukuran lain adalah TITIK AWAL PERKIRAAN (bukan hasil ukur): driver kecil tak sanggup bass dalam, jadi HPF subsonic dinaikkan,
 * boost dikecilkan (langkah cone terbatas), limiter diturunkan. Sesuaikan dengan Fs/Qts dan box driver yang dipakai.
 */
private data class DriverSize(
    val label: String,
    val hpf: Int,        // Hz, dasar karakter "Natural"
    val pkHz: Int,       // Hz, frekuensi boost
    val boostPct: Int,   // % dari boost karakter
    val harmPct: Int,    // % dari harmonik karakter
    val limitDrop: Int,  // poin persen dikurangi dari limiter karakter
    val hint: String,
)

private enum class SubCharacter(val label: String, val hpfX: Float, val pkX: Float, val db10: Int, val q10: Int, val harm: Int, val limit: Int) {
    NATURAL("Natural", 1.00f, 1.00f, 30, 10, 0, 90),
    BERBOBOT("Berbobot", 1.07f, 1.00f, 40, 10, 40, 85),
    MEGAH("Megah", 1.17f, 0.91f, 50, 9, 70, 80),
}

private val DRIVER_SIZES = listOf(
    DriverSize("3\"", 85, 95, 30, 60, 10, "3\": tak sanggup di bawah ±90 Hz. Pakai crossover 150 Hz; fungsinya lebih ke bass-satelit. Jaga volume (daya & langkah cone kecil)."),
    DriverSize("4\"", 70, 80, 45, 70, 8, "4\": batas bawah ±70 Hz. Crossover 150 Hz disarankan; harmonik di atas crossover akan terpotong."),
    DriverSize("5\"", 50, 65, 70, 90, 4, "5\": batas bawah ±50 Hz. Boost dibatasi karena langkah cone terbatas."),
    DriverSize("6–6,5\"", 30, 55, 100, 100, 0, "6–6,5\": preset asli (box ATP3, port ≈ 45 Hz, perkiraan)."),
    DriverSize("8\"", 28, 50, 100, 100, 0, "8\": titik awal; turunkan HPF jika box dan driver mampu."),
    DriverSize("10\"", 25, 45, 100, 100, 0, "10\": titik awal; perhatikan desis port dan ketukan cone pada volume tinggi."),
    DriverSize("12\"", 22, 40, 100, 100, 0, "12\": titik awal; HPF rendah hanya aman bila box dan port memang dirancang untuk itu."),
)
private const val DEFAULT_SIZE_IDX = 3   /* 6–6,5" */

private fun voiceFor(d: DriverSize, c: SubCharacter): SubVoice {
    val hpf = Math.round(d.hpf * c.hpfX).coerceIn(20, 120)
    /* Boost di bawah HPF akan terpotong filter → frekuensi boost minimal 1,3× HPF (tidak mengubah angka 6,5"). */
    val pk = maxOf(Math.round(d.pkHz * c.pkX), Math.round(hpf * 1.3f)).coerceIn(30, 150)
    return SubVoice(
        on = true,
        hpf = hpf,
        pkHz = pk,
        pkDb10 = Math.round(c.db10 * d.boostPct / 100f / 5f) * 5,
        pkQ10 = c.q10,
        harm = Math.round(c.harm * d.harmPct / 100f).coerceIn(0, 100),
        limit = (c.limit - d.limitDrop).coerceIn(50, 100),
    )
}

/** Voicing sub wireless: HPF subsonic, boost parametrik, harmonik psikoakustik, limiter. Firmware ≥ 2.9.14. */
@Composable
fun SubVoiceCard(s: TunerState, vm: TunerViewModel) {
    var sizeIdx by remember { mutableStateOf(DEFAULT_SIZE_IDX) }
    var charSel by remember { mutableStateOf(SubCharacter.NATURAL) }
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
        val size = DRIVER_SIZES[sizeIdx]
        Text("Ukuran driver", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DRIVER_SIZES.forEachIndexed { i, d ->
                FilterChip(
                    selected = sizeIdx == i,
                    onClick = { sizeIdx = i; vm.setSubVoice(voiceFor(d, charSel)) },
                    label = { Text(d.label) },
                )
            }
        }
        Text(size.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Karakter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubCharacter.values().forEach { c ->
                FilterChip(
                    selected = charSel == c && v == voiceFor(size, c),
                    onClick = { charSel = c; vm.setSubVoice(voiceFor(size, c)) },
                    label = { Text(c.label) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledSlider("Subsonic HPF (lindungi driver)", "${v.hpf} Hz", v.hpf.toFloat(), 20f..120f, enabled = v.on) {
                vm.setSubVoice(v.copy(hpf = it.toInt()))
            }
            LabeledSlider("Boost bobot", String.format(Locale.US, "%+.1f dB", v.pkDb10 / 10f), v.pkDb10.toFloat(), -60f..90f, enabled = v.on) {
                vm.setSubVoice(v.copy(pkDb10 = (Math.round(it / 5f) * 5)))
            }
            LabeledSlider("Frekuensi boost", "${v.pkHz} Hz", v.pkHz.toFloat(), 30f..150f, enabled = v.on) {
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
                "agar telinga tetap mendengar bass dalam (ilusi psikoakustik, bukan output 30 Hz sungguhan). Angka per ukuran driver adalah " +
                "titik awal perkiraan; HPF di atas 60 Hz butuh firmware master & slave versi terbaru.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
