package com.tas5805m.tuner.data

/** Penilaian kesehatan satu slave sub wireless. Murni Kotlin (tanpa Android) supaya mudah diuji.
 *
 *  Dua prinsip (3.7.0):
 *  1. VALIDITAS DATA — "Sehat" hanya bila data yang dibutuhkan ada, segar, dan cukup. Data kosong / basi / belum cukup
 *     menghasilkan "Belum valid", bukan hijau. Temuan WARN/BAD tidak pernah disembunyikan oleh ketiadaan data.
 *  2. ADAPTIF — ambang dihitung dari kondisi nyata (laju sample aktual, ukuran paket, rata-rata slave itu sendiri,
 *     panjang jendela, ada/tidaknya stream), bukan angka tetap. */
enum class HealthLevel { OFF, UNKNOWN, OK, WARN, BAD }

data class HealthReason(val level: HealthLevel, val text: String, val advice: String? = null)

/** Satu sumber data dan apakah saat ini valid. required = wajib valid agar boleh disebut "Sehat". */
data class CoverageItem(val name: String, val ok: Boolean, val required: Boolean)

data class WsubHealth(val level: HealthLevel, val reasons: List<HealthReason>, val coverage: List<CoverageItem> = emptyList()) {
    val title: String
        get() = when (level) {
            HealthLevel.OFF -> "Offline"
            HealthLevel.UNKNOWN -> "Belum valid"
            HealthLevel.OK -> "Sehat"
            HealthLevel.WARN -> "Waspada"
            HealthLevel.BAD -> "Bermasalah"
        }
    val coverageText: String
        get() = "Cakupan data ${coverage.count { it.ok }}/${coverage.size}: " +
            coverage.joinToString(" · ") { (if (it.ok) "✓ " else "✗ ") + it.name }
}

object WsubHealthEval {
    private const val ADV_RF = "Reduce RF interference or distance: move the sub closer to the master, or change WSUB_CHANNEL (1/6/11) to a quieter 2.4 GHz channel."
    private const val ADV_LAT = "Increase the wireless latency (trim) or reduce RF interference."
    private const val ADV_FAULT = "Check speaker wiring for shorts, supply voltage and ventilation of the sub amp; power-cycle the sub."
    private const val ADV_FLASH = "Flash the slave with firmware 2.9.15 so fault and level are reported."
    private const val ADV_REFRESH = "Keep the Monitor tab open (or reconnect) so status keeps refreshing."
    private const val ADV_WAIT = "Wait 10-20 seconds for enough samples."
    private const val ADV_POWER = "Check the sub's power supply (voltage drop / too little current) and its cables; a brownout reset means supply voltage dipped under load."
    private const val ADV_CRASH = "The slave crashed or hit a watchdog: update master and slave to the same firmware and capture the slave serial log."
    private const val ADV_CLIP = "Lower the volume / sub gain or reduce EQ boost to avoid clipping."
    private const val ADV_SAMEFW = "Flash master and slave with the same firmware version."
    private const val ADV_LINK = "Check slave power, pairing and that master and slave use the same WSUB_CHANNEL."

    /** App 3.8.0 berpasangan dengan firmware 2.9.16 (uptime/alasan reset/RSSI); fault & meter sub butuh 2.9.15. */
    const val EXPECTED_FW = 16
    const val FAULT_FW = 15
    const val WINDOW_MS = 60_000L
    /** Status slave dianggap basi bila lebih lama dari ini (polling normal ≈ 2 dtk → 3 kali terlewat). */
    const val STALE_MS = 6_000L
    const val LVL_FRESH_MS = 3_000L
    const val DIAG_FRESH_MS = 10_000L
    const val MIN_TREND_SAMPLES = 5
    const val MIN_TREND_SPAN_MS = 8_000L
    /** Master mendesimasi ÷16 (WSUB_DECIM) → paket/dtk = rate / (16 × sample per paket). */
    const val WSUB_DECIM = 16
    /** RSSI ESP-NOW di sisi slave: lemah di bawah ini, kuat di atas ini (dBm). */
    const val WEAK_RSSI = -85
    const val STRONG_RSSI = -70
    /** Reset abnormal yang masih "baru" (detik uptime) dinilai BAD; reboot terdeteksi dari uptime yang turun antar-sampel. */
    const val RECENT_RESET_S = 600

    /** Total kenaikan counter dalam deret. Penurunan (reset diagnostik / wrap 16 bit) tidak dihitung negatif. */
    fun rise(values: List<Int>): Int {
        var r = 0
        for (i in 1 until values.size) {
            val d = values[i] - values[i - 1]
            if (d > 0) r += d
        }
        return r
    }

    /** Kenaikan counter hanya pada interval saat stream aktif (rx > 0); saat idle/standby counter tidak relevan. */
    fun riseActive(win: List<WsubTrendPt>, pick: (WsubTrendPt) -> Int): Int {
        var r = 0
        for (i in 1 until win.size) {
            if (win[i].rx <= 0) continue
            val d = pick(win[i]) - pick(win[i - 1])
            if (d > 0) r += d
        }
        return r
    }

    fun median(values: List<Int>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m].toDouble() else (s[m - 1] + s[m]) / 2.0
    }

    /** Paket audio/dtk yang seharusnya, dari laju sample aktual dan ukuran paket yang dilaporkan slave; null bila belum diketahui. */
    fun expectedRx(p: WsubPeer): Double? {
        val pkt = p.diag?.pkt ?: 0
        return if (p.rate > 0 && pkt > 0) p.rate.toDouble() / (WSUB_DECIM * pkt) else null
    }

    private fun f1(v: Double) = "%.1f".format(v)

    fun evaluate(p: WsubPeer, trend: List<WsubTrendPt>, lvlDb10: Int?, seen: WsubSeen?, nowMs: Long, masterFw: Int): WsubHealth {
        if (!p.link) {
            return WsubHealth(
                HealthLevel.OFF,
                listOf(HealthReason(HealthLevel.OFF, "Tidak terhubung ke master", ADV_LINK)),
            )
        }
        val findings = ArrayList<HealthReason>()   // WARN / BAD
        val missing = ArrayList<HealthReason>()    // alasan data belum valid
        val info = ArrayList<HealthReason>()

        // ── kesegaran status ─────────────────────────────────────────────
        val ageMs = if (seen != null && seen.statMs > 0) nowMs - seen.statMs else -1L
        val statusOk = ageMs in 0..STALE_MS
        val sfx = if (ageMs > STALE_MS) " (data lama)" else ""
        if (ageMs < 0) missing += HealthReason(HealthLevel.UNKNOWN, "Belum ada status dari slave", ADV_REFRESH)
        else if (ageMs > STALE_MS) missing += HealthReason(HealthLevel.UNKNOWN, "Data slave basi ${ageMs / 1000} dtk — status tidak diperbarui (buka tab Monitor)", ADV_REFRESH)

        val idle = p.hiz || p.rate <= 0   // tidak ada stream → aturan radio/underrun tidak relevan

        // ── RSSI (slave ≥ 2.9.16): menentukan saran radio — sinyal lemah ≠ interferensi ──
        val rssiOk = p.rssi != 0
        val weak = rssiOk && p.rssi < WEAK_RSSI
        val strong = rssiOk && p.rssi >= STRONG_RSSI
        val advRf = when {
            weak -> "Signal is weak (${p.rssi} dBm): move the sub closer to the master or remove obstacles between them."
            strong -> "Signal is strong (${p.rssi} dBm), so the problem is more likely interference/airtime congestion: change WSUB_CHANNEL (1/6/11) away from busy Wi-Fi and keep other 2.4 GHz devices away."
            else -> ADV_RF
        }
        if (weak) findings += HealthReason(HealthLevel.WARN, "Sinyal radio lemah (${p.rssi} dBm)$sfx", advRf)

        // ── uptime & alasan reset (slave ≥ 2.9.16) ───────────────────────
        val upOk = p.upS >= 0
        if (upOk) {
            val up = p.upS
            if (isAbnormalReset(p.rst) && up < RECENT_RESET_S) {
                findings += HealthReason(
                    HealthLevel.BAD,
                    "Slave restart ${up} dtk lalu: ${resetReasonLabel(p.rst)}$sfx",
                    if (p.rst == 9) ADV_POWER else ADV_CRASH,
                )
            } else if (isAbnormalReset(p.rst)) {
                info += HealthReason(HealthLevel.OK, "Restart terakhir slave: ${resetReasonLabel(p.rst)} (uptime ${up / 3600} j ${(up % 3600) / 60} mnt)")
            } else if (up < 30 && p.rst == 1) {
                info += HealthReason(HealthLevel.OK, "Slave baru dinyalakan (uptime $up dtk)")
            }
            val ups = trend.map { it.up }.filter { it >= 0 }
            var reboots = 0
            for (i in 1 until ups.size) if (ups[i] + 2 < ups[i - 1]) reboots++   // uptime turun = reboot (toleransi 2 dtk)
            if (reboots > 0) {
                findings += HealthReason(HealthLevel.WARN, "Slave reboot $reboots× dalam ±${trend.size * 2} dtk terakhir$sfx", ADV_POWER)
            }
        }

        // ── fault amp ────────────────────────────────────────────────────
        val faultOk = p.flt >= 0
        if (p.flt > 0) {
            val defs = p.fltKeys.mapNotNull { k -> FAULT_DEFS.firstOrNull { it.key == k } }
            val critical = defs.any { it.critical }
            findings += HealthReason(
                if (critical) HealthLevel.BAD else HealthLevel.WARN,
                "Fault amp sub: " + defs.joinToString(", ") { it.label } + sfx,
                ADV_FAULT,
            )
        } else if (p.flt == 0 && p.fcnt > 0) {
            info += HealthReason(HealthLevel.OK, "Slave pernah fault ${p.fcnt}× sejak boot (saat ini bersih)")
        } else if (p.flt < 0) {
            missing += HealthReason(HealthLevel.UNKNOWN, "Fault amp sub tidak dilaporkan — firmware slave lama (butuh 2.9.$FAULT_FW)", ADV_FLASH)
        }

        // ── tren (jendela ±1 menit) ──────────────────────────────────────
        val win = trend.filter { nowMs - it.t in 0..WINDOW_MS }
        val spanMs = if (win.size >= 2) win.last().t - win.first().t else 0L
        val trendOk = win.size >= MIN_TREND_SAMPLES && spanMs >= MIN_TREND_SPAN_MS
        if (!trendOk) missing += HealthReason(HealthLevel.UNKNOWN, "Mengumpulkan tren (${win.size}/$MIN_TREND_SAMPLES sampel)", ADV_WAIT)

        // ── radio: laju paket audio, adaptif terhadap laju sample & ukuran paket ──
        val radioOk = p.rxAudio >= 0
        if (!radioOk) {
            missing += HealthReason(HealthLevel.UNKNOWN, "Diagnostik radio slave tidak tersedia (firmware slave lama)", ADV_FLASH)
        } else if (!idle) {
            val exp = expectedRx(p)
            val base = median(win.map { it.rx }.filter { it > 0 }).takeIf { win.count { w -> w.rx > 0 } >= 8 }
            val ref = exp ?: base
            if (ref != null && ref > 0) {
                val ratio = p.rxAudio / ref
                val refTxt = if (exp != null) "seharusnya ≈ ${f1(exp)}" else "rata-rata ≈ ${f1(ref)}"
                when {
                    ratio < 0.5 -> findings += HealthReason(HealthLevel.BAD, "Paket audio ${p.rxAudio}/dtk ($refTxt)$sfx", advRf)
                    ratio < 0.85 -> findings += HealthReason(HealthLevel.WARN, "Paket audio ${p.rxAudio}/dtk ($refTxt)$sfx", advRf)
                    exp != null && base != null && base < 0.9 * exp ->
                        findings += HealthReason(HealthLevel.WARN, "Laju paket rata-rata ${f1(base)}/dtk di bawah seharusnya ≈ ${f1(exp)}", advRf)
                    base != null && p.rxAudio < 0.8 * base ->
                        findings += HealthReason(HealthLevel.WARN, "Paket audio turun ke ${p.rxAudio}/dtk dari rata-rata ≈ ${f1(base)}$sfx", advRf)
                }
            } else if (p.rxAudio == 0) {
                findings += HealthReason(HealthLevel.BAD, "Tidak ada paket audio diterima padahal stream aktif$sfx", advRf)
            }
        }

        // ── underrun & gap: dinormalkan per menit sesuai panjang jendela sebenarnya; hanya saat stream aktif ──
        if (trendOk && !idle) {
            val perMinFactor = 60_000.0 / maxOf(spanMs, 20_000L)
            val und = riseActive(win) { it.und }
            val gap = riseActive(win) { it.gap }
            val spanTxt = "${spanMs / 1000} dtk"
            if (und >= 1) {
                val perMin = und * perMinFactor
                findings += HealthReason(
                    if (perMin >= 5) HealthLevel.BAD else HealthLevel.WARN,
                    "Underrun $und× dalam $spanTxt (≈ ${f1(perMin)}/menit)$sfx",
                    ADV_LAT,
                )
            }
            if (gap >= 1) {
                val perMin = gap * perMinFactor
                if (perMin >= 50) findings += HealthReason(HealthLevel.BAD, "Paket hilang/gap $gap× dalam $spanTxt (≈ ${f1(perMin)}/menit)$sfx", advRf)
                else if (perMin >= 10) findings += HealthReason(HealthLevel.WARN, "Paket hilang/gap $gap× dalam $spanTxt (≈ ${f1(perMin)}/menit)$sfx", advRf)
            }
        }

        // ── level: berkelanjutan, bukan satu sampel sesaat ───────────────
        val lvlOk = lvlDb10 != null && seen != null && seen.lvlMs > 0 && nowMs - seen.lvlMs <= LVL_FRESH_MS
        val lvls = win.mapNotNull { it.lvl }
        if (lvls.any { it >= -5 }) {
            findings += HealthReason(HealthLevel.WARN, "Level sub menyentuh 0 dB (clipping)$sfx", ADV_CLIP)
        } else {
            val hot = lvls.count { it > -30 }
            if (hot >= 3) findings += HealthReason(HealthLevel.WARN, "Level sub sering mendekati 0 dB ($hot dari ${lvls.size} sampel)$sfx", ADV_CLIP)
        }

        // ── jitter: jeda audio dibanding isi buffer sebenarnya ───────────
        val d = p.diag
        val diagOk = d != null && seen != null && seen.diagMs > 0 && nowMs - seen.diagMs <= DIAG_FRESH_MS
        if (d != null) {
            if (!idle && diagOk) {
                val limit = maxOf(40, (p.fillMs * 1.5).toInt())
                if (d.gmaxMs > limit) {
                    findings += HealthReason(HealthLevel.WARN, "Jeda audio ${d.gmaxMs} ms melebihi isi buffer (${p.fillMs} ms) — berisiko underrun", ADV_LAT)
                }
            }
            if (d.fw in 1 until EXPECTED_FW) {
                info += HealthReason(HealthLevel.OK, "Slave 2.9.${d.fw}: uptime, alasan reset & RSSI butuh 2.9.$EXPECTED_FW")
            }
            if (masterFw > 0 && d.fw > 0 && d.fw != masterFw) {
                findings += HealthReason(HealthLevel.WARN, "Firmware master 2.9.$masterFw ≠ slave 2.9.${d.fw} (harus sama)", ADV_SAMEFW)
            }
        }

        val coverage = listOf(
            CoverageItem("status", statusOk, true),
            CoverageItem("fault amp", faultOk, true),
            CoverageItem("radio", radioOk, true),
            CoverageItem("tren", trendOk, true),
            CoverageItem("level", lvlOk || p.hiz, false),
            CoverageItem("jitter", diagOk, false),
            CoverageItem("uptime", upOk, false),
            CoverageItem("RSSI", rssiOk, false),
        )

        val worstFinding = findings.maxOfOrNull { it.level.ordinal }
        val level = when {
            worstFinding != null -> HealthLevel.values()[worstFinding]
            coverage.any { it.required && !it.ok } -> HealthLevel.UNKNOWN
            else -> HealthLevel.OK
        }
        val reasons = ArrayList<HealthReason>()
        if (level == HealthLevel.OK) reasons += HealthReason(HealthLevel.OK, "Tidak ada masalah terdeteksi (data valid)")
        reasons += findings.sortedByDescending { it.level.ordinal }
        reasons += missing
        reasons += info
        return WsubHealth(level, reasons, coverage)
    }
}
