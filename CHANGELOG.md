# CHANGELOG

## v1 — 2026-10-24
- Port JADX 1.5.6 ke Android: modul `:jadx-engine` (sumber upstream + 7 override) dan `:app` (Compose + WorkManager).
- Ekspor hasil dekompilasi sebagai ZIP ke `Documents/JadxDroid`; log mentah ke `Documents/JadxDroid/logs`.
- CI GitHub Actions: lintDebug, detekt, build debug/release, laporan `laporan-untuk-claude.txt`.
- Status validasi: NOT VERIFIED (belum ada hasil CI).
