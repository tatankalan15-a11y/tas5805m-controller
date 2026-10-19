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
| `mixer ll\|lr\|rl\|rr 0..2` | gain matriks mixer | `OK mixer` |
| `fault` / `diag` | baca (dan hapus) fault | `FAULT …`, `OK fault` |
| `ping` | tes koneksi | `OK ping` (firmware lama juga mengirim `PONG`) |

Perintah lain diteruskan ke CLI firmware (`esp_console_run`): `eqm`, `eqp`, `eq`, `vol`, `volume`, `mod`, `dacmode`, `clip`, `mixermode`, `lvl on|off`, … Output CLI keluar di UART, hanya `OK <perintah>` yang kembali lewat SPP.

### Format baris

```
MODE 2 xover=150 master=90 sat=90 sub=90
STATE gain=18 amp=play eq=1 ll=1.00 lr=0.00 rl=0.00 rr=1.00
FAULT err0=0x00 err1=0x00 err2=0x00 ot=0x00 oc_r=0 oc_l=0 dc_r=0 dc_l=0 uv=0 ov=0 clk=0 bq=0 crc=0 ot_sd=0 ot_w=0
```

## Ekstensi v2 (ditambahkan di `firmware/src/main.cpp`)

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
