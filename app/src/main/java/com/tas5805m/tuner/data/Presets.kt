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
                List(4) { mixArr.optDouble(it, 0.0).toFloat().coerceIn(0f, 2f) }
            else listOf(1f, 0f, 0f, 1f)
            return Preset(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name", "Preset"),
                mode = o.optInt("mode", 0).coerceIn(0, 2),
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

/** Preset EQ bawaan + Profile lengkap (Movie / Music / Night) + Crossfeed */
object FactoryPresets {

    private fun eq(name: String, g: List<Int>) = Preset(
        id = "factory_" + name.lowercase().replace(' ', '_'),
        name = name, eqL = g, eqR = g, eqOnly = true, factory = true, created = 0L,
    )

    /** Profile lengkap (bukan eqOnly) → termasuk mixer, sat/sub, xover */
    private fun full(
        name: String,
        eq: List<Int>,
        mix: List<Float>,
        sat: Int = 90,
        sub: Int = 90,
        xover: Int = 150,
        master: Int = 90,
    ) = Preset(
        id = "factory_" + name.lowercase().replace(' ', '_').replace("/", "_"),
        name = name,
        mode = 1,                       // default 2-chip 2.1
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
    )

    // Crossfeed values (sudah dikompensasi)
    private val MIX_STEREO     = listOf(1.00f, 0.00f, 0.00f, 1.00f)
    private val MIX_RINGAN     = listOf(0.95f, 0.25f, 0.25f, 0.95f)
    private val MIX_SEDANG     = listOf(0.90f, 0.40f, 0.40f, 0.90f)
    private val MIX_KUAT       = listOf(0.85f, 0.60f, 0.60f, 0.85f)
    private val MIX_ASIMETRIS  = listOf(0.92f, 0.45f, 0.30f, 0.92f)

    val all: List<Preset> = listOf(

        // ---------- EQ only (lama) ----------
        eq("Flat", List(EQ_BANDS) { 0 }),
        eq("Bass boost", listOf(6, 6, 5, 4, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Vokal", listOf(-3, -3, -2, -1, 0, 1, 2, 3, 3, 2, 1, 0, -1, -1, -2)),
        eq("Loudness", listOf(6, 5, 4, 2, 0, 0, 0, -1, -1, 0, 0, 1, 3, 4, 4)),
        eq("V-shape", listOf(5, 5, 4, 2, 0, -2, -3, -3, -2, 0, 2, 3, 4, 5, 5)),
        eq("Treble boost", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6)),
        eq("Malam (lembut)", listOf(-3, -2, -1, 0, 0, 0, 0, 0, 0, -1, -2, -3, -4, -5, -6)),
        eq("Kecilkan boomy", listOf(-4, -4, -3, -2, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Spatial", listOf(1, 1, 0, 0, -1, -2, -2, -1, 0, 1, 2, 2, 3, 3, 2)),
        eq("Psycho Bass", listOf(4, 5, 6, 5, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        eq("Rock", listOf(5, 4, 3, 1, 0, -1, -2, -1, 0, 1, 2, 3, 4, 4, 3)),
        eq("Pop", listOf(2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 3, 2)),
        eq("Jazz", listOf(3, 2, 1, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 2)),

        // ---------- Profile lengkap (Movie / Music / Night) ----------
        // Movie: bass impact + dialog jelas + crossfeed sedang
        full(
            name = "Movie",
            eq = listOf(5, 5, 4, 3, 2, 1, 0, 1, 2, 2, 1, 0, 0, 1, 1),
            mix = MIX_SEDANG,
            sat = 88, sub = 95, xover = 120, master = 92
        ),

        // Music: balance natural + crossfeed ringan
        full(
            name = "Music",
            eq = listOf(2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 3, 2),
            mix = MIX_RINGAN,
            sat = 92, sub = 88, xover = 140, master = 90
        ),

        // Night: volume lebih rendah, sub dikurangi, crossfeed sedang, high dipotong
        full(
            name = "Night",
            eq = listOf(-3, -2, -1, 0, 0, 0, 0, 0, 0, -1, -2, -3, -4, -5, -6),
            mix = MIX_SEDANG,
            sat = 80, sub = 70, xover = 150, master = 70
        ),

        // ---------- Crossfeed only (bisa dipakai di mode stereo maupun 2.1) ----------
        full(name = "Crossfeed Ringan", eq = List(EQ_BANDS) { 0 }, mix = MIX_RINGAN),
        full(name = "Crossfeed Sedang", eq = List(EQ_BANDS) { 0 }, mix = MIX_SEDANG),
        full(name = "Crossfeed Kuat", eq = List(EQ_BANDS) { 0 }, mix = MIX_KUAT),
        full(name = "Crossfeed Asimetris", eq = List(EQ_BANDS) { 0 }, mix = MIX_ASIMETRIS),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoReconnect: Boolean = true,
    val keepScreenOn: Boolean = false,
    val autoSave: Boolean = true,          // NEW: auto save ke device setelah perubahan
    val lastAddress: String? = null,
    val lastName: String? = null,
)

class Store(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("tuner", Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<Preset>> = _presets

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings

    private fun loadPresets(): List<Preset> {
        val raw = sp.getString("presets", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { Preset.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun savePresets(list: List<Preset>) {
        val arr = JSONArray()
        list.filter { !it.factory }.forEach { arr.put(it.toJson()) }
        sp.edit().putString("presets", arr.toString()).apply()
        _presets.value = list
    }

    private fun loadSettings(): Settings {
        val t = sp.getString("theme", "SYSTEM") ?: "SYSTEM"
        return Settings(
            theme = try { ThemeMode.valueOf(t) } catch (_: Exception) { ThemeMode.SYSTEM },
            dynamicColor = sp.getBoolean("dynamic", false),
            autoReconnect = sp.getBoolean("autoReconnect", true),
            keepScreenOn = sp.getBoolean("keepScreenOn", false),
            autoSave = sp.getBoolean("autoSave", true),
            lastAddress = sp.getString("lastAddress", null),
            lastName = sp.getString("lastName", null),
        )
    }

    fun updateSettings(block: (Settings) -> Settings) {
        val ns = block(_settings.value)
        sp.edit()
            .putString("theme", ns.theme.name)
            .putBoolean("dynamic", ns.dynamicColor)
            .putBoolean("autoReconnect", ns.autoReconnect)
            .putBoolean("keepScreenOn", ns.keepScreenOn)
            .putBoolean("autoSave", ns.autoSave)
            .putString("lastAddress", ns.lastAddress)
            .putString("lastName", ns.lastName)
            .apply()
        _settings.value = ns
    }
}
