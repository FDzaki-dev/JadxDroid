# PROJECT_STATE — JadxDroid

## [RESUME POINT]
[Batch v2: tutup NewApi engine + jalur rilis GitHub] -> [KODE BELUM DIVALIDASI CI: v1 = assembleDebug/Release sukses, detekt 0 temuan, lint 7 error NewApi (kini dipatch); v2 NOT VERIFIED sampai CI jalan] -> [DAILY UPDATE v2, tunggu Android CI hijau (cek NewApi = 0 di laporan), lalu tag `v1.5.6-android.2` untuk rilis pertama]

Commit message batch: fix: tutup NewApi engine API 33-35 dan tambah jalur rilis GitHub

## Batch v2 — 2026-10-24
Task 1 (8 file): 6 override upstream baru + daftar override di `jadx-engine/build.gradle.kts` + guard API 33 di `MainActivity.kt`. Justifikasi 8-file: perubahan murni kompatibilitas platform (API 33/35 tidak ada di Android 12/13), tanpa perubahan fitur.
| Override baru | Perbaikan |
|---|---|
| `jadx/core/dex/visitors/SaveCode.java` | `PrintWriter(File, Charset)` → `PrintWriter(File, String)`; dipakai menyimpan setiap class |
| `jadx/zip/parser/ZipDeflate.java` | `Inflater.setInput(ByteBuffer)` (API 35) → `byte[]`; dipakai membaca isi APK |
| `jadx/api/ResourcesLoader.java` | `ByteArrayOutputStream.toString(Charset)` → `new String(bytes, charset)` |
| `jadx/core/utils/Utils.java` | `PrintWriter(OutputStream, boolean, Charset)` → writer berlapis setara |
| `jadx/api/ResourceFile.java` | `InputStream.readNBytes` → loop baca |
| `.../finaly/traverser/handlers/MergePathActivePathTraverserHandler.java` | `Collection.toArray(IntFunction)` → `toArray(new T[0])` |
Total override upstream: 13 file. Bump upstream: ganti `third_party/jadx` lalu diff ulang seluruhnya.

Task 2 (5 file): jalur rilis. `.github/workflows/release.yml` (baru), `app/build.gradle.kts` (versi dari `-PreleaseVersion`), `scripts/ci/collect-report.sh` (saring peringatan upstream engine), `AndroidManifest.xml` + `res/xml/data_extraction_rules.xml`.
- Skema versi: `X.Y.Z-android.N`; `versionCode = (X*10000 + Y*100 + Z)*100 + N` (1.5.6-android.2 → 1050602). Tag = sumber kebenaran; default di `app/build.gradle.kts` hanya untuk build lokal.
- Alur: CHANGELOG (descending, bagian wajib ada) → push main → Android CI → tag `vX.Y.Z-android.N` → Release: detekt + lintDebug (gagal bila ada NewApi error), 4 secret wajib (gagal jelas bila kosong), build signed, `apksigner verify`, cek versionName/versionCode APK = tag, SHA256SUMS, mapping R8, atestasi provenance (non-blocking), publish idempotent (re-run aman).
- `workflow_dispatch` = dry run (tanpa publikasi, artefak `release-dry-run`).

### Hasil CI v1 (commit 7b26efb)
- assembleDebug OK, assembleRelease OK, detekt 0 temuan.
- Lint: 7 error NewApi (engine) → dipatch di v2; peringatan DefaultLocale/SimpleDateFormat pada kode upstream tidak dipatch (risiko hanya di locale Turki dll.), InlinedApi/UnusedAttribute/DataExtractionRules → diperbaiki di v2.

## Batch v1 — 2026-10-24
- Struktur: root = proyek Android Gradle (AGP 8.7.3, Gradle 8.11.1, Kotlin 2.0.21, minSdk 31, target/compile 35). Upstream JADX 1.5.6 utuh di `third_party/jadx/` (tidak diubah).
- `:jadx-engine` (library): meng-compile sumber upstream `jadx-core`, `jadx-zip`, `jadx-input-api`, `jadx-dex-input`, `jadx-java-input` lewat task `prepareJadxSources` (Sync ke `build/generated/jadx/java`, file ter-override dikecualikan lalu diganti dari `src/overrides/java`). Resource `jadx-core/src/main/resources` ikut dipaketkan.
- `:app`: Compose UI (edge-to-edge, state via WorkManager + `rememberSaveable`), `DecompileWorker` (WorkManager foreground dataSync, wake lock bertimeout), `JadxRunner` (Dispatchers.Default), `Storage` (SAF → cache via Okio; ekspor ZIP Okio ke `Documents/JadxDroid` via MediaStore), `CrashLogger` (uncaught handler, stacktrace mentah ke `Documents/JadxDroid/logs`).
- CI `.github/workflows/android-ci.yml`: lintDebug + detekt + assembleDebug/Release non-blocking (`continue-on-error`), `scripts/ci/collect-report.sh` → `laporan-untuk-claude.txt` (retensi 7 hari). Laporan/tool absen = `NOT VERIFIED`. ArchUnit/SpotBugs tidak dipasang (validasi hanya lintDebug + detekt sesuai preferensi).

### Override upstream v1 (7 file, justifikasi: API desktop/API 33+ tidak ada di Android, bukan perubahan fitur)
| File (relatif `src/main/java`) | Alasan |
|---|---|
| `jadx/api/gui/tree/ITreeNode.java`, `jadx/api/plugins/gui/ISettingsGroup.java`, `jadx/api/plugins/gui/JadxGuiContext.java` | tipe `javax.swing` → `Object` (antarmuka GUI tidak dipakai di Android) |
| `jadx/core/utils/DotGraphUtils.java` | `java.awt.Color`; dump DOT = no-op |
| `jadx/core/utils/android/Res9patchStreamDecoder.java` | `java.awt`/`javax.imageio`; `decode()` = false → file 9-patch diekspor apa adanya |
| `jadx/core/utils/files/FileUtils.java` | `Files.readString/writeString`, `InputStream.readAllBytes`, `Path.of` (API 33) → padanan API ≤ 26 |
| `jadx/zip/fallback/FallbackZipParser.java` | `InputStream.readAllBytes` (API 33) |

### Temuan runtime Android (sudah ditangani)
- `JadxSecurity` default (`SECURE_XML_PARSER`) memanggil `DocumentBuilderFactory.setFeature` Xerces → ditolak parser Android → gagal membaca `attrs.xml`/manifest. Flag itu dimatikan di `JadxRunner`.
- Plugin dimuat via `AndroidPluginLoader` (daftar eksplisit), bukan `ServiceLoader`.
- `java.io.tmpdir` diarahkan ke cache app di `JadxDroidApp.onCreate` sebelum kelas jadx dimuat (`FileUtils` membuat temp dir di static init).
- `NoOpCodeCache` + output ke disk; `largeHeap=true`; thread jadx maks 4.

### Risiko / belum ditangani
- Thread jadx memakai stack default (≈1 MB di ART): method sangat dalam bisa `StackOverflowError` (jadx menandai class itu gagal, bukan crash). Perbaikan butuh override `jadx/core/utils/Utils.java` (`Utils.java` sudah di-override sejak v2, jadi perubahan ini kini murah; tetap ditunda sampai ada bukti) — tunda sampai ada bukti di laporan CI/log.
- (Tertutup di v2) `ResourceFile.readNBytes` sudah dipatch.
- Tidak termasuk: jadx-gui, jadx-cli, java-convert (R8/dx), smali/raung/aab/xapk/apks/apkm input, kotlin-metadata. Input yang didukung: `.apk`, `.dex`, `.jar`, `.class`.
- Versi plugin/dependensi dipilih konservatif dan belum pernah di-resolve di lingkungan ini.
- Bump upstream: ganti `third_party/jadx`, lalu diff ulang 7 override terhadap file upstream baru.
