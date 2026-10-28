# JadxDroid

Dekompiler APK / DEX / JAR berbasis [JADX](https://github.com/skylot/jadx) 1.5.6 yang berjalan langsung di perangkat Android 12+ (API 31+).

## Cara pakai
1. Buka aplikasi, pilih file `.apk` / `.dex` / `.jar` / `.class`.
2. Proses berjalan sebagai foreground work (tahan rotasi dan app ke background).
3. Hasil: `Documents/JadxDroid/<nama>_<waktu>.zip`. Log kegagalan mentah: `Documents/JadxDroid/logs`.

## Struktur
- `app/` — UI Compose, WorkManager, ekspor ZIP, crash logger.
- `jadx-engine/` — sumber JADX upstream di-compile sebagai library Android (`src/overrides/java` = file yang diganti).
- `third_party/jadx/` — JADX 1.5.6 asli, tidak diubah.
- `scripts/ci/` — pembuat laporan untuk Claude (`laporan-<ID>.txt`).

## APK besar
Heap Java aplikasi dibatasi sistem (±512 MB di banyak perangkat). DEX dipetakan ke memori native; untuk APK yang DEX-nya sangat besar atau setelah `OutOfMemoryError`, aplikasi otomatis memakai mode per-DEX (satu `classesN.dex` per pass). Hasilnya tetap berupa satu ZIP, dengan catatan mode di `JADXDROID-NOTES.txt`.

## Performa saat dekompilasi
- Thread worker JADX berjalan di prioritas nice 9: UI tetap lancar, tetapi worker masih boleh memakai semua core.
- JADX memakai `SimpleCodeWriter` dan `EmptyUsageInfoCache` (sama seperti JADX CLI): tanpa metadata GUI dan tanpa graf usage yang menetap di heap.
- Progres ke WorkManager maksimal 1 tulis per detik (hanya saat persen berubah).
- Kontrol memori (`jadx.android.MemoryGuard`): saat heap melewati 60%, kelas yang sudah selesai dibuat dan dimuat ulang hanya sebagai dependensi dilepas lagi. Bila `OutOfMemoryError` tetap terjadi, worker dihentikan total, lalu diulang mode hemat memori (1 thread), lalu per-DEX.
- Ekspor ZIP memakai kompresi tercepat dan buffer 256 KiB; untuk menukar ukuran arsip dengan kecepatan, ubah level di `Storage.exportZip`.

## Rilis
Versi memakai pola `X.Y.Z-android.N` (X.Y.Z = versi JADX upstream, N = urutan rilis Android).
1. Tambahkan bagian `## X.Y.Z-android.N — tanggal` di paling atas `CHANGELOG.md` dan samakan default `releaseVersion` di `app/build.gradle.kts`.
2. Jalankan perintah "UPDATE + RILIS" dari `PROJECT_STATE.md`: push ke `main`, lalu tag dikirim otomatis dari CHANGELOG.
3. Workflow *Release* menunggu Android CI hijau, menjalankan detekt + lint (NewApi), membangun APK bertanda tangan, memverifikasi tanda tangan dan versi, lalu menerbitkan APK, `SHA256SUMS.txt`, dan file mapping di GitHub Releases.
Bila gagal, jangan pakai ulang tag: perbaiki dan naikkan N. Uji coba tanpa publikasi: Actions → Release → Run workflow. Prasyarat: 4 secret dari Box B (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

## Tab Update
Tab Update memeriksa rilis terbaru repo ini lewat GitHub Releases saat dibuka. Selama memuat dan mengunduh, kartu snapshot rilis menampilkan versi terpasang, versi terbaru, tanggal, ukuran, dan catatan rilis. APK diunduh lewat stream Okio berbuffer di WorkManager, SHA-256 dihitung saat menulis lalu dicocokkan dengan `SHA256SUMS.txt`, dan dipasang lewat PackageInstaller dengan konfirmasi sistem. Hanya APK rilis bertanda tangan sama yang bisa menimpa; build debug dari artifact CI akan ditolak sistem dengan pesan mentah. Kode ada di `update/` (data) dan `ui/UpdateScreen.kt` (tampilan).

## Artifact GitHub Actions
GitHub Release dan GitHub Artifact itu terpisah. Release (halaman rilis dari tag) berisi APK bertanda tangan, `SHA256SUMS.txt`, dan file mapping. Artifact (hasil run Actions) hanya 2 jalur:
1. `apk-JadxDroid-<sha7>-run<N>` — APK debug + release (7 hari), dibuat workflow *Android APK* (`android-apk.yml`) tanpa lint, detekt, atau laporan, jadi tidak menunggu keduanya. Dry-run Release memakai `apk-JadxDroid-<versi>-dryrun-run<N>`.
2. `laporan-JadxDroid_<versi-atau-batch>-<sha7>-run<N>[-a<percobaan>]` — laporan untuk Claude (7 hari), dibuat workflow *Android CI* (`android-ci.yml`: lint, detekt, build, laporan). `laporan-<ID>.txt` selalu ada; bila ada langkah gagal, ikut `log/` (log tiap langkah) dan `laporan-tool/` (detekt, lint). Ringkasan yang sama tampil di halaman run (Summary); untuk run Release yang gagal juga ada perintah Termux `gh run download`. Berkas yang dibagikan untuk analisis cukup `laporan-<ID>.txt`.
Kedua workflow berjalan paralel pada setiap push ke `main`. Nomor `run<N>` dihitung per workflow, jadi pasangan APK dan laporan dikenali dari `<sha7>`. Rilis yang sukses tidak membuat artifact.

Status saat ini dan langkah berikutnya: lihat `PROJECT_STATE.md`.

Lisensi: Apache-2.0 (JADX upstream, lihat `third_party/jadx/LICENSE` dan `NOTICE`).
