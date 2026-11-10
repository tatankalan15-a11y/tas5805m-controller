package com.tas5805m.tuner.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Preset. [eqOnly]=true → hanya menerapkan EQ (preset bawaan);
 * false → snapshot penuh (mode, volume, gain, crossover, level sat/sub, EQ, mixer).
 */
data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: Int = 0,
    val master: Int = 90,
    val gainCode: Int = 18,
    val xover: Int = 150,
    val sat: Int = 90,
    val sub: Int = 90,
    val eqL: List<Int> = List(EQ_BANDS) { 0 },
    val eqR: List<Int> = List(EQ_BANDS) { 0 },
    val eqEnabled: Boolean = true,
    val eqBiamp: Boolean = false,
    val mix: List<Float> = listOf(1f, 0f, 0f, 1f),
    val eqOnly: Boolean = false,
    val factory: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val subVoice: SubVoice? = null,   // voicing sub (firmware ≥ 2.9.14); hanya preset pabrik, tidak disimpan ke JSON
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("mode", mode); put("master", master)
        put("gain", gainCode); put("xover", xover); put("sat", sat); put("sub", sub)
        put("eqL", JSONArray(eqL)); put("eqR", JSONArray(eqR))
        put("eqEnabled", eqEnabled); put("eqBiamp", eqBiamp)
        put("mix", JSONArray(mix.map { it.toDouble() }))
        put("eqOnly", eqOnly); put("created", created)
    }

    companion object {
        private fun ints(a: JSONArray?, def: Int): List<Int> =
            if (a == null || a.length() != EQ_BANDS) List(EQ_BANDS) { def }
            else List(EQ_BANDS) { a.optInt(it, def).coerceIn(EQ_MIN, EQ_MAX) }

        fun fromJson(o: JSONObject): Preset {
            val mixArr = o.optJSONArray("mix")
            val mix = if (mixArr != null && mixArr.length() == 4)
                List(4) { mixArr.optDouble(it, 0.0).toFloat().coerceIn(-1f, 2f) }
            else listOf(1f, 0f, 0f, 1f)
            return Preset(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Preset"),
                mode = o.optInt("mode", 0).coerceIn(0, 4),
                master = o.optInt("master", 90).coerceIn(0, 100),
                gainCode = o.optInt("gain", 18).coerceIn(0, 31),
                xover = o.optInt("xover", 150).coerceIn(60, 150),
                sat = o.optInt("sat", 90).coerceIn(0, 100),
                sub = o.optInt("sub", 90).coerceIn(0, 100),
                eqL = ints(o.optJSONArray("eqL"), 0),
                eqR = ints(o.optJSONArray("eqR"), 0),
                eqEnabled = o.optBoolean("eqEnabled", true),
                eqBiamp = o.optBoolean("eqBiamp", false),
                mix = mix,
                eqOnly = o.optBoolean("eqOnly", false),
                created = o.optLong("created", System.currentTimeMillis()),
            )
        }
    }
}

/** Preset EQ bawaan. Urutan band: 20,32,50,80,125,200,315,500,800,1.25k,2k,3.15k,5k,8k,16k */
object FactoryPresets {
    private fun eq(name: String, g: List<Int>) = Preset(
        id = "factory_" + name.lowercase().replace(' ', '_'),
        name = name, eqL = g, eqR = g, eqOnly = true, factory = true, created = 0L,
    )

    /** Snapshot sistem penuh (bukan eqOnly) untuk setup hardware. */
    private fun system(
        name: String,
        mode: Int,
        master: Int = 85,
        xover: Int = 150,
        sat: Int = 85,
        sub: Int = 70,
        mix: List<Float> = listOf(0.60f, 0.40f, 0.40f, 0.60f),
        eq: List<Int> = List(EQ_BANDS) { 0 },
        subVoice: SubVoice? = null,
    ) = Preset(
        id = "system_" + name.lowercase().replace(' ', '_').replace("+", "p"),
        name = name,
        mode = mode,
        master = master,
        gainCode = 18,
        xover = xover,
        sat = sat,
        sub = sub,
        eqL = eq,
        eqR = eq,
        eqEnabled = true,
        eqBiamp = false,
        mix = mix,
        eqOnly = false,
        factory = true,
        created = 0L,
        subVoice = subVoice,
    )

    /** Soundbar: 2× satelit 4Ω + sub 4Ω, xover 150, crossfeed sedang, high sedikit diangkat. */
    val soundbar21 = system(
        name = "Soundbar 2.1",
        mode = 1,
        master = 85,
        xover = 150,
        sat = 85,
        sub = 65,
        mix = listOf(0.60f, 0.40f, 0.40f, 0.60f),
        // band 0..2 diabaikan firmware di 2.1; 80Hz+ mild presence untuk Samsung rear
        eq = listOf(0, 0, 0, 1, 1, 0, 0, 0, 1, 2, 2, 2, 3, 3, 2),
    )

    /** Indoor: kontrol bass, crossfeed sedang, high tidak agresif. */
    val indoor = system(
        name = "Indoor",
        mode = 1,
        master = 80,
        xover = 150,
        sat = 85,
        sub = 58,
        mix = listOf(0.70f, 0.30f, 0.30f, 0.70f), // crossfeed 30%
        // potong 125–315 Hz (boomy ruangan; 80 Hz sudah di bawah HPF satelit), presence halus
        eq = listOf(0, 0, 0, 0, -1, -2, -1, 0, 0, 1, 1, 1, 1, 1, 0),
    )

    /** Outdoor: sub + presence high, crossfeed ringan, headroom lebih. */
    val outdoor = system(
        name = "Outdoor",
        mode = 1,
        master = 87,   // 2.9.14+: turun 3 dB demi headroom (boost EQ sampai +4 dB)
        xover = 120,
        sat = 80,
        sub = 85,
        mix = listOf(0.85f, 0.15f, 0.15f, 0.85f), // crossfeed 15%
        // angkat 2–8 kHz agar jelas di keramaian; mid-bass sedikit bantu satelit
        eq = listOf(0, 0, 0, 1, 2, 1, 0, 0, 1, 2, 3, 3, 4, 3, 2),
    )

    /**
     * Party (gaya speaker pesta): bass punchy & rapat, tengah sedikit ditarik, presence + treble berkilau, level master tinggi.
     * Satelit: band 0..2 diabaikan di mode 2.1; bass satelit hanya sedikit (80–125 Hz). Sub: voicing 2.9.14 (HPF + boost 60 Hz + harmonik + limiter).
     */
    val party = system(
        name = "Party",
        mode = 4,
        master = 87,   // turun 3 dB demi headroom (boost treble +3 dB di 4 band)
        xover = 120,
        sat = 85,
        sub = 80,
        mix = listOf(0.85f, 0.15f, 0.15f, 0.85f),   // stereo lebar, crossfeed ringan
        eq = listOf(0, 0, 0, 2, 3, 1, -1, -2, -2, 0, 2, 3, 3, 3, 3),
        subVoice = SubVoice(on = true, hpf = 34, pkHz = 60, pkDb10 = 50, pkQ10 = 11, harm = 50, limit = 85),
    )

    /** Party Dalam: bass lebih besar & dalam (boost lebih rendah, harmonik lebih banyak). HPF 36 Hz agar cone 6,5" aman. */
    val partyDeep = system(
        name = "Party Dalam",
        mode = 4,
        master = 87,
        xover = 120,
        sat = 85,
        sub = 85,
        mix = listOf(0.85f, 0.15f, 0.15f, 0.85f),
        eq = listOf(0, 0, 0, 2, 3, 1, -1, -2, -2, 0, 2, 3, 3, 3, 3),
        subVoice = SubVoice(on = true, hpf = 36, pkHz = 50, pkDb10 = 40, pkQ10 = 10, harm = 70, limit = 82),
    )

    /** Gaya Harman Kardon Onyx: hangat, bass penuh, mid sedikit mundur, treble halus. (Pendekatan, bukan hasil ukur.) */
    val harmanOnyx = system(
        name = "Harman Onyx",
        mode = 4,
        master = 86,
        xover = 120,
        sat = 85,
        sub = 78,
        mix = listOf(0.75f, 0.25f, 0.25f, 0.75f),
        eq = listOf(0, 0, 0, 2, 3, 1, 0, -1, -1, 0, 1, 1, 1, 0, -1),
        subVoice = SubVoice(on = true, hpf = 36, pkHz = 65, pkDb10 = 40, pkQ10 = 12, harm = 40, limit = 85),
    )

    /** Gaya Bose: bass dalam & tebal, mid halus, treble digulung (tidak tajam). */
    val boseWarm = system(
        name = "Bose Warm",
        mode = 4,
        master = 86,
        xover = 120,
        sat = 85,
        sub = 82,
        mix = listOf(0.70f, 0.30f, 0.30f, 0.70f),
        eq = listOf(0, 0, 0, 3, 3, 2, 1, 0, -1, -1, 0, 1, 0, -1, -2),
        subVoice = SubVoice(on = true, hpf = 35, pkHz = 55, pkDb10 = 55, pkQ10 = 10, harm = 55, limit = 85),
    )

    /** Gaya JBL: bentuk V, bass punchy, mid ditarik, presence & treble berkilau. */
    val jblPunch = system(
        name = "JBL Punch",
        mode = 4,
        master = 88,
        xover = 120,
        sat = 85,
        sub = 80,
        mix = listOf(0.85f, 0.15f, 0.15f, 0.85f),
        eq = listOf(0, 0, 0, 2, 3, 1, 0, -2, -2, -1, 1, 2, 3, 3, 2),
        subVoice = SubVoice(on = true, hpf = 38, pkHz = 70, pkDb10 = 40, pkQ10 = 13, harm = 40, limit = 85),
    )

    /** Movie Wireless: dialog jelas, LFE dari sub (HPF 32 Hz, boost 48 Hz, limiter longgar), treble datar. Nilai awal, tuning dengan telinga. */
    val movieWireless = system(
        name = "Movie Wireless",
        mode = 4,
        master = 83,   // turun 2 dB: pelebaran stereo menaikkan sinyal sisi (L-R) sampai ~+3 dB
        xover = 100,
        sat = 85,
        sub = 86,
        mix = listOf(1.20f, -0.20f, -0.20f, 1.20f),   // lebar stereo ~40%: sinyal tengah (dialog) tetap 0 dB
        eq = listOf(0, 0, 0, 0, 0, -1, -1, 0, 0, 1, 2, 1, 0, 0, 0),
        subVoice = SubVoice(on = true, hpf = 32, pkHz = 48, pkDb10 = 30, pkQ10 = 12, harm = 25, limit = 88),
    )

    val all: List<Preset> = listOf(
        soundbar21,
        party,
        partyDeep,
        harmanOnyx,
        boseWarm,
        jblPunch,
        movieWireless,
        indoor,
        outdoor,
        system("Soundbar Stereo", mode = 0, master = 85, mix = listOf(1f, 0f, 0f, 1f)),
        // Catatan: band 20/32/50 Hz (3 angka pertama) diabaikan firmware di mode 2.1; efeknya hanya di mode Stereo.
        // Untuk bass di 2.1 gunakan voicing sub.
        eq("Flat", List(EQ_BANDS) { 0 }),
        eq("Bass boost", listOf(6, 6, 5, 4, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Vokal", listOf(-3, -3, -2, -1, 0, 1, 2, 3, 3, 2, 1, 0, -1, -1, -2)),
        eq("Loudness", listOf(6, 5, 4, 2, 0, 0, 0, -1, -1, 0, 0, 1, 3, 4, 4)),
        eq("V-shape", listOf(5, 5, 4, 2, 0, -2, -3, -3, -2, 0, 2, 3, 4, 5, 5)),
        eq("Treble boost", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6)),
        eq("Malam (lembut)", listOf(-3, -2, -1, 0, 0, 0, 0, 0, 0, -1, -2, -3, -4, -5, -6)),
        eq("Kecilkan boomy", listOf(-4, -4, -3, -2, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        // Sinematik: bass impact + dialog jelas + high terkendali
        eq("Movie", listOf(5, 5, 4, 3, 2, 1, 0, 1, 2, 2, 1, 0, 0, 1, 1)),
        // Kesan lebar (EQ only — bukan HRTF/crossfeed sejati)
        eq("Spatial", listOf(1, 1, 0, 0, -1, -2, -2, -1, 0, 1, 2, 2, 3, 3, 2)),
        // Ilusi bass dalam lewat angkat 50–125 Hz (bukan generator harmonik PBE)
        eq("Psycho Bass", listOf(4, 5, 6, 5, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Rock", listOf(5, 4, 3, 1, 0, -1, -2, -1, 0, 1, 2, 3, 4, 4, 3)),
        eq("Pop", listOf(2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 3, 2)),
        eq("Jazz", listOf(3, 2, 1, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 2)),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoReconnect: Boolean = true,
    val keepScreenOn: Boolean = false,
    val bgLog: Boolean = false,   // 3.3.0: rekam log di latar belakang (foreground service + file)
    val lastAddress: String? = null,
    val lastName: String? = null,
)

class Store(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("tuner", Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<Preset>> = _presets

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings

    private fun loadPresets(): List<Preset> = try {
        val a = JSONArray(sp.getString("presets", "[]"))
        List(a.length()) { Preset.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun savePresets(list: List<Preset>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        sp.edit().putString("presets", a.toString()).apply()
        _presets.value = list
    }

    fun upsert(p: Preset) {
        val cur = _presets.value
        savePresets(if (cur.any { it.id == p.id }) cur.map { if (it.id == p.id) p else it } else cur + p)
    }

    fun delete(id: String) = savePresets(_presets.value.filterNot { it.id == id })

    fun exportJson(list: List<Preset>): String {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        return JSONObject().put("app", "tas5805m-tuner").put("version", 1).put("presets", a).toString(2)
    }

    /** @return jumlah preset yang berhasil diimpor, atau -1 jika format salah */
    fun importJson(text: String): Int = try {
        val t = text.trim()
        val arr: JSONArray = when {
            t.startsWith("[") -> JSONArray(t)
            else -> {
                val o = JSONObject(t)
                o.optJSONArray("presets") ?: JSONArray().put(o)
            }
        }
        var n = 0
        for (i in 0 until arr.length()) {
            val p = Preset.fromJson(arr.getJSONObject(i)).copy(id = UUID.randomUUID().toString(), factory = false)
            upsert(p)
            n++
        }
        n
    } catch (e: Exception) {
        -1
    }

    private fun loadSettings() = Settings(
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = sp.getBoolean("dynamic", false),
        autoReconnect = sp.getBoolean("autoReconnect", true),
        keepScreenOn = sp.getBoolean("keepScreen", false),
        bgLog = sp.getBoolean("bgLog", false),
        lastAddress = sp.getString("lastAddress", null),
        lastName = sp.getString("lastName", null),
    )

    fun updateSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("autoReconnect", s.autoReconnect)
            .putBoolean("keepScreen", s.keepScreenOn)
            .putBoolean("bgLog", s.bgLog)
            .putString("lastAddress", s.lastAddress)
            .putString("lastName", s.lastName)
            .apply()
        _settings.value = s
    }
}
