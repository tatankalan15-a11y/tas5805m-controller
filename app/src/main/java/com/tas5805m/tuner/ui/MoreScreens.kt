package com.tas5805m.tuner.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tas5805m.tuner.TunerViewModel
import com.tas5805m.tuner.data.AudioMode
import com.tas5805m.tuner.data.FactoryPresets
import com.tas5805m.tuner.data.Preset
import com.tas5805m.tuner.data.ThemeMode
import com.tas5805m.tuner.data.TunerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(40) },
                singleLine = true, label = { Text("Nama") },
            )
        },
        confirmButton = { TextButton(onClick = { onOk(text) }, enabled = text.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun MoreScreen(s: TunerState, vm: TunerViewModel) {
    var page by rememberSaveable { mutableStateOf("menu") }
    BackHandler(enabled = page != "menu") { page = "menu" }

    if (page == "menu") {
        MenuRow(Icons.Rounded.Bookmarks, "Preset", "Simpan, terapkan, impor & bagikan setelan") { page = "presets" }
        MenuRow(Icons.Rounded.Memory, "DAC lanjutan", "Topologi output, modulasi, soft-clipper") { page = "dac" }
        MenuRow(Icons.Rounded.Terminal, "Konsol", "Kirim perintah mentah & lihat log SPP") { page = "console" }
        MenuRow(Icons.Rounded.Settings, "Pengaturan", "Tema, auto-connect, layar tetap menyala") { page = "settings" }
        MenuRow(Icons.Rounded.Info, "Tentang", "Versi, mode env, daftar perintah") { page = "about" }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { page = "menu" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
        Text(
            when (page) {
                "presets" -> "Preset"
                "dac" -> "DAC lanjutan"
                "console" -> "Konsol"
                "settings" -> "Pengaturan"
                else -> "Tentang"
            },
            style = MaterialTheme.typography.titleLarge,
        )
    }
    when (page) {
        "presets" -> PresetsPage(s, vm)
        "dac" -> DacPage(s, vm)
        "console" -> ConsolePage(vm)
        "settings" -> SettingsPage(vm)
        else -> AboutPage()
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────── Preset ─────────────────────────

@Composable
private fun PresetsPage(s: TunerState, vm: TunerViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val presets by vm.store.presets.collectAsStateWithLifecycle()
    var saveDlg by remember { mutableStateOf(false) }
    var importDlg by remember { mutableStateOf(false) }
    var delTarget by remember { mutableStateOf<Preset?>(null) }

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Preset TAS5805M Tuner")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(i, "Bagikan preset"))
    }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { saveDlg = true }) { Text("Simpan setelan ini") }
        OutlinedButton(onClick = { importDlg = true }) { Text("Impor") }
        OutlinedButton(onClick = { share(vm.store.exportJson(presets)) }, enabled = presets.isNotEmpty()) { Text("Bagikan semua") }
    }

    SectionCard("Preset saya") {
        if (presets.isEmpty()) {
            Text(
                "Belum ada. Preset penuh menyimpan mode, volume, gain, crossover, level, EQ, dan mixer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        presets.forEach { p ->
            PresetRow(
                p,
                "${AudioMode.from(p.mode).short} · master ${p.master}% · ${if (p.eqBiamp) "EQ biamp" else "EQ linked"} · ${fmt.format(Date(p.created))}",
                onApply = { vm.applyPreset(p) },
                onOverwrite = { vm.overwrite(p) },
                onShare = { share(vm.store.exportJson(listOf(p))) },
                onDelete = { delTarget = p },
            )
        }
    }

    SectionCard("EQ bawaan") {
        FactoryPresets.all.forEach { p ->
            PresetRow(p, "Hanya EQ · tidak mengubah volume atau mode", onApply = { vm.applyPreset(p) })
        }
    }

    if (saveDlg) NameDialog("Simpan preset", "", { saveDlg = false }) { vm.saveCurrentAs(it); saveDlg = false }

    if (importDlg) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importDlg = false },
            title = { Text("Impor preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("Tempel JSON preset") }, minLines = 4, maxLines = 8,
                    )
                    TextButton(onClick = { text = clipboard.getText()?.text ?: text }) { Text("Tempel dari clipboard") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = vm.store.importJson(text)
                    vm.showToast(if (n >= 0) "$n preset diimpor" else "Format JSON tidak valid")
                    if (n >= 0) importDlg = false
                }, enabled = text.isNotBlank()) { Text("Impor") }
            },
            dismissButton = { TextButton(onClick = { importDlg = false }) { Text("Batal") } },
        )
    }

    delTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("Hapus \"${p.name}\"?") },
            confirmButton = { TextButton(onClick = { vm.store.delete(p.id); delTarget = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun PresetRow(
    p: Preset,
    subtitle: String,
    onApply: () -> Unit,
    onOverwrite: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onOverwrite != null) IconButton(onClick = onOverwrite) { Icon(Icons.Rounded.Save, contentDescription = "Timpa dengan setelan sekarang") }
        if (onShare != null) IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Bagikan") }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Hapus") }
        FilledTonalButton(onClick = onApply) { Text("Terapkan") }
    }
}

// ───────────────────────── DAC lanjutan ─────────────────────────

@Composable
private fun DacPage(s: TunerState, vm: TunerViewModel) {
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) } // (pesan, perintah)
    var modMode by rememberSaveable { mutableStateOf("bd") }
    var modFreq by rememberSaveable { mutableStateOf("768k") }
    var bdFreq by rememberSaveable { mutableStateOf("175k") }
    var clipGain by remember { mutableFloatStateOf(0f) }
    var clipL by remember { mutableFloatStateOf(0f) }
    var clipR by remember { mutableFloatStateOf(0f) }

    // Sinkronkan pilihan dengan nilai yang dibaca dari chip (firmware v3)
    LaunchedEffect(s.modMode, s.modFreq, s.modBd) {
        s.modMode?.let { if (it != "?") modMode = it }
        s.modFreq?.let { if (it != "?") modFreq = it }
        s.modBd?.let { if (it != "?") bdFreq = it }
    }
    LaunchedEffect(s.clipGain, s.clipL, s.clipR) {
        s.clipGain?.let { clipGain = it.toFloat().coerceIn(-100f, 0f) }
        s.clipL?.let { clipL = it.toFloat().coerceIn(0f, 100f) }
        s.clipR?.let { clipR = it.toFloat().coerceIn(0f, 100f) }
    }

    SectionCard("Topologi output") {
        Text(
            "BTL = dua kanal stereo. PBTL = kedua output digabung paralel untuk satu speaker/sub berdaya besar. " +
                "Salah memilih bisa merusak speaker atau amp — pastikan wiring sesuai.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.dac == "btl", onClick = { confirm = "Set topologi ke BTL (stereo)?" to "btl" }, label = { Text("BTL") })
            FilterChip(selected = s.dac == "pbtl", onClick = { confirm = "Set topologi ke PBTL (paralel)? Pastikan speaker terhubung paralel." to "pbtl" }, label = { Text("PBTL") })
        }
        Text(
            if (s.dac != null) "Aktif di chip: ${s.dac!!.uppercase()}" else "Nilai aktif tidak terbaca (butuh firmware v3).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Modulasi") {
        Text("Mode", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("bd", "1spw", "hybrid").forEach { FilterChip(selected = modMode == it, onClick = { modMode = it }, label = { Text(it.uppercase()) }) }
        }
        Text("Frekuensi proses DSP", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("384k", "480k", "576k", "768k").forEach { FilterChip(selected = modFreq == it, onClick = { modFreq = it }, label = { Text(it) }) }
        }
        Text("Frekuensi switching BD", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("80k", "100k", "120k", "175k").forEach { FilterChip(selected = bdFreq == it, onClick = { bdFreq = it }, label = { Text(it) }) }
        }
        Button(onClick = { confirm = "Terapkan modulasi $modMode / $modFreq / $bdFreq?" to "mod" }) { Text("Terapkan modulasi") }
        Text(
            "Pengaturan ini mengubah perilaku EMI dan efisiensi output. Biarkan default bila tidak yakin.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Soft clipper") {
        LabeledSlider("Gain clipper", "%.1f dB".format(clipGain / 10f), clipGain, -100f..0f) { clipGain = it.roundToInt().toFloat() }
        LabeledSlider("Makeup kiri", "%.1f dB".format(clipL / 10f), clipL, 0f..100f) { clipL = it.roundToInt().toFloat() }
        LabeledSlider("Makeup kanan", "%.1f dB".format(clipR / 10f), clipR, 0f..100f) { clipR = it.roundToInt().toFloat() }
        Button(onClick = { vm.setClipper(clipGain.roundToInt(), clipL.roundToInt(), clipR.roundToInt()) }) { Text("Terapkan clipper") }
        Text(
            if (s.hasV3) "Satuan: deci-dB (−30 = −3,0 dB). Nilai slider dibaca dari chip." else "Satuan: deci-dB (−30 = −3,0 dB). Firmware dasar tidak mengirim balik nilai clipper, jadi slider bukan nilai chip saat ini.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    confirm?.let { (msg, cmd) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Konfirmasi") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    when (cmd) {
                        "btl", "pbtl" -> vm.setDac(cmd)
                        "mod" -> vm.setModulation(modMode, modFreq, bdFreq)
                        else -> vm.raw(cmd)
                    }
                    confirm = null
                }) { Text("Terapkan") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

// ───────────────────────── Konsol ─────────────────────────

@Composable
private fun ConsolePage(vm: TunerViewModel) {
    val log by vm.log.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.size - 1) }

    SectionCard("Kirim perintah") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), singleLine = true, label = { Text("mis. status, eqm 2, vol 90") },
            )
            IconButton(onClick = { vm.raw(input); input = "" }, enabled = input.isNotBlank()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Kirim")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("status", "fault", "caps", "eqdump", "ping", "lvl", "help").forEach { c ->
                AssistChip(onClick = { vm.raw(c) }, label = { Text(c) })
            }
        }
    }

    SectionCard("Log", trailing = { TextButton(onClick = { vm.clearLog() }) { Text("Bersihkan") } }) {
        LazyColumn(Modifier.fillMaxWidth().height(340.dp), state = listState) {
            items(log) { e ->
                val c = when (e.dir) {
                    '>' -> MaterialTheme.colorScheme.primary
                    '<' -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Text(
                    "${fmt.format(Date(e.time))} ${e.dir} ${e.text}",
                    color = c, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                )
            }
        }
        Text(
            "Catatan: perintah CLI firmware (mis. eqm, vol, mod) mencetak hasilnya ke UART, bukan ke SPP — di sini hanya \"OK …\" yang terlihat.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────── Pengaturan ─────────────────────────

@Composable
private fun SettingsPage(vm: TunerViewModel) {
    val st by vm.settings.collectAsStateWithLifecycle()
    SectionCard("Tampilan") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Terang", ThemeMode.DARK to "Gelap").forEach { (m, t) ->
                FilterChip(selected = st.theme == m, onClick = { vm.store.updateSettings { it.copy(theme = m) } }, label = { Text(t) })
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow("Warna dinamis (Material You)", "Ikuti wallpaper", st.dynamicColor) { v -> vm.store.updateSettings { it.copy(dynamicColor = v) } }
        }
        SwitchRow("Layar tetap menyala", "Berguna saat tuning sambil mendengarkan", st.keepScreenOn) { v -> vm.store.updateSettings { it.copy(keepScreenOn = v) } }
    }
    SectionCard("Koneksi") {
        SwitchRow("Sambung otomatis", "Ke perangkat terakhir saat aplikasi dibuka", st.autoReconnect) { v -> vm.store.updateSettings { it.copy(autoReconnect = v) } }
        Text(
            "Perangkat terakhir: ${st.lastName ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { vm.store.updateSettings { it.copy(lastAddress = null, lastName = null) } }, enabled = st.lastAddress != null) {
            Text("Lupakan perangkat terakhir")
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ───────────────────────── Tentang ─────────────────────────

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    val ver = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }
    SectionCard("TAS5805M Tuner") {
        Text("Versi $ver", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Klien Bluetooth SPP untuk firmware ESP32 + TAS5805M. Perangkat target: \"TAS5805M-CTRL\" (RFCOMM, UUID 00001101).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard("Tiga env firmware") {
        AudioMode.entries.forEach {
            Column {
                Text("${it.code} · ${it.env}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(it.subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    SectionCard("Perintah SPP") {
        Text(
            "Dasar: status · mode N · xover HZ · master N · satvol N · subvol N · gain N · ampstate X · eq on|off · mixer ll|lr|rl|rr G · fault · ping\n" +
                "Ekstensi v2: caps · eqband l|r|b BAND GAIN · eqdump · lvl\n" +
                "Lainnya diteruskan ke CLI firmware (eqm, eqp, mod, dacmode, clip, …).",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
