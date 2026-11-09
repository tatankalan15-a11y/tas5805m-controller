package com.tas5805m.tuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service tanpa logika: hanya menjaga proses app tetap hidup (dan CPU bangun) agar koneksi SPP + polling
 * status + perekaman log tidak dimatikan sistem saat layar dikunci. Dijalankan hanya bila opsi
 * "Rekam log di latar belakang" aktif.
 */
class BgLogService : Service() {
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Rekam log", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("TAS5805M Controller")
            .setContentText("Merekam log di latar belakang")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(ID, n)
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (wl == null) {
            wl = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tas5805m:bglog")
                .apply { acquire(12L * 60 * 60 * 1000) }   // maks 12 jam, dilepas saat service berhenti
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wl?.takeIf { it.isHeld }?.release() }
        wl = null
        super.onDestroy()
    }

    companion object {
        private const val CH = "bglog"
        private const val ID = 4201
        fun start(ctx: Context) { runCatching { ctx.startForegroundService(Intent(ctx, BgLogService::class.java)) } }
        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, BgLogService::class.java)) } }
    }
}
