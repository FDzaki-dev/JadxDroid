# CHANGELOG

## 1.5.6-android.2 — 2026-10-24
- Perbaikan crash Android 12/12L/13 dari laporan lint CI: `SaveCode` (simpan setiap class), `ZipDeflate` (baca isi APK), `ResourcesLoader`, `Utils`, `ResourceFile`, dan `MergePathActivePathTraverserHandler` tidak lagi memanggil API 33/35.
- Jalur rilis GitHub: tag `vX.Y.Z-android.N` memicu build APK bertanda tangan, verifikasi tanda tangan dan versi, checksum SHA-256, file mapping R8, atestasi provenance, lalu GitHub Release dengan catatan dari CHANGELOG ini.
- Versi aplikasi kini berasal dari tag (`-PreleaseVersion`), `versionCode` dihitung deterministik.
- Aplikasi: izin notifikasi hanya diminta di Android 13+; aturan ekstraksi data (backup/transfer) ditambahkan.
- Laporan CI menyaring peringatan upstream pada engine (error engine tetap tampil).
- Status validasi: build debug + release sukses dan detekt 0 temuan pada CI v1; hasil CI v2 menunggu.

## 1.5.6-android.1 — 2026-10-24
- Port JADX 1.5.6 ke Android: modul `:jadx-engine` (sumber upstream + 7 override) dan `:app` (Compose + WorkManager).
- Ekspor hasil dekompilasi sebagai ZIP ke `Documents/JadxDroid`; log mentah ke `Documents/JadxDroid/logs`.
- CI GitHub Actions: lintDebug, detekt, build debug/release, laporan `laporan-untuk-claude.txt`.
- Status validasi: NOT VERIFIED (belum ada hasil CI).
