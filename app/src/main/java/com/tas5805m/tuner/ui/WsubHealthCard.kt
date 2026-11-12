package com.tas5805m.tuner.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.HealthLevel
import com.tas5805m.tuner.data.LatencyAdvisor
import com.tas5805m.tuner.data.TuningAdvice
import com.tas5805m.tuner.data.TuningKind
import com.tas5805m.tuner.data.TunerState
import com.tas5805m.tuner.data.WsubDiag
import com.tas5805m.tuner.data.WsubHealth
import com.tas5805m.tuner.data.WsubHealthEval
import com.tas5805m.tuner.data.WsubPeer
import com.tas5805m.tuner.data.WsubTrendPt

/** Kesehatan sub wireless: lencana per slave + alasan, tren (sparkline), histogram jitter, dan laporan diagnosa untuk dibagikan. */
@Composable
fun WsubHealthCard(s: TunerState, vm: TunerViewModel) {
    val w = s.wsub ?: return
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val now = System.currentTimeMillis()
    val healths = w.peers.map { WsubHealthEval.evaluate(it, s.wsubTrend[it.slot].orEmpty(), s.wsubLvl[it.slot], s.wsubSeen[it.slot], now, w.mfw) }
    // BAD > WARN > UNKNOWN > OK; kalau ada yang belum valid, ringkasan tidak boleh hijau
    val online = healths.filter { it.level != HealthLevel.OFF }
    val worst: HealthLevel? = when {
        online.any { it.level == HealthLevel.BAD } -> HealthLevel.BAD
        online.any { it.level == HealthLevel.WARN } -> HealthLevel.WARN
        online.any { it.level == HealthLevel.UNKNOWN } -> HealthLevel.UNKNOWN
        online.isNotEmpty() -> HealthLevel.OK
        else -> null
    }

    SectionCard(
        "Kesehatan sub wireless",
        trailing = {
            Text(
                when {
                    w.peers.isEmpty() -> "—"
                    worst == null -> "OFFLINE"
                    else -> when (worst) {
                        HealthLevel.BAD -> "BERMASALAH"
                        HealthLevel.WARN -> "WASPADA"
                        HealthLevel.UNKNOWN -> "BELUM VALID"
                        else -> "SEHAT"
                    }
                },
                color = if (worst == null) MaterialTheme.colorScheme.onSurfaceVariant else levelColor(worst),
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        if (w.peers.isEmpty()) {
            Text(
                "Belum ada sub wireless terpasang (tab Output → Sub wireless).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        w.peers.forEachIndexed { i, p ->
            val tr = s.wsubTrend[p.slot].orEmpty()
            PeerHealth(p, healths[i], tr, LatencyAdvisor.advise(p, tr, s.wsubTiming, now))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                clipboard.setText(AnnotatedString(vm.buildDiagSummary()))
                Toast.makeText(ctx, "Ringkasan disalin", Toast.LENGTH_SHORT).show()
            }) { Text("Ringkasan") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                clipboard.setText(AnnotatedString(vm.buildDiagReport()))
                Toast.makeText(ctx, "Laporan lengkap disalin", Toast.LENGTH_SHORT).show()
            }) { Text("Lengkap") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Diagnosa TAS5805M Tuner")
                    putExtra(Intent.EXTRA_TEXT, vm.buildDiagReport())
                }
                ctx.startActivity(Intent.createChooser(i, "Bagikan laporan diagnosa"))
            }) { Text("Bagikan") }
        }
        Text(
            "Sehat hanya bila status segar (< 6 dtk), fault amp, radio, dan tren ±1 menit semuanya valid; kalau tidak, hasilnya \"Belum valid\". Ambang menyesuaikan sample rate & ukuran paket slave. Ketuk slave untuk tren & jitter.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun levelColor(l: HealthLevel): Color = when (l) {
    HealthLevel.OK -> OkGreen
    HealthLevel.WARN -> WarnAmber
    HealthLevel.BAD -> BadRed
    HealthLevel.OFF, HealthLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun PeerHealth(p: WsubPeer, h: WsubHealth, trend: List<WsubTrendPt>, tuning: TuningAdvice) {
    var open by remember(p.slot) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { open = !open }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(levelColor(h.level), CircleShape))
            Text("Slot ${p.slot} · ${p.mac}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(h.title, color = levelColor(h.level), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
        if (h.coverage.isNotEmpty()) {
            Text(h.coverageText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        h.reasons.forEach { r ->
            Text(
                "• ${r.text}",
                style = MaterialTheme.typography.bodySmall,
                color = when (r.level) {
                    HealthLevel.BAD -> BadRed
                    HealthLevel.WARN -> WarnAmber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (open) {
            if (trend.size >= 2) {
                val maxFill = (trend.maxOf { it.fill }).coerceAtLeast(1).toFloat()
                TrendRow("Buffer (ms)", "0–${maxFill.toInt()}", trend.map { it.fill.toFloat() }, 0f, maxFill)
                if (trend.any { it.rx >= 0 }) {
                    TrendRow("Paket audio/dtk", "0–100", trend.map { it.rx.coerceAtLeast(0).toFloat() }, 0f, 100f)
                }
                if (trend.any { it.lvl != null }) {
                    TrendRow("Level sub (dB)", "−60–0", trend.map { (it.lvl ?: -600) / 10f }, -60f, 0f)
                }
                if (trend.any { it.rssi != 0 }) {
                    TrendRow("Sinyal RSSI (dBm)", "−100–−30", trend.map { (if (it.rssi == 0) -100 else it.rssi).toFloat() }, -100f, -30f)
                }
                Text(
                    "${trend.size} titik ≈ ${trend.size * 2} dtk terakhir",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Mengumpulkan tren…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tuning latensi: ${tuning.title}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = when (tuning.kind) {
                    TuningKind.OK -> OkGreen
                    TuningKind.INSUFFICIENT -> MaterialTheme.colorScheme.onSurfaceVariant
                    TuningKind.ADAPTING -> WarnAmber
                    TuningKind.SATURATED, TuningKind.RADIO -> BadRed
                })
            tuning.details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            p.diag?.let { JitterBlock(it) }
                ?: Text(
                    "Jitter: menunggu baris WSUBD dari master (firmware ≥ 2.9.10).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun TrendRow(label: String, range: String, values: List<Float>, lo: Float, hi: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Sparkline(values, lo, hi, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Sparkline(values: List<Float>, lo: Float, hi: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceContainer
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(track),
    ) {
        if (values.size < 2) return@Canvas
        val dx = size.width / (values.size - 1)
        val rng = (hi - lo).coerceAtLeast(0.001f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val y = size.height - ((v.coerceIn(lo, hi) - lo) / rng) * size.height
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * dx, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Histogram gap kedatangan audio (penghitung kumulatif sejak boot slave) + angka jitter/versi. */
@Composable
private fun JitterBlock(d: WsubDiag) {
    val labels = listOf(">40", ">60", ">80", ">100", ">200", ">500")
    val max = (d.gaps.maxOrNull() ?: 0).coerceAtLeast(1)
    Text("Gap kedatangan audio (ms), kumulatif sejak boot slave", style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, l ->
            val c = d.gaps.getOrElse(i) { 0 }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$c", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((4 + 28 * c / max).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i >= 3 && c > 0) WarnAmber else MaterialTheme.colorScheme.primary),
                )
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        "Gap maks ${d.gmaxMs} ms · jitter ≈ ${"%.1f".format(d.estMs)} ms · diminta slave ${if (d.needMs > 0f) "%.1f ms".format(d.needMs) else "—"} · " +
            "buffer min ${if (d.fillMinMs >= 0f) "%.1f ms".format(d.fillMinMs) else "—"} · firmware slave 2.9.${if (d.fw > 0) d.fw.toString() else "?"}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
