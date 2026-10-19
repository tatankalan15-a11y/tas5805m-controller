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

> Kode belum pernah dikompilasi sebelum dikemas. Bila build pertama gagal, kirim log error-nya.
