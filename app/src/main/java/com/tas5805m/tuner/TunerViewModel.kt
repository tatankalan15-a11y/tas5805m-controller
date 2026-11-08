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
import com.tas5805m.tuner.data.WsubState
import kotlin.math.roundToInt
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
    private var manualDisc = false
    private var reconnectJob: Job? = null
    private val lastLog = HashMap<String, Pair<String, Long>>()

    init {
        viewModelScope.launch { client.lines.collect { onLine(it) } }
        viewModelScope.launch { client.tx.collect { addLog('>', it) } }
        viewModelScope.launch {
            var prev = SppClient.Link.IDLE
            client.link.collect { l ->
                if (l == SppClient.Link.CONNECTED && prev != SppClient.Link.CONNECTED) onConnected()
                if (l != SppClient.Link.CONNECTED) {
                    stopMonitor()
                    _ui.update { it.copy(synced = false, hasExt = false, hasV3 = false, hasLvl2 = false, wsub = null) }
                    // Putus tanpa diminta pengguna (ESP32 reboot / di luar jangkauan) → coba sambung ulang.
                    if (prev == SppClient.Link.CONNECTED && !manualDisc) scheduleReconnect()
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
        reconnectJob?.cancel()
        manualDisc = false
        doConnect(address, name)
    }

    private fun doConnect(address: String, name: String) {
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
        manualDisc = true
        reconnectJob?.cancel()
        autoTried = true
        client.disconnect()
    }

    /** Sambung ulang otomatis (maks 5×, jeda 3 dtk) bila koneksi putus sendiri dan opsi "Sambung otomatis" aktif. */
    private fun scheduleReconnect() {
        val st = store.settings.value
        val addr = st.lastAddress ?: return
        if (!st.autoReconnect) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for (i in 1..5) {
                delay(3000)
                if (manualDisc || client.link.value == SppClient.Link.CONNECTED) return@launch
                addLog('!', "Menyambung ulang ($i/5)…")
                doConnect(addr, st.lastName ?: addr)
                var waited = 0
                while (client.link.value == SppClient.Link.CONNECTING && waited < 20) { delay(1000); waited++ }
                if (client.link.value == SppClient.Link.CONNECTED) return@launch
            }
            addLog('!', "Sambung ulang gagal — sambungkan manual dari layar Koneksi.")
        }
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
        if (!(rx is Rx.Level || rx is Rx.Level2) && shouldLog(line)) addLog('<', line)
        when (rx) {
            is Rx.Mode -> _ui.update { s ->
                s.copy(
                    mode = if (fresh("mode", 1500)) s.mode else AudioMode.from(rx.mode),
                    xover = if (fresh("xover")) s.xover else rx.xover,
                    master = if (fresh("master")) s.master else rx.master,
                    sat = if (fresh("sat")) s.sat else rx.sat,
                    sub = if (fresh("sub")) s.sub else rx.sub,
                    ctr = if (fresh("ctr")) s.ctr else rx.ctr,
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
            is Rx.Wsub -> _ui.update { s ->
                // pertahankan diagnostik slave (WSUBS) dari status sebelumnya sampai baris baru tiba
                val old = s.wsub?.peers.orEmpty()
                val peers = rx.state.peers.map { p ->
                    val o = old.firstOrNull { it.mac.equals(p.mac, ignoreCase = true) }
                    if (o != null) p.copy(rxAudio = o.rxAudio, rxCtrl = o.rxCtrl, rej = o.rej, stx = o.stx) else p
                }
                val keep = fresh("wsubtrim", 1500)   // jangan timpa trim yang baru diketuk dengan status lama
                val cur = s.wsub
                val merged = if (keep && cur != null) rx.state.copy(peers = peers, trim = cur.trim, lat = cur.lat)
                             else rx.state.copy(peers = peers)
                s.copy(wsub = merged)
            }
            is Rx.WsubStat -> _ui.update { s ->
                val cur = s.wsub
                if (cur == null) s
                else s.copy(
                    wsub = cur.copy(
                        peers = cur.peers.map { p ->
                            if (p.mac.equals(rx.mac, ignoreCase = true))
                                p.copy(rxAudio = rx.rxAudio, rxCtrl = rx.rxCtrl, rej = rx.rej, stx = rx.stx)
                            else p
                        }
                    )
                )
            }
            is Rx.WsubPeerLine -> _ui.update { s ->
                val cur = s.wsub ?: WsubState()
                val rest = cur.peers.filterNot { it.mac.equals(rx.peer.mac, ignoreCase = true) || it.slot == rx.peer.slot }
                val merged = (rest + rx.peer).sortedBy { it.slot }.take(cur.maxPeers)
                s.copy(wsub = cur.copy(peers = merged))
            }
            is Rx.Fx -> _ui.update { s ->
                s.copy(
                    subInv = if (fresh("subinv")) s.subInv else rx.inv,
                    standbyMin = if (fresh("stby")) s.standbyMin else rx.stby,
                    standbyActive = rx.sb,
                )
            }
            is Rx.EqC -> _ui.update { s -> if (fresh("eqc", 900)) s else s.copy(eqC = rx.gains) }
            is Rx.EqS -> _ui.update { s -> if (fresh("eqc", 900)) s else s.copy(eqS = rx.gains) }
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
        _ui.update { it.copy(master = v.coerceIn(0, 100), saved = false) }
        client.send("master ${v.coerceIn(0, 100)}", "master")
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
        _ui.update { it.copy(sat = v.coerceIn(0, 100), saved = false) }
        client.send("satvol ${v.coerceIn(0, 100)}", "sat")
    }

    // ───────────────────── Sub wireless ─────────────────────

    fun requestStatus() { client.send("status", "status") }

    /** Cari slave (HELLO) ±8 dtk; hasil muncul di daftar "found". */
    fun wsubScan() {
        client.send("wsub_scan")
        viewModelScope.launch { repeat(9) { delay(1000); client.send("status") } }
    }

    fun wsubPair(mac: String) {
        client.send("wsub_pair $mac")
        viewModelScope.launch { repeat(6) { delay(1000); client.send("status") } }
    }

    fun wsubUnpair(mac: String? = null) {
        if (mac.isNullOrBlank()) client.send("wsub_unpair")
        else client.send("wsub_unpair $mac")
        viewModelScope.launch { repeat(4) { delay(800); client.send("status") } }
    }

    /** Geser sub terhadap satelit (ms, −60..100; + = sub lebih lambat). Firmware ≥ 2.9.8. */
    fun wsubTrim(ms: Int) {
        val t = ms.coerceIn(-60, 100)
        // Optimistis: tap beruntun menumpuk dari nilai terbaru, bukan dari status lama yang belum kembali.
        _ui.update { s -> s.copy(wsub = s.wsub?.let { w -> w.copy(trim = t, lat = (w.lat + t - w.trim).coerceAtLeast(0)) }) }
        touch("wsubtrim")
        client.send("wsub_trim $t", "wsub_trim")   // latest-wins
        viewModelScope.launch { delay(900); client.send("status") }
    }

    fun wsubTrimBy(delta: Int) = wsubTrim((_ui.value.wsub?.trim ?: 0) + delta)

    /** Nolkan penghitung diagnostik TX master (txf/ack/nomem). */
    fun wsubDiagReset() {
        client.send("wsub_diag_reset")
    }

    fun wsubUnpairAll() {
        client.send("wsub_unpair_all")
        // Fallback firmware lama: unpair tanpa argumen
        client.send("wsub_unpair")
        viewModelScope.launch { repeat(4) { delay(800); client.send("status") } }
    }

    /** Balik polaritas sub (mode 2.1 / 3.1). */
    fun setSubInv(inv: Boolean) {
        touch("subinv")
        _ui.update { it.copy(subInv = inv, saved = false) }
        client.send("subinv ${if (inv) 1 else 0}")
    }

    /** Auto-standby: 0 = mati, selain itu menit tanpa audio sebelum amp → Hi-Z. */
    fun setStandby(min: Int) {
        touch("stby")
        _ui.update { it.copy(standbyMin = min.coerceIn(0, 240), saved = false) }
        client.send("standby ${min.coerceIn(0, 240)}")
    }

    /**
     * Dialog boost (mode 3): EQ khusus center. Band 2 kHz = +db, 1,25 kHz & 3,15 kHz = setengahnya.
     * Satelit dan sub tidak berubah.
     */
    fun setDialogBoost(db: Int) {
        val d = db.coerceIn(0, 6)
        val half = (d + 1) / 2
        touch("eqc")
        _ui.update { s ->
            val c = s.eqC.toMutableList()
            c[9] = half; c[10] = d; c[11] = half
            s.copy(eqC = c, saved = false)
        }
        client.send("eqc c 10 $d", "eqc10")
        client.send("eqc c 9 $half", "eqc9")
        client.send("eqc c 11 $half", "eqc11")
    }

    /** Level center (hanya mode 3). */
    fun setCtr(v: Int) {
        touch("ctr")
        _ui.update { it.copy(ctr = v.coerceIn(0, 100), saved = false) }
        client.send("ctr ${v.coerceIn(0, 100)}", "ctr")
    }

    fun setSub(v: Int) {
        touch("sub")
        _ui.update { it.copy(sub = v.coerceIn(0, 100), saved = false) }
        client.send("subvol ${v.coerceIn(0, 100)}", "sub")
    }

    /**
     * Balance sat vs sub: 0 = hanya sat, 0.5 = seimbang (keduanya 100%), 1 = hanya sub.
     * Sisi yang lebih besar selalu 100% sehingga total volume tidak turun di tengah.
     * Kebalikannya ada di [bassBalanceOf].
     */
    fun setBassBalance(balance: Float) {
        val b = balance.coerceIn(0f, 1f)
        val sat = (200f * (1f - b)).roundToInt().coerceIn(0, 100)
        val sub = (200f * b).roundToInt().coerceIn(0, 100)
        setSat(sat)
        setSub(sub)
    }

    private fun lockedToast(what: String) {
        showToast("$what dikunci di mode 2.1 (dikelola crossover firmware)")
    }

    fun setMixer(idx: Int, value: Float) {
        // Mode 2 (1-chip 2.1): mixer dipakai routing sat/sub — dilarang.
        // Mode 1: firmware menulis hanya ke chip satelit.
        if (_ui.value.mode == AudioMode.ONE_CHIP_21) {
            showToast("Mixer dikunci di mode 2 (1-chip 2.1)")
            return
        }
        touch("mix")
        val v = (Math.round(value * 100f) / 100f).coerceIn(-1f, 2f)   // negatif = pelebaran stereo
        _ui.update { s -> s.copy(mix = s.mix.toMutableList().also { it[idx] = v }, saved = false) }
        client.send("mixer ${listOf("ll", "lr", "rl", "rr")[idx]} ${"%.2f".format(java.util.Locale.US, v)}", "mix$idx")
    }

    fun setMixerAll(m: List<Float>) {
        if (_ui.value.mode == AudioMode.ONE_CHIP_21) {
            showToast("Mixer dikunci di mode 2 (1-chip 2.1)")
            return
        }
        for (i in 0 until 4) setMixer(i, m[i])
    }

    /** Apply mixer matrix then save to NVS (long-press preset). */
    fun setMixerAllAndSave(m: List<Float>) {
        setMixerAll(m)
        saveToDevice()
        showToast("Mixer diterapkan + disimpan ke NVS")
    }

    fun muteAll() {
        setAmp(AmpState.MUTE)
        showToast("Mute All")
    }

    fun unmutePlay() {
        setAmp(AmpState.PLAY)
    }

    fun resetToStereoMix() {
        setMixerAll(listOf(1f, 0f, 0f, 1f))
        showToast("Mixer → Stereo")
    }

    fun applyCrossfeedMedium() {
        setMixerAll(listOf(0.60f, 0.40f, 0.40f, 0.60f))
        showToast("Crossfeed Sedang")
    }

    fun clearPeaks() {
        _ui.update {
            it.copy(
                peakL = it.levelL, peakR = it.levelR,
                peak2L = it.level2L, peak2R = it.level2R,
            )
        }
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
                if (_ui.value.hasLvl2 && _ui.value.mode.isTwoChip) client.send("lvl2", "lvl2")
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
        _ui.update { if (it.mode.isTwoChip && it.hasLvl2) it.copy(dac2 = mode) else it.copy(dac = mode) }
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
        if (_ui.value.mode == AudioMode.ONE_CHIP_21) {
            showToast("mixmode dikunci di mode 2 (1-chip 2.1)")
            return
        }
        if (_ui.value.hasV3) client.send("mixmode $name") else client.send("mixermode $name")
    }

    fun setEqProfile(ch: Char, n: Int) {
        if (_ui.value.locked21) { lockedToast("Profil filter"); return }
        if (_ui.value.hasV3) client.send("eqpset $ch $n")
    }

    fun saveToDevice() {
        client.send("save")
        // Optimistically mark saved; firmware replies SAVED 1
        _ui.update { it.copy(saved = true) }
    }
    fun eraseSaved() {
        client.send("erase")
        _ui.update { it.copy(saved = false) }
    }
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
            // Mixer: mode 0 & 1 saja (mode 2 dikunci firmware, jadi dilewati tanpa toast)
            if (m != AudioMode.ONE_CHIP_21) setMixerAll(p.mix)
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

    /** Satu ketuk: mode 2-chip + xover 150 + crossfeed sedang + balance sat/sub untuk Samsung+ATP3. */
    fun applySoundbar21() {
        applyPreset(com.tas5805m.tuner.data.FactoryPresets.soundbar21)
        viewModelScope.launch {
            delay(400)
            client.send("save")
            _ui.update { it.copy(saved = true) }
        }
    }

    fun applyIndoor() {
        applyPreset(com.tas5805m.tuner.data.FactoryPresets.indoor)
        viewModelScope.launch {
            delay(400)
            client.send("save")
            _ui.update { it.copy(saved = true) }
        }
    }

    fun applyOutdoor() {
        applyPreset(com.tas5805m.tuner.data.FactoryPresets.outdoor)
        viewModelScope.launch {
            delay(400)
            client.send("save")
            _ui.update { it.copy(saved = true) }
        }
    }

    // ───────────────────────── util ─────────────────────────

    fun showToast(msg: String) { _toast.value = msg }
    fun toastShown() { _toast.value = null }

    /** Polling `status` tiap 2 dtk membanjiri log dengan MODE/STATE/FX identik; saring yang sama persis (<15 dtk).
     *  Baris berawalan WSUB (termasuk WSUBS = SLAVE) selalu dicatat karena nilainya berubah — itu bahan diagnostik. */
    private fun shouldLog(line: String): Boolean {
        val head = line.substringBefore(' ')
        if (head != "MODE" && head != "STATE" && head != "FX") return true
        val now = SystemClock.uptimeMillis()
        val prev = lastLog[head]
        if (prev != null && prev.first == line && now - prev.second < 15_000L) return false
        lastLog[head] = line to now
        return true
    }

    private fun addLog(dir: Char, text: String) {
        _log.update { (it + LogEntry(System.currentTimeMillis(), dir, text)).takeLast(1000) }
    }

    override fun onCleared() {
        client.disconnect()
        super.onCleared()
    }
}

/** Kebalikan [TunerViewModel.setBassBalance]: posisi slider (0..1) dari level sat/sub. */
fun bassBalanceOf(sat: Int, sub: Int): Float = when {
    sat <= 0 && sub <= 0 -> 0.5f
    sat >= sub -> sub / (2f * sat)
    else -> 1f - sat / (2f * sub)
}
