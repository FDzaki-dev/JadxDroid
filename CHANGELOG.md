# CHANGELOG

## 1.5.6-android.6 — 2026-10-27
- Perbaikan gerbang rilis: `v1.5.6-android.5` diblokir detekt pada `DexSplitter.kt` (kedalaman blok `extractResourcesArchive` dan baris KDoc melebihi 120 kolom). Fungsi dipecah dan komentar diringkas; perilaku aplikasi tidak berubah.
- Memperbaiki `OutOfMemoryError` saat membuka APK besar (dilaporkan pada APK ±100 MB dengan banyak DEX): data DEX kini dipetakan ke memori native (di luar heap Java 512 MB) dan hanya entri DEX yang dibaca, bukan seluruh isi APK.
- Mode per-DEX otomatis untuk APK raksasa atau setelah `OutOfMemoryError`: setiap `classesN.dex` didekompilasi terpisah (referensi lintas-DEX tidak terselesaikan penuh); `JADXDROID-NOTES.txt` di dalam ZIP hasil mencatat mode dan hasil tiap pass.
- Jumlah thread JADX disesuaikan dengan heap perangkat.

## 1.5.6-android.5 — tidak dirilis
- Tag `v1.5.6-android.5` diblokir gerbang detekt (2 temuan di `DexSplitter.kt`: `NestedBlockDepth` dan `MaxLineLength`); perubahannya dirilis sebagai 1.5.6-android.6.

## 1.5.6-android.4 — tidak dirilis
- Tag `v1.5.6-android.4` diblokir gerbang rilis: kompilasi Kotlin gagal (komentar KDoc di `DexSplitter.kt` memuat `res/**`; Kotlin menyarangkan komentar sehingga `/**` di dalam komentar tidak tertutup). Perubahannya dirilis sebagai 1.5.6-android.5.

## 1.5.6-android.3 — tidak dirilis
- Tag `v1.5.6-android.3` diblokir gerbang detekt (2 temuan: pemanggilan GC eksplisit dan baris terlalu panjang); perubahannya dirilis sebagai 1.5.6-android.4.

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
