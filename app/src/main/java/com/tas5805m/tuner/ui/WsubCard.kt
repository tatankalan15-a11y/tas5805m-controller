package com.tas5805m.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tas5805m.tuner.data.WsubState
import kotlinx.coroutines.delay

/** Status + pairing multi-sub wireless (hingga 4 slave). Firmware ≥ 2.9; multi ≥ 3.1. */
@Composable
fun WsubCard(s: TunerState, vm: TunerViewModel) {
    val w = s.wsub ?: return
    LaunchedEffect(Unit) {
        while (true) {
            vm.requestStatus()
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
                            if (p.link && p.rxAudio >= 0) {
                                Text(
                                    "Slave: rx audio ${p.rxAudio}/dtk · ctrl ${p.rxCtrl}/dtk · ditolak ${p.rej} · tx gagal ${p.stx}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { vm.wsubUnpair(p.mac) }) { Text("Lepas") }
                    }
                }
            }
        }

        if (w.diag) WsubDiag(w, vm)

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

/** Sinkron sub ↔ satelit (trim) + diagnostik link radio master. Firmware ≥ 2.9.8. */
@Composable
private fun WsubDiag(w: WsubState, vm: TunerViewModel) {
    Text("Sinkron sub ↔ satelit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Latensi sub ${w.lat} ms · trim ${if (w.trim > 0) "+" else ""}${w.trim} ms",
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(-5, -1, 1, 5).forEach { d ->
            OutlinedButton(onClick = { vm.wsubTrimBy(d) }) { Text(if (d > 0) "+$d" else "$d") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.wsubTrim(0) }) { Text("Trim 0") }
        TextButton(onClick = { vm.wsubDiagReset() }) { Text("Reset diagnostik") }
    }
    Text(
        "+ = sub lebih lambat. Putar klik/sweep bass, rekam satelit & sub, geser sampai puncaknya sejajar.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val total = w.ackOk + w.ackFail
    Text(
        "Radio master: ch ${w.ch} · ACK ${w.ackOk}/$total · tanpa ACK ${w.ackFail} · antrean penuh ${w.nomem} · gagal kirim ${w.txf} · error ${w.err}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val (text, bad) = wsubVerdict(w)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

/** Ringkasan penyebab dari penghitung master. Pair: (teks, bermasalah). */
private fun wsubVerdict(w: WsubState): Pair<String, Boolean> {
    val total = w.ackOk + w.ackFail
    return when {
        !w.link -> "Slave belum terhubung — cek pairing, channel (master & slave harus sama) dan jarak." to true
        w.nomem > 0L || w.err == "NO_MEM" ->
            "Antrean ESP-NOW penuh (radio tersita Bluetooth A2DP). Build master dengan WSUB_PKT_TX lebih besar atau WSUB_FAST_RATE=1." to true
        total >= 50L && w.ackFail * 10L > total ->
            "Lebih dari 10% paket tanpa ACK — dekatkan slave dan pastikan channel master = slave." to true
        total < 50L -> "Mengumpulkan data… putar musik beberapa detik." to false
        else -> "Link radio sehat." to false
    }
}
