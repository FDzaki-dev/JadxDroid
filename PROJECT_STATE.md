# PROJECT_STATE — JadxDroid

## [RESUME POINT]
[Batch v18: kegagalan beruntun setelah OOM (log decompile-failure_20261028_191851, Infinix X6850, sdk 36) -> tiap langkah dekompilasi di proses baru (:jadx lalu :jadx2), eskalasi utuh -> per-DEX di tingkat proses; versi 1.5.6-android.13] -> [v18 NOT VERIFIED: kompilasi Gradle, detekt, lint belum dijalankan (sandbox tanpa Gradle/SDK/kotlinc), perilaku di perangkat belum diukur; penyebab OOM langkah pertama itu sendiri (heap penuh di mode utuh pada APK 51 MB, 4 dex + assets/audience_network.dex) belum dipecahkan, hanya pemulihannya] -> [Jalankan UPDATE + RILIS (tag dari CHANGELOG = v1.5.6-android.13); uji ulang APK yang sama; baca JADXDROID-NOTES.txt di ZIP hasil; kandidat: ambang SPLIT_HEAP_RATIO (kini 1/8 heap) langsung per-DEX bila total DEX besar, resume dari kelas selesai]

Commit message batch: fix: tiap percobaan dekompilasi di proses baru, eskalasi OOM utuh ke per-DEX (android.13)

## Skrip Termux terverifikasi (placeholder terisi: BRANDING_NAME = TERMUX_ROOT = JadxDroid)
Pesan commit diambil dari baris "Commit message batch" di atas dan diperbarui tiap batch. Diuji di sandbox (repo asal tiruan + ZIP asli); DAILY UPDATE terkonfirmasi sukses di Termux pengguna.

Alur rilis = SATU perintah. Workflow Release menunggu Android CI hijau untuk commit yang sama (maks 45 menit), lalu detekt + lint (NewApi), build bertanda tangan, `apksigner verify`, cek versi APK = tag, checksum, mapping, dan publish. Dry-run tidak lagi wajib (jalur rilis terbukti pada v1.5.6-android.2); opsional lewat Actions → Release → Run workflow. Bila Release gagal: jangan pakai ulang tag; perbaiki lewat batch baru dan naikkan N di CHANGELOG + default `releaseVersion` di `app/build.gradle.kts`.

### UPDATE + RILIS (batch yang dirilis; tag dibaca dari bagian paling atas CHANGELOG.md)
```bash
LATEST_ZIP="$(ls -t ~/storage/downloads/JadxDroid*.zip 2>/dev/null | head -n 1)" && [ -f "$LATEST_ZIP" ] || { echo "ERROR: ZIP tidak ditemukan!"; exit 1; } && echo "[1/6] Pakai ZIP: $LATEST_ZIP" && unzip -t "$LATEST_ZIP" >/dev/null || { echo "ERROR: ZIP rusak/tidak valid!"; exit 1; } && PROJ_DIR=~/projects/JadxDroid && mkdir -p "$PROJ_DIR" && cd "$PROJ_DIR" && ( [ -d .git ] || (git init && git branch -M main) ) && (git add -A && (git diff --staged --quiet || git commit -q -m "chore: auto-save local state before sync")) && echo "[2/6] Ekstrak ZIP..." && TMP_EXTRACT="tmp_extract_$(date +%s)" && rm -rf "$TMP_EXTRACT" && mkdir -p "$TMP_EXTRACT" && unzip -oq "$LATEST_ZIP" -d "$TMP_EXTRACT" || { echo "ERROR: Gagal ekstrak ZIP!"; rm -rf "$TMP_EXTRACT"; exit 1; } && [ -f "$TMP_EXTRACT/app/src/main/AndroidManifest.xml" ] || { echo "ERROR: Struktur ZIP tidak valid (Manifest hilang)!"; rm -rf "$TMP_EXTRACT"; exit 1; } && echo "[3/6] Sinkron dengan origin/main..." && git fetch origin main 2>/dev/null && git reset -q --hard FETCH_HEAD 2>/dev/null && OLD_COUNT=$(find . -type f ! -path './.git/*' ! -path "./$TMP_EXTRACT/*" | wc -l | tr -d ' ') && echo "[4/6] OLD_COUNT=$OLD_COUNT" && find . -mindepth 1 -maxdepth 1 ! -name '.*' ! -name "$TMP_EXTRACT" ! -name 'release.keystore' ! -name 'PROJECT_STATE.md' -exec rm -rf {} + && cp -r "$TMP_EXTRACT"/. ./ && rm -rf "$TMP_EXTRACT" && NEW_COUNT=$(find . -type f ! -path '*/.*' | wc -l | tr -d ' ') && echo "[5/6] NEW_COUNT=$NEW_COUNT" && if [ "$OLD_COUNT" -gt 0 ] && { [ "$NEW_COUNT" -lt "$((OLD_COUNT * 70 / 100))" ] || [ ! -f "app/src/main/AndroidManifest.xml" ]; }; then echo "ROLLBACK: Integritas struktur/file kritis drop!"; git reset --hard FETCH_HEAD 2>/dev/null; else echo "[6/6] Commit dan push..." && git branch -M main 2>/dev/null && git add -A && git commit -q -m "fix: tiap percobaan dekompilasi di proses baru, eskalasi OOM utuh ke per-DEX (android.13)" && git push -q origin main && echo "SELESAI: push berhasil" && echo "[7/7] Tag rilis dari CHANGELOG..." && VER="$(grep -m1 -oE '^## [0-9]+\.[0-9]+\.[0-9]+-android\.[0-9]+' CHANGELOG.md | cut -c4-)" && [ -n "$VER" ] && git tag -a "v$VER" -m "JadxDroid $VER" && git push -q origin "v$VER" && echo "SELESAI: v$VER terkirim, rilis berjalan di GitHub Actions (menunggu CI lalu publish)"; fi
```

### DAILY UPDATE (batch tanpa rilis)
```bash
LATEST_ZIP="$(ls -t ~/storage/downloads/JadxDroid*.zip 2>/dev/null | head -n 1)" && [ -f "$LATEST_ZIP" ] || { echo "ERROR: ZIP tidak ditemukan!"; exit 1; } && echo "[1/6] Pakai ZIP: $LATEST_ZIP" && unzip -t "$LATEST_ZIP" >/dev/null || { echo "ERROR: ZIP rusak/tidak valid!"; exit 1; } && PROJ_DIR=~/projects/JadxDroid && mkdir -p "$PROJ_DIR" && cd "$PROJ_DIR" && ( [ -d .git ] || (git init && git branch -M main) ) && (git add -A && (git diff --staged --quiet || git commit -q -m "chore: auto-save local state before sync")) && echo "[2/6] Ekstrak ZIP..." && TMP_EXTRACT="tmp_extract_$(date +%s)" && rm -rf "$TMP_EXTRACT" && mkdir -p "$TMP_EXTRACT" && unzip -oq "$LATEST_ZIP" -d "$TMP_EXTRACT" || { echo "ERROR: Gagal ekstrak ZIP!"; rm -rf "$TMP_EXTRACT"; exit 1; } && [ -f "$TMP_EXTRACT/app/src/main/AndroidManifest.xml" ] || { echo "ERROR: Struktur ZIP tidak valid (Manifest hilang)!"; rm -rf "$TMP_EXTRACT"; exit 1; } && echo "[3/6] Sinkron dengan origin/main..." && git fetch origin main 2>/dev/null && git reset -q --hard FETCH_HEAD 2>/dev/null && OLD_COUNT=$(find . -type f ! -path './.git/*' ! -path "./$TMP_EXTRACT/*" | wc -l | tr -d ' ') && echo "[4/6] OLD_COUNT=$OLD_COUNT" && find . -mindepth 1 -maxdepth 1 ! -name '.*' ! -name "$TMP_EXTRACT" ! -name 'release.keystore' ! -name 'PROJECT_STATE.md' -exec rm -rf {} + && cp -r "$TMP_EXTRACT"/. ./ && rm -rf "$TMP_EXTRACT" && NEW_COUNT=$(find . -type f ! -path '*/.*' | wc -l | tr -d ' ') && echo "[5/6] NEW_COUNT=$NEW_COUNT" && if [ "$OLD_COUNT" -gt 0 ] && { [ "$NEW_COUNT" -lt "$((OLD_COUNT * 70 / 100))" ] || [ ! -f "app/src/main/AndroidManifest.xml" ]; }; then echo "ROLLBACK: Integritas struktur/file kritis drop!"; git reset --hard FETCH_HEAD 2>/dev/null; else echo "[6/6] Commit dan push..." && git branch -M main 2>/dev/null && git add -A && git commit -q -m "fix: tiap percobaan dekompilasi di proses baru, eskalasi OOM utuh ke per-DEX (android.13)" && git push -q origin main && echo "SELESAI: push berhasil"; fi
```

### Temuan pada skrip terkunci (bawa perbaikannya ke master)
1. Box A: `gh repo view ... -jq .url` salah, harus `--jq`; rantai `&&` berhenti sebelum remote/commit/push.
2. DAILY UPDATE dan Box A: `tmp_extract_*` ada di dalam proyek dan tidak berawalan titik, jadi ikut terhapus oleh `find ... -exec rm -rf` sebelum `cp -r "$TMP_EXTRACT"/. ./` (error `cannot stat`, folder proyek kosong). Perbaikan: `! -name "$TMP_EXTRACT"` pada find penghapus dan `! -path "./$TMP_EXTRACT/*"` pada penghitung.
3. DAILY UPDATE: `find ... -exec printf '.' \;` memulai satu proses per file (± 5.000 proses karena ikut menghitung `tmp_extract_*`), terasa hang di Termux; selain itu hitungan ganda memicu ROLLBACK palsu. Perbaikan: `find ... | wc -l`, `unzip -oq`, penanda langkah `[1/6]`..`[6/6]`.

## Batch v18 — 2026-10-28
Masukan pengguna: berkas log kegagalan `decompile-failure_20261028_191851.txt` (Infinix X6850, sdk 36), tanpa teks tambahan.
Pembenaran lebih dari 5 file: kontrak antarproses (protokol Messenger + dua proses) bukan fitur UI: `JadxRunner.kt`, `JadxService.kt` (+ `JadxFallbackService`), `RemoteDecompiler.kt`, `AndroidManifest.xml`, versi `app/build.gradle.kts`.
Pembacaan log (fakta dari berkas):
- Penyebab teratas: `IllegalStateException: Semua pass per-DEX gagal` dengan lima pass (resources, assets/audience_network.dex, classes.dex sampai classes4.dex) semuanya `OutOfMemoryError` pada alokasi kecil (16 sampai 48 byte, `<1% of heap free after GC`, batas 536870912).
- Suppressed 1: `OutOfMemoryError` di `InitCodeVariables.initCodeVar` pada thread worker saat menyimpan sumber (kegagalan mode utuh yang asli, kini tercatat berkat v15).
- Suppressed 2: `OutOfMemoryError` saat `JadxZipParser.load` mengalokasikan 51.610.168 byte dengan hanya 4.435.232 byte bebas (percobaan kedua, memuat APK 51 MB): heap sudah penuh SEBELUM percobaan kedua mulai, dan tetap penuh sampai semua pass per-DEX. Jadi sisa percobaan pertama menahan heap di dalam proses yang sama (zombie thread atau referensi yang tak terlepas; penyebab pastinya tidak terlihat dari log).
Perubahan:
- Tiap langkah berjalan di proses sendiri: `RemoteDecompiler.attempt` menjalankan langkah 0 (`Strategy.WHOLE`, `JadxService` di `:jadx`) lalu, bila `RemoteFailure.outOfMemory` atau `died`, membersihkan output dan menjalankan langkah 1 (`Strategy.PER_DEX`, `JadxFallbackService` di `:jadx2`) dengan kegagalan sebelumnya (raw, maks 20.000 karakter masing-masing) dikirim lewat `KEY_EARLIER`. Dua nama proses supaya langkah berikutnya tidak pernah memakai proses yang baru berhenti (bind ulang cepat ke proses yang sedang mati). Langkah terakhir yang gagal dilempar apa adanya.
- `JadxRunner`: `Strategy` (CHAIN untuk jalur proses utama, WHOLE, PER_DEX) dan `Options.earlier`; WHOLE = satu run tanpa rantai internal; PER_DEX mulai hemat memori dan menulis kegagalan sebelumnya ke NOTES serta suppressed pada error akhir; praperiksaan heap (`SPLIT_HEAP_RATIO`) tetap berlaku. `isOutOfMemory` dan `clearOutput` menjadi fungsi level-file internal.
- `JadxService`: `open class`, membaca strategi dan kegagalan sebelumnya, mengirim `KEY_OOM`; `JadxFallbackService` subkelas kosong untuk `:jadx2` (manifest).
Tidak diubah: jalur cadangan di proses utama (rantai utuh -> utuh hemat -> per-DEX), pelepasan kelas, jumlah thread, UI.
Belum terjawab: mengapa mode utuh kehabisan heap pada APK ini (51 MB, 4 dex + dex di assets) padahal ada pelepasan kelas; baseline graf kelas semua DEX di heap 512 MB kemungkinan sudah besar. Kandidat berikutnya: ambang `SPLIT_HEAP_RATIO` lebih kecil agar total DEX besar langsung per-DEX tanpa membuang waktu pada mode utuh yang hampir pasti gagal; resume dari kelas selesai.
Risiko: dua proses berurutan melipatgandakan waktu bila mode utuh gagal di ujung; bind ke `:jadx2` bisa gagal di perangkat tertentu (lalu `null` dan jalur cadangan proses utama); `Strategy.entries` memerlukan Kotlin 1.9+ (proyek 2.0.21).
Pemeriksaan lokal: kurung seimbang, baris <= 120, tidak ada sisa referensi ke fungsi yang dipindah. Kompilasi Kotlin/Gradle, detekt, lint, dan uji perangkat: NOT VERIFIED sampai CI.

## Batch v17 — 2026-10-28
Permintaan pengguna: dekompilasi masih sangat lambat; minimal beri mekanisme persisten saat ditinggal kerja di background.
Pembenaran lebih dari 5 file: kontrak memori mesin + perilaku background lintas lapisan (engine, proses :jadx, worker, UI, manifest), bukan fitur baru: `MemoryGuard.java`, override `Utils.java`, `JadxService.kt`, `RemoteDecompiler.kt`, `DecompileWorker.kt`, `MainScreen.kt`, `AndroidManifest.xml`, versi `app/build.gradle.kts`.
Diagnosis (dari kode, belum diukur di perangkat):
- Perlambatan buatan batch sebelumnya: v15/v16 melepas kelas dependensi saat `totalMemory - freeMemory` > 60-70%. Angka itu ikut menghitung sampah yang belum dikumpulkan GC, jadi pada heap 512 MB dengan 3 thread kondisi itu hampir selalu benar dan dependensi dimuat ulang dan diproses ulang (inferensi tipe, region) berulang kali.
- Prioritas: worker berjalan di nice 9 (v14) dan v16 juga menurunkan semua thread proses :jadx ke nice 9 (termasuk GC). Begitu pengguna pindah ke aplikasi lain, worker kalah rebutan CPU dengan semua proses lain.
- Background: wake lock hanya 2 jam; tidak ada permintaan pengecualian baterai; proses :jadx yang mati membuat run langsung gagal.
Perubahan:
- `MemoryGuard.underPressure`: bila heap terpakai > 80% (ekonomi 50%) atau sedang tertekan, paling sering tiap 3 dtk: `System.gc()` lalu ukur; `livePressure` = heap hidup > 55% (ekonomi 40%). Pelepasan kelas dan gerbang paralelisme (`awaitHeadroom`, kini hanya bila `livePressure` dan terpakai > 90%) hanya aktif saat `livePressure`. Heap lapang = tidak ada pelepasan sama sekali (kecepatan setara v14).
- `MemoryGuard.setWorkerPriority/workerPriority` dibaca `Utils.lowerPriority` (override); default 9 (proses utama, demi UI); `JadxService.onCreate` menetapkan `THREAD_PRIORITY_FOREGROUND`; `lowerThreadPriorities` (thread GC) dihapus.
- `MainScreen`: sebelum file picker, sekali per peluncuran (rememberSaveable) membuka `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` bila belum dikecualikan; hasil apa pun dilanjutkan ke picker; gagal membuka = langsung picker. Manifest: izin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`tools:ignore=BatteryLife`), `@SuppressLint("BatteryLife")` pada pembuat intent.
- `DecompileWorker`: wake lock 6 jam; `RemoteFailure.died` (proses :jadx hilang) dan `runAttemptCount < 2` = `Result.retry()` (WorkManager mengulang dari awal; input diambil ulang dari URI yang izinnya persisten).
Tidak ada resume dari kelas selesai: butuh penulisan atomik per kelas, penanda input, dan pembedaan batal-oleh-pengguna vs dihentikan-sistem di `Storage.cleanup`; dicatat sebagai kandidat berikutnya, bukan dikerjakan tanpa dasar uji.
Risiko yang diketahui: `System.gc()` memblokir sebentar (maks sekali per 3 dtk dan hanya saat heap > 80%); pengulangan dari awal setelah proses mati tetap lambat; dialog baterai dapat tampil sekali per peluncuran bila pengguna belum mengizinkan; OEM tertentu punya pembatas daya sendiri di luar dialog standar.
Pemeriksaan lokal: `MemoryGuard.java` dikompilasi javac terhadap stub signature asli tanpa peringatan; Kotlin: baris <= 120, kurung seimbang, tanpa sisa referensi yang dihapus. Kompilasi Kotlin/Gradle, detekt, lint, dan uji perangkat: NOT VERIFIED sampai CI.

## Batch v16 — 2026-10-28
Permintaan pengguna: aplikasi masih kurang akselerasi dan stutter saat dekompilasi (layar: GIF Maker_2.0.4.apk, 5186/17278); workflow Release paling lama sendiri (layar run: Release #11 8m49s, Android CI #15 4m32s, Android APK #5 3m29s, commit cd12649, v1.5.6-android.10 sukses).
Pembenaran lebih dari 5 file: perubahan arsitektur proses (dekompiler di proses sendiri) + workflow, bukan fitur UI: kode `JadxService.kt` dan `RemoteDecompiler.kt` (baru), `DecompileWorker.kt`, `AndroidManifest.xml`, `JadxRunner.kt` (jumlah thread), `MemoryGuard.java` (gerbang), `logback.xml`, `release.yml`, versi `app/build.gradle.kts`.
Diagnosis (belum diukur di perangkat):
- Stutter: v14 hanya menurunkan prioritas thread worker; GC ART dan beban alokasi JADX tetap satu proses dengan UI. Dengan live set besar (heap 512 MB), GC konkuren memakai banyak core pada prioritas normal dan alokasi thread UI bisa menunggu GC; prioritas worker tidak menyentuh itu. Solusi struktural: proses terpisah.
- Akselerasi: jumlah thread dikunci 2 karena heap; dengan `MemoryGuard` memori kini terkendali, jadi batas bisa dinaikkan dengan pengaman adaptif.
- Release lama: langkah `Wait for green Android CI` menunggu ±4,5 mnt (CI hampir selalu 'success' karena langkahnya continue-on-error, jadi bukan gerbang nyata), lalu detekt, lintDebug, dan assembleRelease dijalankan sebagai tiga run Gradle terpisah, padahal CI sudah menjalankan lint dan detekt untuk commit yang sama.
Perubahan:
- `JadxService` (proses `:jadx`, `android:exported=false`): Messenger; MSG_START (path input/output + skipResources), MSG_PROGRESS, MSG_DONE (errors, warnings), MSG_FAILED (raw stack trace). Menurunkan prioritas semua thread di `/proc/self/task` ke nice 9 (termasuk thread GC). `onDestroy` mematikan prosesnya sendiri: proses hidup untuk satu run, unbind = berhenti (juga jalur pembatalan; heap kembali utuh; tidak ada worker zombie).
- `RemoteDecompiler`: `bindService(BIND_AUTO_CREATE | BIND_IMPORTANT)`, tunggu koneksi 10 dtk; null bila gagal bind/timeout sehingga `DecompileWorker` memakai jalur lama di proses utama; proses mati di tengah jalan = `RemoteFailure` ('berhenti mendadak'); kegagalan di `:jadx` dikirim sebagai `RemoteFailure(trace)` dan ditampilkan mentah. Input/output memakai path yang sama (cache/files aplikasi, uid sama).
- `DecompileWorker`: remote dulu, fallback `JadxRunner` lokal; teks kegagalan memakai trace remote apa adanya. Salin input, ekspor ZIP, notifikasi foreground, WorkManager tetap di proses utama.
- `JadxRunner.threadCount`: maks 3 bila heap <= 640 MB (sebelumnya 2), maks 4 selain itu. `MemoryGuard`: `awaitHeadroom()` di awal `pin` (generate baru menunggu maks 3 dtk, per 10 ms, bila heap > 85% dan ada generate lain aktif; generate tunggal tidak pernah menunggu); ambang pelepasan kelas 60% -> 70%. `logback.xml`: root INFO -> ERROR.
- `release.yml`: langkah tunggu CI dihapus (+ izin `actions: read`), keystore didekode lebih awal, satu run `./gradlew detekt lintDebug assembleRelease --continue -PreleaseVersion=...` (id `build`), lalu langkah `gate` memeriksa NewApi dari hasil lint; urutan `STEP_RESULTS` disesuaikan. Gerbang tetap: kegagalan detekt/kompilasi/NewApi menghentikan rilis sebelum publish. Perkiraan: Release ≈ setengah dari 8m49s.
Tidak diubah dengan sengaja: mode dekompilasi dan kualitas keluaran (debug info, finally, inline), `Storage`, UI, WorkManager sebagai satu-satunya sumber state.
Risiko yang diketahui: WorkManager 2.10 ikut terinisialisasi di `:jadx` lewat androidx.startup (aman di versi multi-proses, tetapi belum diuji di perangkat); proses `:jadx` yang dimatikan sistem membuat run gagal (tidak ada pemulihan otomatis); bind ulang terlalu cepat setelah proses sebelumnya mati dapat mendapat 'berhenti mendadak' (pengguna perlu memulai lagi); thread 3 dapat menaikkan peluang OOM pada APK sangat besar (rantai fallback v15 tetap melindungi).
Pemeriksaan lokal: `MemoryGuard.java` dikompilasi javac terhadap stub signature asli tanpa peringatan; YAML `release.yml` valid dan urutan langkah diperiksa; Kotlin baru: baris <= 120, kurung seimbang, fungsi per kelas jauh di bawah 11, `ThrowsCount` <= 2 per fungsi, `ReturnCount` <= 3, tanpa `/*` di komentar. Kompilasi Kotlin/Gradle, detekt, lint, dan uji perangkat: NOT VERIFIED sampai CI.
Kandidat batch berikutnya (butuh pengukuran): ambang gerbang 85% dan pelepasan 70% disetel dari data, resume dari kelas yang sudah tersimpan, memparalelkan pemindaian usage di `load()`, baseline profile.

## Batch v15 — 2026-10-28
Permintaan pengguna: perbaikan v14 kurang signifikan; menjelang penghujung dekompilasi gejala yang sama kambuh (layar: APK 1.1.1.1_6.38.9.apk, semua pass per-DEX gagal `OutOfMemoryError`, heap 536870912 byte, <1% bebas setelah GC, bahkan pass resources yang kecil).
Pembenaran lebih dari 5 file: kontrak memori mesin (override engine + pelepasan kelas), bukan fitur UI: 6 file kode + versi (`MemoryGuard.java` baru, override `ProcessClass.java` baru, `SaveCode.java`, `jadx-engine/build.gradle.kts`, `JadxRunner.kt`, `app/build.gradle.kts`).
Diagnosis dari source JADX (third_party, belum diukur di perangkat):
- `DecompilerScheduler` menaruh kelas dengan dependensi terbanyak paling akhir (batch = semua dependensinya + kelas itu). `ProcessClass.generateCode` memuat dan memproses SEMUA dependensi sebelum codegen; dependensi yang sudah di-generate lalu dimuat ulang tidak pernah di-unload lagi. Set kelas termuat hanya bertambah, jadi heap 512 MB penuh di ujung proses.
- `TaskExecutor.wrapTask` menangkap `OutOfMemoryError` dan hanya `shutdownNow` pada executor luar; thread pool paralel (`task-p`) tetap menyelesaikan batch yang berjalan dan `awaitTermination()` tidak menunggunya. `JadxRunner` lalu langsung memulai mode per-DEX sementara thread lama masih memegang kelas: semua pass gagal seketika (cocok dengan layar). Kegagalan mode utuh yang asli juga hilang (tidak dicatat).
Perubahan:
- `jadx/android/MemoryGuard.java` (baru): `pin/Pins` (kelas + dependensinya ditandai dipakai selama satu generate), `classSaved` (titik aman di `SaveCode.save`, tanpa kunci kelas): bila heap terpakai > 60% (economy 30%) kelas yang sudah di-generate, berstatus PROCESS_COMPLETE (dimuat ulang sebagai dependensi), dan tidak di-pin di-unload di bawah kunci `ClassInfo` lalu diberi status GENERATED_AND_UNLOADED (jalur yang sama dengan upstream setelah generate; pemuat berikutnya memuat ulang lewat `process`). Pin dan status diperiksa ulang di dalam kunci. `interruptWorkers` mencari thread `jadx-task-*` dan menginterupsinya.
- Override `ProcessClass.generateCode`: hanya `pin` di awal dan `release` di `finally` (diff 5 baris dari upstream). Override `SaveCode.save`: `try/finally` memanggil `classSaved`.
- `JadxRunner.kt`: `awaitTasks` + `haltWorkers` (terminate, interupsi, tunggu maks 30 dtk, tanpa bisa dibatalkan) pada kegagalan APA PUN; `MemoryGuard.begin/end` per run; urutan OOM: utuh -> utuh hemat memori (1 thread) -> per-DEX (mulai hemat; pass OOM diulang 1 thread); semua kegagalan awal dicatat mentah di `JADXDROID-NOTES.txt` dan sebagai suppressed pada error akhir (CrashLogger).
Tidak diubah dengan sengaja: jumlah thread normal, mode dekompilasi, UI, `DecompileWorker`, `Storage`.
Batas yang diketahui: puncak satu kelas dengan ratusan dependensi tetap harus muat di heap (dipin); satu DEX raksasa tetap bisa OOM; mode hemat mengulang dari nol dan lebih lambat; kelas yang dilepas diproses ulang bila dipakai lagi (CPU lebih tinggi hanya saat heap tegang); versi utuh yang berhasil lewat mode hemat tetap berkualitas penuh (referensi lintas-DEX utuh), per-DEX tidak.
Pemeriksaan lokal: `MemoryGuard.java` dikompilasi dengan javac terhadap stub berisi signature asli (AFlag, ProcessState asli; ClassNode stub sesuai upstream) tanpa peringatan; `ProcessClass.java` dan `SaveCode.java` tanpa error sintaks (hanya impor library yang tidak ada di sandbox); `JadxRunner.kt`: baris <= 120, kurung seimbang, 8 fungsi di kelas (batas 11), `decompilePerDex` 73 baris kode (batas 80), kedalaman blok `runJadx` dikurangi (mengikuti pelajaran v9), tiap fungsi <= 2 `throw`, tanpa `/*` di dalam komentar. Kompilasi Kotlin/Gradle, detekt, lint, dan uji perangkat: NOT VERIFIED sampai CI.
Kandidat batch berikutnya (butuh bukti dari perangkat): jumlah thread adaptif per ukuran batch, ambang 60% disetel dari log (`kelas dilepas` di NOTES), resume dari kelas yang sudah tersimpan alih-alih mengulang dari nol.

## Batch v14 — 2026-10-28
Permintaan pengguna: terapkan akselerasi pada proses dekompilasi dan atasi aplikasi yang seketika laggy saat memproses. Dasar: CI v13 (laporan-JadxDroid_v13-beb5013-run13) hijau: assembleDebug OK, assembleRelease OK, lint 0, detekt 0; jadi basis v13 aman.
Diagnosis dari source (belum diukur di perangkat; tidak ada log lag dari pengguna):
- Thread worker JADX (`task-s`, `task-p`, dibuat `Utils.simpleThreadFactory`) berprioritas normal dan menjenuhkan core, berebut dengan thread UI dan RenderThread.
- `setProgress` dipanggil tiap 500 ms saat dekompilasi dan tiap 25 berkas saat ekspor; tiap panggilan = tulis database WorkManager + query ulang `WorkInfo` + recompose, dan pada ekspor dikerjakan berurutan di dalam loop ZIP (memperlambat ekspor sendiri).
- `JadxArgs` default memakai `AnnotatedCodeWriter` (peta anotasi dan baris per class) dan `InMemoryUsageInfoCache` (graf usage seluruh aplikasi tetap di heap): banyak objek, GC sering, dan GC yang memblokir alokasi di thread utama terasa sebagai patah-patah.
- Ekspor ZIP memakai level deflate default dan tulis 8 KiB per panggilan ke penyimpanan FUSE (MediaStore).
Task 1 (4 file kode + versi): `jadx-engine/src/overrides/java/jadx/core/utils/Utils.java` (pabrik thread: `Process.setThreadPriority` nice 9 = BACKGROUND + MORE_FAVORABLE; sengaja di bawah 10 karena >= 10 memindahkan thread ke cgroup background yang dibatasi ke core kecil; kegagalan memanggilnya diabaikan), `engine/JadxRunner.kt` (`SimpleCodeWriter`, `EmptyUsageInfoCache`: pengaturan yang sama dengan `JadxCLI`; diperiksa di source bahwa `restoreUsageData` hanya terjadi lewat `JavaClass.reload/unload` yang khusus GUI, bukan jalur simpan), `work/DecompileWorker.kt` (`report`: kirim hanya bila fase berubah, atau persen berubah dan >= 1 detik sejak tulis terakhir), `data/Storage.kt` (`Deflater.BEST_SPEED`, `BufferedInput/OutputStream` 256 KiB di bawah Okio untuk salin input dan tulis ZIP; tetap stream Okio `use {}`). Default releaseVersion → 1.5.6-android.9; CHANGELOG android.9; README bagian Performa.
Tidak diubah dengan sengaja: jumlah thread JADX (batas heap yang menentukan; mengubahnya berisiko OOM), `setDebugInfo`/`setExtractFinally`/mode dekompilasi (mengurangi kualitas hasil), UI Compose (tidak ada bukti masalah di sana).
Pemeriksaan lokal: kotlinc 2.0.21 pada 5 berkas Kotlin terkait tanpa error sintaks (hanya unresolved reference karena dependensi tidak ada di sandbox), javac parse `Utils.java` 0 error, baris <= 120 kolom, kurung seimbang, tidak ada `/*` baru di komentar. Kompilasi penuh, detekt, lint, dan uji perangkat: NOT VERIFIED sampai CI.
Efek samping yang diketahui: ZIP hasil sedikit lebih besar (level 1 vs 6). Dekompilasi tetap satu thread pada tahap `load()` (pemindaian usage berurutan di JADX); itu bukan sumber lag UI tetapi membatasi kecepatan awal.
Kandidat batch berikutnya (butuh bukti dari log/pengukuran, ditunda): paralelkan pemindaian usage di `load()` (override `UsageInfoVisitor`/`UseSet` thread-safe), baseline profile ART untuk jalur JADX dan Compose, jumlah thread adaptif terhadap heap bebas, hapus direktori hasil secara asinkron agar status selesai tidak menunggu `deleteRecursively`.

## Batch v13 — 2026-10-27
Sebab (artefak laporan-JadxDroid_v1.5.6-android.7-0a50fb5-run8): langkah `gate` gagal di `:app:detekt` (26 dtk), 1 temuan: `UpdateScreen.kt:32:7 MatchingDeclarationName` (berkas hanya punya satu kelas top-level, `UpdateActions`, jadi namanya harus sama dengan berkas). Langkah keystore/build/verify/publish dilewati, jadi tidak ada GitHub Release; tag `v1.5.6-android.7` dibiarkan tanpa Release dan tidak dipakai ulang.
Task 1 (2 file kode + versi): `ui/UpdateActions.kt` (baru, hanya kelas `UpdateActions`), `ui/UpdateScreen.kt` (kelas dan import `Immutable` dihapus; paket sama, jadi `MainScreen`/`MainActivity` tidak berubah). Default releaseVersion → 1.5.6-android.8; CHANGELOG android.8 (android.7 ditandai tidak dirilis, butir fitur dipindah ke android.8).
Pemeriksaan lokal: semua berkas Kotlin baru dicek terhadap MatchingDeclarationName (hanya `UpdateRepository.kt` dan `UpdateWorker.kt` punya satu kelas top-level, dan namanya cocok); baris <= 120 kolom, kurung seimbang. Gerbang berikutnya menjalankan lintDebug yang mengompilasi kode tab Update untuk pertama kalinya: NOT VERIFIED sampai CI.

## Batch v12 — 2026-10-27
Permintaan pengguna: tab Update di aplikasi dengan unduhan Okio stream yang otomatis menampilkan snapshot saat menunggu loading; rapi dan tidak serabutan. Snapshot ditafsirkan sebagai kartu info rilis (versi terpasang, versi terbaru, tanggal, ukuran, catatan rilis) yang tampil otomatis begitu tab dibuka.
Pembenaran lebih dari 5 file: domain baru (pembaruan in-app) dipecah dua tugas, masing-masing 5 file, lapisan data lalu lapisan UI.
Task 1 (data, 5 file): `update/ReleaseInfo.kt` (model + parser JSON rilis, versionCode sama dengan rumus gradle), `update/UpdateRepository.kt` (GitHub API lewat HttpURLConnection; unduh stream Okio berbuffer `use {}` dengan HashingSink SHA-256 saat menulis, dicocokkan dengan SHA256SUMS.txt; hanya host github.com dan githubusercontent.com lewat HTTPS), `update/UpdateWorker.kt` (WorkManager expedited, progres, verifikasi paket, hasil mentah ke CrashLogger), `update/UpdateInstaller.kt` (sesi PackageInstaller, APK ditulis lewat Okio; `InstallResultReceiver` tidak diekspor), `AndroidManifest.xml` (INTERNET, REQUEST_INSTALL_PACKAGES, receiver).
Task 2 (UI, 5 file): `update/UpdateViewModel.kt` (state: cek rilis + WorkInfo + status pemasangan jadi satu `UpdateUiState`; pemeriksaan otomatis sekali saat tab dibuka), `ui/UpdateScreen.kt` (kartu snapshot selalu tampil, indikator memuat, progres unduhan, kegagalan mentah yang bisa disalin), `ui/MainScreen.kt` (host tab `TabRow`; tab dan toggle disimpan `rememberSaveable`; isi tab Dekompilasi dipindah tanpa perubahan perilaku), `MainActivity.kt` (menyambungkan dua ViewModel), `res/values/strings.xml`.
Versi: default releaseVersion dan CHANGELOG ke 1.5.6-android.7 (catatan CHANGELOG ikut tampil di snapshot rilis berikutnya).
Ketergantungan: nama aset rilis `JadxDroid-<versi>.apk` dan `SHA256SUMS.txt` (dibuat release.yml); API GitHub tanpa token (batas 60 permintaan/jam per IP, cukup untuk pemeriksaan sekali per pembukaan tab). Pembaruan menimpa hanya APK dengan tanda tangan sama; build debug dari artifact CI ditolak sistem dan pesan mentahnya tampil.
Pemeriksaan lokal: baris Kotlin <= 120 kolom, kurung seimbang, tidak ada import tidak terpakai, tidak ada pola terlarang; fungsi per file/objek di bawah batas TooManyFunctions; semua R.string terdefinisi; API Android yang dipakai tidak melebihi API 31. Kompilasi Kotlin, detekt, lint, dan uji perangkat tidak bisa dijalankan di sandbox: NOT VERIFIED sampai CI.

## Batch v11 — 2026-10-27
Koreksi pengguna: jalur artifact aplikasi harus terpisah dari workflow laporan agar APK tidak tertahan (update real-time). Sebab: hanya `android-ci.yml` aktif pada push, dan APK baru diunggah di akhir job yang sama setelah lint, detekt, build, dan laporan (run #10: 1m14s serial).
Task 1 (2 file CI; tanpa kode aplikasi, tanpa naik versi, tanpa tag):
- `android-apk.yml` (baru, workflow "Android APK"): checkout, JDK 17, Gradle, keystore dari secret, `assembleDebug assembleRelease`, unggah `apk-JadxDroid-<sha7>-run<N>` (7 hari) hanya bila build sukses (`if-no-files-found: error`). Tanpa lint, detekt, atau laporan.
- `android-ci.yml` ("Android CI"): langkah Upload APK dihapus; sisanya tetap (lint, detekt, build debug/release continue-on-error, laporan, artifact `laporan-…`).
- Keduanya dipicu push ke `main` + manual dan berjalan paralel di runner terpisah.
Catatan: nomor run dihitung per workflow, jadi `run<N>` pada nama APK dan laporan bisa berbeda; pasangan dikenali dari sha7. Build yang gagal di Android APK tidak menghasilkan artifact; penyebabnya dilaporkan Android CI (lintDebug mengompilasi Kotlin; assembleDebug/Release tercatat di laporan). `release.yml` tidak diubah dan masih menunggu `android-ci.yml` hijau sebelum build rilis (kesimpulan itu hampir selalu success karena langkah continue-on-error, jadi hanya menambah tunggu; keputusan menghapusnya menunggu pengguna). Belum teruji di runner: workflow baru.

## Batch v10 — 2026-10-27
Koreksi pengguna: GitHub Release berbeda dari GitHub Artifact. Artifact hanya boleh 2 jalur: unduhan APK dan unduhan log kegagalan/laporan untuk Claude (bila ada temuan bug/celah baru). Sebelumnya ada 4 keluarga nama: `apk-`, `laporan-`, `gagal-` (CI dan Release), `release-dry-run-`.
Task 1 (4 file CI/skrip; tanpa kode aplikasi dan tanpa naik versi, jadi tanpa tag):
- `android-ci.yml`: tiga langkah bundle/ringkasan `gagal-` dihapus; satu artifact `laporan-…` dari `laporan-bundle/`; APK tetap `apk-…`.
- `collect-report.sh`: menyiapkan `laporan-bundle/` (txt selalu; `log/`, `laporan-tool/`, dan ringkasan halaman run hanya bila ada langkah gagal).
- `release.yml`: bundle kegagalan kini `laporan-…` (bukan `gagal-…`), retensi 7 hari (sebelumnya 14; mengikuti aturan 7 hari); dry-run menjadi `apk-JadxDroid-<versi>-dryrun-run<N>`. Rilis sukses tidak membuat artifact; APK, SHA256SUMS, mapping hanya di GitHub Release.
- `collect-failure.sh`: prefix nama `gagal-` → `laporan-` (hanya dipakai run Release).
Diuji lokal (data asli run6): CI sukses → bundle hanya txt; detekt gagal → txt + `log/` + `laporan-tool/` + ringkasan; Release gagal → `laporan-JadxDroid_v<versi>-<sha7>-run<N>`. YAML kedua workflow valid. Belum teruji di runner: unggah artifact multi-berkas. Nama artifact `gagal-…` pada batch v4–v9 di bawah adalah riwayat.

## Batch v9 — 2026-10-27
Sebab (artefak gagal-JadxDroid_v1.5.6-android.5-5e15a66-run6): langkah `gate` gagal di `:app:detekt` (18 dtk), 2 temuan, keduanya di `DexSplitter.kt`: `:52:9 NestedBlockDepth` (`extractResourcesArchive`, kedalaman 4/4) dan `:51:1 MaxLineLength` (KDoc 124 kolom, batas 120). Sebelumnya tertutup error sintaks android.4 (file tidak teranalisis penuh). Langkah keystore/build/verify/publish dilewati, jadi tidak ada GitHub Release; tag `v1.5.6-android.5` dibiarkan tanpa Release dan tidak dipakai ulang.
Task 1 (1 file kode + versi): `DexSplitter.kt` — isi loop dipindah ke `copyResourceEntries` (private; kedalaman blok 4 → 2), `extractResourcesArchive` jadi expression body, KDoc dipecah ≤ 120 kolom; perilaku identik (urutan entri, level kompresi, hitungan). Default `releaseVersion` → 1.5.6-android.6; CHANGELOG android.6 (android.5 ditandai tidak dirilis, butir fitur dipindah ke android.6).
Pemeriksaan lokal: tidak ada baris Kotlin > 120 kolom; tidak ada komentar bersarang (`/*` tersisa hanya di literal string); API JADX yang dipakai `JadxRunner.kt` (ITaskExecutor, JadxArgs, JadxSecurity, NoOpCodeCache) cocok dengan sumber upstream. Detekt/kompilasi Kotlin tidak bisa dijalankan di sandbox (tanpa jaringan/kotlinc): NOT VERIFIED sampai CI.

## Batch v8 — 2026-10-27
Sebab (artefak gagal-JadxDroid_v1.5.6-android.4-e6be43b-run5): gerbang detekt LOLOS (log 253 byte); lintDebug gagal karena :app:compileDebugKotlin: DexSplitter.kt:49:6 Missing kurung kurawal, :73:1 Unclosed comment, JadxRunner.kt:118 Unresolved reference extractResourcesArchive (turunan). Akar: KDoc di baris 51 berisi `res/**`; Kotlin mendukung komentar bersarang, jadi `/**` di dalam komentar membuka komentar baru yang tak pernah tertutup. Java engine (override DexFileLoader/DexReader) lolos kompilasi terhadap android.jar.
Task 1 (1 file kode + versi): DexSplitter.kt komentar diganti tanpa `/*`; pemindai lokal membuktikan tidak ada komentar bersarang lain di seluruh Kotlin; default releaseVersion → 1.5.6-android.5; CHANGELOG android.5, android.4 ditandai tidak dirilis (tag tidak dipakai ulang).
Catatan: error sintaks menutupi tahap semantik; ulasan manual DexSplitter/JadxRunner tidak menemukan masalah tipe, tetapi belum pernah dikompilasi sampai tuntas. Kesimpulan CI "success" tidak menjamin build hijau (langkah build continue-on-error); yang menentukan adalah gerbang Release dan laporan.
Jalur artefak kegagalan: terbukti (nama gagal-JadxDroid_<tag>-<sha7>-run<N>, langkah gagal ditandai, penyebab mentah terekstrak). Unduhan lewat UI GitHub mengganti titik pada nama zip dengan underscore; berkas di dalamnya tetap bernama asli.

## Batch v7 — 2026-10-26
Sebab kegagalan (log run Release `v1.5.6-android.3`): Resolve version OK, Wait CI OK (sukses setelah ±1 menit), lalu `Quality gate` gagal pada `:app:detekt` — `JadxRunner.kt:112` `ExplicitGarbageCollectionCall`, `JadxRunner.kt:141` `MaxLineLength` (kode batch v5). Tag ada dan Release ter-trigger, tetapi tidak ada GitHub Release karena publish baru terjadi setelah gerbang. Tag `v1.5.6-android.3` dibiarkan tanpa Release; perubahan dirilis sebagai android.4 (tag tidak dipakai ulang).
Task 1 (1 file kode + versi): `JadxRunner.kt` — dua `System.gc()` dihapus (ART sudah GC sebelum melempar OOM), baris panjang dipecah; default `releaseVersion` → 1.5.6-android.4; CHANGELOG android.4.
Task 2 (4 file): jalur artefak kegagalan beridentitas. `scripts/ci/collect-failure.sh` (baru), `scripts/ci/lib.sh` (baru, `ci_fail`), `release.yml`, `android-ci.yml`.
- Nama artefak: `gagal-JadxDroid_<versi/tag atau batch>-<sha7>-run<N>[-a<percobaan>]`; isi: `gagal-<ID>.txt` (identitas, langkah gagal ditandai di tabel hasil langkah, penyebab mentah: Gradle "What went wrong", `e:`/`error:`, temuan detekt, pesan `::error::`), `log/`, `laporan-tool/`; retensi 14 hari.
- Ringkasan di halaman run (`$GITHUB_STEP_SUMMARY`) + tautan artefak + perintah Termux `gh run download`.
- `release.yml`: tiap langkah punya `id`, langkah Gradle di-`tee` ke `ci-logs/`, pesan gerbang lewat `ci_fail` (ke annotation dan `ci-logs/errors.log`), bundle dibuat hanya `if: failure()`. `android-ci.yml`: bundle bila assembleDebug/Release gagal (lint/detekt tetap lewat laporan `laporan-JadxDroid_…`).
- Diuji lokal memakai log gagal asli dari ZIP pengguna: kedua temuan detekt terekstrak, langkah gagal ditandai, nama memuat tag/versi + commit + run.
Belum teruji di runner: unggah artefak, tautan ringkasan; kompilasi Kotlin/Java v5–v7 di Gradle selain yang dilaporkan detekt.

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
