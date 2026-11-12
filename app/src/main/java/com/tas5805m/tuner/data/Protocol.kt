package com.tas5805m.tuner.data

/** Parser untuk baris-baris yang dikirim firmware lewat SPP (TAS5805M-CTRL). */
sealed interface Rx {
    data class Mode(val mode: Int, val xover: Int, val master: Int, val sat: Int, val sub: Int, val ctr: Int = 85) : Rx
    data class State(val gain: Int, val amp: String, val eq: Boolean, val mix: List<Float>) : Rx
    data class Fault(val values: Map<String, Int>) : Rx
    data class Fault2(val values: Map<String, Int>) : Rx   // chip 2 (sub)
    data class Eq(val channel: Char, val gains: List<Int>) : Rx
    data class EqMode(val raw: Int) : Rx
    data class Level(val l: Int, val r: Int) : Rx
    data class Level2(val l: Int, val r: Int) : Rx
    data class Caps(val items: Set<String>) : Rx
    data class Ok(val what: String) : Rx
    data class Err(val what: String) : Rx
    data class Dac(val mode: String) : Rx
    data class Dac2(val mode: String) : Rx   // chip 2 (sub)
    data class Mod(val mode: String, val freq: String, val bd: String) : Rx
    data class Clip(val gain: Int, val ml: Int, val mr: Int) : Rx
    data class Info(val values: Map<String, String>) : Rx
    data class Saved(val saved: Boolean) : Rx
    data class EqProfile(val l: Int, val r: Int) : Rx
    data class EqProfile2(val l: Int, val r: Int) : Rx   // chip 2 (sub)
    data class Fx(val inv: Boolean, val stby: Int, val sb: Boolean) : Rx   // polaritas sub, auto-standby
    data class EqC(val gains: List<Int>) : Rx   // EQ center (mode 3)
    data class Wsub(val state: WsubState) : Rx   // sub wireless (multi)
    /** Baris WLVL (firmware ≥ 2.9.15): level meter per slot sub, deci-dB −1200..0; null = offline / slave lama / basi. */
    data class WsubLevels(val levels: Map<Int, Int?>) : Rx
    data class WsubVoiceLine(val v: SubVoice) : Rx   // baris WSUBV (voicing sub)
    data class WsubPeerLine(val peer: WsubPeer) : Rx  // baris WSUBP per slot
    data class WsubStat(val slot: Int, val mac: String, val rxAudio: Int, val rxCtrl: Int, val rej: Int, val stx: Int, val flt: Int = -1, val fcnt: Int = 0, val up: Int = -1, val rst: Int = -1, val rssi: Int = 0) : Rx  // baris WSUBS (diagnostik slave)
    /** Baris WSUBT dari master: basis latensi (satelit + trim), latensi yang sedang diperintahkan, batas tambahan otomatis. */
    data class WsubTimingLine(val t: WsubTimingInfo) : Rx
    data class WsubDiagLine(val slot: Int, val mac: String, val diag: WsubDiag) : Rx  // baris WSUBD (jitter, versi slave)
    data class EqS(val gains: List<Int>) : Rx   // EQ sub (mode 3)
    data object Pong : Rx
    data class Other(val text: String) : Rx
}

object Protocol {
    private fun kv(parts: List<String>): Map<String, String> {
        val m = HashMap<String, String>()
        for (p in parts) {
            val i = p.indexOf('=')
            if (i > 0) m[p.substring(0, i)] = p.substring(i + 1)
        }
        return m
    }

    private fun num(s: String?): Int? {
        if (s == null) return null
        return if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2).toIntOrNull(16) else s.toIntOrNull()
    }

    fun parse(line: String): Rx {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return Rx.Other(line)
        val head = parts[0]
        val rest = parts.drop(1)
        return try {
            when (head) {
                "MODE" -> {
                    val m = kv(rest)
                    Rx.Mode(
                        mode = rest.firstOrNull()?.toIntOrNull() ?: 0,
                        xover = num(m["xover"]) ?: 150,
                        master = num(m["master"]) ?: 90,
                        sat = num(m["sat"]) ?: 90,
                        sub = num(m["sub"]) ?: 90,
                        ctr = num(m["ctr"]) ?: 85,
                    )
                }
                "STATE" -> {
                    val m = kv(rest)
                    Rx.State(
                        gain = num(m["gain"]) ?: 18,
                        amp = m["amp"] ?: "play",
                        eq = (num(m["eq"]) ?: 1) != 0,
                        mix = listOf("ll", "lr", "rl", "rr").map { m[it]?.toFloatOrNull() ?: 0f },
                    )
                }
                "FAULT" -> {
                    val m = kv(rest)
                    Rx.Fault(m.mapNotNull { (k, v) -> num(v)?.let { k to it } }.toMap())
                }
                "EQ" -> {
                    val m = kv(rest)
                    val ch = if (m.containsKey("l")) 'l' else 'r'
                    val gains = (m[ch.toString()] ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (gains.size == EQ_BANDS) Rx.Eq(ch, gains) else Rx.Other(line)
                }
                "EQM" -> Rx.EqMode(rest.firstOrNull()?.toIntOrNull() ?: 6)
                "WSUB" -> {
                    val m = kv(rest)
                    val found = (m["found"] ?: "none").split(',').map { it.trim() }
                        .filter { it.isNotBlank() && it != "none" }
                    val maxP = num(m["max"]) ?: 4
                    val scan = num(m["scan"]) == 1
                    // Multi: peers=mac1,mac2 links=1,0 fill=.. und=.. gap=.. rate=.. hiz=..
                    val peersCsv = m["peers"] ?: m["peer"]
                    val peers = if (!peersCsv.isNullOrBlank() && peersCsv != "none") {
                        val macs = peersCsv.split(',').map { it.trim() }.filter { it.isNotBlank() && it != "none" }
                        val links = (m["links"] ?: m["link"] ?: "").split(',').map { it.trim() }
                        val fills = (m["fill"] ?: "0").split(',').map { it.trim() }
                        val unds = (m["und"] ?: "0").split(',').map { it.trim() }
                        val gaps = (m["gap"] ?: "0").split(',').map { it.trim() }
                        val rates = (m["rate"] ?: "0").split(',').map { it.trim() }
                        val hizs = (m["hiz"] ?: "0").split(',').map { it.trim() }
                        macs.mapIndexed { i, mac ->
                            WsubPeer(
                                mac = mac,
                                slot = i,
                                link = (links.getOrNull(i) ?: links.getOrNull(0) ?: "0") == "1",
                                fillMs = (fills.getOrNull(i) ?: fills.getOrNull(0) ?: "0").toIntOrNull() ?: 0,
                                und = (unds.getOrNull(i) ?: unds.getOrNull(0) ?: "0").toIntOrNull() ?: 0,
                                gap = (gaps.getOrNull(i) ?: gaps.getOrNull(0) ?: "0").toIntOrNull() ?: 0,
                                rate = (rates.getOrNull(i) ?: rates.getOrNull(0) ?: "0").toIntOrNull() ?: 0,
                                hiz = (hizs.getOrNull(i) ?: hizs.getOrNull(0) ?: "0") == "1",
                            )
                        }
                    } else emptyList()
                    Rx.Wsub(
                        WsubState(
                            peers = peers, maxPeers = maxP, scan = scan, found = found,
                            lat = num(m["lat"]) ?: 0,
                            trim = m["trim"]?.toIntOrNull() ?: 0,
                            txf = m["txf"]?.toLongOrNull() ?: 0L,
                            err = m["err"] ?: "none",
                            ackOk = m["ackok"]?.toLongOrNull() ?: 0L,
                            ackFail = m["ackfail"]?.toLongOrNull() ?: 0L,
                            nomem = m["nomem"]?.toLongOrNull() ?: 0L,
                            drop = m["drop"]?.toLongOrNull() ?: 0L,
                            heap = m["heap"]?.toLongOrNull() ?: 0L,
                            heapMin = m["heapmin"]?.toLongOrNull() ?: 0L,
                            ch = num(m["ch"]) ?: 0,
                            diag = m.containsKey("ackok"),
                            mfw = num(m["mfw"]) ?: 0,
                        )
                    )
                }
                "WSUBS" -> {
                    // Diagnostik slave (relay lewat master): WSUBS slot=0 mac=AA:.. link=1 rxa=86 rxc=10 rej=0 stx=0 ...
                    val m = kv(rest)
                    val mac = m["mac"]?.takeIf { it != "none" && it.isNotBlank() }
                    if (mac == null) Rx.Other(line)
                    else Rx.WsubStat(
                        slot = num(m["slot"]) ?: 0,
                        mac = mac,
                        rxAudio = num(m["rxa"]) ?: -1,
                        rxCtrl = num(m["rxc"]) ?: -1,
                        rej = num(m["rej"]) ?: 0,
                        stx = num(m["stx"]) ?: 0,
                        flt = num(m["flt"]) ?: -1,
                        fcnt = num(m["fcnt"]) ?: 0,
                        up = num(m["up"]) ?: -1,
                        rst = num(m["rst"]) ?: -1,
                        rssi = num(m["rssi"]) ?: 0,
                    )
                }
                "WSUBT" -> {
                    // WSUBT linked=1 lim=.. nomem=.. … latb=70 latc=95 aligned=1 extra_max=100
                    val m = kv(rest)
                    val latb = num(m["latb"])
                    val latc = num(m["latc"])
                    if (latb == null || latc == null) Rx.Other(line)
                    else Rx.WsubTimingLine(WsubTimingInfo(latb, latc, num(m["extra_max"]) ?: 100, m["aligned"] == "1"))
                }
                "WSUBD" -> {
                    // WSUBD slot=0 mac=AA:.. g40=1 g60=0 g80=0 g100=0 g200=0 g500=0 gmax=45 adapt=2 fmin=18 jmax=30 pkt=64 est=3.2 need=12.0 fw=2.9.15 fillmin=14.5 age=800
                    val m = kv(rest)
                    val mac = m["mac"]?.takeIf { it != "none" && it.isNotBlank() }
                    if (mac == null) Rx.Other(line)
                    else Rx.WsubDiagLine(
                        slot = num(m["slot"]) ?: 0,
                        mac = mac,
                        diag = WsubDiag(
                            fw = m["fw"]?.substringAfterLast('.')?.toIntOrNull() ?: 0,
                            gaps = listOf("g40", "g60", "g80", "g100", "g200", "g500").map { num(m[it]) ?: 0 },
                            gmaxMs = num(m["gmax"]) ?: 0,
                            estMs = m["est"]?.toFloatOrNull() ?: 0f,
                            needMs = m["need"]?.toFloatOrNull() ?: 0f,
                            fillMinMs = m["fillmin"]?.toFloatOrNull() ?: -1f,
                            adapt = num(m["adapt"]) ?: 0,
                            jmax = num(m["jmax"]) ?: 0,
                            pkt = num(m["pkt"]) ?: 0,
                            ageMs = num(m["age"]) ?: 0,
                        ),
                    )
                }
                "WLVL" -> {
                    // WLVL s0=-250 s1=na  (kunci = slot; "na" → null)
                    val m = kv(rest)
                    val out = HashMap<Int, Int?>()
                    for ((k, v) in m) {
                        val slot = if (k.length >= 2 && k[0] == 's') k.substring(1).toIntOrNull() else null
                        if (slot != null) out[slot] = num(v)?.coerceIn(-1200, 0)
                    }
                    Rx.WsubLevels(out)
                }
                "WSUBV" -> {
                    // WSUBV on=1 hpf=32 pk=55 db=40 q=10 harm=40 lim=80
                    val m = kv(rest)
                    Rx.WsubVoiceLine(
                        SubVoice(
                            on = num(m["on"]) == 1,
                            hpf = (num(m["hpf"]) ?: 32).coerceIn(20, 60),
                            pkHz = (num(m["pk"]) ?: 55).coerceIn(30, 120),
                            pkDb10 = (num(m["db"]) ?: 0).coerceIn(-60, 90),
                            pkQ10 = (num(m["q"]) ?: 10).coerceIn(5, 30),
                            harm = (num(m["harm"]) ?: 0).coerceIn(0, 100),
                            limit = (num(m["lim"]) ?: 100).coerceIn(50, 100),
                        )
                    )
                }
                "WSUBP" -> {
                    // Per-peer line: WSUBP slot=0 mac=AA:.. link=1 fill=45 und=0 gap=0 rate=44100 hiz=0
                    val m = kv(rest)
                    val mac = m["mac"]?.takeIf { it != "none" && it.isNotBlank() }
                    if (mac == null) Rx.Other(line)
                    else Rx.WsubPeerLine(
                        WsubPeer(
                            mac = mac,
                            slot = num(m["slot"]) ?: 0,
                            link = num(m["link"]) == 1,
                            fillMs = num(m["fill"]) ?: 0,
                            und = num(m["und"]) ?: 0,
                            gap = num(m["gap"]) ?: 0,
                            rate = num(m["rate"]) ?: 0,
                            hiz = num(m["hiz"]) == 1,
                        )
                    )
                }
                "FX" -> {
                    val m = kv(rest)
                    Rx.Fx(inv = num(m["inv"]) == 1, stby = num(m["stby"]) ?: 0, sb = num(m["sb"]) == 1)
                }
                "EQC", "EQS" -> {
                    val gains = (rest.firstOrNull() ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }
                    if (gains.size != EQ_BANDS) Rx.Other(line)
                    else if (head == "EQC") Rx.EqC(gains) else Rx.EqS(gains)
                }
                "FAULT2" -> {
                    val m = kv(rest)
                    Rx.Fault2(m.mapNotNull { (k, v) -> num(v)?.let { k to it } }.toMap())
                }
                "DAC2" -> Rx.Dac2(kv(rest)["mode"] ?: "btl")
                "EQP2" -> {
                    val m = kv(rest)
                    Rx.EqProfile2(num(m["l"]) ?: 0, num(m["r"]) ?: 0)
                }
                "EQP" -> {
                    val m = kv(rest)
                    Rx.EqProfile(num(m["l"]) ?: 0, num(m["r"]) ?: 0)
                }
                "DAC" -> Rx.Dac(kv(rest)["mode"] ?: "btl")
                "MOD" -> {
                    val m = kv(rest)
                    Rx.Mod(m["mode"] ?: "bd", m["freq"] ?: "768k", m["bd"] ?: "175k")
                }
                "CLIP" -> {
                    val m = kv(rest)
                    Rx.Clip(num(m["gain"]) ?: 0, num(m["ml"]) ?: 0, num(m["mr"]) ?: 0)
                }
                "INFO" -> Rx.Info(kv(rest))
                "SAVED" -> Rx.Saved((rest.firstOrNull()?.toIntOrNull() ?: 0) != 0)
                "LVL" -> {
                    val m = kv(rest)
                    Rx.Level(num(m["l"]) ?: -1200, num(m["r"]) ?: -1200)
                }
                "LVL2" -> {
                    val m = kv(rest)
                    Rx.Level2(num(m["l"]) ?: -1200, num(m["r"]) ?: -1200)
                }
                "CAPS" -> Rx.Caps(rest.toSet())
                "OK" -> Rx.Ok(rest.joinToString(" "))
                "ERR" -> Rx.Err(rest.joinToString(" "))
                "PONG" -> Rx.Pong
                else -> Rx.Other(line)
            }
        } catch (e: Exception) {
            Rx.Other(line)
        }
    }

    /** Raw EQ register (6/7/14/15) → (enabled, biamp) */
    fun decodeEqMode(raw: Int): Pair<Boolean, Boolean> = when (raw and 0x0F) {
        6 -> true to false
        7 -> false to false
        14 -> true to true
        15 -> false to true
        else -> true to false
    }
}
