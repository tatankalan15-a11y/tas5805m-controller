# Update dari Grok — fitur 2.1 + optimasi

## Fitur baru
- **Crossfeed slider** + preset chip (Stereo / Ringan / Sedang / Kuat / Asimetris / Mono) di tab Output
- **Bass Balance** (sat vs sub) di tab Output
- **Level meter visual** (gradient + warning CLIP/HOT) di tab Monitor
- **Soft Clipper preset**: Off / Ringan / Sedang / Kuat / Protect 4Ω di DAC lanjutan
- **Profile factory**: Movie / Music / Night (EQ + Crossfeed + Level)
- **Debounce** slider (90–100 ms) agar Bluetooth stabil
- **Auto-Save** ke NVS device (1.5 detik setelah perubahan, butuh firmware v3)

## File yang diubah
- `TunerViewModel.kt`
- `ui/OutputScreen.kt`
- `ui/MonitorScreen.kt`
- `ui/MoreScreens.kt`
- `data/Presets.kt`

## Cara build
1. Upload seluruh folder ini ke repo GitHub baru
2. Tab **Actions** → workflow **Android APK** jalan otomatis
3. Unduh artifact APK

Atau build lokal: buka di Android Studio → Run.

Firmware ESP32 **tidak perlu** diubah untuk fitur ini.
