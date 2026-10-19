package com.tas5805m.tuner.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class BtDev(val name: String, val address: String) {
    val isTarget: Boolean get() = name.contains("TAS5805M", ignoreCase = true) && name.contains("CTRL", ignoreCase = true)
}

object BtUtil {
    fun adapter(ctx: Context): BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun bonded(ctx: Context): List<BtDev> {
        if (!hasPermission(ctx)) return emptyList()
        val set = adapter(ctx)?.bondedDevices ?: return emptyList()
        return set.map { BtDev(it.name ?: it.address, it.address) }
            .sortedWith(compareByDescending<BtDev> { it.isTarget }.thenBy { it.name.lowercase() })
    }

    @SuppressLint("MissingPermission")
    fun remote(ctx: Context, address: String): BluetoothDevice? =
        try { adapter(ctx)?.getRemoteDevice(address) } catch (e: IllegalArgumentException) { null }
}
