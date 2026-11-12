package com.tas5805m.tuner.data

import java.util.Locale

enum class TuningKind { INSUFFICIENT, OK, ADAPTING, SATURATED, RADIO }

data class TuningAdvice(val kind: TuningKind, val title: String, val details: List<String>)

/** Saran latensi/tuning untuk satu slave, dari data nyata (underrun/gap, RSSI) dan perilaku adaptasi master.
 *
 *  Penting — cara kerja firmware: master memerintahkan latensi = basis (WSUB_SAT_LAT_MS + trim) + tambahan otomatis
 *  (naik ≤ 8 ms/dtk sesuai kebutuhan jitter slave, maks WSUB_ALIGN_MAX_EXTRA_MS). Trim menggeser sub terhadap satelit;
 *  trim BUKAN pengatur buffer, jadi saran di sini tidak pernah menyuruh mengubah trim untuk mengatasi underrun. */
object LatencyAdvisor {
    const val MIN_ACTIVE_SAMPLES = 20

    fun advise(p: WsubPeer, trend: List<WsubTrendPt>, timing: WsubTimingInfo?, nowMs: Long): TuningAdvice {
        if (!p.link) return TuningAdvice(TuningKind.INSUFFICIENT, "Sub offline", listOf("No advice while the slave is disconnected."))
        if (timing == null) {
            return TuningAdvice(TuningKind.INSUFFICIENT, "Latency data unavailable", listOf("The master did not report its latency (WSUBT) yet — open the Monitor tab for a few seconds."))
        }
        val win = trend.filter { nowMs - it.t in 0..(3 * 60_000L) }
        val active = win.count { it.rx > 0 }
        if (active < MIN_ACTIVE_SAMPLES) {
            return TuningAdvice(
                TuningKind.INSUFFICIENT, "Not enough playback data",
                listOf("Play audio for at least ~40 s with the Monitor tab open (have $active of $MIN_ACTIVE_SAMPLES samples)."),
            )
        }
        val spanMs = win.last().t - win.first().t
        val und = WsubHealthEval.riseActive(win) { it.und }
        val gap = WsubHealthEval.riseActive(win) { it.gap }
        val gapPerMin = gap * 60_000.0 / maxOf(spanMs, 20_000L)
        val problems = und >= 1 || gapPerMin >= 10
        val weak = p.rssi != 0 && p.rssi < WsubHealthEval.WEAK_RSSI

        val extra = timing.latCmd - timing.latBase
        val headroom = timing.latBase + timing.extraMax - timing.latCmd
        val saturated = timing.aligned && headroom <= 2
        val need = p.diag?.needMs ?: 0f
        val state = "Commanded latency ${timing.latCmd} ms = base ${timing.latBase} ms + ${extra} ms auto (max +${timing.extraMax}), headroom $headroom ms."
        val spanS = spanMs / 1000
        val problem = "$und underrun(s), ${String.format(Locale.US, "%.0f", gapPerMin)} gap(s)/min over the last $spanS s."

        return when {
            problems && weak -> TuningAdvice(
                TuningKind.RADIO, "Weak radio link — latency will not fix this",
                listOf(problem, "RSSI ${p.rssi} dBm. Improve the link first: move the sub closer to the master or remove obstacles; then re-check.", state),
            )
            problems && saturated -> TuningAdvice(
                TuningKind.SATURATED, "Auto-latency is at its limit and the stream still breaks",
                listOf(
                    problem, state,
                    "1) Reduce interference first: change WSUB_CHANNEL (1/6/11), keep Wi-Fi routers and other 2.4 GHz devices away.",
                    "2) If the link is clean: raise WSUB_ALIGN_MAX_EXTRA_MS in the master firmware so it can add more buffer (the sub becomes later — re-check alignment with the satellite).",
                    "3) Raising WSUB_SAT_LAT_MS also works but delays the satellite path too (lip-sync with video).",
                    "Do not use trim for this: trim shifts the sub against the satellite; it does not add safety buffer.",
                ),
            )
            problems -> TuningAdvice(
                TuningKind.ADAPTING, "Auto-latency is still adapting",
                buildList {
                    add(problem)
                    add(state)
                    if (need > 0f) add(String.format(Locale.US, "The slave asks for ≈ %.1f ms.", need))
                    add("If underruns stop within a minute no action is needed; if they continue, check RSSI and the radio channel.")
                },
            )
            else -> TuningAdvice(
                TuningKind.OK, "Latency is adequate",
                buildList {
                    add("No underruns or gaps in the last $spanS s.")
                    add(state)
                    if (extra >= 30) add("Auto-latency had to add +$extra ms over base: conditions are marginal, and if the sub sounds late against the satellite this is the likely cause.")
                    if (p.rssi != 0) add("Signal ${p.rssi} dBm.")
                },
            )
        }
    }
}
