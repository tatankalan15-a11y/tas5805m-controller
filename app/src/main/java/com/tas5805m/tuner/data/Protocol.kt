package com.tas5805m.tuner.data

/** Parser untuk baris-baris yang dikirim firmware lewat SPP (TAS5805M-CTRL). */
sealed interface Rx {
    data class Mode(val mode: Int, val xover: Int, val master: Int, val sat: Int, val sub: Int) : Rx
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
