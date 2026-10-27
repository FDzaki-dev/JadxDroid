#!/usr/bin/env bash
# Builds the report for Claude (laporan-<ID>.txt) from lint + detekt output and build logs, and stages it
# in laporan-bundle/ (the one report artifact). On any failed step, raw logs (log/) and tool reports
# (laporan-tool/) are added to the bundle and the report is echoed to the run page.
# Rule: tool unavailable / failed / report absent  =>  NOT VERIFIED (never "pass").
set -u

PROJECT="JadxDroid"
SHA="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
SHORT_SHA="${SHA:0:7}"
RUN_NUMBER="${GITHUB_RUN_NUMBER:-local}"
# Batch comes from PROJECT_STATE.md ("Batch vN" in the resume point) so the report ties back to the ZIP name.
BATCH="$(grep -m1 -oE 'Batch v[0-9]+' PROJECT_STATE.md 2>/dev/null | head -n 1 | sed 's/Batch //')"
BATCH="${BATCH:-vNA}"
REPORT_ID="${PROJECT}_${BATCH}-${SHORT_SHA}-run${RUN_NUMBER}"
REPORT_NAME="laporan-${REPORT_ID}"
OUT="${REPORT_NAME}.txt"
rm -f laporan-*.txt
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
  echo "LAPORAN CI — ${PROJECT}"
  echo "ID laporan : ${REPORT_ID}"
  echo "Proyek     : ${PROJECT} (${GITHUB_REPOSITORY:-lokal})"
  echo "Batch      : ${BATCH} (ZIP ${PROJECT}_${BATCH}.zip)"
  echo "Commit     : ${SHA}  \"$(git log -1 --pretty=%s 2>/dev/null || echo '-')\""
  echo "Cabang     : ${GITHUB_REF_NAME:-lokal} (${GITHUB_EVENT_NAME:-lokal})"
  echo "Run        : #${RUN_NUMBER} percobaan ${GITHUB_RUN_ATTEMPT:-1}  ${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
  echo "Workflow   : ${GITHUB_WORKFLOW:-lokal}"
  echo "Waktu UTC  : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo
  section_build "assembleDebug" "${DEBUG_OUTCOME:-}" "debug"
  echo
  section_build "assembleRelease" "${RELEASE_OUTCOME:-}" "release"
  echo
  section_lint
  echo
  section_detekt
} > "$OUT"

BUNDLE="laporan-bundle"
rm -rf "$BUNDLE"
mkdir -p "$BUNDLE"
cp "$OUT" "$BUNDLE/"
FAILED=0
for outcome in "${DEBUG_OUTCOME:-}" "${RELEASE_OUTCOME:-}" "${LINT_OUTCOME:-}" "${DETEKT_OUTCOME:-}"; do
  if [ "$outcome" = "failure" ]; then FAILED=1; fi
done
if [ "$FAILED" -eq 1 ]; then
  mkdir -p "$BUNDLE/log" "$BUNDLE/laporan-tool"
  cp ci-logs/* "$BUNDLE/log/" 2>/dev/null
  for f in "$DETEKT_REPORT" "$LINT_REPORT"; do
    if [ -s "$f" ]; then cp "$f" "$BUNDLE/laporan-tool/"; fi
  done
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    { echo "## Laporan: ${REPORT_ID}"; echo; echo '```'; head -n 60 "$OUT"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
  fi
fi

{
  echo "report_name=${REPORT_NAME}"
  echo "report_file=${OUT}"
} >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Laporan ditulis: $OUT ($(wc -l < "$OUT") baris)"
