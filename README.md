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

Status saat ini dan langkah berikutnya: lihat `PROJECT_STATE.md`.

Lisensi: Apache-2.0 (JADX upstream, lihat `third_party/jadx/LICENSE` dan `NOTICE`).
