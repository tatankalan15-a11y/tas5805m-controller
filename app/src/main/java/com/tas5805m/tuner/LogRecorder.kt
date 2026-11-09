package com.tas5805m.tuner

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Merekam log ke file (filesDir/logs) agar tidak hilang saat layar terkunci / app ditutup / melewati batas 20.000 baris.
 * Dua file bergulir (rekam-log.1.txt = lama, rekam-log.txt = baru), masing-masing maks MAX_BYTES.
 * add() murah (hanya antre); flush() dipanggil berkala dari coroutine IO.
 */
class LogRecorder(ctx: Context) {
    private val dir = File(ctx.filesDir, "logs").apply { mkdirs() }
    private val cur = File(dir, "rekam-log.txt")
    private val prev = File(dir, "rekam-log.1.txt")
    private val queue = ConcurrentLinkedQueue<String>()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun add(time: Long, dir: Char, text: String) {
        val t = when {
            text.startsWith("WSUBS") -> "SLAVE" + text.drop(5)
            text.startsWith("WSUBD") -> "SLAVEDIAG" + text.drop(5)
            else -> text
        }
        val stamp = synchronized(fmt) { fmt.format(Date(time)) }
        queue.add("$stamp $dir $t\n")
    }

    @Synchronized
    fun flush() {
        if (queue.isEmpty()) return
        val sb = StringBuilder()
        while (true) { sb.append(queue.poll() ?: break) }
        runCatching {
            if (cur.length() > MAX_BYTES) { prev.delete(); cur.renameTo(prev) }
            cur.appendText(sb.toString(), Charsets.UTF_8)
        }
    }

    @Synchronized
    fun sizeBytes(): Long = (if (cur.exists()) cur.length() else 0L) + (if (prev.exists()) prev.length() else 0L)

    /** Tulis seluruh rekaman (lama lalu baru) ke [out]. */
    @Synchronized
    fun copyTo(out: OutputStream): Boolean = runCatching {
        flush()
        for (f in listOf(prev, cur)) if (f.exists()) f.inputStream().use { it.copyTo(out) }
        true
    }.getOrDefault(false)

    @Synchronized
    fun clear() { queue.clear(); prev.delete(); cur.delete() }

    companion object { const val MAX_BYTES = 12L * 1024 * 1024 }
}
