package com.tas5805m.tuner.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Klien Bluetooth Classic SPP (RFCOMM) untuk firmware "TAS5805M-CTRL".
 * Protokol: satu perintah per baris (\n). Perintah yang memakai key yang sama
 * digabung (latest-wins) supaya slider tidak membanjiri buffer SPP ESP32.
 */
class SppClient(private val scope: CoroutineScope) {

    enum class Link { IDLE, CONNECTING, CONNECTED, FAILED }

    private val _link = MutableStateFlow(Link.IDLE)
    val link: StateFlow<Link> = _link

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val lines: SharedFlow<String> = _lines

    private val _tx = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val tx: SharedFlow<String> = _tx

    private var job: Job? = null
    @Volatile private var socket: BluetoothSocket? = null

    private val lock = Any()
    private val pending = LinkedHashMap<String, String>()
    private val seq = AtomicInteger(0)
    private val generation = AtomicInteger(0)
    private var wake = Channel<Unit>(Channel.CONFLATED)

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        _error.value = null
        _link.value = Link.CONNECTING
        val gen = generation.incrementAndGet()
        val myWake = Channel<Unit>(Channel.CONFLATED)
        wake = myWake
        synchronized(lock) { pending.clear() }

        job = scope.launch(Dispatchers.IO) {
            fun mine() = generation.get() == gen
            var failed = false
            var s: BluetoothSocket? = null
            try {
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s = sock
                socket = sock
                sock.connect()
                if (!mine()) return@launch
                _link.value = Link.CONNECTED
                val input = sock.inputStream
                val output = sock.outputStream
                coroutineScope {
                    val writer = launch { writerLoop(output, myWake) }
                    try {
                        readerLoop(input)
                    } finally {
                        writer.cancel()
                    }
                }
            } catch (e: IOException) {
                if (mine()) {
                    failed = true
                    _error.value = e.message ?: "Koneksi terputus"
                }
            } catch (e: SecurityException) {
                failed = mine()
                if (failed) _error.value = "Izin Bluetooth ditolak"
            } finally {
                runCatching { s?.close() }
                if (mine()) {
                    socket = null
                    synchronized(lock) { pending.clear() }
                    _link.value = if (failed) Link.FAILED else Link.IDLE
                }
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        runCatching { socket?.close() }
        job?.cancel()
        job = null
        _link.value = Link.IDLE
    }

    /** Kirim perintah. Jika [key] sama dengan perintah yang masih antre, perintah lama diganti. */
    fun send(cmd: String, key: String? = null) {
        if (_link.value != Link.CONNECTED) return
        val k = key ?: "#${seq.incrementAndGet()}"
        synchronized(lock) { pending[k] = cmd }
        wake.trySend(Unit)
    }

    private suspend fun writerLoop(out: OutputStream, w: Channel<Unit>) {
        try {
            for (u in w) {
                delay(180)  // coalesce slider bursts (~6 frames)
                val batch = synchronized(lock) {
                    val l = pending.values.toList()
                    pending.clear()
                    l
                }
                for (c in batch) {
                    out.write((c + "\n").toByteArray(Charsets.US_ASCII))
                    out.flush()
                    _tx.tryEmit(c)
                    delay(12)
                }
            }
        } catch (e: IOException) {
            runCatching { socket?.close() } // membangunkan reader
        }
    }

    private fun readerLoop(input: InputStream) {
        val buf = ByteArray(512)
        val sb = StringBuilder()
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            for (i in 0 until n) {
                val c = (buf[i].toInt() and 0xFF).toChar()
                if (c == '\n') {
                    val line = sb.toString().trim()
                    sb.setLength(0)
                    if (line.isNotEmpty()) _lines.tryEmit(line)
                } else if (c != '\r') {
                    if (sb.length < 400) sb.append(c)
                }
            }
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
