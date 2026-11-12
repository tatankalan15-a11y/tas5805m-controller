package com.tas5805m.tuner.data

import java.util.Locale

/** Ringkasan diagnosa sub wireless satu layar (format "WIRELESS SUB DIAGNOSTIC"), dihitung dari data nyata.
 *  Murni Kotlin. Overall mengikuti [WsubHealthEval]: HEALTHY hanya bila semua data wajib valid; selain itu
 *  UNVERIFIED (data belum cukup/basi), WARNING, CRITICAL, atau OFFLINE. */
object DiagSummary {
    private const val NA = "NOT AVAILABLE"

    private fun row(k: String, v: String) = k.padEnd(14) + ": " + v

    private fun overall(l: HealthLevel) = when (l) {
        HealthLevel.OK -> "HEALTHY"
        HealthLevel.WARN -> "WARNING"
        HealthLevel.BAD -> "CRITICAL"
        HealthLevel.UNKNOWN -> "UNVERIFIED"
        HealthLevel.OFF -> "OFFLINE"
    }

    fun build(s: TunerState, nowMs: Long): String {
        val w = s.wsub
        if (w == null || w.peers.isEmpty()) {
            return listOf(
                "WIRELESS SUB DIAGNOSTIC",
                "",
                row("Overall", NA),
                "",
                "RECOMMENDATION",
                "No wireless sub is paired or the master is not in wireless-sub mode.",
            ).joinToString("\n")
        }
        val multi = w.peers.size > 1
        return w.peers.joinToString("\n\n") { p -> block(s, w, p, nowMs, multi) }
    }

    private fun block(s: TunerState, w: WsubState, p: WsubPeer, nowMs: Long, multi: Boolean): String {
        val trend = s.wsubTrend[p.slot].orEmpty()
        val h = WsubHealthEval.evaluate(p, trend, s.wsubLvl[p.slot], s.wsubSeen[p.slot], nowMs, w.mfw)
        val win = trend.filter { nowMs - it.t in 0..WsubHealthEval.WINDOW_MS }
        val undWin = WsubHealthEval.riseActive(win) { it.und }
        val gapWin = WsubHealthEval.riseActive(win) { it.gap }
        val lines = ArrayList<String>()

        lines += "WIRELESS SUB DIAGNOSTIC"
        lines += ""
        if (multi) lines += row("Slot", "${p.slot} (${p.mac})")
        lines += row("Overall", overall(h.level))
        lines += row("Master FW", if (w.mfw > 0) "2.9.${w.mfw}" else "UNKNOWN (old master firmware)")
        lines += row("Slave FW", p.diag?.fw?.takeIf { it > 0 }?.let { "2.9.$it" } ?: "UNKNOWN")

        lines += ""
        lines += "AUDIO"
        val lvl = s.wsubLvl[p.slot]
        lines += row("Level", when {
            lvl != null -> String.format(Locale.US, "%.1f dB", lvl / 10f)
            p.hiz -> "SILENT (amp standby)"
            else -> NA
        })
        val exp = WsubHealthEval.expectedRx(p)
        lines += row("Packet Rate", if (p.rxAudio >= 0)
            "${p.rxAudio} pkt/s" + (if (exp != null) String.format(Locale.US, " (expected ~%.0f)", exp) else "")
        else NA)
        lines += row("Buffer", "${p.fillMs} ms")
        val peak = trend.mapNotNull { it.lvl }.maxOrNull()
        lines += row("Peak (3 min)", if (peak != null)
            String.format(Locale.US, "%.1f dB (headroom %.1f dB)", peak / 10f, -peak / 10f)
        else NA)

        lines += ""
        lines += "WIRELESS"
        val total = w.ackOk + w.ackFail
        if (total > 0) {
            val ok = w.ackOk * 100.0 / total
            val sfx = if (multi) " (master, all slaves)" else ""
            lines += row("ACK Success", String.format(Locale.US, "%.1f%%", ok) + sfx)
            lines += row("ACK Failure", String.format(Locale.US, "%.1f%%", 100.0 - ok) + sfx)
        } else {
            lines += row("ACK Success", NA)
            lines += row("ACK Failure", NA)
        }
        lines += row("Signal (RSSI)", when {
            p.rssi == 0 -> NA + (if (p.diag != null && p.diag.fw in 1 until WsubHealthEval.EXPECTED_FW) " (needs slave 2.9.${WsubHealthEval.EXPECTED_FW})" else "")
            p.rssi < WsubHealthEval.WEAK_RSSI -> "${p.rssi} dBm (weak)"
            p.rssi >= WsubHealthEval.STRONG_RSSI -> "${p.rssi} dBm (strong)"
            else -> "${p.rssi} dBm (fair)"
        })
        lines += row("Underrun", "${p.und}" + (if (undWin > 0) " (+$undWin in last minute)" else ""))
        lines += row("Gap", "${p.gap}" + (if (gapWin > 0) " (+$gapWin in last minute)" else ""))

        lines += ""
        lines += "SLAVE"
        lines += row("Uptime", if (p.upS >= 0) "${p.upS / 3600} h ${(p.upS % 3600) / 60} min ${p.upS % 60} s" else NA)
        lines += row("Last Reset", if (p.upS >= 0) resetReasonLabel(p.rst) + (if (isAbnormalReset(p.rst)) " (ABNORMAL)" else "") else NA)

        lines += ""
        lines += "AMPLIFIER"
        lines += row("Fault", when {
            p.flt < 0 -> "$NA (old slave firmware)"
            p.flt == 0 -> "NONE" + (if (p.fcnt > 0) " (${p.fcnt} event(s) since slave boot)" else "")
            else -> p.fltKeys.joinToString(", ") { FaultIncidents.keyLabel(it) }
        })
        lines += row("Temperature", when {
            p.flt < 0 -> NA
            "ot_sd" in p.fltKeys -> "SHUTDOWN (over-temperature)"
            "ot_w" in p.fltKeys -> "WARNING (over-temperature flag)"
            else -> "NORMAL (flag only; no numeric value from chip)"
        })

        val tuning = LatencyAdvisor.advise(p, trend, s.wsubTiming, nowMs)
        lines += ""
        lines += "TUNING"
        lines += row("Latency", tuning.title)
        tuning.details.forEach { lines += "- $it" }

        val bad = h.reasons.filter { it.level == HealthLevel.WARN || it.level == HealthLevel.BAD || it.level == HealthLevel.UNKNOWN || it.level == HealthLevel.OFF }
        if (h.level != HealthLevel.OK && bad.isNotEmpty()) {
            lines += ""
            lines += "FINDINGS"
            bad.forEach { lines += "- ${it.text}" }
        }

        lines += ""
        lines += "RECOMMENDATION"
        val advice = bad.mapNotNull { it.advice }.distinct()
        when {
            h.level == HealthLevel.OK -> lines += "No abnormal condition detected."
            advice.isNotEmpty() -> advice.forEach { lines += "- $it" }
            else -> lines += "See the full diagnostic report for details."
        }
        return lines.joinToString("\n")
    }
}
