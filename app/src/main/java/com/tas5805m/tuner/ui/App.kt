@file:OptIn(ExperimentalMaterial3Api::class)

package com.tas5805m.tuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.AmpState
import com.tas5805m.tuner.bt.SppClient

private enum class Tab(val label: String, val icon: ImageVector) {
    TUNE("Tune", Icons.Rounded.Tune),
    EQ("EQ", Icons.Rounded.GraphicEq),
    OUTPUT("Output", Icons.Rounded.Speaker),
    MONITOR("Monitor", Icons.Rounded.Speed),
    MORE("Lainnya", Icons.Rounded.MoreHoriz),
}

@Composable
fun TunerApp(vm: TunerViewModel) {
    val link by vm.link.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.tryAutoConnect() }
    LaunchedEffect(toast) {
        toast?.let {
            snack.showSnackbar(it)
            vm.toastShown()
        }
    }

    if (link != SppClient.Link.CONNECTED) ConnectScreen(vm, snack) else MainScaffold(vm, snack)
}

@Composable
private fun MainScaffold(vm: TunerViewModel, snack: SnackbarHostState) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tabIdx by rememberSaveable { mutableStateOf(0) }
    val tab = Tab.entries[tabIdx]

    // Pemantauan live hanya saat tab Monitor terbuka.
    DisposableEffect(tab) {
        if (tab == Tab.MONITOR) vm.startMonitor()
        onDispose { vm.stopMonitor() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier.size(9.dp).background(
                                    if (s.anyFault) BadRed else OkGreen, CircleShape,
                                ),
                            )
                            Text(settings.lastName ?: "TAS5805M", style = MaterialTheme.typography.titleMedium)
                            // Indikator NVS: hijau = tersimpan
                            Text(
                                if (s.saved == true) "● Saved" else if (s.saved == false) "○ Unsaved" else "○ —",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.saved == true) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (s.saved == true) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        Text(
                            s.mode.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.sync() }) { Icon(Icons.Rounded.Sync, contentDescription = "Sinkron") }
                    IconButton(onClick = { vm.disconnect() }) { Icon(Icons.Rounded.LinkOff, contentDescription = "Putus") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = i == tabIdx,
                        onClick = { tabIdx = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Master volume (selalu terlihat)
            LabeledSlider(
                "Master",
                "${s.master}%",
                s.master.toFloat(),
                0f..100f,
            ) { vm.setMaster(it.toInt()) }

            // Quick actions (semua tab)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {
                        if (s.amp == AmpState.MUTE) vm.unmutePlay() else vm.muteAll()
                    },
                    label = { Text(if (s.amp == AmpState.MUTE) "Unmute" else "Mute All") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (s.amp == AmpState.MUTE) BadRed.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
                AssistChip(
                    onClick = { vm.applySoundbar21() },
                    label = { Text("Soundbar 2.1") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                AssistChip(onClick = { vm.resetToStereoMix() }, label = { Text("Reset Stereo") })
                AssistChip(onClick = { vm.applyCrossfeedMedium() }, label = { Text("Crossfeed Sedang") })
                AssistChip(onClick = { vm.saveToDevice() }, label = { Text("Save NVS") })
            }

            when (tab) {
                Tab.TUNE -> TuneScreen(s, vm)
                Tab.EQ -> EqScreen(s, vm)
                Tab.OUTPUT -> OutputScreen(s, vm)
                Tab.MONITOR -> MonitorScreen(s, vm)
                Tab.MORE -> MoreScreen(s, vm)
            }
            Box(Modifier.size(12.dp))
        }
    }
}
