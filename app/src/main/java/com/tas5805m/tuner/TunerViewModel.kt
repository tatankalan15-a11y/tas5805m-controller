package com.tas5805m.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tas5805m.tuner.bt.BtDev
import com.tas5805m.tuner.bt.BtUtil
import com.tas5805m.tuner.bt.SppClient
import com.tas5805m.tuner.data.AmpState
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.EQ_BANDS
import com.tas5805m.tuner.data.EQ_MAX
import com.tas5805m.tuner.data.EQ_MIN
import com.tas5805m.tuner.data.XOVER_BANDS
import com.tas5805m.tuner.data.EqState
import com.tas5805m.tuner.data.FAULT_DEFS
import com.tas5805m.tuner.data.FaultEvent
import com.tas5805m.tuner.data.LogEntry
import com.tas5805m.tuner.data.Preset
import com.tas5805m.tuner.data.Protocol
import com.tas5805m.tuner.data.Rx
import com.tas5805m.tuner.data.Settings
import com.tas5805m.tuner.data.Store
import com.tas5805m.tuner.data.TunerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val client = SppClient(viewModelScope)
    val store = Store(app)

    val link: StateFlow<SppClient.Link> = client.link
    val linkError: StateFlow<String?> = client.error

    private val _ui = MutableStateFlow(TunerState())
    val ui: StateFlow<TunerState> = _ui.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _devices = MutableStateFlow<List<BtDev>>(emptyList())
    val devices: StateFlow<List<BtDev>> = _devices.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val settings: StateFlow<Settings> get() = store.settings

    private val edited = HashMap<String, Long>()
    private var pingSentAt = 0L
    private var monitorJob: Job? = null
    private var autoTried = false

    init {
        viewModelScope.launch { client.lines.collect { onLine(it) } }
        viewModelScope.launch { client.tx.collect { addLog('>', it) } }
        viewModelScope.launch {
            var prev = SppClient.Link.IDLE
            client.link.collect { l ->
                if (l == SppClient.Link.CONNECTED && prev != SppClient.Link.CONNECTED) onConnected()
                if (l != SppClient.Link.CONNECTED) {
                    stopMonitor()
                    _ui.update { it.copy(synced = false, hasExt = false, hasV3 = false, hasLvl2 = false) }
                }
                if (l == SppClient.Link.FAILED) addLog('!', "Koneksi gagal: ${client.error.value ?: "?"}")
                prev = l
            }
        }
    }

    // ───────────────────────── koneksi ─────────────────────────

    fun refreshDevices() {
        _devices.value = BtUtil.bonded(getApplication())
    }

    fun connect(address: String, name: String) {
        val dev = BtUtil.remote(getApplication(), address) ?: run {
            showToast("Alamat perangkat tidak valid"); return
        }
        store.updateSettings { it.copy(lastAddress = address, lastName = name) }
        addLog('!', "Menyambung ke $name ($address)")
        client.connect(dev)
    }

    fun tryAutoConnect() {
        if (autoTried) return
        if (!BtUtil.hasPermission(getApplication())) return
        autoTried = true
        val s = store.settings.value
        val addr = s.lastAddress
        if (s.autoReconnect && addr != null) connect(addr, s.lastName ?: addr)
    }

    fun disconnect() {
        autoTried = true
        client.disconnect()
    }

    private fun onConnected() {
        addLog('!', "Terhubung")
        client.send("caps")
        client.send("status")
        client.send("eqdump")
    }

    // ───────────────────────── penerimaan ─────────────────────────

    private fun touch(f: String) { edited[f] = SystemClock.uptimeMillis() }
    private fun fresh(f: String, ms: Long = 700): Boolean =
        SystemClock.uptimeMillis() - (edited[f] ?: 0L) < ms

    private fun onLine(line: String) {
        val rx = Protocol.parse(line)
        if (!(rx is Rx.Level || rx is Rx.Level2)) addLog('<', line)
        when (rx) {
            is Rx.Mode -> _ui.update { s ->
                s.copy(
                    mode = if (fresh("mode", 1500)) s.mode else AudioMode.from(rx.mode),
                    xover = if (fresh("xover")) s.xover else rx.xover,
                    master = if (fresh("master")) s.master else rx.master,
                    sat = if (fresh("sat")) s.sat else rx.sat,
                    sub = if (fresh("sub")) s.sub else rx.sub,
                    synced = true,
                )
            }
            is Rx.State -> _ui.update { s ->
                s.copy(
                    gainCode = if (fresh("gain")) s.gainCode else rx.gain,
                    amp = if (fresh("amp", 1200)) s.amp else AmpState.from(rx.amp),
                    mix = if (fresh("mix")) s.mix else rx.mix,
                    eq = if (s.hasExt) s.eq else s.eq.copy(enabled = rx.eq),
                )
            }
            is Rx.Fault -> onFault(rx.values, 1)
            is Rx.Fault2 -> onFault(rx.values, 2)
            is Rx.Eq -> _ui.update { s ->
                if (fresh("eq", 900)) s
                else s.copy(eq = if (rx.channel == 'l') s.eq.copy(left = rx.gains) else s.eq.copy(right = rx.gains))
            }
            is Rx.EqMode -> _ui.update { s ->
                if (fresh("eqmode", 1500)) s else {
                    val (en, bi) = Protocol.decodeEqMode(rx.raw)
                    s.copy(eq = s.eq.copy(enabled = en, biamp = bi))
                }
            }
            is Rx.Level -> _ui.update { s ->
                s.copy(
                    levelL = rx.l, levelR = rx.r,
                    peakL = if (rx.l >= s.peakL) rx.l else (s.peakL - 12).coerceAtLeast(-1200),
                    peakR = if (rx.r >= s.peakR) rx.r else (s.peakR - 12).coerceAtLeast(-1200),
                )
            }
            is Rx.Level2 -> _ui.update { s ->
                s.copy(
                    level2L = rx.l, level2R = rx.r,
                    peak2L = if (rx.l >= s.peak2L) rx.l else (s.peak2L - 12).coerceAtLeast(-1200),
                    peak2R = if (rx.r >= s.peak2R) rx.r else (s.peak2R - 12).coerceAtLeast(-1200),
                )
            }
            is Rx.Caps -> {
                val v3 = rx.items.contains("v3")
                _ui.update { it.copy(hasExt = rx.items.contains("v2") || v3, hasV3 = v3, hasLvl2 = rx.items.contains("lvl2")) }
                if (v3) client.send("dump")
            }
            is Rx.Dac -> _ui.update { it.copy(dac = rx.mode) }
            is Rx.Dac2 -> _ui.update { it.copy(dac2 = rx.mode) }
            is Rx.Mod -> _ui.update { it.copy(modMode = rx.mode, modFreq = rx.freq, modBd = rx.bd) }
            is Rx.Clip -> _ui.update { it.copy(clipGain = rx.gain, clipL = rx.ml, clipR = rx.mr) }
            is Rx.Info -> _ui.update { it.copy(info = rx.values) }
            is Rx.Saved -> _ui.update { it.copy(saved = rx.saved) }
            is Rx.EqProfile -> _ui.update { it.copy(eqpL = rx.l, eqpR = rx.r) }
            is Rx.EqProfile2 -> _ui.update { it.copy(eqp2L = rx.l, eqp2R = rx.r) }
            is Rx.Pong -> onPong()
            is Rx.Ok -> if (rx.what == "ping") onPong()
            is Rx.Err -> {
                showToast("Firmware menolak perintah: ${rx.what}")
                // Mode ditolak (mis. 2-chip tanpa chip kedua): kembalikan UI ke state firmware.
                if (rx.what.startsWith("mode")) { edited.remove("mode"); client.send("status") }
            }
            else -> Unit
        }
    }

    /** Firmware terbaru tidak punya handler `ping` (balasan hanya "OK ping"); PONG tetap diterima bila ada. */
    private fun onPong() {
        if (pingSentAt == 0L) return
        val rtt = SystemClock.uptimeMillis() - pingSentAt
        pingSentAt = 0L
        _ui.update { it.copy(rttMs = rtt) }
    }

    /** chip 1 = satelit (FAULT), chip 2 = sub (FAULT2). Kunci riwayat chip 2 diberi akhiran "@2". */
    private fun onFault(values: Map<String, Int>, chip: Int) {
        _ui.update { s ->
            val f1 = if (chip == 1) values else s.faults
            val f2 = if (chip == 2) values else s.faults2
            val active = FAULT_DEFS.filter { (f1[it.key] ?: 0) != 0 }.map { it.key } +
                FAULT_DEFS.filter { (f2[it.key] ?: 0) != 0 }.map { it.key + "@2" }
            var hist = s.faultHistory
            if (active.isNotEmpty() && hist.lastOrNull()?.keys != active) {
                hist = (hist + FaultEvent(System.currentTimeMillis(), active)).takeLast(50)
            }
            s.copy(faults = f1, faults2 = f2, faultHistory = hist)
        }
    }

    // ───────────────────────── kontrol dasar ─────────────────────────

    fun setMode(mode: AudioMode) {
        touch("mode")
        _ui.update { it.copy(mode = mode) }
        client.send("mode ${mode.code}")
        client.send("eqdump")
    }

    fun setMaster(v: Int) {
        touch("master")
        val x = v.coerceIn(0, 100)
        _ui.update { it.copy(master = x) }
        client.send("master $x", "master")
    }

    fun setGainCode(v: Int) {
        touch("gain")
        val x = v.coerceIn(0, 31)
        _ui.update { it.copy(gainCode = x) }
        client.send("gain $x", "gain")
    }

    fun setAmp(a: AmpState) {
        touch("amp")
        _ui.update { it.copy(amp = a) }
        client.send("ampstate ${a.cmd}")
    }

    fun setXover(hz: Int) {
        touch("xover")
        val x = hz.coerceIn(60, 150)
        _ui.update { it.copy(xover = x) }
        client.send("xover $x", "xover")
    }

    fun reapplyMode() {
        client.send("mode ${_ui.value.mode.code}")
    }

    fun setSat(v: Int) {
        touch("sat")
        _ui.update { it.copy(sat = v.coerceIn(0, 100)) }
        client.send("satvol ${v.coerceIn(0, 100)}", "sat")
    }

    fun setSub(v: Int) {
        touch("sub")
        _ui.update { it.copy(sub = v.coerceIn(0, 100)) }
        client.send("subvol ${v.coerceIn(0, 100)}", "sub")
    }

    private fun lockedToast(what: String) {
        showToast("$what dikunci di mode 2.1 (dikelola crossover firmware)")
    }

    fun setMixer(idx: Int, value: Float) {
        if (_ui.value.locked21) { lockedToast("Mixer"); return }
        touch("mix")
        val v = (Math.round(value * 100f) / 100f).coerceIn(0f, 2f)
        _ui.update { s -> s.copy(mix = s.mix.toMutableList().also { it[idx] = v }) }
        client.send("mixer ${listOf("ll", "lr", "rl", "rr")[idx]} ${"%.2f".format(java.util.Locale.US, v)}", "mix$idx")
    }

    fun setMixerAll(m: List<Float>) {
        if (_ui.value.locked21) { lockedToast("Mixer"); return }
        for (i in 0 until 4) setMixer(i, m[i])
    }

    // ───────────────────────── EQ ─────────────────────────

    /** ch: 'l', 'r', atau 'b' (keduanya) */
    fun setEqBand(ch: Char, band: Int, gain: Int) {
        if (band !in 0 until EQ_BANDS) return
        // Band 0..2 = slot crossover di mode 2.1 -> jangan ditimpa
        if (_ui.value.locked21 && band < XOVER_BANDS) return
        val g = gain.coerceIn(EQ_MIN, EQ_MAX)
        val ext = _ui.value.hasExt
        if (!ext && ch == 'r') return // firmware lama tidak bisa EQ kanan terpisah
        touch("eq")
        _ui.update { s ->
            val l = s.eq.left.toMutableList()
            val r = s.eq.right.toMutableList()
            if (ch == 'l' || ch == 'b') l[band] = g
            if (ch == 'r' || ch == 'b') r[band] = g
            s.copy(eq = s.eq.copy(left = l, right = r))
        }
        if (ext) client.send("eqband $ch $band $g", "eq$ch$band")
        else client.send("eq l $band $g", "eql$band")
    }

    fun setEqEnabled(on: Boolean) {
        if (_ui.value.locked21) {
            if (!on) showToast("EQ tidak bisa dimatikan di mode 2.1: crossover memakai DSP EQ")
            return
        }
        touch("eqmode")
        val ns = _ui.value.eq.copy(enabled = on)
        _ui.update { it.copy(eq = ns) }
        if (_ui.value.hasExt) client.send("eqm ${ns.cliCode}") else client.send(if (on) "eq on" else "eq off")
    }

    fun setEqBiamp(on: Boolean) {
        if (!_ui.value.hasExt) return
        if (_ui.value.locked21) return // mode 2.1 diatur firmware (mode 2 = BIAMP otomatis)
        touch("eqmode")
        val ns = _ui.value.eq.copy(biamp = on)
        _ui.update { it.copy(eq = ns) }
        client.send("eqm ${ns.cliCode}")
    }

    fun resetEq() {
        val ch = if (_ui.value.hasExt) 'b' else 'l'
        val from = if (_ui.value.locked21) XOVER_BANDS else 0
        for (i in from until EQ_BANDS) setEqBand(ch, i, 0)
    }

    fun applyEqCurve(left: List<Int>, right: List<Int>) {
        val ext = _ui.value.hasExt
        val from = if (_ui.value.locked21) XOVER_BANDS else 0
        for (i in from until EQ_BANDS) {
            if (!ext) { setEqBand('l', i, left[i]); continue }
            if (left[i] == right[i]) setEqBand('b', i, left[i])
            else { setEqBand('l', i, left[i]); setEqBand('r', i, right[i]) }
        }
    }

    // ───────────────────────── monitor ─────────────────────────

    fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (_ui.value.hasExt) client.send("lvl", "lvl")
                if (_ui.value.hasLvl2 && _ui.value.mode == AudioMode.TWO_CHIP_21) client.send("lvl2", "lvl2")
                if (tick % 12 == 0) client.send("status", "status")
                if (_ui.value.hasV3 && tick % 12 == 6) client.send("info", "info")
                tick++
                delay(160)
            }
        }
    }

    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun readFaults() { client.send("fault") }

    fun clearFaultHistory() { _ui.update { it.copy(faultHistory = emptyList()) } }

    fun ping() {
        pingSentAt = SystemClock.uptimeMillis()
        client.send("ping")
    }

    fun sync() {
        client.send("caps")
        if (_ui.value.hasV3) client.send("dump") else { client.send("status"); client.send("eqdump") }
    }

    // ───────────────────────── konsol & lanjutan ─────────────────────────

    fun setDac(mode: String) {
        client.send(if (_ui.value.hasV3) "dacset $mode" else "dacmode $mode")
        // Mode 2-chip: firmware menerapkannya hanya ke chip 2 (sub)
        _ui.update { if (it.mode == AudioMode.TWO_CHIP_21 && it.hasLvl2) it.copy(dac2 = mode) else it.copy(dac = mode) }
    }

    fun setModulation(mode: String, freq: String, bd: String) {
        client.send(if (_ui.value.hasV3) "modset $mode $freq $bd" else "mod $mode $freq $bd")
        _ui.update { it.copy(modMode = mode, modFreq = freq, modBd = bd) }
    }

    fun setClipper(gain: Int, left: Int, right: Int) {
        client.send(if (_ui.value.hasV3) "clipset $gain $left $right" else "clip $gain $left $right")
        _ui.update { it.copy(clipGain = gain, clipL = left, clipR = right) }
    }

    fun setMixerMode(name: String) {
        if (_ui.value.locked21) { lockedToast("Mode mixer"); return }
        if (_ui.value.hasV3) client.send("mixmode $name") else client.send("mixermode $name")
    }

    fun setEqProfile(ch: Char, n: Int) {
        if (_ui.value.locked21) { lockedToast("Profil filter"); return }
        if (_ui.value.hasV3) client.send("eqpset $ch $n")
    }

    fun saveToDevice() { client.send("save") }
    fun eraseSaved() { client.send("erase") }
    fun rebootDevice() { client.send("reboot") }

    fun raw(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty()) return
        client.send(c)
    }

    fun clearLog() { _log.value = emptyList() }

    // ───────────────────────── preset ─────────────────────────

    fun snapshot(name: String): Preset {
        val s = _ui.value
        return Preset(
            name = name, mode = s.mode.code, master = s.master, gainCode = s.gainCode,
            xover = s.xover, sat = s.sat, sub = s.sub,
            eqL = s.eq.left, eqR = s.eq.right, eqEnabled = s.eq.enabled, eqBiamp = s.eq.biamp, mix = s.mix,
        )
    }

    fun saveCurrentAs(name: String) {
        store.upsert(snapshot(name.trim().ifEmpty { "Preset" }))
        showToast("Preset disimpan")
    }

    fun overwrite(p: Preset) {
        store.upsert(snapshot(p.name).copy(id = p.id))
        showToast("Preset diperbarui")
    }

    fun applyPreset(p: Preset) {
        val ext = _ui.value.hasExt
        val target = if (p.eqOnly) _ui.value.mode else AudioMode.from(p.mode)
        if (!p.eqOnly) {
            val m = AudioMode.from(p.mode)
            client.send("mode ${m.code}")
            _ui.update { it.copy(mode = m) }; touch("mode")
            setXover(p.xover)
            setMaster(p.master)
            if (m.isTwoOne) { setSat(p.sat); setSub(p.sub) }
            setGainCode(p.gainCode)
            if (m == AudioMode.STEREO) setMixerAll(p.mix)
        }
        // Mode 2.1: jangan kirim `eqm` (firmware menjaga BIAMP/EQ-on); band 0..2 dilewati di applyEqCurve
        if (ext && !target.isTwoOne) {
            val en = p.eqEnabled
            val bi = p.eqBiamp && !p.eqOnly
            val ns = EqState(p.eqL, p.eqR, if (p.eqOnly) _ui.value.eq.enabled else en, if (p.eqOnly) _ui.value.eq.biamp else bi)
            touch("eqmode")
            _ui.update { it.copy(eq = it.eq.copy(enabled = ns.enabled, biamp = ns.biamp)) }
            client.send("eqm ${ns.cliCode}")
        }
        applyEqCurve(p.eqL, if (_ui.value.eq.biamp || (!p.eqOnly && p.eqBiamp)) p.eqR else p.eqL)
        showToast("Preset \"${p.name}\" diterapkan")
        viewModelScope.launch { delay(900); client.send("status"); client.send("eqdump") }
    }

    // ───────────────────────── util ─────────────────────────

    fun showToast(msg: String) { _toast.value = msg }
    fun toastShown() { _toast.value = null }

    private fun addLog(dir: Char, text: String) {
        _log.update { (it + LogEntry(System.currentTimeMillis(), dir, text)).takeLast(400) }
    }

    override fun onCleared() {
        client.disconnect()
        super.onCleared()
    }
}
