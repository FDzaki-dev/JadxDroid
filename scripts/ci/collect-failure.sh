#!/usr/bin/env bash
# Builds the failure bundle for a failed workflow run:
#   failure-bundle/gagal-<ID>.txt   identity + failed step + raw causes (the one file to share)
#   failure-bundle/log/             raw step logs written by the workflow (ci-logs/)
#   failure-bundle/laporan-tool/    detekt / lint reports when present
# and appends a readable summary to the run page ($GITHUB_STEP_SUMMARY).
# Inputs (env): FAILURE_KIND (release|ci), STEP_RESULTS ("step=outcome" per line), VERSION (optional), GITHUB_* defaults.
set -u

BUNDLE="failure-bundle"
PROJECT="JadxDroid"
SHA="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
SHORT_SHA="${SHA:0:7}"
RUN_NUMBER="${GITHUB_RUN_NUMBER:-local}"
ATTEMPT="${GITHUB_RUN_ATTEMPT:-1}"
KIND="${FAILURE_KIND:-ci}"

# Identity: release version/tag when known, otherwise the batch from PROJECT_STATE.md.
if [ -n "${VERSION:-}" ]; then
  REF="v${VERSION}"
elif [ "${GITHUB_REF_TYPE:-}" = "tag" ]; then
  REF="${GITHUB_REF_NAME}"
else
  REF="$(grep -m1 -oE 'Batch v[0-9]+' PROJECT_STATE.md 2>/dev/null | sed 's/Batch //')"
  REF="${REF:-vNA}"
fi
ID="${PROJECT}_${REF}-${SHORT_SHA}-run${RUN_NUMBER}"
if [ "$ATTEMPT" -gt 1 ] 2>/dev/null; then ID="${ID}-a${ATTEMPT}"; fi
NAME="gagal-${ID}"
REPORT="${BUNDLE}/${NAME}.txt"
WORKSPACE="${GITHUB_WORKSPACE:-$PWD}"

rm -rf "$BUNDLE"
mkdir -p "$BUNDLE/log" "$BUNDLE/laporan-tool"
cp ci-logs/* "$BUNDLE/log/" 2>/dev/null
for f in app/build/reports/detekt/detekt.txt app/build/reports/lint/lint-results.txt; do
  [ -s "$f" ] && cp "$f" "$BUNDLE/laporan-tool/" 2>/dev/null
done

clean() { sed "s#${WORKSPACE}/##g"; }

# First failed step from STEP_RESULTS.
FAILED_STEP="$(printf '%s\n' "${STEP_RESULTS:-}" | awk -F= '$2 == "failure" { print $1; exit }')"
FAILED_STEP="${FAILED_STEP:-tidak terdeteksi}"

# Raw causes from one log: Gradle "What went wrong", compiler errors, detekt findings, workflow ::error:: lines.
causes() {
  grep -E -A12 '^\* What went wrong' "$1" | head -n 40
  grep -E '^e: |error: |Unresolved reference|\.kt:[0-9]+:[0-9]+: .*\[[A-Za-z]+\]$|^::error::|^\[[a-z_-]+\] ' "$1" \
    | awk '!seen[$0]++' | head -n 60
}

{
  echo "LAPORAN KEGAGALAN — ${PROJECT}"
  echo "ID laporan : ${ID}"
  echo "Jenis      : ${KIND}"
  echo "Versi/tag  : ${REF}"
  echo "Commit     : ${SHA}  \"$(git log -1 --pretty=%s 2>/dev/null || echo '-')\""
  echo "Cabang/tag : ${GITHUB_REF_NAME:-lokal} (${GITHUB_EVENT_NAME:-lokal})"
  echo "Run        : #${RUN_NUMBER} percobaan ${ATTEMPT}  ${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"
  echo "Workflow   : ${GITHUB_WORKFLOW:-lokal}"
  echo "Waktu UTC  : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Langkah gagal: ${FAILED_STEP}"
  echo
  echo "== HASIL LANGKAH =="
  printf '%s\n' "${STEP_RESULTS:-}" | awk 'NF { mark = ($0 ~ /=failure$/) ? "   <== GAGAL" : ""; print "  " $0 mark }'
  echo
  echo "== PENYEBAB (mentah dari log) =="
  FOUND=0
  for log in ci-logs/errors.log ci-logs/*.log; do
    [ -s "$log" ] || continue
    case "$log" in ci-logs/errors.log) [ "$FOUND" -eq 0 ] || continue ;; esac
    OUT="$(causes "$log" | clean)"
    [ -n "$OUT" ] || continue
    FOUND=1
    echo "--- $(basename "$log")"
    printf '%s\n' "$OUT"
  done
  [ "$FOUND" -eq 1 ] || echo "NOT VERIFIED: tidak ada penyebab terekstrak; lihat log/ dan log run di GitHub."
  echo
  echo "== TEMUAN TOOL =="
  if [ -s app/build/reports/detekt/detekt.txt ]; then echo "--- detekt"; clean < app/build/reports/detekt/detekt.txt | head -n 80; fi
  if [ -s app/build/reports/lint/lint-results.txt ]; then
    echo "--- lint (hanya Error)"
    grep -E -A2 ': Error: ' app/build/reports/lint/lint-results.txt | clean | head -n 80
  fi
} > "$REPORT"

{
  echo "name=${NAME}"
  echo "file=${REPORT}"
} >> "${GITHUB_OUTPUT:-/dev/null}"

# Run-page summary: readable on a phone without downloading anything.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## Kegagalan: ${ID}"
    echo
    echo "- Jenis: ${KIND}, versi/tag: \`${REF}\`, commit: \`${SHORT_SHA}\`"
    echo "- Langkah gagal: **${FAILED_STEP}**"
    echo
    echo '```'
    sed -n '/== PENYEBAB/,/== TEMUAN TOOL/p' "$REPORT" | sed '1d;$d' | head -n 40
    echo '```'
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "Bundle kegagalan: ${BUNDLE}/ (${NAME}.txt, $(find "$BUNDLE" -type f | wc -l) berkas)"
