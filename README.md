# ADW PRODUCTION VDLMS

**Virtual Digital Loudspeaker Management System** — aplikasi Android native real-time audio DSP berbasis **Kotlin + Jetpack Compose + Android NDK (C++17) + Google Oboe / AAudio**.

## Fitur Utama
- **Real-Time C++ DSP Engine (`adw_vdlms_engine`)**:
  - Jalur pemrosesan penuh: `INPUT L/R → INPUT GAIN → 5-BAND PARAMETRIC EQ → CROSSOVER → DELAY → LIMITER → OUTPUT GAIN → OUTPUT L/R`.
  - **Parametric EQ 5-Band**: Filter biquad double-precision (`PEAK`, `LOW SHELF`, `HIGH SHELF`).
  - **Crossover Independen (HPF / LPF)**: `Butterworth` & `Linkwitz-Riley` dengan slope `OFF`, `12 dB/oct`, `24 dB/oct`, `48 dB/oct`.
  - **Per-Channel Delay & Limiter**: Time-alignment stereo (`0 – 100 ms`), peak limiter + Gain Reduction meter, serta Polarity Invert (`0° / 180°`) Input & Output.
  - **Local Media Player (`PLAYER`)**: Memilih audio langsung dari penyimpanan lokal Android (`MP3`, `WAV`, `FLAC`, `AAC`, `OGG`), didekode real-time dan diproses melalui seluruh rantai DSP.
  - **USB Audio & Android Audio**: Mendukung perangkat audio bawaan Android maupun *USB Audio Interface* class-compliant dengan proteksi fallback aman.
- **Tampilan 1U Rack Hardware**: Panel depan rack 1U dengan LCD display, meter aktual **IN/OUT L & R** (*Peak Hold* & *Clip LED*), virtual rotary encoder, dan preset lokal.

---

## Download APK

File APK terbaru yang siap di-install dapat diunduh melalui:

**GitHub → Releases → versi terbaru → Assets → APK** (contoh: `ADW-VDLMS-v2.0.0.apk`)

*(Untuk build pengembangan terbaru per commit tanpa tag rilis, APK juga tersedia di tab **Actions** pada artifact **ADW-VDLMS-debug-apk**.)*

---

## Instalasi

1. Download APK.
2. Buka APK di Android.
3. Izinkan instalasi dari sumber yang diizinkan Android jika diminta.
4. Install.
5. Jalankan **ADW PRODUCTION VDLMS**.

---

## Pengembangan

### Build Manual dengan Gradle Wrapper
Pastikan **JDK 21**, **Android SDK (API 36.1)**, **Android NDK (`27.3.13750724`)**, dan **CMake (`3.22.1`)** sudah terpasang, lalu jalankan:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

Pada Windows:
```bat
gradlew.bat assembleDebug
```

### Lokasi File APK Hasil Build
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Membuat Release APK Otomatis di GitHub
Push Git tag berawalan `v` (contoh `v2.0.0`):

```bash
git tag v2.0.0
git push origin v2.0.0
```

Workflow GitHub Actions akan otomatis mem-build project, mengunggah artifact debug APK, dan membuat GitHub Release baru beserta asset `ADW-VDLMS-v2.0.0.apk`.

### Pengujian USB Audio Interface
1. Hubungkan *USB Audio Interface* class-compliant ke ponsel Android menggunakan adaptor **USB OTG**.
2. Buka tab **SYSTEM** pada aplikasi dan pastikan indikator **USB** aktif.
3. Anda dapat menguji DSP menggunakan sumber audio langsung (mic/USB input), **Media Player Lokal** di tab **PLAYER**, maupun sinyal kalibrasi (**1kHz SINE / PINK NOISE**) di tab **SYSTEM**.
