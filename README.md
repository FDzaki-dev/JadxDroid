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
- `scripts/ci/` — pembuat `laporan-untuk-claude.txt`.

## APK besar
Heap Java aplikasi dibatasi sistem (±512 MB di banyak perangkat). DEX dipetakan ke memori native; untuk APK yang DEX-nya sangat besar atau setelah `OutOfMemoryError`, aplikasi otomatis memakai mode per-DEX (satu `classesN.dex` per pass). Hasilnya tetap berupa satu ZIP, dengan catatan mode di `JADXDROID-NOTES.txt`.

## Rilis
Versi memakai pola `X.Y.Z-android.N` (X.Y.Z = versi JADX upstream, N = urutan rilis Android).
1. Tambahkan bagian `## X.Y.Z-android.N — tanggal` di paling atas `CHANGELOG.md` dan samakan default `releaseVersion` di `app/build.gradle.kts`.
2. Jalankan perintah "UPDATE + RILIS" dari `PROJECT_STATE.md`: push ke `main`, lalu tag dikirim otomatis dari CHANGELOG.
3. Workflow *Release* menunggu Android CI hijau, menjalankan detekt + lint (NewApi), membangun APK bertanda tangan, memverifikasi tanda tangan dan versi, lalu menerbitkan APK, `SHA256SUMS.txt`, dan file mapping di GitHub Releases.
Bila gagal, jangan pakai ulang tag: perbaiki dan naikkan N. Uji coba tanpa publikasi: Actions → Release → Run workflow. Prasyarat: 4 secret dari Box B (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

Status saat ini dan langkah berikutnya: lihat `PROJECT_STATE.md`.

Lisensi: Apache-2.0 (JADX upstream, lihat `third_party/jadx/LICENSE` dan `NOTICE`).
