# TAS5805M Controller — Final 3.0.0

Aplikasi Android **lengkap** untuk tuning ESP32 + TAS5805M.

**Kotlin · Jetpack Compose · Material 3 · Bluetooth SPP**

Versi ini didasarkan pada controller **2.12.0** (fitur penuh), dikemas ulang sebagai **rilis final 3.0.0**.

---

## Fitur lengkap

### Koneksi
- Bluetooth Classic **SPP** ke perangkat `TAS5805M-CTRL`
- Pair di pengaturan Bluetooth Android, lalu pilih di app

### Mode sistem (otomatis dari firmware)
- Stereo (1 chip)
- 2.1 dua chip (satelit + sub di satu ESP32)
- 2.1 satu chip / wireless sesuai firmware

### Tab Tune
- Volume master & sub
- Mute
- Bass / treble / loudness (sesuai protokol)
- Quick actions: Mute All · Reset · Crossfeed · **Save NVS**
- Indikator **Saved / Unsaved**

### Tab EQ
- 15-band parametric
- EQ on/off, profile filter
- Mode 2.1: band crossover dikunci (aman)
- Preset / reset (band ≥ 80 Hz di mode 2.1)

### Tab Output
- **Crossfeed** 0–100% + loudness compensation
- Preset: Stereo / Ringan / Sedang / Kuat / Asimetris / Mono
- Matrix mixer manual (−1…2)
- **Lebar stereo**
- Polaritas sub, Center (3.1 jika firmware mendukung)
- DAC mode BTL / PBTL

### Tab Monitor
- Level meter
- Fault satelit & sub (mode 2-chip) terpisah
- Status chip / modulasi / clipper (firmware v3+)

### Lainnya
- Preset simpan/muat
- Auto-standby terkait firmware
- Protokol teks lengkap: lihat `PROTOCOL.md`

---

## Build APK lewat GitHub

1. Buat repository GitHub baru  
2. Upload **seluruh isi** folder ini (termasuk `.github/`)  
3. Tab **Actions** → workflow **Android APK** → tunggu hijau  
4. Download artifact **TAS5805M-Tuner-apk** → install di HP  

```bash
cd tas5805m-controller-final
git init
git add .
git commit -m "TAS5805M Controller Final 3.0.0"
git branch -M main
git remote add origin https://github.com/USERNAME/tas5805m-controller.git
git push -u origin main
```

Tag rilis (APK di Releases, jika workflow mendukung):

```bash
git tag v3.0.0
git push --tags
```

### Secrets opsional (APK signed)

| Secret | Keterangan |
|--------|------------|
| `KEYSTORE_BASE64` | Keystore base64 |
| `KEYSTORE_PASSWORD` | |
| `KEY_ALIAS` | |
| `KEY_PASSWORD` | |

Tanpa secrets, APK tetap di-build (signed debug) dan bisa di-install.

---

## Build lokal

- **Android Studio**: Open folder ini → Sync → Run  
- **CLI** (JDK 17 + Android SDK):

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleRelease
# app/build/outputs/apk/release/
```

---

## Firmware yang dibutuhkan

| Fitur | Firmware |
|-------|----------|
| Kontrol dasar | SPP `TAS5805M-CTRL` |
| EQ per-channel, meter, readback, save | **v3+** |
| 2-chip fault terpisah | **v3.3+** |
| Center / lebar stereo lanjutan | **≥ 2.6.x** sesuai catatan changelog |

Protokol perintah: **`PROTOCOL.md`**

---

## Struktur

```
app/src/main/java/com/tas5805m/tuner/
├── MainActivity.kt
├── TunerViewModel.kt      # logika utama
├── bt/                    # SPP client
├── data/                  # Protocol, Models, Presets
└── ui/                    # Tune, EQ, Output, Monitor, Connect, …
.github/workflows/android.yml
PROTOCOL.md
```

## Lisensi

MIT (lihat LICENSE bila ada). Kode aplikasi disediakan apa adanya; uji di perangkat Anda.

---

### Multi-sub wireless (3.1.0+)

- Hingga **4** slave ESP-NOW
- UI: daftar slot, pair/lepas per MAC, lepas semua
- Protokol: `WSUB peers=…` / `WSUBP` (lihat PROTOCOL.md)
- Firmware 1-peer lama tetap jalan
- Level `subvol`/`xover`/`subinv` broadcast ke semua peer

**Final 3.1.0** — controller lengkap + multi-sub wireless.

