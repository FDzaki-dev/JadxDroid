# CHANGELOG

## 1.5.6-android.11 — 2026-10-28
- Aplikasi tidak lagi patah-patah saat dekompilasi: JADX kini berjalan di proses terpisah (`:jadx`), jadi GC dan beban CPU-nya tidak lagi berbagi proses dengan UI. Semua thread proses itu (termasuk thread GC runtime) berjalan di bawah prioritas UI. Proses itu hidup hanya selama satu dekompilasi dan berakhir saat selesai atau dibatalkan, sehingga heap kembali utuh. Bila proses itu tidak bisa dijalankan, dekompilasi otomatis berjalan di proses utama seperti sebelumnya.
- Lebih cepat: thread JADX naik dari maksimal 2 menjadi maksimal 3 pada heap 512 MB (maksimal 4 pada heap lebih besar). Paralelisme bersifat adaptif: saat heap hampir penuh (di atas 85%) dan ada kelas lain yang sedang dibuat, kelas berikutnya menunggu sebentar, jadi tidak ada dua working set besar sekaligus; saat memori lapang semua thread berjalan penuh. Pelepasan kelas dependensi baru dimulai di 70% heap (sebelumnya 60%), jadi lebih jarang memproses ulang.
- Log JADX ke logcat diturunkan ke ERROR (sebelumnya INFO): ribuan baris peringatan per APK besar memakan CPU dan alokasi.
- Workflow Release tidak lagi menunggu Android CI (penyebab utama waktu terlama, ±4,5 menit) dan tidak lagi menjalankan Gradle tiga kali: detekt, lint, dan build rilis bertanda tangan kini satu run Gradle. Gerbang tetap berlaku: detekt gagal, kompilasi gagal, atau NewApi ditemukan = tidak ada rilis.
- Status validasi: NOT VERIFIED (kompilasi Gradle, detekt, lint, dan uji perangkat menunggu CI dan uji pengguna).

## 1.5.6-android.10 — 2026-10-28
- Memperbaiki kegagalan menjelang akhir dekompilasi APK besar (`OutOfMemoryError`, lalu semua pass per-DEX ikut gagal). Sebab 1: JADX memuat dan memproses semua dependensi tiap kelas, dan dependensi yang dimuat ulang tidak pernah dilepas; kelas dengan dependensi terbanyak dijadwalkan paling akhir, jadi heap penuh di ujung proses. Kini kelas yang sudah selesai dibuat dan hanya dimuat ulang sebagai dependensi dilepas lagi saat heap melewati 60% (kelas yang sedang dipakai kelas lain tidak disentuh; heap lapang = perilaku sama seperti sebelumnya).
- Sebab 2: setelah `OutOfMemoryError`, thread worker JADX tidak berhenti dan tetap memegang kelas, sehingga mode per-DEX langsung kehabisan heap. Kini worker diinterupsi dan ditunggu sampai berhenti sebelum percobaan berikutnya.
- Urutan fallback baru: mode utuh, lalu mode utuh hemat memori (1 thread, kelas dilepas agresif), lalu per-DEX (juga hemat memori; pass yang kehabisan heap diulang 1 thread). Kegagalan mode sebelumnya dicatat mentah di `JADXDROID-NOTES.txt` dalam ZIP hasil, dan di log kegagalan bila semuanya gagal.
- Status validasi: NOT VERIFIED (kompilasi Gradle, detekt, lint, dan uji perangkat menunggu CI dan uji pengguna).

## 1.5.6-android.9 — 2026-10-28
- Dekompilasi tidak lagi membuat UI patah-patah: thread worker JADX kini berprioritas di bawah UI dan render (nice 9). Nilai ini sengaja tetap di atas ambang grup background (10), jadi worker tidak dibatasi ke core kecil dan kecepatan tidak turun saat UI diam.
- Lebih cepat dan hemat memori: JADX memakai code writer ringan tanpa metadata GUI, dan graf usage dibuang setelah diterapkan ke node (pengaturan yang sama dengan JADX CLI). Lebih sedikit objek dan GC saat APK besar.
- Pembaruan progres ke WorkManager dibatasi: hanya saat persen berubah dan maksimal 1 per detik (sebelumnya tiap 500 ms saat dekompilasi dan tiap 25 berkas saat ekspor, masing-masing satu tulis database + query ulang + recompose).
- Ekspor ZIP memakai kompresi tercepat (sumber teks, arsip sedikit lebih besar) dan buffer 256 KiB di atas Okio; salin input ke cache juga berbuffer besar.
- Status validasi: NOT VERIFIED (kompilasi, detekt, lint, dan efek di perangkat menunggu CI dan uji pengguna).

## 1.5.6-android.8 — 2026-10-27
- Perbaikan gerbang rilis: `v1.5.6-android.7` diblokir detekt (MatchingDeclarationName pada kelas `UpdateActions`); kelas dipindah ke file sendiri tanpa perubahan perilaku.
- Tab Update di aplikasi: saat dibuka, otomatis memeriksa rilis terbaru di GitHub dan menampilkan snapshot rilis (versi terpasang, versi terbaru, tanggal, ukuran, catatan rilis) selama memuat dan mengunduh.
- Unduhan APK memakai stream Okio berbuffer di WorkManager (tahan rotasi, bisa dibatalkan); SHA-256 dihitung saat menulis dan dicocokkan dengan SHA256SUMS.txt rilis sebelum dipasang.
- Pemasangan lewat PackageInstaller (APK ditulis ke sesi dengan Okio) dengan konfirmasi sistem; pembaruan hanya bisa menimpa APK yang bertanda tangan sama (rilis).
- Izin baru: INTERNET dan REQUEST_INSTALL_PACKAGES.

## 1.5.6-android.7 — tidak dirilis
- Tag `v1.5.6-android.7` diblokir gerbang detekt (1 temuan: `MatchingDeclarationName`); perubahannya dirilis sebagai 1.5.6-android.8.

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
