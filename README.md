# TAS5805M Tuner — aplikasi Android

Aplikasi tuning untuk ESP32 + TAS5805M lewat Bluetooth SPP (`TAS5805M-CTRL`).
Kotlin · Jetpack Compose · Material 3 · minSdk 26.

## Build APK lewat GitHub
1. Buat repo baru, upload isi zip ini (folder `.github` harus ikut).
2. Tab **Actions** → **Android APK** berjalan otomatis (atau *Run workflow*).
3. Unduh artifact **TAS5805M-Tuner-apk** → install `.apk` di HP.
4. Rilis: `git tag v1.0.0 && git push --tags` → APK terlampir di **Releases**.

Opsional (APK ditandatangani key sendiri): isi Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Tanpa itu, APK release ditandatangani debug key (tetap bisa di-install).

## Build lokal
Android Studio: *Open* folder ini → Run. CLI: `gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug` (JDK 17 + Android SDK 34). Wrapper tidak disertakan; CI memakai Gradle 8.9 langsung.

## Pemakaian
Pair `TAS5805M-CTRL` di Bluetooth Android → buka aplikasi → pilih perangkat. Mode (stereo / 2.1 2-chip / 2.1 1-chip) dibaca otomatis dari firmware.

Butuh **firmware v3** (zip firmware) untuk EQ per-channel, meter level, readback DAC/modulasi/clipper, status chip, dan simpan setelan. Dengan firmware bawaan, kontrol dasar tetap berfungsi.
Protokol: lihat `PROTOCOL.md`.

> Kode belum pernah dikompilasi sebelum dikemas (v2.2: hanya perubahan kecil di `TunerViewModel.kt` + dokumentasi). Bila build pertama gagal, kirim log error-nya.

## Sinkron dengan firmware 2.1 / 2.4
Di mode 2.1 app:
- Mengunci band EQ 20/32/50 Hz (slot crossover) dan menolak EQ dimatikan / eqpset.
- **Mixer matrix & mode mixer terbuka** (untuk eksperimen imaging / crossfeed).
- Preset/Reset hanya mengubah band ≥ 80 Hz.
Firmware menolak perintah berbahaya dengan `ERR … locked_in_2.1`. Belum dikompilasi.

## v2.3 — mode 2-chip
Monitor menampilkan fault satelit (chip 1) dan sub (chip 2) terpisah · topologi DAC di mode 2-chip hanya untuk sub · profil filter ditampilkan per chip. Butuh firmware v3.3+.

## v2.4
Mixer UI selalu aktif (termasuk mode 2.1). Sinkron dengan firmware 2.4. Belum dikompilasi.

