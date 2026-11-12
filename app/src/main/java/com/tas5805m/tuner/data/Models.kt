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

/** Bitmask fault slave (WSUBS flt=): bit n = FAULT_DEFS[n] (urutan sama dengan firmware wsub_fault.h). */
fun faultKeysFromMask(mask: Int): List<String> =
    FAULT_DEFS.filterIndexed { i, _ -> (mask shr i) and 1 != 0 }.map { it.key }

data class TunerState(
    val mode: AudioMode = AudioMode.STEREO,
    val xover: Int = 150,
    val master: Int = 90,
    val sat: Int = 90,
    val sub: Int = 90,
    val ctr: Int = 85,         // level center, hanya mode 3 (firmware ≥ 2.6)
    val subInv: Boolean = false,        // polaritas sub dibalik (firmware ≥ 2.8)
    val subVoice: SubVoice? = null,     // voicing sub (firmware ≥ 2.9.14)
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
    val faultIncidents: List<FaultIncident> = emptyList(),   // riwayat fault: waktu mulai, durasi, jumlah (3.7.0)
    val faultsReadMs: Long = 0,                               // kapan fault chip terakhir dibaca (0 = belum pernah) — "OK" tanpa pembacaan = status palsu
    val faultAuto: Boolean = false,                           // pantau fault chip otomatis tiap 5 dtk (tab Monitor)
    val levelL: Int = -1200,   // deci-dB
    val levelR: Int = -1200,
    val peakL: Int = -1200,
    val peakR: Int = -1200,
    val level2L: Int = -1200,  // meter chip 2 / sub (hanya 2-chip, firmware v3.1)
    val level2R: Int = -1200,
    val peak2L: Int = -1200,
    val peak2R: Int = -1200,
    val hasLvl2: Boolean = false,
    val hasWlvl: Boolean = false,                 // master mendukung `wlvl` (firmware ≥ 2.9.15)
    val wsubLvl: Map<Int, Int> = emptyMap(),      // slot → level sub wireless (deci-dB); slot tidak ada = n/a
    val wsubPeak: Map<Int, Int> = emptyMap(),     // slot → peak-hold (deci-dB)
    val wsubTiming: WsubTimingInfo? = null,                    // basis/perintah latensi dari master (WSUBT)
    val wsubSeen: Map<Int, WsubSeen> = emptyMap(),             // slot → waktu terakhir STATUS/DIAG/level diterima
    val wsubTrend: Map<Int, List<WsubTrendPt>> = emptyMap(),   // slot → tren (maks 90 titik ≈ 3 menit)
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
    val wsubFault: Boolean get() = wsub?.peers?.any { it.flt > 0 } == true
    val anyFault: Boolean get() = FAULT_DEFS.any { faultActive(it.key) } || wsubFault
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
    /** Diagnostik radio dari slave (firmware ≥ 2.9.9); -1 = tidak tersedia. */
    val rxAudio: Int = -1,   // paket audio diterima per detik (normal ≈ 86)
    val rxCtrl: Int = -1,    // paket kontrol per detik (normal ≈ 10)
    val rej: Int = 0,        // paket ditolak (MAC bukan master)
    val stx: Int = 0,        // kegagalan kirim STATUS dari slave
    /** Fault amp slave (firmware slave ≥ 2.9.15): bitmask FAULT_DEFS; -1 = tidak tersedia (slave lama / offline). */
    val flt: Int = -1,
    val fcnt: Int = 0,       // jumlah kejadian fault sejak boot slave
    val diag: WsubDiag? = null,   // baris WSUBD (jitter & versi) — firmware ≥ 2.9.10
    /** Firmware slave ≥ 2.9.16: uptime (detik), alasan reset terakhir (esp_reset_reason), RSSI (dBm, 0 = n/a). */
    val upS: Int = -1,
    val rst: Int = -1,
    val rssi: Int = 0,
) {
    val fltKeys: List<String> get() = if (flt > 0) faultKeysFromMask(flt) else emptyList()
}

/** Alasan reset ESP32 (esp_reset_reason_t) untuk tampilan. */
fun resetReasonLabel(code: Int): String = when (code) {
    1 -> "power-on"
    2 -> "reset eksternal"
    3 -> "restart software"
    4 -> "crash (panic)"
    5 -> "watchdog interrupt"
    6 -> "watchdog task"
    7 -> "watchdog"
    8 -> "deep sleep"
    9 -> "brownout (tegangan drop)"
    10 -> "SDIO"
    else -> "tidak diketahui"
}

/** Reset yang menandakan masalah (crash / watchdog / brownout), bukan power-on atau restart yang disengaja. */
fun isAbnormalReset(code: Int): Boolean = code == 4 || code == 5 || code == 6 || code == 7 || code == 9

/** Waktu latensi dari master (WSUBT): basis = latensi satelit + trim; cmd = yang sedang diperintahkan ke slave (basis + tambahan otomatis). */
data class WsubTimingInfo(val latBase: Int, val latCmd: Int, val extraMax: Int, val aligned: Boolean)

/** Diagnostik jitter/buffer satu slave (baris WSUBD, relay lewat master). */
data class WsubDiag(
    val fw: Int = 0,                 // patch firmware slave (2.9.<n>); 0 = ?
    val gaps: List<Int> = emptyList(),   // jumlah gap kedatangan audio > 40/60/80/100/200/500 ms (kumulatif, wrap 16 bit)
    val gmaxMs: Int = 0,             // gap terbesar sejak DIAG sebelumnya
    val estMs: Float = 0f,           // perkiraan jitter
    val needMs: Float = 0f,          // latensi yang diminta jitter slave; 0 = slave lama
    val fillMinMs: Float = -1f,      // isi buffer minimum; -1 = n/a
    val adapt: Int = 0,
    val jmax: Int = 0,
    val pkt: Int = 0,
    val ageMs: Int = 0,              // umur data di master
)

/** Kapan data tiap slave terakhir diterima (ms epoch) — dasar validasi "data basi". */
data class WsubSeen(val statMs: Long = 0, val diagMs: Long = 0, val lvlMs: Long = 0)

/** Satu titik tren per slave (diambil tiap baris WSUBS ≈ 2 dtk). */
data class WsubTrendPt(val t: Long, val fill: Int, val rx: Int, val und: Int, val gap: Int, val lvl: Int?, val up: Int = -1, val rssi: Int = 0)

/** Status sub wireless — multi-sub hingga [maxPeers] (default 4).
 *  Kompatibel firmware lama: satu `peer` saja → [peers] berisi 0..1 entri.
 */
/** Voicing sub (firmware ≥ 2.9.14): HPF subsonic, boost parametrik, harmonik psikoakustik, limiter. */
data class SubVoice(
    val on: Boolean = false,
    val hpf: Int = 32,        // Hz, 20..60
    val pkHz: Int = 55,       // Hz, 30..120
    val pkDb10: Int = 0,      // ×10 dB, −60..+90
    val pkQ10: Int = 10,      // ×10, 5..30
    val harm: Int = 0,        // %, 0..100
    val limit: Int = 100,     // % dari full-scale, 50..100
)

data class WsubState(
    val peers: List<WsubPeer> = emptyList(),
    val maxPeers: Int = 4,
    val scan: Boolean = false,
    val found: List<String> = emptyList(),
    /** Sinkronisasi & diagnostik master (firmware ≥ 2.9.8). */
    val lat: Int = 0,          // latensi sub yang diminta (ms) = latensi satelit + trim
    val trim: Int = 0,         // geser sub terhadap satelit (ms)
    val txf: Long = 0,         // esp_now_send gagal diantrekan
    val err: String = "none",  // error terakhir esp_now_send
    val ackOk: Long = 0,       // paket di-ACK slave
    val ackFail: Long = 0,     // paket tanpa ACK
    val nomem: Long = 0,       // antrean TX penuh
    val drop: Long = 0,        // paket audio dibuang master (radio sibuk)
    val heap: Long = 0,        // heap internal master bebas (byte)
    val heapMin: Long = 0,     // heap internal terendah sejak boot (byte)
    val ch: Int = 0,           // channel Wi-Fi master
    val diag: Boolean = false, // firmware mengirim diagnostik
    val mfw: Int = 0,          // patch firmware master (2.9.<n>); 0 = master lama
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
