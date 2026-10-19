@file:OptIn(ExperimentalMaterial3Api::class)

package com.tas5805m.tuner.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.bt.BtUtil
import com.tas5805m.tuner.bt.SppClient
import com.tas5805m.tuner.data.AudioMode

@Composable
fun ConnectScreen(vm: TunerViewModel, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val link by vm.link.collectAsStateWithLifecycle()
    val err by vm.linkError.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adapterOn = BtUtil.adapter(ctx)?.isEnabled == true

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            vm.refreshDevices()
            vm.tryAutoConnect()
        } else vm.showToast("Izin Bluetooth diperlukan untuk menyambung ke ESP32")
    }

    LaunchedEffect(Unit) {
        if (BtUtil.hasPermission(ctx)) vm.refreshDevices()
        else permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (BtUtil.hasPermission(ctx)) vm.refreshDevices() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("TAS5805M Tuner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tuning DAC/amplifier TAS5805M di ESP32 lewat Bluetooth SPP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (link == SppClient.Link.CONNECTING) {
                SectionCard("Menyambung…") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Text(settings.lastName ?: "perangkat", style = MaterialTheme.typography.bodyLarge)
                    }
                    OutlinedButton(onClick = { vm.disconnect() }) { Text("Batal") }
                }
            }
            if (link == SppClient.Link.FAILED) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Gagal tersambung", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            (err ?: "Tidak diketahui") + "\nPastikan ESP32 menyala, sudah dipair, dan tidak sedang dipakai aplikasi lain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            if (!BtUtil.hasPermission(ctx)) {
                SectionCard("Izin diperlukan") {
                    Text("Aplikasi butuh izin \"Perangkat di sekitar\" untuk membaca daftar perangkat Bluetooth yang sudah dipair.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Beri izin") }
                }
            } else if (!adapterOn) {
                SectionCard("Bluetooth mati") {
                    Text("Nyalakan Bluetooth lalu kembali ke aplikasi.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Buka pengaturan Bluetooth") }
                }
            } else {
                SectionCard(
                    "Perangkat terpasang",
                    trailing = {
                        androidx.compose.material3.IconButton(onClick = { vm.refreshDevices() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Muat ulang")
                        }
                    },
                ) {
                    if (devices.isEmpty()) {
                        Text(
                            "Belum ada perangkat yang dipair. Pair dulu \"TAS5805M-CTRL\" di pengaturan Bluetooth Android.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    devices.forEach { d ->
                        val isLast = d.address == settings.lastAddress
                        Card(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.connect(d.address, d.name) },
                            shape = RoundedCornerShape(16.dp),
                            border = if (d.isTarget) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        d.address + if (d.isTarget) "  ·  direkomendasikan" else if (isLast) "  ·  terakhir dipakai" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                        Text("Pair perangkat baru")
                    }
                }
            }

            SectionCard("3 mode firmware (env)") {
                AudioMode.entries.forEach {
                    Column {
                        Text("${it.title}  ·  ${it.env}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(it.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Mode aktif dibaca otomatis dari firmware setelah tersambung.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
