package com.tas5805m.tuner.data

/** Empat environment firmware (platformio.ini) = empat mode audio. */
enum class AudioMode(val code: Int, val env: String, val title: String, val subtitle: String, val short: String) {
    STEREO(0, "esp32dev_stereo", "Stereo 1-chip", "1× TAS5805M · L/R full-range", "Stereo"),
    TWO_CHIP_21(1, "esp32dev_2chip_21", "2.1 · 2-chip", "0x2D satelit (HPF) + 0x2E subwoofer (LPF)", "2.1 ×2"),
    ONE_CHIP_21(2, "esp32dev_1chip_21", "2.1 · 1-chip", "OUT L = satelit (HPF) · OUT R = sub (LPF)", "2.1 ×1"),
    THREE_ONE(3, "esp32dev_2chip_31", "3.1 · 2-chip", "0x2D satelit L/R (HPF) · 0x2E: OUT L = center (HPF), OUT R = sub (LPF)", "3.1 ×2"),
    WIRELESS(4, "esp32dev_wireless_master", "2.1 · sub wireless", "Chip 1: satelit L/R (HPF) · hingga 4 sub slave lewat ESP-NOW", "2.1 ⚡");

    /** Semua mode dengan crossover (2.1 dan 3.1). */
    val isTwoOne: Boolean get() = this != STEREO
    /** Mode dengan dua chip (2.1 ×2 dan 3.1). */
    val isTwoChip: Boolean get() = this == TWO_CHIP_21 || this == THREE_ONE

    companion object {
        fun from(code: Int): AudioMode = entries.firstOrNull { it.code == code } ?: STEREO
    }
}

enum class AmpState(val cmd: String, val label: String) {
    PLAY("play", "Play"),
    MUTE("mute", "Mute"),
    HIZ("hiz", "Hi-Z"),
    SLEEP("sleep", "Sleep"),
    DEEP_SLEEP("deepsleep", "Deep sleep");

    companion object {
        fun from(s: String): AmpState = entries.firstOrNull { it.cmd == s } ?: PLAY
    }
}

const val EQ_BANDS = 15

/** Mode 2.1: band 0..2 (20/32/50 Hz) = slot biquad BQ1..BQ3 yang dipakai crossover HPF/LPF. */
const val XOVER_BANDS = 3
const val EQ_MIN = -15
const val EQ_MAX = 15
val EQ_FREQS = intArrayOf(20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000)

fun freqLabel(hz: Int): String = if (hz >= 1000) {
    val k = hz / 1000f
    if (k == k.toInt().toFloat()) "${k.toInt()}k" else "%.2fk".format(k).trimEnd('0')
} else "$hz"

/** EQ mode register: ON=6, OFF=7, BIAMP=14, BIAMP_OFF=15 */
data class EqState(
    val left: List<Int> = List(EQ_BANDS) { 0 },
    val right: List<Int> = List(EQ_BANDS) { 0 },
    val enabled: Boolean = true,
    val biamp: Boolean = false,
) {
    /** Kode argumen CLI `eqm`: 0=ON 1=OFF 2=BIAMP 3=BIAMP_OFF */
    val cliCode: Int get() = when {
        enabled && !biamp -> 0
        !enabled && !biamp -> 1
        enabled && biamp -> 2
        else -> 3
    }
}

data class FaultDef(val key: String, val label: String, val critical: Boolean)

val FAULT_DEFS = listOf(
    FaultDef("oc_l", "Overcurrent L", true),
    FaultDef("oc_r", "Overcurrent R", true),
    FaultDef("dc_l", "DC offset L", true),
    FaultDef("dc_r", "DC offset R", true),
    FaultDef("uv", "Undervoltage", true),
    FaultDef("ov", "Overvoltage", true),
    FaultDef("clk", "Clock fault", true),
    FaultDef("bq", "Biquad write", false),
    FaultDef("crc", "CRC error", false),
    FaultDef("ot_sd", "Over-temp shutdown", true),
    FaultDef("ot_w", "Over-temp warning", false),
)

data class FaultEvent(val time: Long, val keys: List<String>)

data class TunerState(
    val mode: AudioMode = AudioMode.STEREO,
    val xover: Int = 150,
    val master: Int = 90,
    val sat: Int = 90,
    val sub: Int = 90,
    val ctr: Int = 85,         // level center, hanya mode 3 (firmware ≥ 2.6)
    val subInv: Boolean = false,        // polaritas sub dibalik (firmware ≥ 2.8)
    val wsub: WsubState? = null,        // status sub wireless (env wireless_master, firmware ≥ 2.9)
    val standbyMin: Int = 0,            // auto-standby (menit), 0 = mati
    val standbyActive: Boolean = false, // chip sedang di-standby-kan firmware
    val eqC: List<Int> = List(EQ_BANDS) { 0 },   // EQ center (mode 3)
    val eqS: List<Int> = List(EQ_BANDS) { 0 },   // EQ sub (mode 3)
    val gainCode: Int = 18,
    val amp: AmpState = AmpState.PLAY,
    val mix: List<Float> = listOf(1f, 0f, 0f, 1f), // ll lr rl rr
    val eq: EqState = EqState(),
    val faults: Map<String, Int> = emptyMap(),    // chip 1 (satelit)
    val faults2: Map<String, Int> = emptyMap(),   // chip 2 (sub), hanya 2-chip
    val faultHistory: List<FaultEvent> = emptyList(),
    val levelL: Int = -1200,   // deci-dB
    val levelR: Int = -1200,
    val peakL: Int = -1200,
    val peakR: Int = -1200,
    val level2L: Int = -1200,  // meter chip 2 / sub (hanya 2-chip, firmware v3.1)
    val level2R: Int = -1200,
    val peak2L: Int = -1200,
    val peak2R: Int = -1200,
    val hasLvl2: Boolean = false,
    val hasExt: Boolean = false, // firmware v2 (eqband/eqdump/lvl)
    val hasV3: Boolean = false,  // firmware v3 (readback DAC/mod/clip, simpan NVS, info)
    val dac: String? = null,     // "btl" | "pbtl" (chip 1)
    val dac2: String? = null,    // chip 2 (sub)
    val modMode: String? = null, // "bd" | "1spw" | "hybrid"
    val modFreq: String? = null, // "768k" ...
    val modBd: String? = null,   // "175k" ...
    val clipGain: Int? = null,   // deci-dB
    val clipL: Int? = null,
    val clipR: Int? = null,
    val info: Map<String, String> = emptyMap(),
    val saved: Boolean? = null,
    val eqpL: Int? = null,
    val eqpR: Int? = null,
    val eqp2L: Int? = null,      // profil chip 2 (mode 2-chip)
    val eqp2R: Int? = null,
    val synced: Boolean = false,
    val rttMs: Long = -1,
) {
    val gainDb: Float get() = -gainCode * 0.5f

    /** Di mode 2.1 EQ tidak boleh dimatikan, band 0..2 dicadangkan, mixer/profil diatur firmware. */
    val locked21: Boolean get() = mode.isTwoOne
    fun fault1(key: String): Boolean = (faults[key] ?: 0) != 0
    fun fault2(key: String): Boolean = (faults2[key] ?: 0) != 0
    fun faultActive(key: String): Boolean = fault1(key) || fault2(key)
    val anyFault: Boolean get() = FAULT_DEFS.any { faultActive(it.key) }
}

data class LogEntry(val time: Long, val dir: Char, val text: String) // dir: '>' TX, '<' RX, '!' info

/** Satu slave wireless (slot 0..3). */
data class WsubPeer(
    val mac: String,
    val slot: Int = 0,
    val link: Boolean = false,
    val fillMs: Int = 0,
    val und: Int = 0,
    val gap: Int = 0,
    val rate: Int = 0,
    val hiz: Boolean = false,
)

/** Status sub wireless — multi-sub hingga [maxPeers] (default 4).
 *  Kompatibel firmware lama: satu `peer` saja → [peers] berisi 0..1 entri.
 */
data class WsubState(
    val peers: List<WsubPeer> = emptyList(),
    val maxPeers: Int = 4,
    val scan: Boolean = false,
    val found: List<String> = emptyList(),
) {
    /** Firmware single-peer lama / peer pertama. */
    val peer: String? get() = peers.firstOrNull()?.mac
    val link: Boolean get() = peers.any { it.link }
    val fillMs: Int get() = peers.firstOrNull()?.fillMs ?: 0
    val und: Int get() = peers.sumOf { it.und }
    val gap: Int get() = peers.sumOf { it.gap }
    val rate: Int get() = peers.firstOrNull { it.link }?.rate ?: peers.firstOrNull()?.rate ?: 0
    val hiz: Boolean get() = peers.all { !it.link || it.hiz }
    val onlineCount: Int get() = peers.count { it.link }
    val canAdd: Boolean get() = peers.size < maxPeers
}
