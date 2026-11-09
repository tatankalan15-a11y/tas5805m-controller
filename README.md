# TAS5805M Controller **3.2.3**

Aplikasi Android untuk tuning **ESP32 + TAS5805M** lewat Bluetooth **SPP** (`TAS5805M-CTRL`).

Kotlin · Jetpack Compose · Material 3 · minSdk 26

---

## Fitur

- Volume master / satelit / sub / center  
- EQ 15-band + preset (dengan proteksi mode 2.1)  
- Mixer, crossfeed, lebar stereo, DAC BTL/PBTL  
- Monitor level & fault (termasuk 2-chip)  
- Save NVS, standby, polaritas sub  
- **Sub wireless multi** (hingga 4 slave) — butuh firmware ≥ **2.9.3**  
- Mode UI otomatis dari `status` / `caps` firmware  
- **Sinkron sub ↔ satelit**: trim latensi (−5/−1/+1/+5 ms) + diagnostik link radio master (ACK, antrean penuh, channel) + ringkasan penyebab — butuh firmware ≥ **2.9.8**  
- **Log master + slave** di tab Lainnya → Konsol (filter “Sub wireless”): baris `WSUB …` (master) dan `SLAVE …` (diagnostik radio slave, relay lewat master) — baris slave butuh firmware ≥ **2.9.9**  
- **Sambung ulang otomatis** bila koneksi putus sendiri (maks 5×, jeda 3 dtk; ikut opsi “Sambung otomatis”)  
- Log: baris status identik disaring, kapasitas 1000 baris, tombol **Simpan** (tulis ke file .txt, pilih lokasi mis. Downloads) dan **Bagikan** (kirim log untuk diagnosa)  

Protokol: lihat **`PROTOCOL.md`**.

---

## Build APK (GitHub Actions)

1. Buat repo, upload seluruh isi folder ini (termasuk `.github/`)  
2. Tab **Actions** → **Android APK** → unduh artifact  
3. Install APK di HP  

```bash
git init && git add . && git commit -m "Controller 3.1.0"
git branch -M main
git remote add origin https://github.com/USER/tas5805m-controller.git
git push -u origin main
```

Tag rilis: `git tag v3.1.0 && git push --tags`

---

## Pemakaian

1. Flash firmware yang sesuai (stereo / 2-chip / wireless master + slave)  
2. Pair **`TAS5805M-CTRL`** di Bluetooth Android  
3. Buka app → hubungkan  
4. Mode wireless: tab **Output → Sub wireless (multi)** → scan & pair  

---

## Firmware yang didukung

| Firmware | Keterangan |
|----------|------------|
| 2.9.x SPP | Kontrol penuh |
| **2.9.3 multi-sub** | Hingga 4 peer wireless |
| Env stereo / 2.1 / 3.1 / wireless | Mode terbaca otomatis |

Bukan klien BLE JSON (project terpisah).

---

## Struktur

```
app/src/main/java/com/tas5805m/tuner/
  MainActivity.kt · TunerViewModel.kt
  bt/ · data/ · ui/
.github/workflows/android.yml
PROTOCOL.md
```

## Lisensi

MIT — lihat `LICENSE`.

## Perubahan 3.2.1

- Baris SPP > 400 karakter tidak lagi terpotong (status dengan banyak slave + daftar `found=` sebelumnya bisa rusak, termasuk MAC pairing).
- Tombol trim: tap beruntun kini menumpuk (sebelumnya memakai nilai lama sehingga sebagian tap hilang).
- Kartu sub wireless dibersihkan saat koneksi putus (sebelumnya data basi tetap tampil).
- Polling status tidak lagi membanjiri log dengan `MODE/STATE/FX` identik.

## Perubahan 3.2.2

- Log Konsol bisa **disimpan ke file** `tas5805m-log-<tanggal>.txt` (seluruh isi log sesuai filter aktif, hingga 1000 baris, dengan tanggal lengkap). Memakai pemilih file sistem, tanpa izin penyimpanan.

## Perubahan 3.2.3

- Perbaikan build: komentar KDoc di `TunerViewModel.kt` mengandung urutan penutup komentar sehingga compile gagal ("Expecting member declaration"). Dikoreksi.
