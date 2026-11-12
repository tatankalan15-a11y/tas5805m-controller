package com.tas5805m.tuner.data

/** Satu kejadian fault: sumber + jenis, kapan mulai, kapan terakhir terlihat, berapa kali kambuh.
 *
 *  source: "sat" (chip 1 / satelit), "chip2" (sub kabel), "s<N>" (sub wireless slot N).
 *  Durasi = selisih pembacaan pertama dan terakhir yang MELAPORKAN fault — resolusinya sebesar interval baca
 *  (≈2 dtk untuk sub wireless; satelit hanya saat "Baca fault" atau pantau otomatis 5 dtk), jadi dianggap perkiraan. */
data class FaultIncident(
    val source: String,
    val key: String,
    val startMs: Long,
    val lastSeenMs: Long,
    val endMs: Long? = null,        // null = masih aktif
    val count: Int = 1,             // 1 + berapa kali kambuh dalam MERGE_MS setelah berakhir
    val truncated: Boolean = false, // berakhir karena data terputus (slave offline / BT putus / app ditutup), bukan karena pulih
) {
    val active: Boolean get() = endMs == null
    fun durationMs(nowMs: Long): Long = ((if (active) nowMs else (endMs ?: lastSeenMs)) - startMs).coerceAtLeast(0L)
}

object FaultIncidents {
    /** Fault yang muncul lagi dalam jendela ini setelah berakhir dianggap kejadian yang sama (latched lalu di-clear → kambuh). */
    const val MERGE_MS = 6_000L
    const val MAX = 100

    data class Result(val list: List<FaultIncident>, val structural: Boolean)

    /** Perbarui kejadian milik [source] dengan kumpulan fault aktif [active] pada [nowMs].
     *  structural = ada kejadian baru / berakhir / kambuh (lastSeen saja yang berubah → false; berguna untuk menentukan kapan menyimpan). */
    fun update(list: List<FaultIncident>, source: String, active: Set<String>, nowMs: Long): Result {
        var structural = false
        val out = ArrayList<FaultIncident>(list.size + active.size)
        val seen = HashSet<String>()
        for (inc in list) {
            if (inc.source != source || !inc.active) { out += inc; continue }
            if (inc.key in active) {
                out += inc.copy(lastSeenMs = nowMs)
                seen += inc.key
            } else {
                out += inc.copy(endMs = inc.lastSeenMs)
                structural = true
            }
        }
        for (key in active) {
            if (key in seen) continue
            val idx = out.indexOfLast { it.source == source && it.key == key && !it.active }
            val prev = if (idx >= 0) out[idx] else null
            if (prev != null && !prev.truncated && nowMs - (prev.endMs ?: prev.lastSeenMs) <= MERGE_MS) {
                out[idx] = prev.copy(lastSeenMs = nowMs, endMs = null, count = prev.count + 1)
            } else {
                out += FaultIncident(source, key, nowMs, nowMs)
            }
            structural = true
        }
        return Result(trim(out), structural)
    }

    /** Tutup semua kejadian aktif milik [source] (atau semua sumber bila null) karena data terputus. */
    fun closeTruncated(list: List<FaultIncident>, source: String? = null): Result {
        var structural = false
        val out = list.map { inc ->
            if (inc.active && (source == null || inc.source == source)) {
                structural = true
                inc.copy(endMs = inc.lastSeenMs, truncated = true)
            } else inc
        }
        return Result(out, structural)
    }

    private fun trim(list: ArrayList<FaultIncident>): List<FaultIncident> {
        if (list.size <= MAX) return list
        // buang yang sudah berakhir dan paling lama dulu; yang masih aktif tidak dibuang
        val removable = list.filter { !it.active }.sortedBy { it.lastSeenMs }.take(list.size - MAX).toSet()
        return list.filterNot { it in removable }
    }

    fun keyLabel(key: String): String = FAULT_DEFS.firstOrNull { it.key == key }?.label ?: key

    fun sourceLabel(source: String): String = when {
        source == "sat" -> "Satelit"
        source == "chip2" -> "Sub (chip 2)"
        source.startsWith("s") && source.drop(1).toIntOrNull() != null -> "Sub wireless slot ${source.drop(1)}"
        else -> source
    }

    fun isCritical(key: String): Boolean = FAULT_DEFS.firstOrNull { it.key == key }?.critical == true

    /** "sesaat", "8 dtk", "2 mnt 5 dtk", "1 j 3 mnt". */
    fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return when {
            ms < 1000 -> "sesaat"
            s < 60 -> "$s dtk"
            s < 3600 -> "${s / 60} mnt" + (if (s % 60 != 0L) " ${s % 60} dtk" else "")
            else -> "${s / 3600} j" + (if ((s % 3600) / 60 != 0L) " ${(s % 3600) / 60} mnt" else "")
        }
    }

    /** "baru saja", "12 dtk lalu", "3 mnt lalu", "2 j lalu". */
    fun formatAgo(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0L)
        return when {
            s < 5 -> "baru saja"
            s < 60 -> "$s dtk lalu"
            s < 3600 -> "${s / 60} mnt lalu"
            else -> "${s / 3600} j lalu"
        }
    }
}
