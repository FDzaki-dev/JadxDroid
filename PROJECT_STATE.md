# PROJECT_STATE — JadxDroid

## [RESUME POINT]
[Batch v6: rilis satu perintah (Release menunggu CI sendiri) + OOM APK besar] -> [v1.5.6-android.2 sudah dirilis; v5/v6 belum divalidasi CI/perangkat (OOM APK ±100 MB: dex mmap + mode per-DEX)] -> [Jalankan "UPDATE + RILIS" (satu perintah) untuk 1.5.6-android.3, lalu uji ulang APK besar di perangkat dan kirim hasilnya (layar atau JADXDROID-NOTES.txt)]

Commit message batch: fix: OOM APK besar (dex mmap, per-DEX) dan rilis satu perintah

## Skrip Termux terverifikasi (placeholder terisi: BRANDING_NAME = TERMUX_ROOT = JadxDroid)
Pesan commit diambil dari baris "Commit message batch" di atas dan diperbarui tiap batch. Diuji di sandbox (repo asal tiruan + ZIP asli); DAILY UPDATE terkonfirmasi sukses di Termux pengguna.

Alur rilis = SATU perintah. Workflow Release menunggu Android CI hijau untuk commit yang sama (maks 45 menit), lalu detekt + lint (NewApi), build bertanda tangan, `apksigner verify`, cek versi APK = tag, checksum, mapping, dan publish. Dry-run tidak lagi wajib (jalur rilis terbukti pada v1.5.6-android.2); opsional lewat Actions → Release → Run workflow. Bila Release gagal: jangan pakai ulang tag; perbaiki lewat batch baru dan naikkan N di CHANGELOG + default `releaseVersion` di `app/build.gradle.kts`.

### UPDATE + RILIS (batch yang dirilis; tag dibaca dari bagian paling atas CHANGELOG.md)
```bash
LATEST_ZIP="$(ls -t ~/storage/downloads/JadxDroid*.zip 2>/dev/null | head -n 1)" && [ -f "$LATEST_ZIP" ] || { echo "ERROR: ZIP tidak ditemukan!"; exit 1; } && echo "[1/6] Pakai ZIP: $LATEST_ZIP" && unzip -t "$LATEST_ZIP" >/dev/null || { echo "ERROR: ZIP rusak/tidak valid!"; exit 1; } && PROJ_DIR=~/projects/JadxDroid && mkdir -p "$PROJ_DIR" && cd "$PROJ_DIR" && ( [ -d .git ] || (git init && git branch -M main) ) && (git add -A && (git diff --staged --quiet || git commit -q -m "chore: auto-save local state before sync")) && echo "[2/6] Ekstrak ZIP..." && TMP_EXTRACT="tmp_extract_$(date +%s)" && rm -rf "$TMP_EXTRACT" && mkdir -p "$TMP_EXTRACT" && unzip -oq "$LATEST_ZIP" -d "$TMP_EXTRACT" || { echo "ERROR: Gagal ekstrak ZIP!"; rm -rf "$TMP_EXTRACT"; exit 1; } && [ -f "$TMP_EXTRACT/app/src/main/AndroidManifest.xml" ] || { echo "ERROR: Struktur ZIP tidak valid (Manifest hilang)!"; rm -rf "$TMP_EXTRACT"; exit 1; } && echo "[3/6] Sinkron dengan origin/main..." && git fetch origin main 2>/dev/null && git reset -q --hard FETCH_HEAD 2>/dev/null && OLD_COUNT=$(find . -type f ! -path './.git/*' ! -path "./$TMP_EXTRACT/*" | wc -l | tr -d ' ') && echo "[4/6] OLD_COUNT=$OLD_COUNT" && find . -mindepth 1 -maxdepth 1 ! -name '.*' ! -name "$TMP_EXTRACT" ! -name 'release.keystore' ! -name 'PROJECT_STATE.md' -exec rm -rf {} + && cp -r "$TMP_EXTRACT"/. ./ && rm -rf "$TMP_EXTRACT" && NEW_COUNT=$(find . -type f ! -path '*/.*' | wc -l | tr -d ' ') && echo "[5/6] NEW_COUNT=$NEW_COUNT" && if [ "$OLD_COUNT" -gt 0 ] && { [ "$NEW_COUNT" -lt "$((OLD_COUNT * 70 / 100))" ] || [ ! -f "app/src/main/AndroidManifest.xml" ]; }; then echo "ROLLBACK: Integritas struktur/file kritis drop!"; git reset --hard FETCH_HEAD 2>/dev/null; else echo "[6/6] Commit dan push..." && git branch -M main 2>/dev/null && git add -A && git commit -q -m "fix: OOM APK besar (dex mmap, per-DEX) dan rilis satu perintah" && git push -q origin main && echo "SELESAI: push berhasil" && echo "[7/7] Tag rilis dari CHANGELOG..." && VER="$(grep -m1 -oE '^## [0-9]+\.[0-9]+\.[0-9]+-android\.[0-9]+' CHANGELOG.md | cut -c4-)" && [ -n "$VER" ] && git tag -a "v$VER" -m "JadxDroid $VER" && git push -q origin "v$VER" && echo "SELESAI: v$VER terkirim, rilis berjalan di GitHub Actions (menunggu CI lalu publish)"; fi
```

### DAILY UPDATE (batch tanpa rilis)
```bash
LATEST_ZIP="$(ls -t ~/storage/downloads/JadxDroid*.zip 2>/dev/null | head -n 1)" && [ -f "$LATEST_ZIP" ] || { echo "ERROR: ZIP tidak ditemukan!"; exit 1; } && echo "[1/6] Pakai ZIP: $LATEST_ZIP" && unzip -t "$LATEST_ZIP" >/dev/null || { echo "ERROR: ZIP rusak/tidak valid!"; exit 1; } && PROJ_DIR=~/projects/JadxDroid && mkdir -p "$PROJ_DIR" && cd "$PROJ_DIR" && ( [ -d .git ] || (git init && git branch -M main) ) && (git add -A && (git diff --staged --quiet || git commit -q -m "chore: auto-save local state before sync")) && echo "[2/6] Ekstrak ZIP..." && TMP_EXTRACT="tmp_extract_$(date +%s)" && rm -rf "$TMP_EXTRACT" && mkdir -p "$TMP_EXTRACT" && unzip -oq "$LATEST_ZIP" -d "$TMP_EXTRACT" || { echo "ERROR: Gagal ekstrak ZIP!"; rm -rf "$TMP_EXTRACT"; exit 1; } && [ -f "$TMP_EXTRACT/app/src/main/AndroidManifest.xml" ] || { echo "ERROR: Struktur ZIP tidak valid (Manifest hilang)!"; rm -rf "$TMP_EXTRACT"; exit 1; } && echo "[3/6] Sinkron dengan origin/main..." && git fetch origin main 2>/dev/null && git reset -q --hard FETCH_HEAD 2>/dev/null && OLD_COUNT=$(find . -type f ! -path './.git/*' ! -path "./$TMP_EXTRACT/*" | wc -l | tr -d ' ') && echo "[4/6] OLD_COUNT=$OLD_COUNT" && find . -mindepth 1 -maxdepth 1 ! -name '.*' ! -name "$TMP_EXTRACT" ! -name 'release.keystore' ! -name 'PROJECT_STATE.md' -exec rm -rf {} + && cp -r "$TMP_EXTRACT"/. ./ && rm -rf "$TMP_EXTRACT" && NEW_COUNT=$(find . -type f ! -path '*/.*' | wc -l | tr -d ' ') && echo "[5/6] NEW_COUNT=$NEW_COUNT" && if [ "$OLD_COUNT" -gt 0 ] && { [ "$NEW_COUNT" -lt "$((OLD_COUNT * 70 / 100))" ] || [ ! -f "app/src/main/AndroidManifest.xml" ]; }; then echo "ROLLBACK: Integritas struktur/file kritis drop!"; git reset --hard FETCH_HEAD 2>/dev/null; else echo "[6/6] Commit dan push..." && git branch -M main 2>/dev/null && git add -A && git commit -q -m "fix: OOM APK besar (dex mmap, per-DEX) dan rilis satu perintah" && git push -q origin main && echo "SELESAI: push berhasil"; fi
```

### Temuan pada skrip terkunci (bawa perbaikannya ke master)
1. Box A: `gh repo view ... -jq .url` salah, harus `--jq`; rantai `&&` berhenti sebelum remote/commit/push.
2. DAILY UPDATE dan Box A: `tmp_extract_*` ada di dalam proyek dan tidak berawalan titik, jadi ikut terhapus oleh `find ... -exec rm -rf` sebelum `cp -r "$TMP_EXTRACT"/. ./` (error `cannot stat`, folder proyek kosong). Perbaikan: `! -name "$TMP_EXTRACT"` pada find penghapus dan `! -path "./$TMP_EXTRACT/*"` pada penghitung.
3. DAILY UPDATE: `find ... -exec printf '.' \;` memulai satu proses per file (± 5.000 proses karena ikut menghitung `tmp_extract_*`), terasa hang di Termux; selain itu hitungan ganda memicu ROLLBACK palsu. Perbaikan: `find ... | wc -l`, `unzip -oq`, penanda langkah `[1/6]`..`[6/6]`.

## Batch v6 — 2026-10-26
Penyederhanaan alur (keluhan "step belibet"): sebelumnya DAILY UPDATE → tunggu CI → DRY-RUN → RELEASE (3 perintah + 2 kali menunggu manual). Kini 1 perintah.
- `release.yml`: gerbang "CI hijau" kini MENUNGGU (poll 30 dtk, maks 45 mnt; gagal bila tidak ada run CI setelah 5 mnt atau CI tidak sukses) alih-alih langsung gagal; timeout job 90 mnt. Diuji dengan `gh` tiruan (sukses setelah in_progress, gagal, tidak ada run).
- Skrip "UPDATE + RILIS": DAILY UPDATE + langkah `[7/7]` membaca versi dari heading `## X.Y.Z-android.N` teratas CHANGELOG.md lalu `git tag -a vX.Y.Z-android.N` dan push tag. Diuji end-to-end di sandbox.
- DRY-RUN dikeluarkan dari alur rutin. Isi v5 (OOM APK besar, versi action CI) ikut, jadi v6 menggantikan v5.

## Batch v5 — 2026-10-26
Task 1 (6 file, justifikasi: kontrak memori mesin; bukan fitur UI): 2 override baru + daftar override + `JadxRunner.kt` + `DexSplitter.kt` (baru) + versi default di `app/build.gradle.kts`.
- Sebab OOM (layar perangkat, APK 46.2.3 apkmirror): `DexFileLoader` membaca setiap entri APK sampai habis ke `byte[]` (hanya untuk mengecek magic dex) dan menyimpan semua dex di heap Java; batas heap perangkat 536.870.912 byte. Trace: `ZipDeflate.decompressEntryToBytes` ← `DexFileLoader.collectDexFromZip`.
- `DexFileLoader` (override): entri dex dialirkan ke berkas sementara lalu `FileChannel.map` (memori native, berkas langsung di-unlink); entri non-`.dex` hanya diperiksa 4 byte pertamanya; checksum Adler32 diverifikasi bertahap pada buffer terpetakan; DEX v41 (container) kembali ke jalur lama. `DexReader` (override): konstruktor tambahan menerima `ByteBuffer`.
- `JadxRunner`: bila jumlah DEX > 1 dan total DEX > heap/8 → mode per-DEX langsung; selain itu mode utuh, dan `OutOfMemoryError` (juga yang terbungkus cause) memicu per-DEX otomatis. Per-DEX: pass resources (AndroidManifest.xml, resources.arsc, res/**) lalu satu pass per `classesN.dex`; pass yang gagal dicatat dan dilewati, semua gagal = error mentah. Thread JADX maks 2 saat heap ≤ 640 MB. `JADXDROID-NOTES.txt` ditulis ke ZIP hasil.
- Batas yang diketahui: satu DEX tunggal yang terlalu besar tetap bisa OOM; `DexClassData.getDisassembledCode` (tampilan smali, tidak dipakai jalur simpan) memakai `.array()` dan tidak berlaku untuk buffer terpetakan; mode per-DEX menurunkan kualitas referensi lintas-DEX; assets/lib tidak diekspor pada mode per-DEX.
Task 2 (2 file): `android-ci.yml`, `release.yml`: `actions/checkout@v6`, `setup-java@v5` (v4 deprecated), `gradle/actions/setup-gradle@v5`, `upload-artifact@v6`, `attest-build-provenance@v3`, runner dipin `ubuntu-24.04` (peringatan Node.js 20 dan perpindahan `ubuntu-latest` pada run rilis).
Total override upstream: 15 file. Belum teruji: kompilasi Kotlin/Java di Gradle, perilaku di perangkat.

### Hasil run rilis `v1.5.6-android.2` (commit c04ec0a)
- Workflow Release sukses (58 detik); artefak dry-run: SHA256 cocok, APK ber-`classes.dex` + resource JADX, mapping R8 ada.

## Batch v4 — 2026-10-26
Task 1 (2 file): identitas laporan CI. `scripts/ci/collect-report.sh` + `.github/workflows/android-ci.yml`.
- Nama file dan artefak: `laporan-JadxDroid_v<batch>-<sha7>-run<N>` (batch dibaca dari "Batch vN" pertama di PROJECT_STATE.md, jadi sama dengan nama ZIP). ZIP unduhan dari Actions otomatis ikut bernama itu. Berkas `laporan-*.txt` lama dihapus sebelum menulis.
- Header laporan: ID laporan, proyek + repo, batch + nama ZIP, SHA lengkap + subjek commit, cabang/event, nomor run + percobaan + URL, workflow, waktu UTC.
- Artefak APK CI: `apk-JadxDroid-<sha7>-run<N>`.
Task 2 (1 file): `release.yml` menolak tag bila Android CI untuk commit yang sama belum selesai atau tidak sukses (`gh run list --commit`; izin `actions: read`). Dry-run (`workflow_dispatch`) tidak terkena gerbang ini.
- Diuji lokal: regex versi, `versionCode`, ekstraksi catatan rilis dari CHANGELOG (termasuk batas `.2` vs `.20`), skrip laporan dengan env CI. Belum teruji di runner: `apksigner`/`aapt2`, atestasi, `gh release create`.

### Hasil CI commit b3a3e74 (v2/v3)
- assembleDebug OK, assembleRelease OK, detekt 0 temuan, lint 0 temuan (28 peringatan upstream engine disaring, error engine = 0, jadi NewApi = 0).

## Batch v3 — 2026-10-26
- Hanya dokumentasi atas permintaan eksplisit: skrip terverifikasi + temuan di atas disematkan di file ini. Tidak ada perubahan kode, build, atau CI.

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
