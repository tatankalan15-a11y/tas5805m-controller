package com.tas5805m.tuner.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Laporan diagnosa teks (untuk disalin/dibagikan saat minta bantuan). Murni Kotlin. */
object DiagReport {
    fun build(s: TunerState, appVersion: String, logTail: List<LogEntry>, nowMs: Long): String {
        val sb = StringBuilder()
        if (s.wsub != null) {
            sb.appendLine(DiagSummary.build(s, nowMs))
            sb.appendLine()
            sb.appendLine("────────────────────────────────────────")
        }
        val tf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val hf = SimpleDateFormat("HH:mm:ss", Locale.US)
        sb.appendLine("== Laporan diagnosa TAS5805M Tuner ==")
        sb.appendLine("Waktu     : ${tf.format(Date(nowMs))}")
        sb.appendLine("App       : $appVersion")
        sb.appendLine("Mode      : ${s.mode.name}  master=${s.master}  xover=${s.xover} Hz  RTT=${if (s.rttMs >= 0) "${s.rttMs} ms" else "-"}")
        if (s.info.isNotEmpty()) sb.appendLine("Chip      : " + s.info.entries.joinToString(" ") { "${it.key}=${it.value}" })

        val faults1 = FAULT_DEFS.filter { s.fault1(it.key) }.joinToString(", ") { it.label }
        sb.appendLine("Fault satelit: ${if (s.faultsReadMs == 0L) "belum dibaca" else faults1.ifEmpty { "bersih (dibaca ${FaultIncidents.formatAgo(nowMs - s.faultsReadMs)})" }}")
        if (s.hasLvl2) {
            val f2 = FAULT_DEFS.filter { s.fault2(it.key) }.joinToString(", ") { it.label }
            sb.appendLine("Fault chip 2 : ${f2.ifEmpty { "bersih" }}")
        }

        val w = s.wsub
        if (w != null) {
            sb.appendLine()
            sb.appendLine("-- Sub wireless --")
            sb.appendLine("Master fw 2.9.${w.mfw.takeIf { it > 0 } ?: "?"}  ch=${w.ch}  latensi=${w.lat} ms (trim ${w.trim})  slave online ${w.onlineCount}/${w.peers.size}")
            val total = w.ackOk + w.ackFail
            sb.appendLine("Radio master: ACK ${w.ackOk}/$total, tanpa ACK ${w.ackFail}, antrean penuh ${w.nomem}, dibuang ${w.drop}, gagal kirim ${w.txf}, error ${w.err}, heap ${w.heap / 1024} KB (min ${w.heapMin / 1024} KB)")
            w.peers.forEach { p ->
                val h = WsubHealthEval.evaluate(p, s.wsubTrend[p.slot].orEmpty(), s.wsubLvl[p.slot], s.wsubSeen[p.slot], nowMs, w.mfw)
                sb.appendLine()
                sb.appendLine("Slot ${p.slot} ${p.mac}  [${h.title}]")
                sb.appendLine("  link=${if (p.link) "ya" else "tidak"} hiz=${p.hiz} buffer=${p.fillMs} ms rate=${p.rate} Hz und=${p.und} gap=${p.gap}")
                sb.appendLine("  radio slave: rx audio=${p.rxAudio}/dtk ctrl=${p.rxCtrl}/dtk ditolak=${p.rej} tx gagal=${p.stx}")
                val lv = s.wsubLvl[p.slot]
                sb.appendLine("  level sub: ${lv?.let { "%.1f dB".format(it / 10f) } ?: "n/a"}")
                sb.appendLine("  fault amp: " + when {
                    p.flt < 0 -> "tidak tersedia (slave lama/offline)"
                    p.flt == 0 -> "bersih (kejadian sejak boot: ${p.fcnt})"
                    else -> p.fltKeys.joinToString(", ") + " (kejadian sejak boot: ${p.fcnt})"
                })
                p.diag?.let { d ->
                    sb.appendLine("  jitter: gap>40/60/80/100/200/500 ms = ${d.gaps.joinToString("/")}, gmax=${d.gmaxMs} ms, est=${d.estMs} ms, need=${d.needMs} ms, fill-min=${if (d.fillMinMs >= 0f) "${d.fillMinMs} ms" else "-"}, fw slave=2.9.${d.fw.takeIf { it > 0 } ?: "?"}")
                }
                sb.appendLine("  ${h.coverageText}")
                h.reasons.forEach { sb.appendLine("  - [${it.level.name}] ${it.text}") }
            }
        }

        if (s.faultIncidents.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("-- Riwayat fault (terbaru di atas; durasi ≈ selisih pembacaan pertama–terakhir yang melaporkan fault) --")
            s.faultIncidents.sortedByDescending { it.lastSeenMs }.take(20).forEach { e ->
                val dur = FaultIncidents.formatDuration(e.durationMs(nowMs))
                val end = when {
                    e.active -> "masih aktif"
                    e.truncated -> "data terputus ${hf.format(Date(e.endMs ?: e.lastSeenMs))}"
                    else -> "pulih ${hf.format(Date(e.endMs ?: e.lastSeenMs))}"
                }
                sb.appendLine("${hf.format(Date(e.startMs))}  ${FaultIncidents.sourceLabel(e.source)}: ${FaultIncidents.keyLabel(e.key)} · $dur · ${e.count}× · $end")
            }
        }

        if (logTail.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("-- Log terakhir (${logTail.size} baris) --")
            logTail.forEach { e ->
                val t = when {
                    e.text.startsWith("WSUBS") -> "SLAVE" + e.text.drop(5)
                    e.text.startsWith("WSUBD") -> "SLAVEDIAG" + e.text.drop(5)
                    else -> e.text
                }
                sb.appendLine("${hf.format(Date(e.time))} ${e.dir} $t")
            }
        }
        return sb.toString()
    }
}
