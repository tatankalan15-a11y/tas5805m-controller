# Protokol SPP — TAS5805M-CTRL

Transport: Bluetooth Classic RFCOMM (UUID `00001101-0000-1000-8000-00805F9B34FB`), teks ASCII, satu perintah per baris (`\n`), maks. ±200 karakter.

## Perintah dasar (firmware asli)

| Perintah | Arti | Balasan |
|---|---|---|
| `status` / `sync` | kirim ulang seluruh state | `MODE …`, `STATE …`, `FAULT …`, `OK status` |
| `mode 0\|1\|2` | stereo / 2-chip 2.1 / 1-chip 2.1 | `MODE…`, `STATE…`, `OK mode` |
| `xover 60..150` | frekuensi crossover (dibulatkan ke profil 10 Hz) | `OK xover` |
| `master 0..100` | volume master | `OK master` |
| `satvol N` / `subvol N` | level satelit / sub (0..100) | `OK …` |
| `gain 0..31` | gain analog (0 = 0 dB, 31 = −15,5 dB) | `OK gain` |
| `ampstate play\|mute\|hiz\|sleep\|deepsleep` | state amplifier | `OK ampstate` |
| `eq on\|off` | aktif/nonaktifkan EQ | `OK eq` |
| `mixer ll\|lr\|rl\|rr −1..2` | gain matriks mixer. Mode 0: kedua chip · mode 1: hanya chip satelit (0x2D) · **mode 2: ditolak** | `OK mixer` / `ERR mixer locked_in_1chip_21` |
| `fault` / `diag` | baca (dan hapus) fault | `FAULT …`, `OK fault` |
| `ping` | tes koneksi | `OK ping` (firmware lama juga mengirim `PONG`) |

Perintah lain diteruskan ke CLI firmware (`esp_console_run`): `eqm`, `eqp`, `eq`, `vol`, `volume`, `mod`, `dacmode`, `clip`, `mixermode`, `lvl on|off`, … Output CLI keluar di UART, hanya `OK <perintah>` yang kembali lewat SPP.

### Format baris

```
MODE 2 xover=150 master=90 sat=90 sub=90
STATE gain=18 amp=play eq=1 ll=1.00 lr=0.00 rl=0.00 rr=1.00
FAULT err0=0x00 err1=0x00 err2=0x00 ot=0x00 oc_r=0 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0
```

## Ekstensi v2 (ditambahkan di `src/main.cpp`)

| Perintah | Arti | Balasan |
|---|---|---|
| `caps` | deteksi kemampuan | `CAPS eqband eqdump lvl v2`, `OK caps` |
| `eqband l\|r\|b BAND GAIN` | gain satu band (0..14, −15..+15 dB) untuk kiri/kanan/keduanya | `OK eqband` / `ERR eqband` |
| `eqdump` | baca balik EQ & mode | `EQ l=…`, `EQ r=…`, `EQM n`, `OK eqdump` |
| `lvl` | level meter DSP (deci-dB, −1200..0) | `LVL l=-250 r=-310` |

Mengapa `eqband`? CLI `eq l|r BAND GAIN` bawaan mengabaikan argumen kanal (selalu menulis kiri).

`EQM n` = nilai register: `6` ON, `7` OFF, `14` BIAMP, `15` BIAMP_OFF.

Aplikasi mendeteksi firmware v2 lewat `caps`; tanpa itu EQ jatuh kembali ke `eq l BAND GAIN` (linked) dan meter level disembunyikan.

## Ekstensi v3 (firmware lengkap)

`caps` kini juga mengirim `v3 dac mod clip eqp mixmode info save`. Semua hasil kembali lewat SPP (bukan UART).

| Perintah | Balasan | Keterangan |
|---|---|---|
| `dump` | semua baris di bawah + `OK dump` | sinkron penuh saat tersambung |
| `dacget` / `dacset btl\|pbtl` | `DAC mode=btl` | topologi output |
| `modget` / `modset MODE FREQ BD` | `MOD mode=bd freq=768k bd=175k` | MODE: bd 1spw hybrid · FREQ: 384k 480k 576k 768k · BD: 80k 100k 120k 175k |
| `clipget` / `clipset G L R` | `CLIP gain=-30 ml=30 mr=30` | deci-dB; G −300..0, L/R 0..300 |
| `eqpset l\|r\|b N` | `EQP l=N r=N` | profil filter 0=FLAT, 1..10=LPF 60..150, 11..20=HPF 60..150 (`eqdump` juga mengirim `EQP`) |
| `mixmode stereo\|inv\|mono\|left\|right` | `MODE…`, `STATE…` | mixer bawaan chip; cermin g_mix dibaca ulang dari chip |
| `info` | `INFO fs=48KHz bck=64 pwr=PLAY mute_r=0 mute_l=0` | status chip (spasi diganti `_`) |
| `save` / `erase` / `saved` | `SAVED 1` / `SAVED 0` | simpan / hapus / cek setelan di NVS |
| `reboot` | `OK reboot` | restart ESP32 |

### Penyimpanan (NVS, namespace `tas5805m`)
Disimpan: mode, xover, master, sat, sub, gain, EQ kiri/kanan, mode EQ, clipper, mixer. Dipulihkan saat boot **hanya jika mode tersimpan = `AUDIO_MODE` env yang di-flash**. Urutan pemulihan: mode 0 → mode (profil FLAT) lalu EQ; mode 2.1 → EQ lalu crossover (crossover diterapkan terakhir agar sub tidak menerima full-range).

## Band EQ

`20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000` Hz.

## Penguncian mode 2.1 (firmware v3.1 → v3.4)
Pada mode 1/2 firmware membalas `ERR <cmd> locked_in_2.1` untuk: `eq off`, `eqband` band 0..2, `eqpset`, serta CLI `eq*` (bentuk baca seperti `eqm`/`eqp` tetap boleh).  
**Mixer matrix dan `mixmode`:** terbuka di mode 0 dan mode 1 (chip satelit saja); dikunci di mode 2 (firmware ≥ 2.5).  
Mode 2 memakai EQ BIAMP (`EQM 14`) otomatis. Band 0..2 (20/32/50 Hz) = biquad BQ1..BQ3 yang dipakai profil crossover.

## Level meter sub (firmware v3.1, hanya env 2-chip)
`lvl2` → `LVL2 l=-250 r=-250` (deci-dB, chip 2 / sub). `CAPS` memuat token `lvl2` bila chip 2 ada. Env lain: `ERR lvl2 no_chip2`.

## Perubahan v3.2
- `fault` / `diag` kini benar-benar melaporkan fault yang terkunci (sebelumnya selalu nol).
- `mode 1` ditolak `ERR mode no_chip2` bila hardware tidak punya chip kedua; aplikasi lalu menyegarkan state lewat `status`.
- `EQM n` selalu dinormalisasi ke 6/7/14/15.
- Setelan NVS menyimpan env firmware dan mode runtime secara terpisah.

## Perubahan v3.3 (fokus mode 1 / 2-chip)
| Perintah | Balasan | Keterangan |
|---|---|---|
| `status` / `sync` / `fault` / `diag` | `FAULT …` (chip 1) + `FAULT2 …` (chip 2, env 2-chip) | kedua chip dibaca dulu, baru dihapus. `FAULT2_ERR` bila chip 2 tidak terbaca |
| `dacget` | `DAC mode=…` + `DAC2 mode=…` | `DAC2` = chip 2 (hanya env 2-chip) |
| `dacset btl\|pbtl` | `DAC …` (+ `DAC2 …`) | **mode 1: hanya chip 2 (sub)**; chip 1 (satelit) tidak disentuh. Mode lain: kedua chip |
| `eqdump` (mode 1) | `EQP l=.. r=..` (chip 1) + `EQP2 l=.. r=..` (chip 2) | chip 1 = HPF (11..20), chip 2 = LPF (1..10) |

Pastikan wiring PBTL benar sebelum `dacset pbtl` di chip 2.

## Perubahan v3.4 / app v2.4
- (Digantikan firmware 2.5) Mixer matrix dan `mixmode` dibuka di mode 2.1 (UI app selalu aktif).
- File patch eksternal dihapus di sisi firmware; logika 2.1 terintegrasi di source.

## Sinkron dengan firmware 2.5.1
- `mixer` / `mixmode`: mode 0 → kedua channel; mode 1 → hanya chip satelit (0x2D); mode 2 → `ERR mixer locked_in_1chip_21` / `ERR mixmode locked_in_1chip_21`.
- Mode 0: `master`/`satvol`/`subvol` tidak me-reset mixer.
- `mode N` / `xover N` dapat membalas `ERR mode i2c` / `ERR xover i2c` bila penulisan I2C gagal.

## Mode 3 — 2-chip 3.1 (firmware ≥ 2.6.0, app ≥ 2.10.0)
- `mode 3` (alias `31`, `3chip`). Chip 1 (0x2D): satelit L/R + HPF. Chip 2 (0x2E): OUT_L = center (HPF), OUT_R = sub (LPF), EQ BIAMP. Center = downmix (L+R)/2.
- `ctr 0..100` → `OK ctr` + `MODE…`/`STATE…`. Baris `MODE` kini memuat `ctr=`; `CAPS` (env 2-chip) memuat token `ctr`.
- `mixer` / `mixmode` / `dacset`: mixer hanya chip satelit; dacset hanya chip 2 (harus tetap BTL). `EQP` / `EQP2` seperti mode 1.

## Pelebaran stereo (firmware ≥ 2.6.1, app ≥ 2.10.1)
- `mixer ll|lr|rl|rr G` kini menerima **G = −1..2** (sebelumnya 0..2). Gain silang negatif memperkuat sisi (L−R): `LL=RR=1+w`, `LR=RL=−w` (w 0..0.5). Sinyal mono tetap 0 dB; sinyal satu sisi dapat naik hingga +3,5 dB pada w=0.5.
- Berlaku di mode 0 (kedua chip) dan mode 1/3 (chip satelit saja). Mode 2 tetap dikunci.
- Perlu diverifikasi di hardware: pastikan polaritas benar (putar sinyal L-saja → R harus berlawanan fase) dan tidak ada clipping.

## Efek tambahan (firmware ≥ 2.8.0, app ≥ 2.11.0)
| Perintah | Fungsi | Balasan |
|---|---|---|
| `subinv 0\|1` | balik polaritas sub (mixer chip sub dikalikan −1). Mode 1/2/3; mode 0 → `ERR subinv no_sub` | `OK subinv` + `MODE…`/`STATE…`/`FX…` |
| `standby N` | auto-standby: setelah N menit (0–240) tanpa audio A2DP, amp → Hi-Z; audio kembali → Play. 0 = mati. Hanya aktif bila amp berstatus `play`; perintah amp manual membatalkan. | `OK standby` + status |
| `eqc c\|s BAND GAIN` | **mode 3**: EQ center (`c`, chip 2 kiri) / sub (`s`, chip 2 kanan), band 3..14, gain −15..15. Mode lain → `ERR eqc only_mode3` | `OK eqc` / `ERR eqc` |

- Baris baru: `FX inv=0|1 stby=<menit> sb=0|1` (di setiap `status`; `sb=1` = sedang standby). `eqdump` di mode 3 menambah `EQC g0,…,g14` dan `EQS g0,…,g14`.
- Mode 3: `eqband` kini hanya menulis chip 1 (satelit); center/sub diatur lewat `eqc`.
- `save` menyimpan `subinv`, `standby`, EQ center & sub (NVS: `sinv`, `stby`, `eqc`, `eqs`).
- `CAPS` memuat token `subinv standby` (+ `eqc` di env 2-chip).
- Polaritas memakai gain mixer negatif — sama seperti pelebaran stereo, perlu diverifikasi di hardware.
- Auto-standby memakai `a2dp_sink.get_audio_state()`; jeda ≤ 200 ms + waktu bangun chip dari Hi-Z, sehingga awal audio setelah standby dapat tertunda.

## Sub wireless (firmware ≥ 2.9.0, app ≥ 2.12.0) — env `esp32dev_wireless_master` + `esp32dev_sub_slave`
**Mode 4** (`mode 4` / `wsub` / `wireless`): chip 1 = satelit L/R + HPF; sub di board slave. `ERR mode no_wsub` di env lain.

| Perintah (master, SPP) | Fungsi |
|---|---|
| `wsub_scan` | cari slave (HELLO) ±8 dtk → hasil di baris `WSUB … found=` |
| `wsub_pair AA:BB:CC:DD:EE:FF` | pasangkan (disimpan NVS master) → `OK wsub_pair` |
| `wsub_unpair` | lepas pairing master |

Baris status (di setiap `status`, hanya env master): `WSUB peer=<mac|none> link=0|1 fill=<ms> und=<n> gap=<n> rate=<Hz> hiz=0|1 scan=0|1 found=<mac,mac|none>`. `CAPS` memuat token `wsub`.

**Perintah yang berlaku di mode 4:** `master`, `satvol` (volume chip 1), `subvol` (level sub → slave), `subinv` (polaritas sub di slave), `xover` (HPF satelit & LPF slave 60–150 Hz). Level akhir sub dikirim ke slave = `master × subvol / 100`.

**Radio (ESP-NOW, channel 1, tanpa enkripsi):** paket tipe AUDIO (mono, FIR low-pass 700 Hz, decimasi ÷16, 32 sample/paket, header memuat sample rate sumber), CTRL (≈10 Hz: level, flag polaritas, crossover), STATUS (slave → master 1 Hz: buffer, underrun, paket hilang), HELLO (slave tak terpasang, broadcast 1 Hz), PAIR (master → slave; ulang 500 ms sampai ada STATUS). Slave hanya menerima audio/kontrol dari MAC master yang dipasangkan; PAIR diterima bila slave belum terpasang atau dari master yang sama. Unpair slave: tahan tombol BOOT 3 dtk atau ketik `unpair` di serial.

**Slave:** ring buffer, mulai putar saat ≥ 20 ms terisi, interpolasi ×16 ke sample rate sumber, koreksi drift ±1 sampel/4 sampel-wire, paket hilang ≤ 7 ditahan (hold), amp → Hi-Z setelah 10 dtk tanpa audio. Volume slave 0 sampai master mengirim level.


### Multi-sub (app ≥ 3.1, firmware multi-peer)

Hingga **4** slave. App kompatibel mundur dengan 1 peer.

| Perintah | Arti |
|---|---|
| `wsub_scan` | HELLO scan (daftar di `found=`) |
| `wsub_pair AA:BB:CC:DD:EE:FF` | tambah peer (max 4); peer lama tetap |
| `wsub_unpair AA:BB:…` | lepas satu MAC |
| `wsub_unpair` | lepas semua (firmware 1-peer) |
| `wsub_unpair_all` | lepas semua (firmware multi) |

**Status ringkas (satu baris):**
```
WSUB peers=AA:…,BB:… links=1,0 fill=45,0 und=0,1 gap=0,0 rate=44100,0 hiz=0,0 max=4 scan=0 found=CC:…
```
Firmware 1-peer tetap: `WSUB peer=AA:… link=1 fill=…` (tanpa `peers=`).

**Status per slot (opsional):**
```
WSUBP slot=0 mac=AA:… link=1 fill=45 und=0 gap=0 rate=44100 hiz=0
WSUBP slot=1 mac=BB:… link=0 fill=0 und=0 gap=0 rate=0 hiz=1
```

Audio CTRL: level/`subvol`/`xover`/`subinv` dikirim ke **semua** peer yang terpasang (sama seperti broadcast). Volume per-slot belum ada di protokol SPP (semua sub dapat level yang sama).

