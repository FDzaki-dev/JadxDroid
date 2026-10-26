#!/usr/bin/env bash
# Builds laporan-untuk-claude.txt from lint + detekt output and build logs.
# Rule: tool unavailable / failed / report absent  =>  NOT VERIFIED (never "pass").
set -u

OUT="laporan-untuk-claude.txt"
LINT_REPORT="app/build/reports/lint/lint-results.txt"
DETEKT_REPORT="app/build/reports/detekt/detekt.txt"
MAX_LINES=400

# Low-signal lint ids: dependency/version nagging and cosmetic icon checks. Real bugs (NewApi, etc.) stay.
LINT_NOISE='^(GradleDependency|NewerVersionAvailable|AndroidGradlePluginVersion|ObsoleteSdkInt|OldTargetApi|IconLauncherShape|MonochromeLauncherIcon|IconDensities|IconDuplicates|UnusedResources|Typos)$'

section_lint() {
  echo "== ANDROID LINT (lintDebug) outcome=${LINT_OUTCOME:-unknown} =="
  if [ ! -s "$LINT_REPORT" ]; then
    echo "NOT VERIFIED: laporan lint tidak ada ($LINT_REPORT)."
    return
  fi
  awk -v noise="$LINT_NOISE" '
    /^[0-9]+ errors?, [0-9]+ warnings?/ { keep = 0; next }
    /^[^ ].*: (Error|Warning|Information): / {
      keep = ($0 ~ /: (Error|Warning): /)
      if (keep && match($0, /\[[A-Za-z0-9_]+\]$/)) {
        id = substr($0, RSTART + 1, RLENGTH - 2)
        if (id ~ noise) keep = 0
      }
      if (keep && $0 ~ /: Warning: / && $0 ~ /jadx-engine\/build\/generated\//) { keep = 0; engine_dropped++ }
      if (keep) count++
    }
    keep { print }
    END { printf "\nTEMUAN LINT (error+warning, noise disaring): %d | warning upstream engine disaring: %d (error engine tetap ditampilkan)\n", count, engine_dropped }
  ' "$LINT_REPORT" | sed "s#$PWD/##g" | head -n "$MAX_LINES"
}

section_detekt() {
  echo "== DETEKT outcome=${DETEKT_OUTCOME:-unknown} =="
  if [ ! -f "$DETEKT_REPORT" ]; then
    echo "NOT VERIFIED: laporan detekt tidak ada ($DETEKT_REPORT)."
    return
  fi
  if [ ! -s "$DETEKT_REPORT" ]; then
    echo "Detekt: 0 temuan."
    return
  fi
  sed "s#$PWD/##g" "$DETEKT_REPORT" | head -n "$MAX_LINES"
}

section_build() {
  name="$1"; outcome="$2"; log="ci-logs/$3.log"
  echo "== BUILD $name outcome=${outcome:-unknown} =="
  if [ "${outcome:-}" = "success" ]; then
    echo "OK"
    return
  fi
  if [ ! -s "$log" ]; then
    echo "NOT VERIFIED: log build tidak ada ($log)."
    return
  fi
  # Raw compiler / Gradle errors only, no commentary.
  grep -E -A12 '^\* What went wrong' "$log" | head -n 60
  grep -E '^e: |error: |Unresolved reference|Execution failed for task' "$log" | sort -u | head -n 60
}

{
  echo "LAPORAN CI $(date -u +%Y-%m-%dT%H:%M:%SZ) commit=${GITHUB_SHA:-local}"
  echo
  section_build "assembleDebug" "${DEBUG_OUTCOME:-}" "debug"
  echo
  section_build "assembleRelease" "${RELEASE_OUTCOME:-}" "release"
  echo
  section_lint
  echo
  section_detekt
} > "$OUT"

echo "Laporan ditulis: $OUT ($(wc -l < "$OUT") baris)"
