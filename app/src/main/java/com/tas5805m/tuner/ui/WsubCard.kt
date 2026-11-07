package com.tas5805m.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.TunerState
import kotlinx.coroutines.delay

/** Status + pairing multi-sub wireless (hingga 4 slave). Firmware ≥ 2.9; multi ≥ 3.1. */
@Composable
fun WsubCard(s: TunerState, vm: TunerViewModel) {
    val w = s.wsub ?: return
    LaunchedEffect(Unit) {
        while (true) {
            vm.requestStatus()
            vm.requestWlat()
            delay(2000)
        }
    }
    SectionCard("Sub wireless (multi)") {
        Text(
            "${w.onlineCount}/${w.peers.size} online · slot ${w.peers.size}/${w.maxPeers}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (w.link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (w.fallback) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Fallback stereo aktif: sub hilang ≥ 6 dtk, satelit sementara full-range. " +
                        "Crossover (HPF) dipasang lagi otomatis setelah link stabil 2 dtk.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        if (w.peers.isEmpty()) {
            Text(
                "Belum ada slave terpasang",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            w.peers.forEach { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Slot ${p.slot} · ${p.mac}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            val st = when {
                                p.link && p.hiz -> "Terhubung · standby (Hi-Z)"
                                p.link -> "Terhubung · buffer ${p.fillMs} ms · ${"%.1f".format(p.rate / 1000f)} kHz · und ${p.und} · gap ${p.gap}"
                                else -> "Tidak terhubung"
                            }
                            Text(st, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.wsubUnpair(p.mac) }) { Text("Lepas") }
                    }
                }
            }
        }

        if (s.hasWlat) {
            LabeledSlider(
                "Penyelarasan waktu sub", "%+.1f ms".format(s.wtrimMs),
                s.wtrimMs, -20f..50f, steps = 139,
            ) { vm.setWTrim(it) }
            if (s.wlatSatMs > 0f && s.wlatSubMs.isNotEmpty()) {
                val sub = s.wlatSubMs.average().toFloat()
                Text(
                    "Latensi: satelit %.1f ms · sub ≈ %.1f ms (selisih %+.1f ms)".format(s.wlatSatMs, sub, sub - s.wlatSatMs),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "Sub otomatis disejajarkan dengan jalur satelit. Geser untuk menyesuaikan jarak speaker " +
                    "(positif = sub lebih lambat). Angka latensi adalah estimasi dari model dan isi buffer, bukan " +
                    "pengukuran akustik. Jika jaringan radio ramai, buffer sub naik otomatis dan selisih bisa positif.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { vm.wsubScan() }, enabled = !w.scan) {
                Text(if (w.scan) "Mencari…" else "Cari slave")
            }
            if (w.peers.isNotEmpty()) {
                TextButton(onClick = { vm.wsubUnpairAll() }) { Text("Lepas semua") }
            }
        }

        val paired = w.peers.map { it.mac.uppercase() }.toSet()
        val candidates = w.found.filter { it.uppercase() !in paired }
        if (candidates.isEmpty() && w.scan) {
            Text("Memindai HELLO…", style = MaterialTheme.typography.bodySmall)
        }
        candidates.forEach { mac ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(mac, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { vm.wsubPair(mac) },
                    enabled = w.canAdd,
                ) {
                    Text(if (w.canAdd) "Pasangkan" else "Penuh")
                }
            }
        }
        if (!w.canAdd) {
            Text(
                "Maksimal ${w.maxPeers} sub. Lepas salah satu untuk menambah.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Hingga ${w.maxPeers} slave. Yang belum pair menyiarkan HELLO tiap detik. " +
                "Firmware multi-sub: status WSUB peers=… atau baris WSUBP per slot. " +
                "Firmware lama (1 peer) tetap didukung. Tahan BOOT 3 dtk di slave untuk unpair lokal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
