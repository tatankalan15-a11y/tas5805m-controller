# Protokol SPP — TAS5805M-CTRL

Transport: Bluetooth Classic RFCOMM (UUID `00001101-0000-1000-8000-00805F9B34FB`), teks ASCII, satu perintah per baris (`\n`), maks. ±200 karakter.

## Perintah dasar (firmware asli)

| Perintah | Arti | Balasan |
|---|---|---|
| `status` / `sync` | kirim ulang seluruh state | `MODE …`, `STATE …`, `FAULT …`, `OK status` |
| `mode 0\|1\|2\|3\|4` | stereo / 2-chip 2.1 / 1-chip 2.1 / 2-chip 3.1 / sub wireless | `MODE…`, `STATE…`, `OK mode` |
| `xover <hz> [order]` | crossover 40–200 Hz; order 2 (Butterworth) atau 4 (Linkwitz-Riley, default) | `OK xover` |
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
MODE 2 xover=150 ord=4 master=90 sat=90 sub=90
STATE gain=18 amp=play eq=1 ll=1.00 lr=0.00 rl=0.00 rr=1.00
FAULT err0=0x00 err1=0x00 err2=0x00 ot=0x00 oc_r=0 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0
```

## Ekstensi v2 (ditambahkan di `src/main.cpp`)

| Perintah | Arti | Balasan |
|---|---|---|
| `caps` | deteksi kemampuan | `CAPS eqband eqdump lvl v2`, `OK caps` |
| `eqband l\|r\|b BAND GAIN` | gain satu band (0..14, −15..+15 dB) untuk kiri/kanan/keduanya. Menggantikan PEQ yang tersimpan di band itu. Band 12–14 ditolak selama time alignment aktif | `OK eqband` / `ERR eqband` |
| `peq l\|r\|b BAND FREQ Q GAIN` | **Parametric EQ** — tulis koefisien peaking (freq, Q, gain bebas). Disimpan ke NVS lewat `save` | `OK peq` / `ERR peq` |
| `tdelay sat\|sub\|ctr\|b MS` | **Time alignment** perkiraan (3× all-pass pada band 12–14), 0–13 ms | `TDELAY sat=… sub=… ctr=…`, `OK tdelay` / `ERR tdelay …` |
| `eqdump` | baca balik EQ & mode | `EQ l=…`, `EQ r=…`, `EQM n`, `OK eqdump` |
| `lvl` | level meter DSP (deci-dB, −1200..0) | `LVL l=-250 r=-310` |

**`peq`** (firmware ≥ 2.9.5):  
`peq b 5 1000 1.5 3` → band 5, 1000 Hz, Q=1.5, +3 dB pada kedua channel.  
Rentang: band 0..14 (di mode 2.1 band 0–2 dikunci; band 12–14 ditolak selama `tdelay` aktif), freq 10–20000 Hz, Q 0.1–20, gain −15..+15 dB.  
Koefisien dihitung dengan RBJ Cookbook (peaking) lalu ditulis ke Book 0xAA dalam format Q4.27. Parameter (freq, Q, gain, kanal, band) disimpan di NVS lewat `save` dan dipasang ulang setelah profil mode/crossover. PEQ tidak bisa dibaca balik dari DSP.

**`tdelay`** (firmware ≥ 2.9.5): pendekatan group-delay, bukan delay sampel. Maksimum efektif ≈ 13,5 ms (dibatasi 13 ms); delay hanya konsisten di frekuensi rendah (separuhnya hilang di ≈ 1 kHz untuk 1 ms, ≈ 190 Hz untuk 5 ms, ≈ 74 Hz untuk 13 ms), sehingga paling cocok untuk sub. `sat` = kanal satelit; `sub` = sub (mode 1/2/3); `ctr` = center (mode 3); `b` = sinonim `sat`. Mode 2: OUT_L = satelit, OUT_R = sub. Mode 3: chip 2 OUT_L = center, OUT_R = sub. Kode error: `ERR tdelay` (argumen/rentang), `ERR tdelay no_sub` (mode 0), `ERR tdelay no_ctr` (bukan mode 3), `ERR tdelay wireless_slave_unsupported` (sub di mode 4).

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
| `limit <thr_db> [makeup_db]` / `limit off` / `limitget` | `LIMIT on=1 thr=-30 mu=30` | Advanced limiter (dB). Default makeup = −thr. Wrapper soft-clipper. |
| `eqpset l\|r\|b N` | `EQP l=N r=N` | profil filter 0=FLAT, 1..10=LPF 60..150, 11..20=HPF 60..150 (`eqdump` juga mengirim `EQP`) |
| `mixmode stereo\|inv\|mono\|left\|right` | `MODE…`, `STATE…` | mixer bawaan chip; cermin g_mix dibaca ulang dari chip |
| `info` | `INFO fs=48KHz bck=64 pwr=PLAY mute_r=0 mute_l=0` | status chip (spasi diganti `_`) |
| `save` / `erase` / `saved` | `SAVED 1` / `SAVED 0` | simpan / hapus / cek setelan di NVS |
| `reboot` | `OK reboot` | restart ESP32 |

### Penyimpanan (NVS, namespace `tas5805m`)
Disimpan: mode, xover + order, master, sat, sub, ctr, gain, EQ kiri/kanan, parameter PEQ, time alignment, mode EQ, clipper/limiter, mixer. Dipulihkan saat boot **hanya jika mode tersimpan = `AUDIO_MODE` env yang di-flash**. Urutan pemulihan: mode 0 → mode (profil FLAT) lalu EQ; mode 2.1 → EQ lalu crossover (crossover diterapkan terakhir agar sub tidak menerima full-range).

## Band EQ

`20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000, 3150, 5000, 8000, 16000` Hz.

## Penguncian mode 2.1 (firmware v3.1 → v3.4)
Pada mode 1/2 firmware membalas `ERR <cmd> locked_in_2.1` untuk: `eq off`, `eqband` band 0..2, `eqpset`, serta CLI `eq*` (bentuk baca seperti `eqm`/`eqp` tetap boleh).  
**Mixer matrix dan `mixmode`:** terbuka di mode 0 dan mode 1 (chip satelit saja); **dikunci di mode 2** sejak v3.5 — lihat bagian v3.5.  
Mode 2 memakai EQ BIAMP (`EQM 14`) otomatis. Band 0..2 (20/32/50 Hz) = biquad BQ1..BQ3 yang dipakai profil crossover.

## Perubahan v3.2
- `fault` / `diag`: membaca register, melaporkan lewat SPP, lalu menghapus fault. Sebelumnya CLI menghapus fault lebih dulu sehingga laporan selalu nol.
- `mode 1` dan `mode 3` ditolak dengan `ERR mode no_chip2` bila env tidak punya chip kedua.
- NVS menyimpan `env` (AUDIO_MODE yang di-flash) terpisah dari `mode` (mode runtime). Setelan dipulihkan bila `env` cocok dan mode tersimpan valid untuk hardware; mode runtime pilihan pengguna ikut dipulihkan. Simpanan lama (tanpa `env`) tetap terbaca.
- `EQM n` pada `eqdump` dinormalisasi ke 6/7/14/15 (bit0 = bypass, bit3 = biamp), apa pun cara register ditulis.
- Menerapkan profil filter (`xover`, `mode`) mereset cache gain band 0..2 ke 0, karena BQ1..BQ3 ditimpa profil.
- Semua akses I2C/DSP dari SPP, CLI UART, dan fault monitor diserialkan dengan satu mutex rekursif (`g_ctrl_mtx`).

## Perubahan v3.3 (fokus mode 1 / 2-chip)
| Perintah | Balasan | Keterangan |
|---|---|---|
| `status` / `sync` / `fault` / `diag` | `FAULT …` (chip 1) + `FAULT2 …` (chip 2, env 2-chip) | kedua chip dibaca dulu, baru dihapus. `FAULT2_ERR` bila chip 2 tidak terbaca |
| `dacget` | `DAC mode=…` + `DAC2 mode=…` | `DAC2` = chip 2 (hanya env 2-chip) |
| `dacset btl\|pbtl` | `DAC …` (+ `DAC2 …`) | **mode 1: hanya chip 2 (sub)**; chip 1 (satelit) tidak disentuh. Mode lain: kedua chip |
| `eqdump` (mode 1) | `EQP l=.. r=..` (chip 1) + `EQP2 l=.. r=..` (chip 2) | chip 1 = HPF (11..20), chip 2 = LPF (1..10) |

Pastikan wiring PBTL benar sebelum `dacset pbtl` di chip 2.

## Level meter sub (v3.1, hanya env 2-chip)
`lvl2` → `LVL2 l=-250 r=-250` (deci-dB, chip 2 / sub). `CAPS` memuat token `lvl2` bila chip 2 ada. Env lain: `ERR lvl2 no_chip2`.

## Perubahan v3.4
- (Digantikan v3.5) Mixer matrix (`mixer ll|lr|rl|rr`) dan `mixmode` **dibuka** di mode 1/2 (2.1). Proteksi crossover (eq off, eqband 0–2, eqpset, CLI eq*) tetap aktif.
- File patch eksternal (`PATCH_2.1_*.diff`) dihapus; seluruh logika 2.1 sudah terintegrasi di source.

## Perubahan v3.5 (firmware 2.5.1)
- **Mixer per mode** (menggantikan keterangan v3.4 di atas):
  | Mode | `mixer` / `mixmode` |
  |---|---|
  | 0 stereo | tulis ke kedua channel |
  | 1 (2-chip 2.1) | hanya chip satelit 0x2D; sub tetap mono LPF |
  | 2 (1-chip 2.1) | **dikunci**: `ERR mixer locked_in_1chip_21` / `ERR mixmode locked_in_1chip_21` (matriks dipakai routing sat/sub) |
- **Mode 0:** `master`, `satvol`, `subvol` hanya mengubah volume; mixer (crossfeed / mixmode) tidak lagi di-reset ke unity. Unity hanya diset saat masuk mode 0.
- **Error I2C tidak lagi me-reboot ESP32.** `mode N` dan `xover N` membalas `ERR mode i2c` / `ERR xover i2c` bila ada penulisan I2C yang gagal (state `MODE`/`STATE` terbaru tetap dikirim). Saat boot, kegagalan konfigurasi hanya dicatat di log.
- Kode `ERR` yang ada: `mode`, `mode no_chip2`, `mode i2c`, `xover i2c`, `ampstate`, `eq locked_in_2.1`, `eqband`, `peq`, `tdelay`, `tdelay no_sub`, `tdelay no_ctr`, `tdelay wireless_slave_unsupported`, `eqpset`, `eqpset locked_in_2.1`, `mixer`, `mixer locked_in_1chip_21`, `mixmode`, `mixmode locked_in_1chip_21`, `modset`, `clipset`, `dacset`, `lvl`, `lvl2`, `lvl2 no_chip2`, `<perintah CLI> locked_in_2.1`.

## Perubahan v3.6 (firmware 2.6.0) — mode 3: 2-chip 3.1
| Mode | Chip 1 (0x2D) | Chip 2 (0x2E) |
|---|---|---|
| 3 | OUT_L/OUT_R = satelit L/R, mixer stereo, HPF | OUT_L = **center** (mono (L+R)/2 + HPF), OUT_R = **sub** (mono + LPF), EQ BIAMP |

- `mode 3` (alias `31`, `3chip`) — `ERR mode no_chip2` bila env tanpa chip 2.
- `ctr 0..100` → `OK ctr` + `MODE…`/`STATE…`. Level center lewat gain mixer chip 2 (`ctr·0.5`); level sub `sub·0.5`; volume digital chip 1 = `master·sat/100`, chip 2 = `master`. `ctr` tersimpan di NVS (`save`).
- Baris `MODE` kini: `MODE <m> xover=<hz> master=<n> sat=<n> ctr=<n> sub=<n>` (parser lama yang mengabaikan key tak dikenal tetap kompatibel).
- `CAPS` (env 2-chip) menambahkan token `ctr`.
- `mixer` / `mixmode` di mode 3: hanya chip satelit (DEV_1). `dacset`: hanya chip 2. `EQP` / `EQP2` dilaporkan seperti mode 1 (`EQP2 l` = center HPF, `r` = sub LPF).
- Proteksi 2.1 berlaku: band EQ 0–2 dicadangkan untuk crossover; `eq off`, `eqband` 0–2, `eqpset` ditolak (`locked_in_2.1`).
- `xover` (40–200 Hz) berlaku untuk HPF satelit + center dan LPF sub sekaligus.

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
**Mode 4** (`mode 4` / `wsub` / `wireless`): chip 1 = satelit L/R + HPF; sub di board slave. `ERR mode no_wsub` di env lain. Di env wireless master hanya mode 4 yang diterima: mode lain → `ERR mode locked_wireless`. Slave sub: ESP32 classic saja (`esp32dev_sub_slave`).

| Perintah (master, SPP) | Fungsi |
|---|---|
| `wsub_scan` | cari slave (HELLO) ±8 dtk → hasil di baris `WSUB … found=` |
| `wsub_pair AA:BB:CC:DD:EE:FF` | pasangkan (disimpan NVS master) → `OK wsub_pair` |
| `wsub_unpair` | lepas pairing master |

Baris status (di setiap `status`, hanya env master): `WSUB peer=<mac|none> link=0|1 fill=<ms> und=<n> gap=<n> rate=<Hz> hiz=0|1 scan=0|1 found=<mac,mac|none> fb=0|1`. `CAPS` memuat token `wsub`. `fb=0|1` (firmware ≥ 2.9.1): 1 = sub hilang ≥ 6 dtk, satelit sementara FLAT (fallback stereo); HPF dipasang lagi setelah link stabil 2 dtk. Mode tetap 4.

**Perintah yang berlaku di mode 4:** `master`, `satvol` (volume chip 1), `subvol` (level sub → slave), `subinv` (polaritas sub di slave), `xover` (HPF satelit & LPF slave, dibatasi 60–150 Hz di mode 4). Level akhir sub dikirim ke slave = `master × subvol / 100`.

**Radio (ESP-NOW, channel 1, tanpa enkripsi):** paket tipe AUDIO (mono, FIR low-pass 700 Hz, decimasi ÷16, **16 sample/paket** ≈ 5,8 ms; header memuat sample rate sumber; dikirim **sekali ke alamat broadcast** untuk semua slave, tanpa ACK/retry; laju PHY ESP-NOW 24 Mbps, dapat diubah dengan `-D WSUB_PHY_RATE=…`; `-D WSUB_AUDIO_UNICAST=1` kembali ke unicast per slave), CTRL (≈10 Hz: level, flag polaritas, crossover, dan opsional `lat_ms` = latensi tap→speaker yang diminta untuk sub; paket 10 B, master lama mengirim 8 B tanpa `lat_ms`), STATUS (slave → master 1 Hz: buffer, underrun, paket hilang), HELLO (slave tak terpasang, broadcast 1 Hz), PAIR (master → slave; ulang 500 ms sampai ada STATUS). Slave hanya menerima audio/kontrol dari MAC master yang dipasangkan; PAIR diterima bila slave belum terpasang atau dari master yang sama. Unpair slave: tahan tombol BOOT 3 dtk atau ketik `unpair` di serial.

**Slave:** ring buffer dengan **jitter buffer adaptif** (margin minimum ≈ 3,6 ms; naik 8 sampel-wire per underrun sampai ≈ 40 ms; turun 2 sampel-wire tiap 30 dtk tanpa underrun). Bila master mengirim `lat_ms`, isi buffer minimum dihitung agar latensi sub = `lat_ms`, margin jitter menjadi batas bawah. Mulai putar saat isi = target (kelebihan dibuang), interpolasi ×16 ke sample rate sumber, koreksi drift ±1 sampel/4 sampel-wire (≈ 1,6 %), paket hilang ≤ 11 ditahan (hold), DMA I2S 3 × 64 frame, amp → Hi-Z setelah 10 dtk tanpa audio. Volume slave 0 sampai master mengirim level.


### Multi-sub (2.9.3+)
- Max 4 peers; `wsub_pair MAC` adds; `wsub_unpair MAC` removes one; `wsub_unpair` / `wsub_unpair_all` clears all.
- Status: `WSUB peers=mac1,mac2 links=1,0 fill=… max=4 scan=… found=…` (+ `peer=`/`link=` for legacy).

### Level meter sub wireless (2.9.4+, env `esp32dev_wireless_master`)
- `wlvl` → `WLVL v=<deci-dB>` (−1200..0).
- Nilai = **peak sejak pemanggilan `wlvl` sebelumnya** (dibaca = di-reset) dari sinyal mono 0–700 Hz yang dikirim ke slave, dalam dBFS.
- Diukur **sebelum** volume slave dan penguat; bukan level di speaker. `-1200` bila tidak ada audio atau tidak ada slave linked.
- `caps` memuat token `wlvl` (bersama `wsub`). Env lain menjawab `ERR wlvl no_wsub`.

**Latensi dan penyelarasan sub (firmware ≥ 2.9.6, env wireless master):**
- `wlat` → `WLAT sat=<ms> sub=<ms,…> diff=<ms,…> trim=<ms> tgt=<ms> pkt=<n> rate=<Hz> bc=<0|1>`. `sat` = kedalaman antrean DMA I2S satelit (latensi tap A2DP → speaker). `sub` = estimasi latensi jalur tiap sub dari model + isi buffer yang dilaporkan slave (`fill=` di `WSUB` kini rata-rata yang dihaluskan). `diff` = sub − sat. **Ini estimasi, bukan pengukuran akustik.**
- `wtrim <ms>` → `OK wtrim`; `wtrim` → `WTRIM v=<ms>`. Rentang −20..+50 ms, positif = sub lebih lambat. Disimpan lewat `save` (NVS `wtrim`). `tdelay sub` di mode 4 menjawab `ERR tdelay wireless_use_wtrim`.
- Latensi target sub = `sat` + `trim`. DMA I2S satelit default 6 × 256 frame ≈ 35 ms (`-D WSUB_I2S_DMA_COUNT=… -D WSUB_I2S_DMA_LEN=…`; 4 × 256 ≈ 23 ms lebih rendah tetapi margin jitter lebih tipis).
- `caps` memuat token `wlat` dan `wtrim`. Env lain menjawab `ERR wlat no_wsub` / `ERR wtrim no_wsub`.
- Setiap `status` pada env master juga memuat `WTRIM v=<ms>`.
- Token `lvl2` tetap berarti chip 2 (env 2-chip); tidak dipakai di wireless.

### Catatan clipper (2.9.4)
- `clipset G L R`: tambahan syarat `L <= G + 300` dan `R <= G + 300` (makeup linear harus < 2.0, batas Q2.30). Di luar itu → `ERR clipset`.
- `clipget`/`CLIP gain=` kini bertanda **sama** dengan `clipset` (sebelumnya terbalik). Nilai clipper di NVS dari firmware lama bertanda terbalik, jadi **otomatis diabaikan** saat boot (penanda `clv`=2). Setel ulang dengan `clipset` lalu `save`.
- Konvensi tanda mengikuti bantuan CLI library: G negatif (mis. −30 = −3,0 dB), makeup positif. README/komentar header upstream menulis G positif; belum diverifikasi di hardware mana yang menghaluskan puncak.
