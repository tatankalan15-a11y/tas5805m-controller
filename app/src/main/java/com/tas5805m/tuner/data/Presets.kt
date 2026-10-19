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

/** Preset EQ bawaan. Urutan band: 20,32,50,80,125,200,315,500,800,1.25k,2k,3.15k,5k,8k,16k */
object FactoryPresets {
    private fun eq(name: String, g: List<Int>) = Preset(
        id = "factory_" + name.lowercase().replace(' ', '_'),
        name = name, eqL = g, eqR = g, eqOnly = true, factory = true, created = 0L,
    )

    val all: List<Preset> = listOf(
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
            .putString("lastAddress", s.lastAddress)
            .putString("lastName", s.lastName)
            .apply()
        _settings.value = s
    }
}
